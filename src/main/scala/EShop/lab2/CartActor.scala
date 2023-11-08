package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command

  sealed trait Event
  case class ItemAdded(id: String)     extends Event
  case class ItemRemoved(id: String)   extends Event

  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {

  import context._
  import CartActor._

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)
  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer: Cancellable = scheduler.scheduleOnce(
    cartTimerDuration, self, ExpireCart
  )

  def receive: Receive = LoggingReceive {
    empty
  }

  def empty: Receive = LoggingReceive {
    case AddItem(item: Any) => {
      context.become(nonEmpty(Cart.empty.addItem(item), scheduleTimer))
    }
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item: Any) => {
      log.info(s"Added item '${item}' to cart. (cart.size == ${cart.size}")
      timer.cancel()
      context.become(nonEmpty(cart.addItem(item), scheduleTimer))
    }
    case RemoveItem(item: Any) if cart.size == 1 && cart.contains(item) => {
      log.info(s"Removed item '${item}' from cart. It was the last one.")
      timer.cancel()
      context.become(empty)
    }
    case RemoveItem(item: Any) if cart.size > 1 && cart.contains(item) => {
      log.info(s"Removed item '${item}' from cart. (cart.size == ${cart.size}")
      timer.cancel()
      context.become(nonEmpty(cart.removeItem(item), scheduleTimer))
    }
    case ExpireCart => {
      log.warning(s"Your cart was not updated for ${cartTimerDuration} and it expired.")
      timer.cancel()
      context.become(empty)
    }
    case StartCheckout if cart.size > 0 => {
      log.info("Starting checkout.")
      timer.cancel()
      context.become(inCheckout(cart))
    }
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case ConfirmCheckoutCancelled => {
      context.become(nonEmpty(cart, scheduleTimer))
    }
    case ConfirmCheckoutClosed => {
      context.become(empty)
    }
  }

}
