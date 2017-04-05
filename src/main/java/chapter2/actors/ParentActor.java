package chapter2.actors;

import akka.actor.*;
import akka.japi.pf.DeciderBuilder;
import lombok.Data;
import scala.concurrent.duration.Duration;

import java.util.stream.LongStream;

public class ParentActor extends AbstractLoggingActor {

    @Data public static class StartChildBots {}

    public static Props props() {
        return Props.create(ParentActor.class, ParentActor::new);
    }

    private ParentActor() {}

    @Override
    public void preStart() throws Exception {
        super.preStart();
        LongStream.rangeClosed(0, 99).boxed()
                .forEach(it -> {
                    ActorRef child = this.getContext().actorOf(ChildActor.props());
                    this.getContext().watch(child);
                });
    }

    @Override
    public Receive createReceive() {
        return this.receiveBuilder()
                .match(StartChildBots.class, this::onStartChildBots)
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

    private void onStartChildBots(StartChildBots message) {
        this.log().info(">> {}", message);
        this.getContext().getChildren().forEach(child -> child.tell(new ChildActor.Move(), this.getSelf()));
    }

    private void onTerminated(Terminated message) {
        this.log().info(">> {}", message);

        ActorRef child = this.getContext().actorOf(Props.create(ChildActor.class));
        this.getContext().watch(child);
    }
}
