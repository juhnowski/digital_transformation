package ex4;


import java.io.IOException;

import akka.actor.ActorSystem;
import akka.actor.ActorRef;

public class PabApp {

    public static void main(String[] args) throws IOException {
        ActorSystem system = ActorSystem.create("pab-system");

        try {
            ActorRef supervisor = system.actorOf(PabSupervisor.props(), "iot-supervisor");

            System.out.println("Press ENTER to exit the system");
            System.in.read();
        } finally {
            system.terminate();
        }
    }
}
