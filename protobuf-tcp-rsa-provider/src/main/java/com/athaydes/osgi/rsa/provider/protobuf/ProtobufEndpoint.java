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
 *
 */
public class ProtobufEndpoint implements Endpoint {

    private final EndpointDescription description;
    private final ProtobufServer server;

    ProtobufEndpoint(Object service,
                     Map<String, Object> effectiveProperties) {
        if (service == null) {
            throw new NullPointerException("Service must not be null");
        }

        int port = getIntFrom(effectiveProperties, DOMAIN + ".port")
                .orElse(5556);

        String hostName = getStringFrom(effectiveProperties, DOMAIN + ".hostname")
                .orElse("localhost");

        this.server = new ProtobufServer(port, service);

        String endpointId = String.format("tcp://%s:%s", hostName, port);
        effectiveProperties.put(RemoteConstants.ENDPOINT_ID, endpointId);
        effectiveProperties.put(RemoteConstants.SERVICE_EXPORTED_CONFIGS, "");
        this.description = new EndpointDescription(effectiveProperties);
    }

    @Override
    public EndpointDescription description() {
        return description;
    }

    @Override
    public void close() throws IOException {
        server.close();
    }
}
