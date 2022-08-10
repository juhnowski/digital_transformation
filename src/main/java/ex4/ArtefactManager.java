package ex4;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.util.HashMap;
import java.util.Map;

public class ArtefactManager extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props props() {
        return Props.create(ArtefactManager.class, ArtefactManager::new);
    }

    public static final class RequestTrackArtefact {
        public final String groupId;
        public final String artefactId;

        public RequestTrackArtefact(String groupId, String artefactId) {
            this.groupId = groupId;
            this.artefactId = artefactId;
        }
    }

    public static final class ArtefactRegistered {}

    final Map<String, ActorRef> groupIdToActor = new HashMap<>();
    final Map<ActorRef, String> actorToGroupId = new HashMap<>();

    @Override
    public void preStart() {
        log.info("ArtefactManager started");
    }

    @Override
    public void postStop() {
        log.info("ArtefactManager stopped");
    }

    private void onTrackArtefact(RequestTrackArtefact trackMsg) {
        String groupId = trackMsg.groupId;
        ActorRef ref = groupIdToActor.get(groupId);
        if (ref != null) {
            ref.forward(trackMsg, getContext());
        } else {
            log.info("Creating artefact group actor for {}", groupId);
            ActorRef groupActor = getContext().actorOf(ArtefactGroup.props(groupId), "group-" + groupId);
            getContext().watch(groupActor);
            groupActor.forward(trackMsg, getContext());
            groupIdToActor.put(groupId, groupActor);
            actorToGroupId.put(groupActor, groupId);
        }
    }

    private void onTerminated(Terminated t) {
        ActorRef groupActor = t.getActor();
        String groupId = actorToGroupId.get(groupActor);
        log.info("Artefact group actor for {} has been terminated", groupId);
        actorToGroupId.remove(groupActor);
        groupIdToActor.remove(groupId);
    }

    public Receive createReceive() {
        return receiveBuilder()
                .match(RequestTrackArtefact.class, this::onTrackArtefact)
                .match(Terminated.class, this::onTerminated)
                .build();
    }
}