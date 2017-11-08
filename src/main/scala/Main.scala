import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, Props, _}
import akka.io.{IO, Tcp}

class TCPServer(port: Int) extends Actor with ActorLogging{

  import Tcp._
  import context.system

  val io = IO(Tcp)
  io ! Bind(self, new InetSocketAddress("localhost", port))
  override def receive: PartialFunction[Any, Unit] = {
    case b@Bound(_) =>
      log.info("bound")
      context.parent ! b

    case CommandFailed(_: Bind) =>
      log.info("failed")
      context stop self

    case c@Connected(remote, local) =>
      log.info(s"connected $remote $local")
      val connection = sender()
      val handler = context.actorOf(Props(classOf[SimplisticHandler], connection))
      connection ! Register(handler)

    case _ =>
      log.info("else")
  }


}


object Main extends App {

  override def main(args: Array[String]): Unit = {
    val port = 8000
    val system = akka.actor.ActorSystem("ServerSystem")
    val serverActor = system.actorOf(Props(new TCPServer(port)))
  }

}