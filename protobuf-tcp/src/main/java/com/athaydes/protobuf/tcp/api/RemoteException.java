package com.athaydes.protobuf.tcp.api;

/**
 * Exception resulting from a remote service throwing an {@link Exception} of any type.
 * <p>
 * The type of the Exception thrown by the remote service can be retrieved with the
 * {@link #getExceptionType()} method, and the message with the
 * {@link #getMessage()} method.
 */
public class RemoteException extends RuntimeException {

    private final String exceptionType;

    public RemoteException(String exceptionType, String message) {
        super(message);
        this.exceptionType = exceptionType;
    }

    /**
     * @return the fully qualified name of the type of the Exception thrown by the remote service.
     */
    public String getExceptionType() {
        return exceptionType;
    }

    @Override
    public String toString() {
        return "RemoteException{" +
                "exceptionType='" + exceptionType + '\'' +
                "message='" + getMessage() + '\'' +
                '}';
    }
}
