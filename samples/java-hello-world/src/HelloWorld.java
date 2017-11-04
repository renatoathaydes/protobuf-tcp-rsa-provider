// This code can be run with JGrab: https://github.com/renatoathaydes/jgrab
// #jgrab com.athaydes.osgi:protobuf-tcp-rsa-provider:0.1.0
// #jgrab org.slf4j:slf4j-simple:1.7.25

import com.athaydes.osgi.rsa.provider.protobuf.api.RemoteServices;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.IntToDoubleFunction;

public class HelloWorld {

    public static void main(String[] args) throws IOException {
        // service implementation
        IntToDoubleFunction squareCalculator = (n) -> Math.pow(n, 2);

        // create server
        Closeable server = RemoteServices.provideService(squareCalculator, 8023, IntToDoubleFunction.class);

        // create client
        IntToDoubleFunction client = RemoteServices.createClient(IntToDoubleFunction.class, "localhost", 8023);

        try {
            // invoke the remote service
            double response = client.applyAsDouble(5);
            System.out.println("The square of 5 is " + response);
        } finally {
            server.close();
        }
    }
}

