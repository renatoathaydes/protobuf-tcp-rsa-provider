public interface MyService {
    String sayHelloTo(String name);
}

class SimpleMyService implements MyService {
    @Override
    public String sayHelloTo(String name) {
        return "Hello " + name;
    }
}
