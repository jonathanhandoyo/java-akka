package chapter1.actors;

import akka.actor.*;
import akka.japi.pf.DeciderBuilder;
import lombok.Data;
import scala.concurrent.duration.Duration;

import java.util.stream.LongStream;

/**
 * Reference: https://dzone.com/refcardz/reactive-programming-akka
 * Note:
 * <ol>
 *     <li>Regarding general implementation of the Actor:</li>
 *     <ul>
 *         <li>User implemented Actors should extend either {@code AbstractActor} or {@code AbstractLoggingActor}.</li>
 *         <li>{@code AbstractActor} may require you to annotate with {@code @Slf4j}.</li>
 *         <li>{@code AbstractLoggingActor} already provides logging abstraction via {@code this.log()}.</li>
 *     </ul>
 *     <li>Regarding message classes:</li>
 *     <ul>
 *          <li>All message classes should be defined as close as possible to the {@code Actor} that consumes the message.</li>
 *          <li>All message classes should be annotated with lombok's {@code @Data} to remove boilerplates.</li>
 *          <li>All message classes should be static classes, since they are inner classes instantiated independently.</li>
 *     </ul>
 *     <li>Regarding constructors:</li>
 *     <ul>
 *          <li>All constructors should replicate {@code Props.create(...)} in terms of all Props defined in the class.</li>
 *          <li>All logic pertaining to what should the {@code Actor} do when first created should be defined here.</li>
 *     </ul>
 *     <li>Regarding {@code public Receive createReceive()}:</li>
 *     <ul>
 *          <li>The overridden method should use DSL to build the receiver.</li>
 *          <li>Use {@code .matchEquals(Object obj, ...)} to defined most specific message (typically String).</li>
 *          <li>Use {@code .match(Class<?> clazz, ...)} to defined less specific message (typically message classes).</li>
 *          <li>Use {@code .matchAny(...)} to defined generic message (typically dead-letters).</li>
 *          <li>Map every {@code match} with a method reference instead of lambda for improved readability, e.g.: {@code .match(Move.class, this::onMove)}.</li>
 *          <li>If the child actor is fault-prone, watch it. e.g.: {@code this.getContext().watch(child);}. This way, when the child is terminated the supervisor will receive a message {@code Terminated}.</li>
 *     </ul>
 * </ol>
 */
public class BotSupervisor extends AbstractLoggingActor {

    @Data public static class StartChildBots {}

    public static Props props() {
        return Props.create(BotSupervisor.class, BotSupervisor::new);
    }

    private BotSupervisor() {}

    @Override
    public void preStart() throws Exception {
        super.preStart();
        LongStream.rangeClosed(0, 99).boxed()
                .forEach(it -> {
                    ActorRef child = this.getContext().actorOf(BotChild.props());
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
        this.getContext().getChildren().forEach(child -> child.tell(new BotChild.Move(), this.getSelf()));
    }

    private void onTerminated(Terminated message) {
        this.log().info(">> {}", message);

        ActorRef child = this.getContext().actorOf(Props.create(BotChild.class));
        this.getContext().watch(child);
    }
}
