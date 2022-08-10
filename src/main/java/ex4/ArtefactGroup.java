package ex4;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ArtefactGroup extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    final String groupId;

    public ArtefactGroup(String groupId) {
        this.groupId = groupId;
    }

    public static Props props(String groupId) {
        return Props.create(ArtefactGroup.class, () -> new ArtefactGroup(groupId));
    }

    public static final class RequestArtefactList {
        final long requestId;

        public RequestArtefactList(long requestId) {
            this.requestId = requestId;
        }
    }

    public static final class ReplyArtefactList {
        final long requestId;
        final Set<String> ids;

        public ReplyArtefactList(long requestId, Set<String> ids) {
            this.requestId = requestId;
            this.ids = ids;
        }
    }

    final Map<String, ActorRef> artefactIdToActor = new HashMap<>();
    final Map<ActorRef, String> actorToArtefactId = new HashMap<>();

    @Override
    public void preStart() {
        log.info("ArtefactGroup {} started", groupId);
    }

    @Override
    public void postStop() {
        log.info("ArtefactGroup {} stopped", groupId);
    }

    private void onTrackArtefact(ArtefactManager.RequestTrackArtefact trackMsg) {
        if (this.groupId.equals(trackMsg.groupId)) {
            ActorRef artefactActor = artefactIdToActor.get(trackMsg.artefactId);
            if (artefactActor != null) {
                artefactActor.forward(trackMsg, getContext());
            } else {
                log.info("Creating artefact actor for {}", trackMsg.artefactId);
                artefactActor =
                        getContext()
                                .actorOf(Artefact.props(groupId, trackMsg.artefactId), "artefact-" + trackMsg.artefactId);
                getContext().watch(artefactActor);
                actorToArtefactId.put(artefactActor, trackMsg.artefactId);
                artefactIdToActor.put(trackMsg.artefactId, artefactActor);
                artefactActor.forward(trackMsg, getContext());
            }
        } else {
            log.warning(
                    "Ignoring TrackArtefact request for {}. This actor is responsible for {}.",
                    groupId,
                    this.groupId);
        }
    }

    private void onArtefactList(RequestArtefactList r) {
        getSender().tell(new ReplyArtefactList(r.requestId, artefactIdToActor.keySet()), getSelf());
    }

    private void onTerminated(Terminated t) {
        ActorRef artefactActor = t.getActor();
        String artefactId = actorToArtefactId.get(artefactActor);
        log.info("Artefact actor for {} has been terminated", artefactId);
        actorToArtefactId.remove(artefactActor);
        artefactIdToActor.remove(artefactId);
    }

    @Override
    public AbstractActor.Receive createReceive() {
        return receiveBuilder()
                .match(ArtefactManager.RequestTrackArtefact.class, this::onTrackArtefact)
                .match(RequestArtefactList.class, this::onArtefactList)
                .match(Terminated.class, this::onTerminated)
                .match(RequestAllWells.class, this::onAllWells)
                .build();
    }

    public static final class RequestAllWells {
        final long requestId;

        public RequestAllWells(long requestId) {
            this.requestId = requestId;
        }
    }

    public static final class RespondAllWells {
        final long requestId;
        final Map<String, WellReading> wells;

        public RespondAllWells(long requestId, Map<String, WellReading> temperatures) {
            this.requestId = requestId;
            this.wells = temperatures;
        }
    }

    public static interface WellReading {}

    public static final class Well implements WellReading {
        public final double value;

        public Well(double value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Well that = (Well) o;

            return Double.compare(that.value, value) == 0;
        }

        @Override
        public int hashCode() {
            long well = Double.doubleToLongBits(value);
            return (int) (well ^ (well >>> 32));
        }

        @Override
        public String toString() {
            return "Well{" + "value=" + value + '}';
        }
    }

    public enum WellNotAvailable implements WellReading {
        INSTANCE
    }

    public enum ArtefactNotAvailable implements WellReading {
        INSTANCE
    }

    public enum ArtefactTimedOut implements WellReading {
        INSTANCE
    }

    private void onAllWells(RequestAllWells r) {
        Map<ActorRef, String> actorToArtefactIdCopy = new HashMap<>(this.actorToArtefactId);

        getContext()
                .actorOf(
                        ArtefactGroupQuery.props(
                                actorToArtefactIdCopy,
                                r.requestId,
                                getSender(),
                                new FiniteDuration(3, TimeUnit.SECONDS)));
    }


}