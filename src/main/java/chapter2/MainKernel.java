package chapter2;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import chapter2.actors.ParentActor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainKernel {

    public static void main(String[] args) throws Exception {
        ActorSystem system = ActorSystem.create("system2");
        ActorRef actor = system.actorOf(ParentActor.props(), "ParentActor");

        actor.tell(new ParentActor.StartChildBots(), ActorRef.noSender());

        Thread.sleep(2_000L);
        log.info("Shutting down actor system...");
        system.terminate();
    }
}
