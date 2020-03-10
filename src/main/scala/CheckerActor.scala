package upmc.akka.leader

import java.util.Date

import akka.actor._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

abstract class Tick
case class CheckerTick () extends Tick
case class LeadElected (id:Int)

class CheckerActor (val id:Int, val terminaux:List[Terminal], electionActor:ActorRef) extends Actor {

     var time : Int = 200
     val father = context.parent

     var nodesAlive:List[Int] = List()
     var datesForChecking:List[Date] = List()
     var lastDate:Date = null

     var leader : Int = -1

    def receive = {

         // Initialisation
        case Start => {
          self ! CheckerTick
        }

        case Message (content) => {
          father ! Message (content)
        }
        // A chaque fois qu'on recoit un Beat : on met a jour la liste des nodes
        case IsAlive (nodeId) => {
          lastDate = new Date()
          if (!nodesAlive.contains(nodeId)) {
            nodesAlive.patch(0, Seq(nodeId), 0)
            datesForChecking.patch(0, Seq(new Date()), 0)
          }
          else {
            datesForChecking.patch(nodesAlive.indexOf(nodeId), Seq(new Date()), 1)
          }
        };

        case IsAliveLeader (nodeId) => {
          leader = nodeId
        };

        // A chaque fois qu'on recoit un CheckerTick : on verifie qui est mort ou pas
        // Objectif : lancer l'election si le leader est mort

        case CheckerTick => {
          //val scheduler = context.system.scheduler
          //scheduler.schedule(0 milliseconds, time milliseconds) {
          //scheduler.scheduleOnce(time milliseconds) {}
          father ! Message ("checker " + this.id)
          val deadIndexes: List[Int] = List()
          datesForChecking.foreach(date => {
            val now = new Date()
            if (date.before(now)) deadIndexes.patch(0, Seq(datesForChecking.indexOf(date)), 0)
          })
          if (leader == -1 || deadIndexes.contains(nodesAlive.indexOf(leader))) {
            electionActor ! Start
          }
          deadIndexes.foreach(i => {
            nodesAlive = nodesAlive diff List(nodesAlive.lift(i))
            datesForChecking = datesForChecking diff List(datesForChecking.lift(i))
          })
        }

        case LeadElected (id) => {
          father ! Message("We got a leader !!!!!!")
          leader = id
          father ! LeaderChanged(id)
        }
    }


}
