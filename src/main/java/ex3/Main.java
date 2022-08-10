package ex3;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;


import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {

        ActorSystem system = ActorSystem.create("testSystem");

        ActorRef supervisingActor = system.actorOf(SupervisingActor.props(), "supervising-actor");
        supervisingActor.tell("failChild", ActorRef.noSender());

        System.out.println(">>> Press ENTER to exit <<<");
        try {
            System.in.read();
        } finally {
            system.terminate();
        }
        //-----------------

    }
}

/*

 */