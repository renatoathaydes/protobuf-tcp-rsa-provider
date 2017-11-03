package com.athaydes.osgi.rsa.provider.protobuf;

import org.apache.aries.rsa.spi.Endpoint;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

import java.io.IOException;
import java.util.Map;

import static com.athaydes.osgi.rsa.provider.protobuf.ProtobufProvider.DOMAIN;
import static com.athaydes.osgi.rsa.provider.protobuf.Utils.getIntFrom;
import static com.athaydes.osgi.rsa.provider.protobuf.Utils.getStringFrom;

/**
 * <a href="http://aries.apache.org/modules/rsa.html">Apache Aries RSA</a> {@link Endpoint}
 * implementation for the protobuf-tcp-rsa-provider module.
 */
public class ProtobufEndpoint implements Endpoint {

    private final EndpointDescription description;
    private final ProtobufServer server;
    private final Thread serverThread;

    ProtobufEndpoint(Object service,
                     Map<String, Object> effectiveProperties,
                     Class[] exportedInterfaces) {
        if (service == null) {
            throw new NullPointerException("Service must not be null");
        }
        if (exportedInterfaces.length == 0) {
            throw new IllegalArgumentException("Cannot export service without any interfaces");
        }

        int port = getIntFrom(effectiveProperties, DOMAIN + ".port")
                .orElse(5556);

        String hostName = getStringFrom(effectiveProperties, DOMAIN + ".hostname")
                .orElse("localhost");

        this.server = new ProtobufServer(port, service, exportedInterfaces);

        String endpointId = String.format("tcp://%s:%s", hostName, port);
        effectiveProperties.put(RemoteConstants.ENDPOINT_ID, endpointId);
        effectiveProperties.put(RemoteConstants.SERVICE_EXPORTED_CONFIGS, "");
        this.description = new EndpointDescription(effectiveProperties);
        this.serverThread = new Thread(server, description.getId());
    }

    @Override
    public EndpointDescription description() {
        return description;
    }

    void start() {
        serverThread.start();
    }

    @Override
    public void close() throws IOException {
        server.close();
        serverThread.interrupt();
    }
}
