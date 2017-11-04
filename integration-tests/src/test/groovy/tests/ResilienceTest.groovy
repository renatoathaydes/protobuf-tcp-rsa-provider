package tests

import com.athaydes.osgi.rsa.provider.protobuf.api.RemoteServices
import groovy.transform.Immutable
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.Duration

class ResilienceTest extends Specification {

    def "Should be able to exchange very large messages without problems"() {
        given: 'A very large message'
        def message = ('a'..'z').join('') * 10_000

        and: 'A remote echo service is started'
        def remoteService = RemoteServices.provideService(new EchoServiceImpl(), 5089, EchoService)

        and: 'A client is created for the remote service'
        def localService = RemoteServices.createClient(EchoService, 'localhost', 5089)

        when: 'The message is sent out to a remote echo service'
        def response = localService.echo(message)

        then: 'The exact same message is received back'
        response == message

        cleanup:
        remoteService?.close()
    }

    def "It is possible to exchange messages even after the client socket times out"() {
        given: '3 messages to be exchanged'
        def message1 = 'Hello Mary'
        def message2 = 'Mary, you still there?'
        def message3 = 'Alright, see you later then...'

        and: 'A remote echo service is started'
        def remoteService = RemoteServices.provideService(new EchoServiceImpl(), 5095, EchoService)

        and: 'A client is created for the remote service'
        def localService = RemoteServices.createClient(EchoService, 'localhost', 5095)

        when: 'The first message is sent out to a remote echo service'
        def response1 = localService.echo(message1)

        then: 'The expected response is received'
        response1 == message1

        when: 'The second message is sent out after a delay'
        sleep 5_000
        def response2 = localService.echo(message2)

        then: 'The expected response is received'
        response2 == message2

        when: 'The final message is sent out after a short delay'
        sleep 2_000
        def response3 = localService.echo(message3)

        then: 'The expected response is received'
        response3 == message3

        cleanup:
        remoteService?.close()
    }

    def "Slow services do not timeout within a reasonable amount of time"() {
        given: 'A simple message'
        def message = 'hello remote'

        and: 'A remote delayed echo service is started which takes at least 8 seconds to process a message'
        def remoteService = RemoteServices.provideService(new DelayedEchoService(8), 5090, EchoService)

        and: 'A client is created for the remote service'
        def localService = RemoteServices.createClient(EchoService, 'localhost', 5090)

        when: 'The message is sent out to a remote echo service'
        def response = localService.echo(message)

        then: 'The exact same message is received back'
        response == message

        cleanup:
        remoteService?.close()
    }

    def "It is possible to exchange a large number of messages without issues"() {
        given: 'A large number of random messages varying greatly in size'
        def rand = new Random()
        def messages = (1..1_000).collect { n ->
            byte[] array = new byte[rand.nextInt(Byte.MAX_VALUE - 12) + 12]
            rand.nextBytes(array)
            new String(array, StandardCharsets.UTF_8)
        }

        and: 'A remote echo service is started'
        def remoteService = RemoteServices.provideService(new EchoServiceImpl(), 5091, EchoService)

        and: 'A client is created for the remote service'
        def localService = RemoteServices.createClient(EchoService, 'localhost', 5091)

        when: 'The messages are sent out to a remote echo service'
        def responses = messages.collect { message -> localService.echo(message) }

        then: 'The exact same messages are received back'
        responses == messages

        cleanup:
        remoteService?.close()
    }

    def "Server continues serving requests after a client socket hangs or closes"() {
        given: '2 simple messages'
        def message1 = 'hello remote'
        def message2 = 'bye remote'

        and: 'A remote echo service is started'
        def remoteService = RemoteServices.provideService(new EchoServiceImpl(), 5098, EchoService)

        and: 'A client is created for the remote service'
        def localService = RemoteServices.createClient(EchoService, 'localhost', 5098)

        when: 'A bad client starts sending data to the server but does not finish'
        Socket badClient = new Socket("127.0.0.1", 5098)
        badClient.outputStream.with {
            write(10) // claims to send 10 bytes but sends none
            flush()
        }

        and: 'The first message is sent out to a remote echo service by the good client'
        def response = localService.echo(message1)

        then: 'The exact same message is received back'
        response == message1

        when: 'The bad client shuts down the socket'
        badClient.close()

        and: 'The second message is sent out to a remote echo service by the good client'
        def response2 = localService.echo(message2)

        then: 'The exact same message is received back'
        response2 == message2

        cleanup:
        badClient?.close()
        remoteService?.close()
    }

}

interface EchoService {
    String echo(String message)
}

class EchoServiceImpl implements EchoService {
    @Override
    String echo(String message) { message }
}

@Immutable
class DelayedEchoService implements EchoService {
    long delayInSeconds

    @Override
    String echo(String message) {
        sleep Duration.ofSeconds(delayInSeconds).toMillis()
        message
    }
}