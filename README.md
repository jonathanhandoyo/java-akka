The Obligatory Akka Sample with Java
===

Introduction
---
### What is Akka?
Akka is (put simply) a framework by Lightbend to write correct distributed, concurrent, fault-tolerant, and scalable application.

### Reactive Manifesto
Reactive Systems are:
- Responsive
- Resilient
- Elastic
- Message Driven 

Content
---
### Chapter 1 - Actor Systems
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

### Chapter 2 - Fault Tolerance
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

### Chapter 3 - Configuration
If you are writing an Akka application, keep your configuration in `application.conf` at the root of the class path. If you are writing an Akka-based library, keep its configuration in `reference.conf` at the root of the JAR file.

Akka should be able to accept `{application|reference}.{conf|json|properties}`. Hierarchically, you can stack configuration maps. On the basic level there are 3 layers:
- `ConfigFactory.defaultOverrides()` this is system properties (environment variables)
- `ConfigFactory.defaultApplication()` this is provided from `application.conf`
- `ConfigFactory.defaultReference()` this is provided from `reference.conf` -> provided by library as sensible defaults to use the aforementioned library

You should be customize ONLY the 2nd layer with:
```java
ConfigFactory.defaultApplication()
    .withFallback(...)
    .withFallback(...)
    .withFallback(...);
```

Then provide the merged `Config` object to the `ActorSystem` during creation with:
```java
Config config = ConfigFactory.defaultApplication();
ActorSystem system = ActorSystem.create("system", config);
```

Similar to Spring, Akka has a default reference configuration with default values. Read [here](http://doc.akka.io/docs/akka/2.5/general/configuration.html), it's very long. There are sections for:
- akka-actor
- akka-agent
- akka-camel
- akka-cluster
- akka-multi-node-testkit
- akka-persistence
- akka-remove
- akka-testkit
- akka-cluster-metrics
- akka-cluster-tools
- akka-cluster-sharding
- akka-distributed-data

Note that these provide a `reference.conf` which would be at the lowest priority.

### Chapter 4 - Routing
Messages can be sent via router to efficiently route them to destination actors (routees). Akka comes with several implementation of routing strategies.

```java
public class ParentActor extends AbstractLoggingActor {

    private Router router;

    @Data public static class Work {}

    public static Props props() {
        return Props.create(ParentActor.class, ParentActor::new);
    }

    private ParentActor() {}

    @Override
    public void preStart() throws Exception {
        super.preStart();
        
        // Instantiate the routing strategy here, note that the children are watched and registered into RoutingLogic instance
        this.router = new Router(
                new RoundRobinRoutingLogic(),
                LongStream
                        .rangeClosed(1, 5).boxed()
                        .map(index -> {
                            ActorRef child = this.getContext().actorOf(ChildActor.props());
                            this.getContext().watch(child);
                            return new ActorRefRoutee(child);
                        })
                        .collect(Collectors.toList())
        );
    }

    @Override
    public Receive createReceive() {
        return this.receiveBuilder()
                .match(Work.class, this::onWork)
                .match(Terminated.class, this::onTerminated)
                .build();
    }

    // On message receipt, the message is then passed through the Router. Note that the message stays unchanged
    private void onWork(Work message) {
        this.log().info(">> {}", message);
        this.getRouter().route(message, this.getSender());
    }

    // On termination, the dead child needs to be removed from the router, recreated, then re-registered into the router
    private void onTerminated(Terminated message) {
        this.log().info(">> {}", message);

        //remove the dead routee from the router
        this.router = this.getRouter().removeRoutee(message.actor());

        //add the newly re-created routee
        ActorRef child = this.getContext().actorOf(Props.create(ChildActor.class));
        this.getContext().watch(child);
        this.router.addRoutee(new ActorRefRoutee(child));
    }
}
```

### Chapter 5 - Scheduling
Scheduler is a facility to send a predefined Message to an Actor System. There are 2 main facillities:
- Schedule Once: given an `initialDelay`, sends a message to the specified `Actor`.
- Schedule Every: given an `initialDelay`, sends a message to the specified `Actor` every specified `interval`

Best practise:
- Create the schedule in `preStart(...)`
- Cancel the schedule in `postStop(...)`

```java
public class ParentActor extends AbstractLoggingActor {

    private Cancellable scheduleOnce;
    private Cancellable scheduleEvery;

    @Data public static class Work {}

    public static Props props() {
        return Props.create(ParentActor.class, ParentActor::new);
    }

    private ParentActor() {}

    @Override
    public void preStart() throws Exception {
        super.preStart();

        // This schedules a Message to be sent ONCE, 5 seconds after Actor creation
        this.scheduleOnce = this.getContext().getSystem().scheduler().scheduleOnce(
                Duration.create(5, TimeUnit.SECONDS),
                this.getSelf(), new Work(),
                this.getContext().dispatcher(),
                ActorRef.noSender()
        );

        // This schedules a Message to be sent EVERY second, starting 5 seconds after Actor creation
        this.scheduleEvery = this.getContext().getSystem().scheduler().schedule(
                Duration.create(5, TimeUnit.SECONDS),
                Duration.create(1, TimeUnit.SECONDS),
                this.getSelf(), new Work(),
                this.getContext().dispatcher(),
                ActorRef.noSender()
        );
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();

        // Cancels all scheduled messages
        if (this.scheduleOnce != null) this.scheduleOnce.cancel();
        if (this.scheduleEvery != null) this.scheduleEvery.cancel();
    }

    @Override
    public Receive createReceive() {
        return this.receiveBuilder()
                .match(Work.class, this::onWork)
                .match(Terminated.class, this::onTerminated)
                .build();
    }

    private void onWork(Work message) {
        this.log().info(">> {}", message);
    }

    private void onTerminated(Terminated message) {
        this.log().info(">> {}", message);
    }
}
```

### Chapter 6 - Clustering & Remoting

Appendices
---
### Appendix A - References
- [Akka Documentation](http://doc.akka.io/docs/akka/2.5/java.html)
- [Reactive Programming with Akka - DZone](https://dzone.com/refcardz/reactive-programming-akka)
- [Akka Samples - GitHub](https://github.com/akka/akka-samples)
- [Akka Introduction - SlideShare](https://www.slideshare.net/jboner/introducing-akka)
- [Reactive Manifesto](http://www.reactivemanifesto.org/)

### Appendix B - Logging
Nothing! Akka uses `SLF4J` with `Logback` automatically.

If you are within the context of `ActorSystem`, implement an `Actor` by extending `AbstractLoggingActor`. This will give you access to `this.log()` that is the existing logging framework.

If you are not within the context of `ActorSystem` yet:
- Use `Lombok` and annotate with `@Slf4j`. Log format not guaranteed
- Use `System.out.println()` since everything should be console output anyways. Log format non-existent.
