package com.athaydes.protobuf.tcp.api;

import java.io.IOException;

/**
 * Exception that occurs when a remote service is called but a communication error occurs.
 * <p>
 * Communication errors may be the result of connection, protocol or network issues.
 */
public class CommunicationException extends RuntimeException {

    public CommunicationException(IOException cause) {
        super(cause);
    }

}
