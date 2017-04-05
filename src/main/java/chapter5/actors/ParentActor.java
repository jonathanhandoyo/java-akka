package chapter5.actors;

import akka.actor.*;
import akka.japi.pf.DeciderBuilder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

@Getter
@Setter
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
    }

    private void onTerminated(Terminated message) {
        this.log().info(">> {}", message);
    }
}
