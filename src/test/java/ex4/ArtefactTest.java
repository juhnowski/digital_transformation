package ex4;

import akka.actor.*;
import akka.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ArtefactTest extends AbstractJavaTest {

    public static class SomeActor extends AbstractActor {
        ActorRef target = null;

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .matchEquals(
                            "hello",
                            message -> {
                                getSender().tell("world", getSelf());
                                if (target != null) target.forward(message, getContext());
                            })
                    .match(
                            ActorRef.class,
                            actorRef -> {
                                target = actorRef;
                                getSender().tell("done", getSelf());
                            })
                    .build();
        }
    }

    static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testIt() {
        /*
         * Wrap the whole test procedure within a testkit constructor
         * if you want to receive actor replies or use Within(), etc.
         */
        new TestKit(system) {
            {
                final Props props = Props.create(SomeActor.class);
                final ActorRef subject = system.actorOf(props);

                // can also use JavaTestKit “from the outside”
                final TestKit probe = new TestKit(system);
                // “inject” the probe by passing it to the test subject
                // like a real resource would be passed in production
                subject.tell(probe.getRef(), getRef());
                // await the correct response
                expectMsg(Duration.ofSeconds(1), "done");

                // the run() method needs to finish within 3 seconds
                within(
                        Duration.ofSeconds(3),
                        () -> {
                            subject.tell("hello", getRef());

                            // This is a demo: would normally use expectMsgEquals().
                            // Wait time is bounded by 3-second deadline above.
                            awaitCond(probe::msgAvailable);

                            // response must have been enqueued to us before probe
                            expectMsg(Duration.ZERO, "world");
                            // check that the probe we injected earlier got the msg
                            probe.expectMsg(Duration.ZERO, "hello");
                            assertEquals(getRef(), probe.getLastSender());

                            // Will wait for the rest of the 3 seconds
                            expectNoMessage();
                            return null;
                        });
            }
        };
    }

    @Test
    public void testReplyWithEmptyReadingIfNoWellIsKnown() {
        TestKit probe = new TestKit(system);
        ActorRef artefactActor = system.actorOf(Artefact.props("group", "artefact"));
        artefactActor.tell(new Artefact.ReadWell(42L), probe.getRef());
        Artefact.RespondWell response = probe.expectMsgClass(Artefact.RespondWell.class);
        assertEquals(42L, response.requestId);
        assertEquals(Optional.empty(), response.value);
    }

    @Test
    public void testReplyWithLatestWellReading() {
        TestKit probe = new TestKit(system);
        ActorRef artefactActor = system.actorOf(Artefact.props("group", "artefact"));

        artefactActor.tell(new Artefact.RecordWell(1L, 24.0), probe.getRef());
        assertEquals(1L, probe.expectMsgClass(Artefact.WellRecorded.class).requestId);

        artefactActor.tell(new Artefact.ReadWell(2L), probe.getRef());
        Artefact.RespondWell response1 = probe.expectMsgClass(Artefact.RespondWell.class);
        assertEquals(2L, response1.requestId);
        assertEquals(Optional.of(24.0), response1.value);

        artefactActor.tell(new Artefact.RecordWell(3L, 55.0), probe.getRef());
        assertEquals(3L, probe.expectMsgClass(Artefact.WellRecorded.class).requestId);

        artefactActor.tell(new Artefact.ReadWell(4L), probe.getRef());
        Artefact.RespondWell response2 = probe.expectMsgClass(Artefact.RespondWell.class);
        assertEquals(4L, response2.requestId);
        assertEquals(Optional.of(55.0), response2.value);
    }

    @Test
    public void testReplyToRegistrationRequests() {
        TestKit probe = new TestKit(system);
        ActorRef artefactActor = system.actorOf(Artefact.props("group", "artefact"));

        artefactActor.tell(new ArtefactManager.RequestTrackArtefact("group", "artefact"), probe.getRef());
        probe.expectMsgClass(ArtefactManager.ArtefactRegistered.class);
        assertEquals(artefactActor, probe.getLastSender());
    }

    @Test
    public void testIgnoreWrongRegistrationRequests() {
        TestKit probe = new TestKit(system);
        ActorRef artefactActor = system.actorOf(Artefact.props("group", "artefact"));

        artefactActor.tell(new ArtefactManager.RequestTrackArtefact("wrongGroup", "artefact"), probe.getRef());
        probe.expectNoMessage();

        artefactActor.tell(new ArtefactManager.RequestTrackArtefact("group", "wrongArtefact"), probe.getRef());
        probe.expectNoMessage();
    }

    @Test
    public void testRegisterArtefactActor() {
        TestKit probe = new TestKit(system);
        ActorRef groupActor = system.actorOf(ArtefactGroup.props("group"));

        groupActor.tell(new ArtefactManager.RequestTrackArtefact("group", "artefact1"), probe.getRef());
        probe.expectMsgClass(ArtefactManager.ArtefactRegistered.class);
        ActorRef artefactActor1 = probe.getLastSender();

        groupActor.tell(new ArtefactManager.RequestTrackArtefact("group", "artefact2"), probe.getRef());
        probe.expectMsgClass(ArtefactManager.ArtefactRegistered.class);
        ActorRef artefactActor2 = probe.getLastSender();
        assertNotEquals(artefactActor1, artefactActor2);

        artefactActor1.tell(new Artefact.RecordWell(0L, 1.0), probe.getRef());
        assertEquals(0L, probe.expectMsgClass(Artefact.WellRecorded.class).requestId);
        artefactActor2.tell(new Artefact.RecordWell(1L, 2.0), probe.getRef());
        assertEquals(1L, probe.expectMsgClass(Artefact.WellRecorded.class).requestId);
    }

    @Test
    public void testIgnoreRequestsForWrongGroupId() {
        TestKit probe = new TestKit(system);
        ActorRef groupActor = system.actorOf(ArtefactGroup.props("group"));

        groupActor.tell(new ArtefactManager.RequestTrackArtefact("wrongGroup", "artefact1"), probe.getRef());
        probe.expectNoMessage();
    }

    @Test
    public void testReturnSameActorForSameArtefactId() {
        TestKit probe = new TestKit(system);
        ActorRef groupActor = system.actorOf(ArtefactGroup.props("group"));

        groupActor.tell(new ArtefactManager.RequestTrackArtefact("group", "artefact1"), probe.getRef());
        probe.expectMsgClass(ArtefactManager.ArtefactRegistered.class);
        ActorRef artefactActor1 = probe.getLastSender();

        groupActor.tell(new ArtefactManager.RequestTrackArtefact("group", "artefact1"), probe.getRef());
        probe.expectMsgClass(ArtefactManager.ArtefactRegistered.class);
        ActorRef artefactActor2 = probe.getLastSender();
        assertEquals(artefactActor1, artefactActor2);
    }

    @Test
    public void testReturnWellValueForWorkingArtefacts() {
        TestKit requester = new TestKit(system);

        TestKit artefact1 = new TestKit(system);
        TestKit artefact2 = new TestKit(system);

        Map<ActorRef, String> actorToArtefactId = new HashMap<>();
        actorToArtefactId.put(artefact1.getRef(), "artefact1");
        actorToArtefactId.put(artefact2.getRef(), "artefact2");

        ActorRef queryActor =
                system.actorOf(
                        ArtefactGroupQuery.props(
                                actorToArtefactId, 1L, requester.getRef(), new FiniteDuration(3, TimeUnit.SECONDS)));

        assertEquals(0L, artefact1.expectMsgClass(Artefact.ReadWell.class).requestId);
        assertEquals(0L, artefact2.expectMsgClass(Artefact.ReadWell.class).requestId);

        queryActor.tell(new Artefact.RespondWell(0L, Optional.of(1.0)), artefact1.getRef());
        queryActor.tell(new Artefact.RespondWell(0L, Optional.of(2.0)), artefact2.getRef());

        ArtefactGroup.RespondAllWells response =
                requester.expectMsgClass(ArtefactGroup.RespondAllWells.class);
        assertEquals(1L, response.requestId);

        Map<String, ArtefactGroup.WellReading> expectedWells = new HashMap<>();
        expectedWells.put("artefact1", new ArtefactGroup.Well(1.0));
        expectedWells.put("artefact2", new ArtefactGroup.Well(2.0));

        assertEquals(expectedWells, response.wells);
    }

    @Test
    public void testReturnWellNotAvailableForArtefactsWithNoReadings() {
        TestKit requester = new TestKit(system);

        TestKit artefact1 = new TestKit(system);
        TestKit artefact2 = new TestKit(system);

        Map<ActorRef, String> actorToArtefactId = new HashMap<>();
        actorToArtefactId.put(artefact1.getRef(), "artefact1");
        actorToArtefactId.put(artefact2.getRef(), "artefact2");

        ActorRef queryActor =
                system.actorOf(
                        ArtefactGroupQuery.props(
                                actorToArtefactId, 1L, requester.getRef(), new FiniteDuration(3, TimeUnit.SECONDS)));

        assertEquals(0L, artefact1.expectMsgClass(Artefact.ReadWell.class).requestId);
        assertEquals(0L, artefact2.expectMsgClass(Artefact.ReadWell.class).requestId);

        queryActor.tell(new Artefact.RespondWell(0L, Optional.empty()), artefact1.getRef());
        queryActor.tell(new Artefact.RespondWell(0L, Optional.of(2.0)), artefact2.getRef());

        ArtefactGroup.RespondAllWells response =
                requester.expectMsgClass(ArtefactGroup.RespondAllWells.class);
        assertEquals(1L, response.requestId);

        Map<String, ArtefactGroup.WellReading> expectedWells = new HashMap<>();
        expectedWells.put("artefact1", ArtefactGroup.WellNotAvailable.INSTANCE);
        expectedWells.put("artefact2", new ArtefactGroup.Well(2.0));

        assertEquals(expectedWells, response.wells);
    }

    @Test
    public void testReturnArtefactNotAvailableIfArtefactStopsBeforeAnswering() {
        TestKit requester = new TestKit(system);

        TestKit artefact1 = new TestKit(system);
        TestKit artefact2 = new TestKit(system);

        Map<ActorRef, String> actorToArtefactId = new HashMap<>();
        actorToArtefactId.put(artefact1.getRef(), "artefact1");
        actorToArtefactId.put(artefact2.getRef(), "artefact2");

        ActorRef queryActor =
                system.actorOf(
                        ArtefactGroupQuery.props(
                                actorToArtefactId, 1L, requester.getRef(), new FiniteDuration(3, TimeUnit.SECONDS)));

        assertEquals(0L, artefact1.expectMsgClass(Artefact.ReadWell.class).requestId);
        assertEquals(0L, artefact2.expectMsgClass(Artefact.ReadWell.class).requestId);

        queryActor.tell(new Artefact.RespondWell(0L, Optional.of(1.0)), artefact1.getRef());
        artefact2.getRef().tell(PoisonPill.getInstance(), ActorRef.noSender());

        ArtefactGroup.RespondAllWells response =
                requester.expectMsgClass(ArtefactGroup.RespondAllWells.class);
        assertEquals(1L, response.requestId);

        Map<String, ArtefactGroup.WellReading> expectedWells = new HashMap<>();
        expectedWells.put("artefact1", new ArtefactGroup.Well(1.0));
        expectedWells.put("artefact2", ArtefactGroup.ArtefactNotAvailable.INSTANCE);

        assertEquals(expectedWells, response.wells);
    }

    @Test
    public void testReturnWellReadingEvenIfArtefactStopsAfterAnswering() {
        TestKit requester = new TestKit(system);

        TestKit artefact1 = new TestKit(system);
        TestKit artefact2 = new TestKit(system);

        Map<ActorRef, String> actorToArtefactId = new HashMap<>();
        actorToArtefactId.put(artefact1.getRef(), "artefact1");
        actorToArtefactId.put(artefact2.getRef(), "artefact2");

        ActorRef queryActor =
                system.actorOf(
                        ArtefactGroupQuery.props(
                                actorToArtefactId, 1L, requester.getRef(), new FiniteDuration(3, TimeUnit.SECONDS)));

        assertEquals(0L, artefact1.expectMsgClass(Artefact.ReadWell.class).requestId);
        assertEquals(0L, artefact2.expectMsgClass(Artefact.ReadWell.class).requestId);

        queryActor.tell(new Artefact.RespondWell(0L, Optional.of(1.0)), artefact1.getRef());
        queryActor.tell(new Artefact.RespondWell(0L, Optional.of(2.0)), artefact2.getRef());
        artefact2.getRef().tell(PoisonPill.getInstance(), ActorRef.noSender());

        ArtefactGroup.RespondAllWells response =
                requester.expectMsgClass(ArtefactGroup.RespondAllWells.class);
        assertEquals(1L, response.requestId);

        Map<String, ArtefactGroup.WellReading> expectedWells = new HashMap<>();
        expectedWells.put("artefact1", new ArtefactGroup.Well(1.0));
        expectedWells.put("artefact2", new ArtefactGroup.Well(2.0));

        assertEquals(expectedWells, response.wells);
    }

    @Test
    public void testReturnArtefactTimedOutIfArtefactDoesNotAnswerInTime() {
        TestKit requester = new TestKit(system);

        TestKit artefact1 = new TestKit(system);
        TestKit artefact2 = new TestKit(system);

        Map<ActorRef, String> actorToArtefactId = new HashMap<>();
        actorToArtefactId.put(artefact1.getRef(), "artefact1");
        actorToArtefactId.put(artefact2.getRef(), "artefact2");

        ActorRef queryActor =
                system.actorOf(
                        ArtefactGroupQuery.props(
                                actorToArtefactId, 1L, requester.getRef(), new FiniteDuration(1, TimeUnit.SECONDS)));

        assertEquals(0L, artefact1.expectMsgClass(Artefact.ReadWell.class).requestId);
        assertEquals(0L, artefact2.expectMsgClass(Artefact.ReadWell.class).requestId);

        queryActor.tell(new Artefact.RespondWell(0L, Optional.of(1.0)), artefact1.getRef());

        ArtefactGroup.RespondAllWells response =
                requester.expectMsgClass(
                        java.time.Duration.ofSeconds(5), ArtefactGroup.RespondAllWells.class);
        assertEquals(1L, response.requestId);

        Map<String, ArtefactGroup.WellReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put("artefact1", new ArtefactGroup.Well(1.0));
        expectedTemperatures.put("artefact2", ArtefactGroup.ArtefactTimedOut.INSTANCE);

        assertEquals(expectedTemperatures, response.wells);
    }

    @Test
    public void testCollectWellsFromAllActiveArtefacts() {
        TestKit probe = new TestKit(system);
        ActorRef groupActor = system.actorOf(ArtefactGroup.props("group"));

        groupActor.tell(new ArtefactManager.RequestTrackArtefact("group", "artefact1"), probe.getRef());
        probe.expectMsgClass(ArtefactManager.ArtefactRegistered.class);
        ActorRef artefactActor1 = probe.getLastSender();

        groupActor.tell(new ArtefactManager.RequestTrackArtefact("group", "artefact2"), probe.getRef());
        probe.expectMsgClass(ArtefactManager.ArtefactRegistered.class);
        ActorRef artefactActor2 = probe.getLastSender();

        groupActor.tell(new ArtefactManager.RequestTrackArtefact("group", "artefact3"), probe.getRef());
        probe.expectMsgClass(ArtefactManager.ArtefactRegistered.class);
        ActorRef artefactActor3 = probe.getLastSender();

        // Check that the artefact actors are working
        artefactActor1.tell(new Artefact.RecordWell(0L, 1.0), probe.getRef());
        assertEquals(0L, probe.expectMsgClass(Artefact.WellRecorded.class).requestId);
        artefactActor2.tell(new Artefact.RecordWell(1L, 2.0), probe.getRef());
        assertEquals(1L, probe.expectMsgClass(Artefact.WellRecorded.class).requestId);
        // No well for artefact 3

        groupActor.tell(new ArtefactGroup.RequestAllWells(0L), probe.getRef());
        ArtefactGroup.RespondAllWells response =
                probe.expectMsgClass(ArtefactGroup.RespondAllWells.class);
        assertEquals(0L, response.requestId);

        Map<String, ArtefactGroup.WellReading> expectedWells = new HashMap<>();
        expectedWells.put("artefact1", new ArtefactGroup.Well(1.0));
        expectedWells.put("artefact2", new ArtefactGroup.Well(2.0));
        expectedWells.put("artefact3", ArtefactGroup.WellNotAvailable.INSTANCE);

        assertEquals(expectedWells, response.wells);
    }
}