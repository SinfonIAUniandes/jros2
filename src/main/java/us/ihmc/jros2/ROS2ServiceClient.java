/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A ROS 2-compatible service client for sending service requests and receiving responses.
 * <p>
 * Service clients implement the request-reply pattern using two DDS topics:
 * <ul>
 *    <li>Request topic: {@code rq/<serviceName>Request}</li>
 *    <li>Response topic: {@code rr/<serviceName>Reply}</li>
 * </ul>
 * <p>
 * Thread-safe. Use {@link ROS2Node#createServiceClient} to create instances.
 *
 * @param <Request>  The service request message type
 * @param <Response> The service response message type
 */
public class ROS2ServiceClient<Request extends ROS2Message<Request>, Response extends ROS2Message<Response>> implements Closeable
{
   /**
    * Default timeout for async requests in milliseconds.
    */
   private static final long DEFAULT_ASYNC_TIMEOUT_MS = 5000;

   /*
    * Service identification
    */
   private final String serviceName;

   /*
    * ROS 2 primitives
    */
   private final ROS2Publisher<Request> requestPublisher;
   private final ROS2Subscription<Response> responseSubscription;

   /*
    * Threading
    */
   private final ExecutorService executorService;

   /*
    * Locks
    */
   private final ReadWriteLock closeLock;
   private boolean closed;

   /**
    * Package-private constructor. Use {@link ROS2Node#createServiceClient} to create instances.
    *
    * @param node          The ROS 2 node managing this service client
    * @param serviceName   The name of the service to call
    * @param requestTopic  The topic for publishing service requests
    * @param responseTopic The topic for receiving service responses
    * @param qosProfile    The quality-of-service profile for the service
    */
   ROS2ServiceClient(ROS2Node node, String serviceName, ROS2Topic<Request> requestTopic, ROS2Topic<Response> responseTopic, ROS2QoSProfile qosProfile)
   {
      this.serviceName = serviceName;
      this.requestPublisher = node.createPublisher(requestTopic, qosProfile, "rq");
      this.responseSubscription = node.createSubscription(responseTopic, null, qosProfile, "rr");
      this.executorService = Executors.newCachedThreadPool(r ->
      {
         Thread t = new Thread(r, "ROS2ServiceClient-" + serviceName);
         t.setDaemon(true);
         return t;
      });
      this.closeLock = new ReentrantReadWriteLock(true);
      this.closed = false;
   }

   /**
    * Send a service request synchronously and wait for a response.
    * <p>
    * This method blocks until a response is received or the timeout expires.
    *
    * @param request   The service request message
    * @param timeoutMs Timeout in milliseconds to wait for a response
    * @return The service response, or null if timeout occurred or an error occurred
    */
   public Response sendRequestSync(Request request, long timeoutMs)
   {
      CompletableFuture<Response> future = sendRequestAsync(request);
      try
      {
         return future.get(timeoutMs, TimeUnit.MILLISECONDS);
      }
      catch (Exception e)
      {
         return null;
      }
   }

   /**
    * Send a service request asynchronously.
    * <p>
    * This method returns immediately with a {@link CompletableFuture} that will be completed
    * when a response is received. The response will be waited for up to {@link #DEFAULT_ASYNC_TIMEOUT_MS}.
    *
    * @param request The service request message
    * @return A {@link CompletableFuture} that will contain the response when available
    */
   public CompletableFuture<Response> sendRequestAsync(Request request)
   {
      return sendRequestAsync(request, DEFAULT_ASYNC_TIMEOUT_MS);
   }

   /**
    * Send a service request asynchronously with a custom timeout.
    * <p>
    * This method returns immediately with a {@link CompletableFuture} that will be completed
    * when a response is received or the timeout expires.
    *
    * @param request   The service request message
    * @param timeoutMs Maximum time in milliseconds to wait for a response
    * @return A {@link CompletableFuture} that will contain the response when available, or null on timeout
    */
   public CompletableFuture<Response> sendRequestAsync(Request request, long timeoutMs)
   {
      CompletableFuture<Response> future = new CompletableFuture<>();

      closeLock.readLock().lock();
      try
      {
         if (closed)
         {
            future.complete(null);
            return future;
         }

         // Publish the request
         requestPublisher.publish(request);
      }
      finally
      {
         closeLock.readLock().unlock();
      }

      // Wait for response in background
      executorService.submit(() ->
      {
         try
         {
            long startTime = System.nanoTime();
            long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            Response response = null;

            while (System.nanoTime() - startTime < timeoutNanos && !closed)
            {
               response = responseSubscription.read();
               if (response != null)
               {
                  break;
               }
               Thread.sleep(1);
            }

            future.complete(response);
         }
         catch (Exception e)
         {
            future.completeExceptionally(e);
         }
      });

      return future;
   }

   /**
    * Get the name of the service this client calls.
    *
    * @return The service name
    */
   public String getServiceName()
   {
      return serviceName;
   }

   /**
    * Close this service client and release resources.
    * <p>
    * For internal use only. This is called by {@link ROS2Node#destroyServiceClient}.
    *
    * @param fastddsParticipant The Fast-DDS participant pointer (for cleanup)
    */
   void close(Pointer fastddsParticipant)
   {
      closeLock.writeLock().lock();
      try
      {
         if (!closed)
         {
            closed = true;
            executorService.shutdownNow();
            try
            {
               if (!executorService.awaitTermination(1, TimeUnit.SECONDS))
               {
                  jros2.logError("ExecutorService did not terminate in time for service client: " + serviceName, null);
               }
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt();
            }
            requestPublisher.close(fastddsParticipant);
            responseSubscription.close(fastddsParticipant);
         }
      }
      finally
      {
         closeLock.writeLock().unlock();
      }
   }

   /**
    * Do not call directly. Use {@link ROS2Node#destroyServiceClient} instead.
    *
    * @throws UnsupportedOperationException always
    */
   @Override
   public void close()
   {
      throw new UnsupportedOperationException("Use ROS2Node.destroyServiceClient() instead");
   }
}
