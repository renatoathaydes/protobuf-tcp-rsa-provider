import com.athaydes.protobuf.tcp.api.RemoteServices
import java.util.function.IntToDoubleFunction

/**
 * Starts a client and a server, runs a remote method, then stops the server.
 */
fun main(args: Array<String>) {
    // service implementation
    val squareCalculator = IntToDoubleFunction { n -> Math.pow(n.toDouble(), 2.0) }

    // create server
    val server = RemoteServices.provideService(squareCalculator, 8023, IntToDoubleFunction::class.java)

    // create client
    val client = RemoteServices.createClient(IntToDoubleFunction::class.java, "localhost", 8023)

    server.use { _ ->
        // invoke the remote service
        val response = client.applyAsDouble(5)
        println("The square of 5 is $response")
    }
}
