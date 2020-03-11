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

case class ALG(i: Int, j: Int) extends LeaderAlgoMessage

case class AVS(i: Int, j: Int) extends LeaderAlgoMessage

case class AVSRSP(i: Int, j: Int) extends LeaderAlgoMessage

case class StartWithNodeList(list: List[Int])

class ElectionActor(val id: Int, val terminaux: List[Terminal]) extends Actor {

    val father = context.parent
    var nodesAlive: List[Int] = List(id)

    //var candSucc: Int = -1
    //var candPred: Int = -1
    //var status: NodeStatus = new Passive
    var status = scala.collection.mutable.Map[Int, NodeStatus]()
    var candPred = scala.collection.mutable.Map[Int, Int]()
    var candSucc = scala.collection.mutable.Map[Int, Int]()

    def receive = {

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
            nodesAlive.foreach(n => {
                status += (n -> new Passive)
                candSucc += (n -> -1)
                candPred += (n -> -1)
            })
            self ! Initiate
        }

        case Initiate => {
            father ! Message("Election Start !")
            status(id) = new Candidate
            val nei = (nodesAlive.indexOf(id)+1)%nodesAlive.size
            self ! ALG(id, nodesAlive(nei))
        }

        case ALG(i, init) => {
            if (status(i).isInstanceOf[Passive]) {
                status(i) = new Dummy
                val neigh = (nodesAlive.indexOf(init) + 1) % nodesAlive.size
                self ! ALG(init, nodesAlive(neigh))
            }
            if (status(i).isInstanceOf[Candidate]) {
                candPred(i) = init
                if (i > init) {
                    if (candSucc(i) == -1) {
                        status(i) = new Waiting
                        self ! AVS(i, init)
                    } else {
                        self ! AVSRSP(candPred(i), candSucc(i))
                        status(i) = new Dummy
                    }
                }
                if (init == i) {
                    status(i) = new Leader
                    father ! Message("LeaderChanged " + i)
                }
            }
        }

        case AVS(i, j) => {

            if (status(i).isInstanceOf[Candidate]) {
                if (candPred(i) == -1) candSucc(i) = j
                else {
                    self ! AVSRSP(candPred(i), j)
                    status(i) = new Dummy
                }
            }
            if (status(i).isInstanceOf[Waiting]) candSucc(i) = j
        }

        case AVSRSP(i, k) => {
            if (status(i).isInstanceOf[Waiting]) {
                if (i == k) {
                    status(i) = new Leader
                    father ! Message("LeaderChanged " + i)
                }
                else {
                    candPred(i) = k
                    if(candSucc(i) == -1){
                        if (k < i) {
                            status(i) = new Waiting
                            self ! AVS(i, k)
                        }
                    } else {
                        status(i) = new Dummy
                        self ! AVSRSP(k, candSucc(i))
                    }
                }
            }
        }

    }

}
