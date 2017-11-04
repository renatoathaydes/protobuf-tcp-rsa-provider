/**
 * Run with 'groovy say-hello'
 */

@Grab('org.slf4j:slf4j-simple:1.7.25')
@Grab('com.athaydes.osgi:protobuf-tcp-rsa-provider:0.1.0')
import com.athaydes.osgi.rsa.provider.protobuf.api.RemoteServices

interface HelloService {
    String sayHello(String name)
}

class Server {
    private static class HelloServiceImpl implements HelloService {
        @Override
        String sayHello(String name) { "Hello $name" }
    }

    static void run() {
        RemoteServices.provideService(new HelloServiceImpl(), 5562, HelloService)
    }
}

println 'Groovy Protobuffer TCP-RSA-Provider SayHello Client'

if (args.length > 0 && args[0] == '--no-server') {
    println 'Running client only, no server'
} else {
    println "Starting local server, run with the '--no-server' to not start a server"
    Server.run()
}

println '\nEnter "exit" to quit.\n'

HelloService service = RemoteServices.createClient(HelloService, '127.0.0.1', 5562)

while (true) {
    print 'Say hello to whom? '
    def name = System.in.newReader().readLine()
    if (name == 'exit') break
    println service.sayHello(name)
}

