/**
 *
 */

@Grab(group = 'com.athaydes.osgi', module = 'protobuf-tcp-rsa-provider', version = '1.0-SNAPSHOT')
import com.athaydes.osgi.rsa.provider.protobuf.api.Api
import com.google.protobuf.Any
import com.google.protobuf.StringValue

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

println 'Groovy Protobuffer TCP-RSA-Provider Client'
println '\nEnter "exit" to quit.\n'

def port = null

while (!(port instanceof Integer)) {
    port = read 'Please enter the port number of your server: '
    try {
        port = port.toInteger()
    } catch (NumberFormatException e) {
        println e.toString()
        println "The port must be a number, please try again."
    }
}

while (true) {
    Api.MethodInvocation invocation = null

    while (!invocation) {
        def method = read 'Method to call remotely: '
        def parts = method.split(' ', 2)
        if (parts.size() != 2) {
            println 'Invalid syntax! Enter method using the form: methodName arg1, arg2, ...'
            continue
        }
        try {
            def invocationBuilder = Api.MethodInvocation.newBuilder().setMethodName(parts[0])
            def args = parts[1].split('\\s+,\\s+')
            args.each { invocationBuilder.addArgs(Any.pack(StringValue.newBuilder().setValue(it).build())) }
            invocation = invocationBuilder.build()
        } catch (e) {
            println e.toString()
            println 'An error occurred, please try again!'
        }
    }

    println 'Sending invocation: ' + invocation

    try {
        new Socket('127.0.0.1', port).withStreams { input, out ->
            try {
                invocation.writeDelimitedTo(out)
                def result = Api.Result.parseDelimitedFrom(input)
                println result
                println()
            } catch (e) {
                e.printStackTrace()
                println "An error occurred, please try again!"
            }
        }
    } catch (ConnectException e) {
        println "Server not responding: $e"
    }
}

