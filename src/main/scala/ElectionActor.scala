package upmc.akka.leader

import akka.actor._

abstract class NodeStatus

case class Passive() extends NodeStatus

case class Candidate() extends NodeStatus

case class Dummy() extends NodeStatus

case class Waiting() extends NodeStatus

case class Leader() extends NodeStatus

abstract class LeaderAlgoMessage

case class Initiate() extends LeaderAlgoMessage

case class ALG(list : List[Int], init: Int) extends LeaderAlgoMessage

case class AVS(list: List[Int], j: Int) extends LeaderAlgoMessage

case class AVSRSP(list: List[Int], k: Int) extends LeaderAlgoMessage

case class StartWithNodeList(list: List[Int])

class ElectionActor(val id: Int, val terminaux: List[Terminal]) extends Actor {

    val father = context.parent
    var nodesAlive: List[Int] = List(id)

    var status: NodeStatus = new Passive
    var candPred: Int = -1
    var candSucc: Int = -1
    var electionActorNeigh: ActorSelection = null


    def receive: PartialFunction[Any, Unit] = {

        // Initialisation
        case Start => {
            self ! Initiate
        }

        case StartWithNodeList(list) => {
            if (list.isEmpty) {
                this.nodesAlive = this.nodesAlive ::: List(id)
            }
            else {
                this.nodesAlive = list
            }
            status = new Candidate
            self ! Initiate
        }

        case Initiate => {
            if (nodesAlive.size > 0){
                father ! Message("Election start !")
                val nei = 1 % nodesAlive.size
                terminaux.foreach(n => {
                    if (n.id == nodesAlive(nei)) {
                        electionActorNeigh = context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node/electionActor")
                    }
                })
                electionActorNeigh ! ALG(nodesAlive, nodesAlive(0))
            }
        }

        case ALG(list, init) => {
            if (status.isInstanceOf[Passive]) {
                status = new Dummy
                val neigh = (list.indexOf(id) + 1) % list.size
                terminaux.foreach(n => {
                    if (n.id == list(neigh)) {
                        electionActorNeigh = context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node/electionActor")
                    }
                })
                electionActorNeigh ! ALG(list, init)
            }
            if (status.isInstanceOf[Candidate]) {
                candPred = init
                if (id > init) {
                    if (candSucc == -1) {
                        status = new Waiting
                        self ! AVS(list, init)
                    } else {
                        terminaux.foreach(n => {
                            if (n.id == init) {
                                electionActorNeigh = context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node/electionActor")
                            }
                        })
                        electionActorNeigh ! AVSRSP(list, candSucc)
                        status = new Dummy
                    }
                }
                if (init == id) {
                    status = new Leader
                    father ! Message("LeaderChanged " + id)
                }
            }
        }

        case AVS(list, j) => {
            if (status.isInstanceOf[Candidate]) {
                if (candPred == -1) candSucc = j
                else {
                    terminaux.foreach(n => {
                        if (n.id == candPred) {
                            electionActorNeigh = context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node/electionActor")
                        }
                    })
                    electionActorNeigh ! AVSRSP(list, j)
                    status = new Dummy
                }
            }
            if (status.isInstanceOf[Waiting]) candSucc = j
        }

        case AVSRSP(list, k) => {
            if (status.isInstanceOf[Waiting]) {
                if (id == k) {
                    status = new Leader
                    father ! Message("LeaderChanged " + id)
                }
                else {
                    candPred = k
                    if(candSucc == -1){
                        if (k < id) {
                            status = new Waiting
                            self ! AVS(list, k)
                        }
                    } else {
                        status = new Dummy
                        terminaux.foreach(n => {
                            if (n.id == k) {
                                electionActorNeigh = context.actorSelection("akka.tcp://LeaderSystem" + n.id + "@" + n.ip + ":" + n.port + "/user/Node/electionActor")
                            }
                        })
                        electionActorNeigh ! AVSRSP(list, candSucc)
                    }
                }
            }
        }

    }

}
