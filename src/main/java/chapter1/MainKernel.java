package chapter1;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import chapter1.actors.BotSupervisor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainKernel {

    public static void main(String[] args) throws Exception {
        ActorSystem system = ActorSystem.create("system");
        ActorRef actor = system.actorOf(Props.create(BotSupervisor.class), BotSupervisor.class.getSimpleName());

        actor.tell(new BotSupervisor.StartChildBots(), ActorRef.noSender());

        Thread.sleep(2_000L);
        log.info("Shutting down actor system...");
        system.terminate();
    }
}
