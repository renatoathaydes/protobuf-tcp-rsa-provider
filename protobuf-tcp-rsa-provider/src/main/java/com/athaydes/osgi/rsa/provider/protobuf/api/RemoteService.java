package com.athaydes.osgi.rsa.provider.protobuf.api;

import com.athaydes.osgi.rsa.provider.protobuf.ProtobufInvocationHandler;
import com.athaydes.osgi.rsa.provider.protobuf.ProtobufServer;

import java.io.Closeable;
import java.lang.reflect.Proxy;
import java.net.URI;

/**
 * Simple interface to create both server and client remote services.
 */
public class RemoteService {

    @SuppressWarnings("unchecked")
    public static <T> T createClient(Class<T> serviceType, String host, int port, ClassLoader loader) {
        ProtobufInvocationHandler handler = new ProtobufInvocationHandler(URI.create("tcp://" + host + ":" + port));
        return (T) Proxy.newProxyInstance(loader, new Class[]{serviceType}, handler);
    }

    public static <T> T createClient(Class<T> serviceType, String host, int port) {
        return createClient(serviceType, host, port, serviceType.getClassLoader());
    }

    public static Closeable provideService(Object service, int port, Class... interfaces) {
        ProtobufServer server = new ProtobufServer(service, port, interfaces);
        server.run();
        return server;
    }

}
