// This code can be run with JGrab: https://github.com/renatoathaydes/jgrab
// #jgrab com.athaydes.osgi:protobuf-tcp-rsa-provider:0.1.0
// #jgrab org.slf4j:slf4j-simple:1.7.25

import com.athaydes.protobuf.tcp.api.RemoteServices;
import java.io.Closeable;
import java.io.IOException;
import java.util.Scanner;

/**
 * A very simple CLI client for a remote service.
 */
public class SimpleCliClient {

    public static void main(String[] args) throws IOException {
        // create server
        Closeable server = RemoteServices.provideService(new SimpleMyService(), 8022, MyService.class);

        // create client
        MyService myService = RemoteServices.createClient(MyService.class, "localhost", 8022);

        System.out.println("Welcome to the SimpleCliClient!");
        System.out.println("Enter 'exit' to quit.\n");

        try {
            Scanner in = new Scanner(System.in);
            while (true) {
                System.out.print("Say hello to whom? ");
                String name = in.nextLine();
                if (name.equals("exit")) {
                    break;
                } else {
                    System.out.println(myService.sayHelloTo(name));
                }
            }
        } finally {
            server.close();
        }
    }

    public interface MyService {
        String sayHelloTo(String name);
    }

    private static class SimpleMyService implements MyService {
        @Override
        public String sayHelloTo(String name) {
            return "Hello " + name;
        }
    }

}
