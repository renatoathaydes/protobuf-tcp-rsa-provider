# ProtoBuffer TCP RSA Provider

This project implements a tiny RPC protocol over TCP connections using
[Protobuffer](https://developers.google.com/protocol-buffers/) as a serialization mechanism.

It also contains a implementation of OSGi RSA (Remote Service Admin) 
`DistributionProvider`, which allows any OSGi service to be exported as a remote service
via the [Apache Aries RSA](http://aries.apache.org/modules/rsa.html) implementation.

## RPC Protocol

The RPC protocol is as simple as it can be and consists of the single 
[rpc.proto](protobuf-tcp-rsa-provider/src/main/proto/rpc.proto) file:

```proto
syntax = "proto3";

package com.athaydes.protobuf.tcp.api;

import "google/protobuf/any.proto";

option java_package = "com.athaydes.protobuf.tcp.api";
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

This protocol can represent any method invocation and can be used from any language
that supports Protobuffers.

## Java Server/Client example

The following example shows how to start a remote service server and client in Java:

```java
// This code can be run with JGrab: https://github.com/renatoathaydes/jgrab
// #jgrab com.athaydes.osgi:protobuf-tcp-rsa-provider:0.1.0
// #jgrab org.slf4j:slf4j-simple:1.7.25

import com.athaydes.protobuf.tcp.api.RemoteServices;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.IntToDoubleFunction;

public class HelloWorld {

    public static void main(String[] args) throws IOException {
        // service implementation
        IntToDoubleFunction squareCalculator = (n) -> Math.pow(n, 2);

        // create server
        Closeable server = RemoteServices.provideService(squareCalculator, 8023, IntToDoubleFunction.class);

        // create client
        IntToDoubleFunction client = RemoteServices.createClient(IntToDoubleFunction.class, "localhost", 8023);

        try {
            // invoke the remote service
            double response = client.applyAsDouble(5);
            System.out.println("The square of 5 is " + response);
        } finally {
            server.close();
        }
    }
}
```

Other Java examples can be found in the [samples/java](samples/java) directory.

## Samples in other languages

Check the [samples](samples) directory for examples in other languages.

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
public class MyService implements SomeService {}
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
