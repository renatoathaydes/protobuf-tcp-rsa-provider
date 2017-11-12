package com.athaydes.osgi.rsa.provider.protobuf;

import com.athaydes.protobuf.tcp.api.RemoteServices;
import com.athaydes.protobuf.tcp.api.ServicePropertyReader;
import com.athaydes.protobuf.tcp.api.ServiceReference;
import java.io.IOException;
import java.util.Map;
import org.apache.aries.rsa.spi.Endpoint;
import org.osgi.service.remoteserviceadmin.EndpointDescription;
import org.osgi.service.remoteserviceadmin.RemoteConstants;

import static com.athaydes.osgi.rsa.provider.protobuf.ProtobufProvider.DOMAIN;

/**
 * <a href="http://aries.apache.org/modules/rsa.html">Apache Aries RSA</a> {@link Endpoint}
 * implementation for the protobuf-tcp-rsa-provider module.
 */
public class ProtobufEndpoint implements Endpoint {

    private final EndpointDescription description;
    private final ServiceReference<?> server;
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

        ServicePropertyReader reader = ServicePropertyReader.getDefault();

        int port = reader.getIntFrom(effectiveProperties, DOMAIN + ".port")
                .orElse(5556);

        String hostName = reader.getStringFrom(effectiveProperties, DOMAIN + ".hostname")
                .orElse("localhost");

        this.server = RemoteServices.createService(service, port, exportedInterfaces);

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
