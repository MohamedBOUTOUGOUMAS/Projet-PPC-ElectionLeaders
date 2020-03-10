package upmc.akka.leader

import akka.actor._

abstract class NodeStatus
case class Passive () extends NodeStatus
case class Candidate () extends NodeStatus
case class Dummy () extends NodeStatus
case class Waiting () extends NodeStatus
case class Leader () extends NodeStatus

abstract class LeaderAlgoMessage
case class Initiate () extends LeaderAlgoMessage
case class ALG (list:List[Int], nodeId:Int) extends LeaderAlgoMessage
case class AVS (list:List[Int], nodeId:Int) extends LeaderAlgoMessage
case class AVSRSP (list:List[Int], nodeId:Int) extends LeaderAlgoMessage

case class StartWithNodeList (list:List[Int])

class ElectionActor (val id:Int, val terminaux:List[Terminal]) extends Actor {

     val father = context.parent
     var nodesAlive:List[Int] = List(id)

     var candSucc:Int = -1
     var candPred:Int = -1
     var status:NodeStatus = new Passive

     def receive = {

          // Initialisation
          case Start => {
            self ! Initiate
          }

          case StartWithNodeList (list) => {
               if (list.isEmpty) {
                    this.nodesAlive = this.nodesAlive:::List(id)
               }
               else {
                    this.nodesAlive = list
               }

               // Debut de l'algorithme d'election
               self ! Initiate
          }

          case Initiate => {
            status = new Candidate
            candSucc = -1
            candPred = -1
            self ! ALG(nodesAlive, id)
          }

          case ALG (list, init) => {
            if (status.isInstanceOf[Passive]) {
              status = new Dummy;
              self ! ALG(list, init);
            }
            if(status.isInstanceOf[Candidate]) {
              candPred = init
              if(id > init){
                   if(candSucc == -1) {
                        status = new Waiting
                        self ! AVS(list, init)
                   } else {
                        self ! AVSRSP(list, candSucc)
                        status = new Dummy
                   }
              }
              if (init == id) {
                   status = new Leader
              };
            }
          }

          case AVS (list, j) => {
               if (status.isInstanceOf[Candidate]) {
                    if (candPred == -1) candSucc = j;
                    else {
                         self ! AVSRSP(list, j);
                         status = new Dummy
                    }
               }
               if (status.isInstanceOf[Waiting]) candSucc = j;
          }

          case AVSRSP (list, k) => {
               if (status.isInstanceOf[Waiting]){
                    if(id == k){
                         status = new Leader
                    }
                    else {
                         candPred = k;
                         if(k<id){
                              status = new Waiting
                              self ! AVS(list, k);
                         }else {
                              status = new Dummy
                              self ! AVSRSP(list, candSucc);
                         }
                    }
               }
          }

     }

}
