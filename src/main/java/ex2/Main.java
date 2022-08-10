package ex2;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;


import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {

        ActorSystem system = ActorSystem.create("testSystem");

        ActorRef first = system.actorOf(StartStopActor1.props(), "first");
        first.tell("stop", ActorRef.noSender());

        System.out.println(">>> Press ENTER to exit <<<");
        try {
            System.in.read();
        } finally {
            system.terminate();
        }
        //-----------------

    }
}
