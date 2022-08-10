package ex4;

import java.util.Optional;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class Artefact extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    final String groupId;

    final String artefactId;

    public Artefact(String groupId, String artefactId) {
        this.groupId = groupId;
        this.artefactId = artefactId;
    }

    public static Props props(String groupId, String artefactId) {
        return Props.create(Artefact.class, () -> new Artefact(groupId, artefactId));
    }

    public static final class RecordWell {
        final long requestId;
        final double value;

        public RecordWell(long requestId, double value) {
            this.requestId = requestId;
            this.value = value;
        }
    }

    public static final class WellRecorded {
        final long requestId;

        public WellRecorded(long requestId) {
            this.requestId = requestId;
        }
    }

    public static final class ReadWell {
        final long requestId;

        public ReadWell(long requestId) {
            this.requestId = requestId;
        }
    }

    public static final class RespondWell {
        final long requestId;
        final Optional<Double> value;

        public RespondWell(long requestId, Optional<Double> value) {
            this.requestId = requestId;
            this.value = value;
        }
    }

    Optional<Double> lastWellReading = Optional.empty();

    @Override
    public void preStart() {
        log.info("Artefact actor {}-{} started", groupId, artefactId);
    }

    @Override
    public void postStop() {
        log.info("Artefact actor {}-{} stopped", groupId, artefactId);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(
                        ArtefactManager.RequestTrackArtefact.class,
                        r -> {
                            if (this.groupId.equals(r.groupId) && this.artefactId.equals(r.artefactId)) {
                                getSender().tell(new ArtefactManager.ArtefactRegistered(), getSelf());
                            } else {
                                log.warning(
                                        "Ignoring TrackArtefact request for {}-{}.This actor is responsible for {}-{}.",
                                        r.groupId,
                                        r.artefactId,
                                        this.groupId,
                                        this.artefactId);
                            }
                        })
                .match(
                        RecordWell.class,
                        r -> {
                            log.info("Recorded well reading {} with {}", r.value, r.requestId);
                            lastWellReading = Optional.of(r.value);
                            getSender().tell(new WellRecorded(r.requestId), getSelf());
                        })
                .match(
                        ReadWell.class,
                        r -> {
                            getSender()
                                    .tell(new RespondWell(r.requestId, lastWellReading), getSelf());
                        })
                .build();
    }
}