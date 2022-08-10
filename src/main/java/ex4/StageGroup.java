package ex4;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class StageGroup extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    final String groupId;

    public StageGroup(String groupId) {
        this.groupId = groupId;
    }

    public static Props props(String groupId) {
        return Props.create(StageGroup.class, () -> new StageGroup(groupId));
    }

    public static final class RequestStageList {
        final long requestId;

        public RequestStageList(long requestId) {
            this.requestId = requestId;
        }
    }

    public static final class ReplyStageList {
        final long requestId;
        final Set<String> ids;

        public ReplyStageList(long requestId, Set<String> ids) {
            this.requestId = requestId;
            this.ids = ids;
        }
    }

    final Map<String, ActorRef> stageIdToActor = new HashMap<>();
    final Map<ActorRef, String> actorToStageId = new HashMap<>();

    @Override
    public void preStart() {
        log.info("StageGroup {} started", groupId);
    }

    @Override
    public void postStop() {
        log.info("StageGroup {} stopped", groupId);
    }

    private void onTrackStage(StageManager.RequestTrackStage trackMsg) {
        if (this.groupId.equals(trackMsg.groupId)) {
            ActorRef stageActor = stageIdToActor.get(trackMsg.stageId);
            if (stageActor != null) {
                stageActor.forward(trackMsg, getContext());
            } else {
                log.info("Creating stage actor for {}", trackMsg.stageId);
                stageActor =
                        getContext()
                                .actorOf(Stage.props(groupId, trackMsg.stageId), "stage-" + trackMsg.stageId);
                getContext().watch(stageActor);
                actorToStageId.put(stageActor, trackMsg.stageId);
                stageIdToActor.put(trackMsg.stageId, stageActor);
                stageActor.forward(trackMsg, getContext());
            }
        } else {
            log.warning(
                    "Ignoring TrackStage request for {}. This actor is responsible for {}.",
                    groupId,
                    this.groupId);
        }
    }

    private void onStageList(RequestStageList r) {
        getSender().tell(new ReplyStageList(r.requestId, stageIdToActor.keySet()), getSelf());
    }

    private void onTerminated(Terminated t) {
        ActorRef stageActor = t.getActor();
        String stageId = actorToStageId.get(stageActor);
        log.info("Stage actor for {} has been terminated", stageId);
        actorToStageId.remove(stageActor);
        stageIdToActor.remove(stageId);
    }

    @Override
    public AbstractActor.Receive createReceive() {
        return receiveBuilder()
                .match(StageManager.RequestTrackStage.class, this::onTrackStage)
                .match(RequestStageList.class, this::onStageList)
                .match(Terminated.class, this::onTerminated)
                .build();
    }
}