package com.athaydes.osgi.rsa.provider.protobuf;

import org.apache.aries.rsa.spi.DistributionProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Protobuf-TCP RSA Provider Bundle-Activator.
 */
public class Activator implements BundleActivator {

    private final AtomicReference<ProtobufProvider> providerRef = new AtomicReference<>();

    @Override
    public void start(BundleContext context) throws Exception {
        ProtobufProvider provider = new ProtobufProvider();

        providerRef.set(provider);

        Dictionary<String, Object> props = new Hashtable<>();
        props.put(RemoteConstants.REMOTE_INTENTS_SUPPORTED, new String[]{});
        props.put(RemoteConstants.REMOTE_CONFIGS_SUPPORTED, provider.getSupportedTypes());
        context.registerService(DistributionProvider.class, provider, props);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        ProtobufProvider provider = providerRef.getAndSet(null);

        if (provider != null) {
            provider.stop();
        }
    }

}
