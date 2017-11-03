/**
 *
 */

@Grab('org.slf4j:slf4j-simple:1.7.25')
@Grab(group = 'com.athaydes.osgi', module = 'protobuf-tcp-rsa-provider', version = '0.1.0')
import com.athaydes.osgi.rsa.provider.protobuf.api.RemoteService

def read = { label ->
    print label
    def input = System.in.newReader().readLine()
    if (input == 'exit') {
        println 'Bye!'
        System.exit(0)
    }
    println()
    input
}

println 'Groovy Protobuffer TCP-RSA-Provider SayHello Client'
println '\nEnter "exit" to quit.\n'

interface HelloService {
    String sayHello(String name)
}

HelloService service = RemoteService.createClient(HelloService, '127.0.0.1', 5562)

while (true) {
    def name = read 'Say hello to whom? '
    println service.sayHello(name)
}

