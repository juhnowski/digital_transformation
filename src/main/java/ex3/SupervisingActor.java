package ex3;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;

class SupervisingActor extends AbstractActor {
    static Props props() {
        return Props.create(SupervisingActor.class, SupervisingActor::new);
    }

    ActorRef child = getContext().actorOf(SupervisedActor.props(), "supervised-actor");

    @Override
    public AbstractActor.Receive createReceive() {
        return receiveBuilder()
                .matchEquals(
                        "failChild",
                        f -> {
                            child.tell("fail", getSelf());
                        })
                .build();
    }
}