package chapter1.actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;
import lombok.Data;

import java.util.Random;

public class BotChild extends AbstractLoggingActor {

    @Data public static class Move {}
    @Data public static class Stop {}

    public static Props props() {
        return Props.create(BotChild.class, BotChild::new);
    }

    private BotChild() {}

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Move.class, this::onMove)
                .match(Stop.class, this::onStop)
                .build();
    }

    private void onMove(Move message) {
        this.log().info(">> {}", message);

        final Random random = new java.util.Random();
        final int nextInt = random.nextInt(10);
        if ((nextInt % 2) == 0) {
            this.getContext().stop(this.getSelf());
        }
    }

    private void onStop(Stop message) {
        this.log().info(">> {}", message);
    }
}
