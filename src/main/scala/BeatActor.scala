package upmc.akka.leader

import akka.actor._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

sealed trait BeatMessage

case class Beat(id: Int) extends BeatMessage

case class BeatLeader(id: Int) extends BeatMessage

case class BeatTick() extends Tick

case class LeaderChanged(nodeId: Int)

class BeatActor(val id: Int) extends Actor {

    val time: Int = 500
    val father = context.parent
    var leader: Int = 0 // On estime que le premier Leader est 0

    def receive = {

        // Initialisation
        case Start => {
            self ! BeatTick
            if (this.id == this.leader) {
                father ! Message("I am the leader")
            }
        }

        // Objectif : prevenir tous les autres nodes qu'on est en vie
        case BeatTick => {
            val scheduler = context.system.scheduler
            scheduler.schedule(0 milliseconds, time milliseconds) {
                father ! Message("Beat " + id)
                if (leader == id) father ! Message("BeatLeader " + leader)
            }
        }

        case LeaderChanged(nodeId) => leader = nodeId
    }

}
