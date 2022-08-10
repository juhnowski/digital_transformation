package ex4;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.util.HashMap;
import java.util.Map;

public class StageManager extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props props() {
        return Props.create(StageManager.class, StageManager::new);
    }

    public static final class RequestTrackStage {
        public final String groupId;
        public final String stageId;

        public RequestTrackStage(String groupId, String stageId) {
            this.groupId = groupId;
            this.stageId = stageId;
        }
    }

    public static final class StageRegistered {}

    final Map<String, ActorRef> groupIdToActor = new HashMap<>();
    final Map<ActorRef, String> actorToGroupId = new HashMap<>();

    @Override
    public void preStart() {
        log.info("StageManager started");
    }

    @Override
    public void postStop() {
        log.info("StageManager stopped");
    }

    private void onTrackStage(RequestTrackStage trackMsg) {
        String groupId = trackMsg.groupId;
        ActorRef ref = groupIdToActor.get(groupId);
        if (ref != null) {
            ref.forward(trackMsg, getContext());
        } else {
            log.info("Creating stage group actor for {}", groupId);
            ActorRef groupActor = getContext().actorOf(StageGroup.props(groupId), "group-" + groupId);
            getContext().watch(groupActor);
            groupActor.forward(trackMsg, getContext());
            groupIdToActor.put(groupId, groupActor);
            actorToGroupId.put(groupActor, groupId);
        }
    }

    private void onTerminated(Terminated t) {
        ActorRef groupActor = t.getActor();
        String groupId = actorToGroupId.get(groupActor);
        log.info("Stage group actor for {} has been terminated", groupId);
        actorToGroupId.remove(groupActor);
        groupIdToActor.remove(groupId);
    }

    public Receive createReceive() {
        return receiveBuilder()
                .match(RequestTrackStage.class, this::onTrackStage)
                .match(Terminated.class, this::onTerminated)
                .build();
    }
}