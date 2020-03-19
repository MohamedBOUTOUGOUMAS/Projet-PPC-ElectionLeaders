package upmc.akka.leader

import java.util.Date

import akka.actor._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

abstract class Tick

case class CheckerTick() extends Tick

class CheckerActor(val id: Int, val terminaux: List[Terminal], electionActor: ActorRef) extends Actor {

    var time: Int = 2000
    val father = context.parent

    var nodesAlive: List[Int] = List()
    var datesForChecking = scala.collection.mutable.Map[Int, Date]()
    var candPred = scala.collection.mutable.Map[Int, Int]()

    var oldNbAlive: Int = -1
    var leader: Int = -1

    def receive = {

        // Initialisation
        case Start => {
            self ! CheckerTick
        }

        // A chaque fois qu'on recoit un Beat : on met a jour la liste des nodes
        case IsAlive(nodeId) => {
            //father ! Message("IsAlive "+nodeId)
            if (!nodesAlive.contains(nodeId)) {
                val now = new Date().getTime
                nodesAlive = nodeId :: nodesAlive
                father ! Message("Node "+nodeId+" connected")
                father ! Message("Nodes a live "+nodesAlive)
                datesForChecking += (nodeId -> new Date(now + time))
            }
            else {
                val now = new Date().getTime
                val newDate = new Date(now + time)
                datesForChecking(nodeId) = newDate
            }
            nodesAlive = nodesAlive.sorted
        }

        case IsAliveLeader(nodeId) => {
            //father ! Message("IsAliveLeader "+leader)
            self ! IsAlive(nodeId)
        }

        case LeaderChanged(nodeId) => leader = nodeId
        // A chaque fois qu'on recoit un CheckerTick : on verifie qui est mort ou pas
        // Objectif : lancer l'election si le leader est mort

        case CheckerTick => {
            var leadTmp = leader
            val scheduler = context.system.scheduler
            scheduler.schedule(time milliseconds, time milliseconds) {
                if (leader != leadTmp) {
                    father ! Message("The Leader is "+leader)
                    leadTmp = leader
                }
                var deadIndexes: List[Int] = List()
                val now = new Date()
                for((nodeId, date) <- datesForChecking) {
                    if (date.before(now)) {
                        deadIndexes = nodeId :: deadIndexes
                    }
                }
                deadIndexes.foreach(i => {
                    if(i != leader) father ! Message("Node "+i+" disconnected")
                    else father ! Message("Leader "+i+" disconnected")
                    nodesAlive = nodesAlive diff List(i)
                    datesForChecking = datesForChecking.-(i)
                })

                val leadersNeigh = nodesAlive(0)
                if(deadIndexes.contains(leader) && id == leadersNeigh) {
                    electionActor ! StartWithNodeList(nodesAlive)
                }
            }
        }
    }
}
