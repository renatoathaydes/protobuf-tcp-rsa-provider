package com.athaydes.osgi.rsa.provider.protobuf.api;

import com.athaydes.osgi.rsa.provider.protobuf.ProtobufInvocationHandler;
import com.athaydes.osgi.rsa.provider.protobuf.ProtobufServer;

import java.io.Closeable;
import java.lang.reflect.Proxy;
import java.net.URI;

/**
 * Simple interface to create both server and client remote services.
 */
public class RemoteServices {

    private RemoteServices() {
        // hide constructor
    }

    /**
     * Create a remote service client.
     *
     * @param serviceType the type of the service, normally an interface.
     * @param host        host name of the server
     * @param port        port of the server
     * @param loader      class loader to use to create the service instance locally
     * @param <T>         type of the service
     * @return a proxy to the remote service
     */
    @SuppressWarnings("unchecked")
    public static <T> T createClient(Class<T> serviceType, String host, int port, ClassLoader loader) {
        ProtobufInvocationHandler handler = new ProtobufInvocationHandler(URI.create("tcp://" + host + ":" + port));
        return (T) Proxy.newProxyInstance(loader, new Class[]{serviceType}, handler);
    }

    /**
     * Create a remote service client.
     *
     * @param serviceType the type of the service, normally an interface.
     * @param host        host name of the server
     * @param port        port of the server
     * @param <T>         type of the service
     * @return a proxy to the remote service
     */
    public static <T> T createClient(Class<T> serviceType, String host, int port) {
        return createClient(serviceType, host, port, serviceType.getClassLoader());
    }

    /**
     * Provide a remote service.
     * <p>
     * The service will become available immediately for service clients to connect to.
     *
     * @param service    instance of the local service
     * @param port       port to use for remote connections
     * @param interfaces the interfaces that can be provided by the service. If none is provided,
     *                   all methods of the service will be exposed remotely.
     * @return reference to the server wrapper around the local service that can be used to close it later
     */
    public static Closeable provideService(Object service, int port, Class... interfaces) {
        ProtobufServer server = new ProtobufServer(service, port, interfaces);
        server.run();
        return server;
    }

}
