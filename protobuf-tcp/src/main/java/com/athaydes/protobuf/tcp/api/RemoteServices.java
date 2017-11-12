package com.athaydes.protobuf.tcp.api;

import com.athaydes.protobuf.tcp.ProtobufInvocationHandler;
import com.athaydes.protobuf.tcp.ProtobufServer;
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
     * <p>
     * To close the connection to the remote service, cast the returned value to a {@link Closeable} if it is not
     * already one, then call the {@link Closeable#close()} method.
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
     * Create a remote service client.
     * <p>
     * To close the connection to the remote service, cast the returned value to a {@link Closeable} if it is not
     * already one, then call the {@link Closeable#close()} method.
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
     * <p>
     * The returned value can be safely cast to all of the given interfaces provided that the
     * remote service indeed implements them.
     * <p>
     * To close the connection to the remote service, call the {@link Closeable#close()} method on the returned value.
     *
     * @param address     address of the remote service. The only protocol supported is TCP.
     * @param interfaces  the interfaces provided by the remote service.
     * @param classLoader class loader to use to define the proxy class of the client
     * @return a proxy to the remote service
     */
    public static Closeable createClient(URI address, Class[] interfaces, ClassLoader classLoader) {
        if (!"tcp".equals(address.getScheme())) {
            throw new IllegalArgumentException("Unsupported scheme (only TCP allowed): " + address.getScheme());
        }

        ProtobufInvocationHandler handler = new ProtobufInvocationHandler(address);
        return (Closeable) Proxy.newProxyInstance(classLoader, interfaces, handler);
    }

    /**
     * Provide a remote service.
     * <p>
     * The service will become available immediately for service clients to connect to.
     * <p>
     * To stop the remote service, call {@link Closeable#close()} on the returned {@link Closeable}.
     *
     * @param service    instance of the local service
     * @param port       port to use for remote connections
     * @param interfaces the interfaces that can be provided by the service. If none is provided,
     *                   all methods of the service will be exposed remotely.
     * @return reference to the server wrapper around the local service that can be used to close it later
     * @see #createService(Object, int, Class[])
     */
    public static Closeable provideService(Object service, int port, Class... interfaces) {
        ServiceReference<?> server = createService(service, port, interfaces);
        server.run();
        return server;
    }

    /**
     * Create a remote service.
     * <p>
     * Unlike {@link RemoteServices#provideService(Object, int, Class[])}, this method does not start the
     * remote service. Call {@link ServiceReference#run()} to start the remote service.
     * <p>
     * To stop the remote service, call {@link ServiceReference#close()} on the returned {@link ServiceReference}.
     *
     * @param service    instance of the local service
     * @param port       port to use for remote connections
     * @param interfaces the interfaces that can be provided by the service. If none is provided,
     *                   all methods of the service will be exposed remotely.
     * @return reference to the server wrapper around the local service that can be used to start and close it later
     * @see #provideService(Object, int, Class[])
     */
    public static <T> ServiceReference<T> createService(T service, int port, Class... interfaces) {
        return new ProtobufServer<>(service, port, interfaces);
    }
}
