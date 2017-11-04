import com.athaydes.osgi.rsa.provider.protobuf.api.RemoteServices;

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
}
