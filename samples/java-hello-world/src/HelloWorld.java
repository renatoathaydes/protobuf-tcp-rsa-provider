import com.athaydes.osgi.rsa.provider.protobuf.api.RemoteService;

import java.io.Closeable;
import java.io.IOException;
import java.util.Scanner;

class SimpleMyService implements MyService {
    @Override
    public String sayHelloTo(String name) {
        return "Hello " + name;
    }
}

public class HelloWorld {

    public static void main(String[] args) throws IOException {
        // create server
        Closeable server = RemoteService.provideService(new SimpleMyService(), 8022, MyService.class);

        // create client
        MyService myService = RemoteService.createClient(MyService.class, "localhost", 8022);

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

        server.close();
    }
}

