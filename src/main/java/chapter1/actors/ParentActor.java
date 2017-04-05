package chapter1.actors;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;

import java.util.stream.LongStream;

public class ParentActor extends AbstractLoggingActor {

    public static Props props() {
        return Props.create(ParentActor.class, ParentActor::new);
    }

    private ParentActor() {}

    @Override
    public void preStart() throws Exception {
        super.preStart();
        LongStream.rangeClosed(0, 99).boxed().forEach(it -> this.getContext().actorOf(ChildActor.props()));
    }

    @Override
    public Receive createReceive() {
        return emptyBehavior();
    }
}
