package ex4;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ArtefactGroupQuery extends AbstractActor {
    public static final class CollectionTimeout {}

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    final Map<ActorRef, String> actorToArtefactId;
    final long requestId;
    final ActorRef requester;

    Cancellable queryTimeoutTimer;

    public ArtefactGroupQuery(
            Map<ActorRef, String> actorToArtefactId,
            long requestId,
            ActorRef requester,
            FiniteDuration timeout) {
        this.actorToArtefactId = actorToArtefactId;
        this.requestId = requestId;
        this.requester = requester;

        queryTimeoutTimer =
                getContext()
                        .getSystem()
                        .scheduler()
                        .scheduleOnce(
                                timeout,
                                getSelf(),
                                new CollectionTimeout(),
                                getContext().getDispatcher(),
                                getSelf());
    }

    public static Props props(
            Map<ActorRef, String> actorToArtefactId,
            long requestId,
            ActorRef requester,
            FiniteDuration timeout) {
        return Props.create(
                ArtefactGroupQuery.class,
                () -> new ArtefactGroupQuery(actorToArtefactId, requestId, requester, timeout));
    }

    @Override
    public void preStart() {
        for (ActorRef artefactActor : actorToArtefactId.keySet()) {
            getContext().watch(artefactActor);
            artefactActor.tell(new Artefact.ReadWell(0L), getSelf());
        }
    }

    @Override
    public void postStop() {
        queryTimeoutTimer.cancel();
    }

    @Override
    public Receive createReceive() {
        return waitingForReplies(new HashMap<>(), actorToArtefactId.keySet());
    }

    public Receive waitingForReplies(
            Map<String, ArtefactGroup.WellReading> repliesSoFar, Set<ActorRef> stillWaiting) {
        return receiveBuilder()
                .match(
                        Artefact.RespondWell.class,
                        r -> {
                            ActorRef artefactActor = getSender();
                            ArtefactGroup.WellReading reading =
                                    r.value
                                            .map(v -> (ArtefactGroup.WellReading) new ArtefactGroup.Well(v))
                                            .orElse(ArtefactGroup.WellNotAvailable.INSTANCE);
                            receivedResponse(artefactActor, reading, stillWaiting, repliesSoFar);
                        })
                .match(
                        Terminated.class,
                        t -> {
                            receivedResponse(
                                    t.getActor(),
                                    ArtefactGroup.ArtefactNotAvailable.INSTANCE,
                                    stillWaiting,
                                    repliesSoFar);
                        })
                .match(
                        CollectionTimeout.class,
                        t -> {
                            Map<String, ArtefactGroup.WellReading> replies = new HashMap<>(repliesSoFar);
                            for (ActorRef artefactActor : stillWaiting) {
                                String artefactId = actorToArtefactId.get(artefactActor);
                                replies.put(artefactId, ArtefactGroup.ArtefactTimedOut.INSTANCE);
                            }
                            requester.tell(new ArtefactGroup.RespondAllWells(requestId, replies), getSelf());
                            getContext().stop(getSelf());
                        })
                .build();
    }

    public void receivedResponse(
            ActorRef artefactActor,
            ArtefactGroup.WellReading reading,
            Set<ActorRef> stillWaiting,
            Map<String, ArtefactGroup.WellReading> repliesSoFar) {
        getContext().unwatch(artefactActor);
        String artefactId = actorToArtefactId.get(artefactActor);

        Set<ActorRef> newStillWaiting = new HashSet<>(stillWaiting);
        newStillWaiting.remove(artefactActor);

        Map<String, ArtefactGroup.WellReading> newRepliesSoFar = new HashMap<>(repliesSoFar);
        newRepliesSoFar.put(artefactId, reading);
        if (newStillWaiting.isEmpty()) {
            requester.tell(new ArtefactGroup.RespondAllWells(requestId, newRepliesSoFar), getSelf());
            getContext().stop(getSelf());
        } else {
            getContext().become(waitingForReplies(newRepliesSoFar, newStillWaiting));
        }
    }
}