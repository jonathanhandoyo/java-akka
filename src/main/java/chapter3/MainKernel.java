package chapter3;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import chapter3.actors.ParentActor;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainKernel {

    public static void main(String[] args) throws Exception {
        Config config = ConfigFactory.defaultApplication();
        ActorSystem system = ActorSystem.create("system3", config);
        ActorRef actor = system.actorOf(ParentActor.props(), "ParentActor");

        actor.tell(new ParentActor.StartChildBots(), ActorRef.noSender());

        Thread.sleep(2_000L);
        log.info("Shutting down actor system...");
        system.terminate();
    }
}
