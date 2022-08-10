package ex4;

import java.util.Optional;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class Stage extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    final String groupId;

    final String stageId;

    public Stage(String groupId, String stageId) {
        this.groupId = groupId;
        this.stageId = stageId;
    }

    public static Props props(String groupId, String stageId) {
        return Props.create(Stage.class, () -> new Stage(groupId, stageId));
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
        log.info("Stage actor {}-{} started", groupId, stageId);
    }

    @Override
    public void postStop() {
        log.info("Stage actor {}-{} stopped", groupId, stageId);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(
                        StageManager.RequestTrackStage.class,
                        r -> {
                            if (this.groupId.equals(r.groupId) && this.stageId.equals(r.stageId)) {
                                getSender().tell(new StageManager.StageRegistered(), getSelf());
                            } else {
                                log.warning(
                                        "Ignoring TrackStage request for {}-{}.This actor is responsible for {}-{}.",
                                        r.groupId,
                                        r.stageId,
                                        this.groupId,
                                        this.stageId);
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