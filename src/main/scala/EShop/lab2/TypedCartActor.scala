package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.OrderManager

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                                             extends Command
  case class RemoveItem(item: Any)                                          extends Command
  case object ExpireCart                                                    extends Command
  case class StartCheckout(orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ConfirmCheckoutCancelled                                      extends Command
  case object ConfirmCheckoutClosed                                         extends Command
  case class GetItems(sender: ActorRef[Cart])                               extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event
}

class TypedCartActor {

  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable = context.scheduleOnce(
    cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, message) => message match {
      case AddItem(item: Any) => {
        nonEmpty(Cart(Seq(item)), scheduleTimer(context))
      }
    }
  )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, message) => message match {
      case AddItem(item: Any) => {
        context.log.info(s"Added item '${item}' to cart. (cart.size == ${cart.size}")
        timer.cancel()
        nonEmpty(cart.addItem(item), scheduleTimer(context))
      }
      case RemoveItem(item: Any) if cart.size == 1 && cart.contains(item) => {
        context.log.info(s"Removed item '${item}' from cart. It was the last one.")
        timer.cancel()
        empty
      }
      case RemoveItem(item: Any) if cart.size > 1 && cart.contains(item) => {
        context.log.info(s"Removed item '${item}' from cart. (cart.size == ${cart.size}")
        timer.cancel()
        nonEmpty(cart.removeItem(item), scheduleTimer(context))
      }
      case ExpireCart => {
        context.log.warn(s"Your cart was not updated for ${cartTimerDuration} and it expired.")
        timer.cancel()
        empty
      }
      case StartCheckout if cart.size > 0 => {
        context.log.info("Starting checkout.")
        timer.cancel()
        inCheckout(cart)
      }
    }
  )

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, message) => message match {
      case ConfirmCheckoutCancelled => {
        nonEmpty(cart, scheduleTimer(context))
      }
      case ConfirmCheckoutClosed => {
        empty
      }
    }
  )

}
