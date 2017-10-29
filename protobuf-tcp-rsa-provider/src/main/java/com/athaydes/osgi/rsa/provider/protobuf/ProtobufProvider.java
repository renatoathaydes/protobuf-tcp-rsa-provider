package com.athaydes.osgi.rsa.provider.protobuf;

import org.apache.aries.rsa.spi.DistributionProvider;
import org.apache.aries.rsa.spi.Endpoint;
import org.apache.aries.rsa.spi.IntentUnsatisfiedException;
import org.osgi.framework.BundleContext;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 *
 */
public class ProtobufProvider implements DistributionProvider {

    static final String DOMAIN = "com.athaydes.protobuf";

    private final Deque<AutoCloseable> closeables = new ConcurrentLinkedDeque<>();

    @Override
    public String[] getSupportedTypes() {
        return new String[]{DOMAIN};
    }

    @Override
    public Endpoint exportService(Object serviceO,
                                  BundleContext serviceContext,
                                  Map<String, Object> effectiveProperties,
                                  Class[] exportedInterfaces) {
        effectiveProperties.put(RemoteConstants.SERVICE_IMPORTED_CONFIGS, getSupportedTypes());
        ProtobufEndpoint endpoint = new ProtobufEndpoint(serviceO, effectiveProperties);
        closeables.add(endpoint);
        return endpoint;
    }

    @Override
    public Object importEndpoint(ClassLoader cl,
                                 BundleContext consumerContext,
                                 Class[] interfaces,
                                 EndpointDescription endpoint)
            throws IntentUnsatisfiedException {
        try {
            URI address = new URI(endpoint.getId());
            ProtobufInvocationHandler handler = new ProtobufInvocationHandler(address);
            closeables.add(handler);
            return Proxy.newProxyInstance(cl, interfaces, handler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void stop() {
        for (AutoCloseable closeable : closeables) {
            try {
                closeable.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
