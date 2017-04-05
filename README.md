The Obligatory Akka Sample with Java
===

Introduction
---
#### What is Akka?
Akka is (put simply) a framework by Lightbend to write correct distributed, concurrent, fault-tolerant, and scalable application.

#### Reactive Manifesto
Reactive Systems are:
- Responsive
- Resilient
- Elastic
- Message Driven 

Content
---
#### Chapter 1 - Actor Systems
Actors are objects which encapsulates state and behavior. They communicate by exchanging messages. Actors can be organized into hierarchical structure with higher level as supervisor and lower level as workers.

```java
public class MainKernel {
    public static void main(String[] args) throws Exception {
        //1. Create the ActorSystem
        ActorSystem system = ActorSystem.create("system");
        
        //2. Create the Guardian Actors, further Actors down the hierarchy should be created by the Guardian or Supervisor
        ActorRef parent = system.actorOf(ParentActor.props(), "ParentActor");
        
        //3. Shutdown (for development)
        System.out.println("Shutting down...");
        system.terminate();
    }
}
```

```java
public class ParentActor extends AbstractLoggingActor {
    
    //1. Define all Message class here. They may contain further members but they should be minimal and native
    @Data public static class Start {}
    @Data public static class Stop {}
    
    //2. Define static props() method here for convenience of Actor creations 
    public static Props props() {
        return Props.create(ParentActor.class, ParentActor::new);
    }
    
    //3. Define private constructors so that the Actors could not be instantiated outside the system
    private ParentActor() {}
    
    //4. Override lifecycle methods if necessary
    @Override
    public void preStart() throws Exception {
        super.preStart();
        //... further logic here
        //... if you need child actors or other actors of lower hierarchy, create here.
        ActorRef child = this.getContext().actorOf(ChildActor.props);
    }
    
    @Override
    public void postStop() throws Exception {
        super.postStop();
        //... further logic here
    }
    
    //5. Define the Receiver. Best practise is not to define the logic here, instead defer to another method with naming convention as onXXX.
    @Override
    public Receive createReceive() {
        return this.receiveBuilder()
                .match(Start.class, this::onStart)
                .match(Stop.class, this::onStop)
                .matchAny(this::onUnknown) // typically logger for dead-letters
                .build();
    }
    
    //6. Define the handler private methods
    private void onStart(Start message) {}
    private void onStop(Stop message) {}
}
```

```java
public class ChildActor extends AbstractLoggingActor {
    
    //1. Define all Message class here. They may contain further members but they should be minimal and native
    @Data public static class Notify {}
    
    //2. Define static props() method here for convenience of Actor creations 
    public static Props props() {
        return Props.create(ChildActor.class, ChildActor::new);
    }
    
    //3. Define private constructors so that the Actors could not be instantiated outside the system
    private ChildActor() {}
    
    //4. Define the Receiver. Best practise is not to define the logic here, instead defer to another method with naming convention as onXXX.
    @Override
    public Receive createReceive() {
        return this.receiveBuilder()
                .match(Notify.class, this::onNotify)
                .matchAny(this::onUnknown) // typically logger for dead-letters
                .build();
    }
    
    //5. Define the handler private methods
    private void onNotify(Notify message) {}
}
```

Regarding Receiver:
- use `.matchEquals(Object object, ...)` to define most specific message (typically String).
- use `.match(Class<?> clazz, ...)` to define less specific message (typically message class).
- use `.matchAny(...)` to define generic messsage (typically dead-letters).
- map every `.match(...)` clause to a method reference instead of using lambda for improved readability.
- if the child is fault prone, `.watch(...)` it. This way the parent will get `Terminated` message when the child is terminated.

#### Chapter 2 - Fault Tolerance
Supervisor should supervise it's children, as such it should define fault handling supervisor strategy. `SupervisorStrategy` is basically a mapping of `Exception` classes (that may be thrown within the context of the said actor hierarchy) to a lifecycle strategy.

```java
public class ParentActor extends AbstractLoggingActor {
    
    //5. Define SupervisorStrategy here, below should be sensible default
    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new OneForOneStrategy(10, Duration.create("1 minute"),
                DeciderBuilder
                        .match(ArithmeticException.class, e -> SupervisorStrategy.resume())
                        .match(NullPointerException.class, e -> SupervisorStrategy.restart())
                        .match(IllegalArgumentException.class, e -> SupervisorStrategy.stop())
                        .matchAny(e -> SupervisorStrategy.escalate())
                        .build()
        );
    }
    
    //6. Define the Receiver. Best practise is not to define the logic here, instead defer to another method with naming convention as onXXX.
    @Override
    public Receive createReceive() {
        return this.receiveBuilder()
                .match(Start.class, this::onStart)
                .match(Stop.class, this::onStop)
                .match(Terminated.class, this::onTerminated) // only matched for Guardian or Supervisors, see Fault-Tolerance
                .matchAny(this::onUnknown) // typically logger for dead-letters
                .build();
    }
    
    //7. Define the handler private methods
    private void onTerminated(Terminated message) {}
}
```

Regarding `SupervisorStrategy`
- use `.match(Class<?> clazz, e -> {})` to define most specific Exceptions.
- use `.matchAny(e -> {})` to define generic Exceptions, typically will be wired to `SupervisorStrategy.escalate()`

#### Chapter 3 - Configuration
#### Chapter 4 - Routing
#### Chapter 5 - Clustering & Remoting

Appendices
---
#### Appendix A - References
- [Akka Documentation](http://doc.akka.io/docs/akka/2.5/java.html)
- [Reactive Programming with Akka - DZone](https://dzone.com/refcardz/reactive-programming-akka)
- [Akka Samples - GitHub](https://github.com/akka/akka-samples)
- [Akka Introduction - SlideShare](https://www.slideshare.net/jboner/introducing-akka)
- [Reactive Manifesto](http://www.reactivemanifesto.org/)

#### Appendix B - Logging
Nothing! Akka uses `SLF4J` with `Logback` automatically.

If you are within the context of `ActorSystem`, implement an `Actor` by extending `AbstractLoggingActor`. This will give you access to `this.log()` that is the existing logging framework.

If you are not within the context of `ActorSystem` yet:
- Use `Lombok` and annotate with `@Slf4j`. Log format not guaranteed
- Use `System.out.println()` since everything should be console output anyways. Log format non-existent.

#### Appendix C - Scheduling
