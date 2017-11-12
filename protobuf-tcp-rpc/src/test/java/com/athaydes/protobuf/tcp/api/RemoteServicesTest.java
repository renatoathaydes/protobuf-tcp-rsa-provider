package com.athaydes.protobuf.tcp.api;

import com.athaydes.protobuf.tcp.internal.Utils;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import org.junit.After;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RemoteServicesTest {

    public interface SimpleService {
        String hello(String name);
    }

    public interface OtherService {
        boolean mirror(boolean value);
    }

    public static class ImplementsTwoServices implements SimpleService, OtherService {
        @Override
        public String hello(String name) {
            return "Hello " + name;
        }

        @Override
        public boolean mirror(boolean value) {
            return value;
        }
    }

    public static class CloseableService implements SimpleService, Closeable {
        boolean isClosed = false;

        @Override
        public String hello(String name) {
            return "Hi " + name;
        }

        @Override
        public void close() throws IOException {
            isClosed = true;
        }
    }

    private Closeable serviceToClose;

    @After
    public void closeService() {
        if (serviceToClose != null) {
            Utils.closeQuietly(serviceToClose);
        }
    }

    @Test
    public void canCastRemoteServiceClientToInterface() {
        // start the server providing both SimpleService and OtherService
        serviceToClose = RemoteServices.provideService(new ImplementsTwoServices(), 8000,
                SimpleService.class, OtherService.class);

        // create client implementing only SimpleService
        Closeable client = RemoteServices.createClient(URI.create("tcp://localhost:8000"),
                new Class[]{SimpleService.class},
                ClassLoader.getSystemClassLoader());

        // cast it to the service the client implements
        SimpleService service = (SimpleService) client;

        // check if it works
        assertThat(service.hello("Joe"), equalTo("Hello Joe"));

        // try to cast it to a service the server implements but not the client
        try {
            OtherService otherService = (OtherService) client;
            fail("Should not be able to call method on interface the client did not implement: " + otherService);
        } catch (ClassCastException e) {
            // expected
        }
    }

    @Test
    public void canCastRemoteServiceClientToSecondInterface() {
        // start the server providing both SimpleService and OtherService
        serviceToClose = RemoteServices.provideService(new ImplementsTwoServices(), 8000,
                SimpleService.class, OtherService.class);

        // create client implementing only OtherService
        Closeable client = RemoteServices.createClient(URI.create("tcp://localhost:8000"),
                new Class[]{OtherService.class},
                ClassLoader.getSystemClassLoader());

        // cast it to the service the client implements
        OtherService service = (OtherService) client;

        // check if it works
        assertThat(service.mirror(true), equalTo(true));

        // try to cast it to a service the server implements but not the client
        try {
            SimpleService otherService = (SimpleService) client;
            fail("Should not be able to call method on interface the client did not implement: " + otherService);
        } catch (ClassCastException e) {
            // expected
        }
    }

    @Test
    public void canCastRemoteServiceClientToTwoInterfaces() {
        // start the server providing both SimpleService and OtherService
        serviceToClose = RemoteServices.provideService(new ImplementsTwoServices(), 8000,
                SimpleService.class, OtherService.class);

        // create client implementing SimpleService and OtherService
        Closeable client = RemoteServices.createClient(URI.create("tcp://localhost:8000"),
                new Class[]{SimpleService.class, OtherService.class},
                ClassLoader.getSystemClassLoader());

        // cast it to the SimpleService
        SimpleService service = (SimpleService) client;

        // check if it works
        assertThat(service.hello("Joe"), equalTo("Hello Joe"));

        // cast it to the OtherService
        OtherService otherService = (OtherService) client;

        // check if it works
        assertThat(otherService.mirror(true), equalTo(true));
    }

    @Test
    public void canCastRemoteServiceClientToInterfaceEvenIfServerDoesNotImplementIt() {
        // start the server providing only SimpleService
        serviceToClose = RemoteServices.provideService(new ImplementsTwoServices(), 8000,
                SimpleService.class);

        // create client implementing SimpleService and OtherService
        Closeable client = RemoteServices.createClient(URI.create("tcp://localhost:8000"),
                new Class[]{SimpleService.class, OtherService.class},
                ClassLoader.getSystemClassLoader());

        // cast it to the SimpleService
        SimpleService service = (SimpleService) client;

        // check if it works
        assertThat(service.hello("Joe"), equalTo("Hello Joe"));

        // cast it to a service the client implements but not the server
        OtherService otherService = (OtherService) client;

        // try to call a method on the client that the server does not provide
        try {
            otherService.mirror(false);
            fail("Should not be able to call method on interface the server did not provide");
        } catch (RemoteException e) {
            assertThat(e.getExceptionType(), equalTo(NoSuchMethodException.class.getName()));
        }
    }

    @Test(expected = ClassCastException.class)
    public void cannotCreateServerImplementingWrongInterface() {
        // try to create server implementing Runnable when the actual service does not implement it
        serviceToClose = RemoteServices.provideService(new ImplementsTwoServices(), 8000,
                SimpleService.class, Runnable.class);
    }

    @Test
    public void canCloseClientByCastingToCloseable() throws IOException {
        SimpleService client = RemoteServices.createClient(SimpleService.class, "127.0.0.1", 5090);
        ((Closeable) client).close();
    }

    @Test
    public void serviceCloseMethodCalledRemotelyIfExported() throws IOException {
        CloseableService remoteService = new CloseableService();

        serviceToClose = RemoteServices.provideService(remoteService, 8000,
                SimpleService.class, Closeable.class);

        // when the client is closed
        Closeable client = RemoteServices.createClient(URI.create("tcp://127.0.0.1:8000"),
                new Class[]{SimpleService.class, Closeable.class}, ClassLoader.getSystemClassLoader());
        client.close();

        // the remote service should have been closed
        assertTrue(remoteService.isClosed);
    }

    @Test
    public void noErrorWhenCloseableClientIsClosedButRemoteServiceIsDown() throws IOException {
        // do not provide the remote service remotely
        CloseableService remoteService = new CloseableService();

        // when the client is closed
        Closeable client = RemoteServices.createClient(URI.create("tcp://127.0.0.1:8000"),
                new Class[]{SimpleService.class, Closeable.class}, ClassLoader.getSystemClassLoader());
        client.close();

        // no error happens and the remote service should NOT have been closed as it is down
        assertFalse(remoteService.isClosed);
    }

    @Test
    public void serviceCloseMethodNotCalledRemotelyIfNotExported() throws IOException {
        CloseableService remoteService = new CloseableService();

        // does not export Closeable
        serviceToClose = RemoteServices.provideService(remoteService, 8000,
                SimpleService.class);

        // when the client is closed
        Closeable client = RemoteServices.createClient(URI.create("tcp://127.0.0.1:8000"),
                new Class[]{SimpleService.class}, ClassLoader.getSystemClassLoader());
        client.close();

        // the remote service should NOT have been closed
        assertFalse(remoteService.isClosed);
    }

    @Test
    public void serviceCloseMethodCalledRemotelyIfProvidedByClientButFailsIfNotExported() throws IOException {
        CloseableService remoteService = new CloseableService();

        // does not export Closeable
        serviceToClose = RemoteServices.provideService(remoteService, 8000,
                SimpleService.class);

        // client erroneously provides the Closeable interface
        Closeable client = RemoteServices.createClient(URI.create("tcp://127.0.0.1:8000"),
                new Class[]{SimpleService.class, Closeable.class}, ClassLoader.getSystemClassLoader());

        // when the client is closed
        // the remote service method is called but fails
        try {
            client.close();
            fail("Should have failed to call close() on the remote service as Closeable is not exported");
        } catch (RemoteException e) {
            assertThat(e.getExceptionType(), equalTo(NoSuchMethodException.class.getName()));
        }

        assertFalse(remoteService.isClosed);
    }

}
