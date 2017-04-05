package chapter4.actors;

import akka.actor.*;
import akka.japi.pf.DeciderBuilder;
import akka.routing.ActorRefRoutee;
import akka.routing.RoundRobinRoutingLogic;
import akka.routing.Router;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import scala.concurrent.duration.Duration;

import java.util.stream.Collectors;
import java.util.stream.LongStream;

@Getter
@Setter
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

    private void onWork(Work message) {
        this.log().info(">> {}", message);
        this.getRouter().route(message, this.getSender());
    }

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
