package upmc.akka.leader

import akka.actor._

case class Start()

sealed trait SyncMessage

case class Sync(nodes: List[Int]) extends SyncMessage

case class SyncForOneNode(nodeId: Int, nodes: List[Int]) extends SyncMessage

sealed trait AliveMessage

case class IsAlive(id: Int) extends AliveMessage

case class IsAliveLeader(id: Int) extends AliveMessage

class Node(val id: Int, val terminaux: List[Terminal]) extends Actor {

    // Les differents acteurs du systeme
    val electionActor = context.actorOf(Props(new ElectionActor(this.id, terminaux)), name = "electionActor")
    val checkerActor = context.actorOf(Props(new CheckerActor(this.id, terminaux, electionActor)), name = "checkerActor")
    val beatActor = context.actorOf(Props(new BeatActor(this.id)), name = "beatActor")
    val displayActor = context.actorOf(Props[DisplayActor], name = "displayActor")

    var allNodes: List[ActorSelection] = List()

    def receive = {

        // Initialisation
        case Start => {
            displayActor ! Message("Node " + this.id + " is created")
            checkerActor ! Start
            beatActor ! Start

            // Initilisation des autres remote, pour communiquer avec eux
            terminaux.foreach(n => {
                if (n.id != id) {
                    val remote = context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node")
                    // Mise a jour de la liste des nodes
                    this.allNodes = this.allNodes ::: List(remote)
                }
            })
        }

        // Envoi de messages (format texte)
        case Message(content) => {
            val c = content.split(" ")
            if (c(0).equals("LeaderChanged")) {
                val leader = c(1).toInt
                self ! LeaderChanged(leader)
            }
            else if (c(0).equals("Beat")) {
                val nodeId = c(1).toInt
                checkerActor ! IsAlive(nodeId)
                self ! Beat(nodeId)
            }
            else if (c(0).equals("BeatLeader")) {
                val nodeId = c(1).toInt
                checkerActor ! IsAliveLeader(nodeId)
                self ! BeatLeader(nodeId)
            }
            else displayActor ! Message(content)
        }

        case BeatLeader(nodeId) => {
            self ! IsAliveLeader(nodeId)
            self ! LeaderChanged(nodeId)
            if(id == nodeId) {
                allNodes.foreach(node => {
                    node ! BeatLeader(nodeId)
                })
            }
        }

        case Beat(nodeId) => {
            self ! IsAlive(nodeId)
            allNodes.foreach(node => {
                node ! IsAlive(nodeId)
            })
        }

        // Messages venant des autres nodes : pour nous dire qui est encore en vie ou mort
        case IsAlive(id) => {
            checkerActor ! IsAlive(id)
        }

        case IsAliveLeader(id) => {
            checkerActor ! IsAliveLeader(id)
        }

        // Message indiquant que le leader a change
        case LeaderChanged(nodeId) => {
            beatActor ! LeaderChanged(nodeId)
            checkerActor ! LeaderChanged(nodeId)
        }

    }

}