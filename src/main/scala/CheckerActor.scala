package upmc.akka.leader

import java.util.Date

import akka.actor._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

abstract class Tick

case class CheckerTick() extends Tick

class CheckerActor(val id: Int, val terminaux: List[Terminal], electionActor: ActorRef) extends Actor {

    var time: Int = 200
    val father = context.parent

    var nodesAlive: List[Int] = List()
    var datesForChecking: List[Date] = List()
    var oldNbAlive: Int = -1
    var leader: Int = -1

    def receive = {

        // Initialisation
        case Start => {
            self ! CheckerTick
        }

        // A chaque fois qu'on recoit un Beat : on met a jour la liste des nodes
        case IsAlive(nodeId) => {
            if (!nodesAlive.contains(nodeId)) {
                val now = new Date().getTime
                nodesAlive = nodeId :: nodesAlive
                datesForChecking = new Date(now + (time * 3)) :: datesForChecking
            }
            else {
                val now = new Date().getTime
                val index = nodesAlive.indexOf(nodeId)
                val newDate = new Date(now + (time * 3))
                datesForChecking = datesForChecking.patch(index, Seq(newDate), 1)
            }
        }

        case IsAliveLeader(nodeId) => {
            if(nodeId == leader){
                self ! IsAlive(nodeId)
            }
        }

        case SetLeader(nodeId) => leader = nodeId

        // A chaque fois qu'on recoit un CheckerTick : on verifie qui est mort ou pas
        // Objectif : lancer l'election si le leader est mort

        case CheckerTick => {
            val scheduler = context.system.scheduler
            scheduler.schedule(0 milliseconds, time milliseconds) {
                var deadIndexes: List[Int] = List()
                datesForChecking.foreach(date => {
                    val now = new Date()
                    if (date.before(now)) {
                        deadIndexes = datesForChecking.indexOf(date) :: deadIndexes
                    }
                })

                deadIndexes.foreach(i => {
                    nodesAlive = nodesAlive diff List(nodesAlive(i))
                    datesForChecking = datesForChecking diff List(datesForChecking(i))
                })

                if (oldNbAlive != nodesAlive.size) {
                    oldNbAlive = nodesAlive.size
                    electionActor ! StartWithNodeList(nodesAlive)
                }

                father ! Message("Lead "+leader)
            }
        }
    }


}
