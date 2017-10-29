# ProtoBuffer TCP RSA Provider

This project implements a tiny RPC protocol over a TCP connection using
[Protobuffer](https://developers.google.com/protocol-buffers/) as a serialization mechanism.

It also contains a implementation of OSGi RSA (Remote Service Admin) 
`DistributionProvider`, which allows any OSGi service to be exported as a remote service
via the [Apache Aries RSA](http://aries.apache.org/modules/rsa.html) implementation.

## RPC Protocol

The RPC protocol is as simple as possible and consists of the single 
[rpc.proto](protobuf-tcp-rsa-provider/src/main/proto/rpc.proto) file:

```proto
syntax = "proto3";

package com.athaydes.osgi.rsa.provider.protobuf.api;

import "google/protobuf/any.proto";

option java_package = "com.athaydes.osgi.rsa.provider.protobuf.api";
option java_outer_classname = "Api";

message MethodInvocation {
    string methodName = 1;
    repeated google.protobuf.Any args = 2;
}

message Exception {
    string type = 1;
    string message = 5;
}

message Result {
    oneof result {
        google.protobuf.Any successResult = 1;
        Exception exception = 2;
    }
}
```

This protocol can represent any Java method invocation and can be used from any language
that supports Protobuffers.

## Java Server/Client example

The following example shows how to send a method invocation from a Java client:

```java
// This code can be run with JGrab: https://github.com/renatoathaydes/jgrab
// #jgrab com.athaydes.osgi:protobuf-tcp-rsa-provider:1.0-SNAPSHOT
import com.athaydes.osgi.rsa.provider.protobuf.api.Api;
import com.athaydes.osgi.rsa.provider.protobuf.ProtobufServer;
import com.google.protobuf.Any;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.net.Socket;


public class Example {
    public static void main(String[] args) throws Exception {
        // create and start a server first
        ProtobufServer server = new ProtobufServer(5562, new HelloService());
        Thread serverThread = new Thread(server);
        serverThread.start();
        Thread.sleep(500L); // let the server get up
        
        // here, the client code starts
        try (Socket socket = new Socket("127.0.0.1", 5562)) {
            System.out.println("Successfully connected to server, sending message");
            
            // send invocation
            Api.MethodInvocation.newBuilder()
                        .setMethodName("sayHello")
                        .addArgs(Any.pack(StringValue.newBuilder().setValue("The Client!").build()))
                        .build()
                        .writeDelimitedTo(socket.getOutputStream());
            
            // receive response
            System.out.println("Response:\n" + Api.Result.parseDelimitedFrom(socket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            serverThread.interrupt();
            server.close();
        }
    }
    
    // a simple service to run on the server
    public static class HelloService {
        public StringValue sayHello(StringValue message) {
            return StringValue.newBuilder()
                    .setValue("Hello " + message.getValue())
                    .build();
        }
    }

}
```

## Groovy client example

There is a sample [Groovy script client](protobuf-tcp-rsa-provider/src/scripts/groovy/test-client.groovy) which
shows how to create a simple CLI that communicates with a remote service.

The script was written to call the toy service provided by the `ProtobufServer` `main` method.

To run `ProtobufServer.java`, copy this [build.gradle](protobuf-tcp-rsa-provider/src/scripts/groovy/build.gradle) 
file to some directory, then run the following command from the same directory:

```
gradle run
```

Now, from another shell, run the [test-client.groovy](protobuf-tcp-rsa-provider/src/scripts/groovy/test-client.groovy)
script:

```
> groovy test-client.groovy
Groovy Protobuffer TCP-RSA-Provider Client

Enter "exit" to quit.

Please enter the port number of your server: 5562

Method to call remotely: sayHello joe

Sending invocation: methodName: "sayHello"
args {
  type_url: "type.googleapis.com/google.protobuf.StringValue"
  value: "\n\003joe"
}

successResult {
  type_url: "type.googleapis.com/google.protobuf.StringValue"
  value: "\n\tHello joe"
}
```

## Creating a Java client service

To create a local Java service which is backed by a remote service implementation, you can do something like this:

```java
static final int PORT = 5556;

ProtobufInvocationHandler handler = new ProtobufInvocationHandler(URI.create("tcp://127.0.0.1:" + PORT));

// wrap handler into a Proxy
Service proxyService = (Service) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(),
        new Class[]{Service.class}, handler);
```

## OSGi usage

To use this bundle in OSGi, you'll need to install the [Aries RSA](http://aries.apache.org/modules/rsa.html) runtime
bundles.

There is an [Aries RSA Example](https://github.com/renatoathaydes/aries-rsa-example) project that uses
Gradle and the [osgi-run](https://github.com/renatoathaydes/osgi-run) plugin to create a runtime.

The OSGi runtime declarations look like this:

```groovy
dependencies {
    // Aries RSA bundles
    final ariesRsaVersion = '1.11.0'
    osgiRuntime "org.apache.aries.rsa:org.apache.aries.rsa.core:$ariesRsaVersion"
    osgiRuntime "org.apache.aries.rsa:org.apache.aries.rsa.spi:$ariesRsaVersion"

    // set topology-manager's startLevel to 6 to make sure it starts AFTER the RSA Core,
    // otherwise it will not work
    osgiRuntime osgi("org.apache.aries.rsa:org.apache.aries.rsa.topology-manager:$ariesRsaVersion:6".toString())

    osgiRuntime "org.apache.aries.rsa.discovery:org.apache.aries.rsa.discovery.local:$ariesRsaVersion"
    osgiRuntime "com.athaydes.osgi:protobuf-tcp-rsa-provider:1.0-SNAPSHOT"

    // Logging
    osgiRuntime 'org.apache.felix:org.apache.felix.log:1.0.1', {
        transitive = false
    }
    osgiRuntime 'com.athaydes.osgiaas:slf4j-to-osgi-log:1.7.0'
}
```

### Exporting a OSGi service remotely

To export a service remotely, you just need to declare one service property:

```
service.exported.interfaces=*
```

You can also set the port to which the service is bound by declaring the following property
(in this case, setting the port to `5561`):

```
com.athaydes.protobuf.port=5561
```

Hence, using Declarative Services annotations, you would annotate your service with the following:

```java
@Component(immediate = true, property = {
        "service.exported.interfaces=*",
        "com.athaydes.protobuf.port=5561"
})
```

### Importing a remote OSGi service

To import a remote OSGi service, you must declare at least the following property:

```xml
<property name="service.imported.configs">com.athaydes.protobuf</property>
```

If you are using [Local Discovery](https://github.com/apache/aries-rsa/tree/master/discovery/local),
you can create a XML descriptor like the following in order to import a remote service:

```xml
<endpoint-descriptions xmlns="http://www.osgi.org/xmlns/rsa/v1.0.0">
    <endpoint-description>
        <property name="objectClass">
            <array>
                <value>com.athaydes.osgi.api.MessageService</value>
            </array>
        </property>
        <property name="endpoint.id">tcp://127.0.0.1:5561</property>
        <property name="service.imported.configs">com.athaydes.protobuf</property>
    </endpoint-description>
</endpoint-descriptions>
```

See the [XML Descriptor](https://github.com/renatoathaydes/aries-rsa-example/blob/master/osgi-client/src/main/resources/OSGI-INF/remote-service/server.xml)
in the [Aries RSA Example](https://github.com/renatoathaydes/aries-rsa-example) for details.
