package com.athaydes.protobuf.tcp.api;

import java.io.Closeable;

/**
 * Reference to a remote service provided by a local service.
 * <p>
 * The remote service can be started with a call to the {@link Runnable#run()} method,
 * and stopped with {@link Closeable#close()}.
 */
public interface ServiceReference<T> extends Runnable, Closeable {

    /**
     * @return the local service that backs the remote service represented by this service reference.
     */
    T getLocalService();

}
