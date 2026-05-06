<p align="center">
  <img src="media/jros2.png" width="30%" /><br>
  <a href="https://github.com/ihmcrobotics/jros2/wiki">Wiki</a>
  <a href="https://github.com/ihmcrobotics/jros2/issues">Issues</a>
</p>

-----------------

A ROS 2 library for Java. Uses Fast-DDS middleware. Fully compatible with other supported ROS 2 DDS middlewares.

Fast-DDS version: `3.2.2`

ROS 2 compatible and tested distros: `[humble, jazzy, kilted]`

Supported platforms:
- Linux (Ubuntu 20.04+ or similar x86_64, arm64, armhf)
- Windows (Windows 10+ x86_64)
- macOS (macOS 13+ Intel, Apple Silicon)
- Android (Android 12+ x86_64, arm64-v8a)

Works on NVIDIA Jetson and all versions of Raspberry Pi!

## Features
- Fully compatible with ROS 2 humble or newer (may also work with older ROS 2 distros)
- Does not require a ROS 2 installation on the system
- Ready-to-use Java library, just add to your Maven or Gradle dependencies!
- Publish and subscribe to ROS 2 topics
- Supports custom message types
- Generate Java classes from ROS 2 .msg files
- Fast-DDS backend
- Minimal and fast implementation
- Fully thread-safe
- Async and allocation-free API
- (soon) ROS 2 services
- (soon) ROS 2 actions
- (soon) ROS 2 parameters

## Usage
Read in-depth documentation on the [Wiki](https://github.com/ihmcrobotics/jros2/wiki)!

### Gradle
```
dependencies {
  implementation("us.ihmc:jros2:1.1.6")
}
```
### Maven
```
<dependencies>
  <dependency>
    <groupId>us.ihmc</groupId>
    <artifactId>jros2</artifactId>
    <version>1.1.6</version>
  </dependency>
</dependencies>
```

### Here's the basics:

Create a ROS2Node:
```
int domainId = 100;
ROS2Node node = new ROS2Node("my_node", domainId); // domainId is 0 by default
// Call node.close() when you're done with the node!
```

Create a ROS2Topic:
```
ROS2Topic<Int32> intTopic = new ROS2Topic("/int_topic", Int32.class);
```

Create a ROS2Publisher and publish a message on the intTopic:
```
ROS2Publisher<Int32> intPublisher = node.createPublisher(intTopic);
Int32 message = new Int32();
message.setData(123);
intPublisher.publish(message);
```

Create a subscription to the intTopic:
```
ROS2Subscription<Int32> subscription = node.createSubscription(intTopic, reader -> {
  Int32 message = reader.read();
  // Do something with the message!
};
```

### Talker and listener example
Run a talker and listener example with `./run_talker_listener.sh`. If you have a local ROS 2 installation or use a ROS 2 container, you
can verify that ROS 2 is able to communicate with jros2.

```
[New shell]
jros2$ ./run_talker_listener.sh
> Task :examples:ros2-ros2-talker-listener:run
Publishing: 'Hello world: 0'
Publishing: 'Hello world: 1'
I heard: 'Hello world: 1'
Publishing: 'Hello world: 2'
I heard: 'Hello world: 2'
[...]
```
```
[New shell]
jros2$ source /opt/ros/humble/setup.bash 
jros2$ ros2 topic echo /chatter
data: 'Hello world: 1'
---
data: 'Hello world: 2'
---
[...]
```

## Compiling from Source (Android / ARM64)

If you need to make changes to the native middleware (Fast-DDS) or the JavaCPP wrapper bindings, you must recompile the C++ libraries before building the Java project.

### 1. C++ / Native Build

The native cross-compilation pipeline is managed via bash scripts that download the C++ dependencies and use the Android NDK to compile them.

**Prerequisites:**
- Android NDK (e.g., `30.0.14904198`) installed via Android Studio.
- CMake installed via Android SDK.
- Git Bash (or a similar Unix shell on Windows).

Run the build script from the repository root:
```bash
./run_build.bash
```
*Note: This script invokes `build-android-arm64.bash` which wipes the `cppbuild` directory, runs `cppbuild.bash` to compile Fast-CDR and Fast-DDS for `arm64-v8a`, and generates the JNI bindings (`libjnifastddsjava.so`). Finally, it publishes the Java AAR.*

### 2. Java / Gradle Build

If you only made changes to the Java source code (and not the native layer), you can skip the C++ compilation and directly build the Java libraries.

To publish the desktop JAR to Maven Local:
```bash
./gradlew publishToMavenLocal
```

To publish the Android AAR (which packages the `.so` JNI libraries):
```bash
cd android
../gradlew publishReleasePublicationToMavenLocal
```
