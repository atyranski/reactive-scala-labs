package EShop.lab2

import akka.actor.{Actor, ActorSystem, Props}
import akka.event.LoggingReceive

class Shop extends Actor {
  val cart = context.actorOf(Props[CartActor], "cart")

  def receive: Receive = LoggingReceive {
    case CartActor.AddItem(item: Any) => {

    }
  }
}

object ShopApp extends App {
  val system = ActorSystem("Reactive2")
  val shopActor = system.actorOf(Props[Shop], "shopActor")

//  shopActor ! Shop.Init
}
