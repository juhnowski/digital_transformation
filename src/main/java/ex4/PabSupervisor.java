package ex4;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class PabSupervisor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props props() {
        return Props.create(PabSupervisor.class, PabSupervisor::new);
    }

    @Override
    public void preStart() {
        log.info("PAB Application started");
    }

    @Override
    public void postStop() {
        log.info("PAB Application stopped");
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().build();
    }
}