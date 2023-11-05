package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._

object TypedCheckout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ConfirmPaymentReceived              extends Command

  sealed trait Event
  case object CheckOutClosed                        extends Event
  case class PaymentStarted(payment: ActorRef[Any]) extends Event
}

class TypedCheckout {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  private def checkoutTimer(context: ActorContext[Command]): Cancellable = context.scheduleOnce(
    checkoutTimerDuration, context.self, ExpireCheckout)
  private def paymentTimer(context: ActorContext[Command]): Cancellable = context.scheduleOnce(
    paymentTimerDuration, context.self, ExpirePayment)

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, message) => message match {
      case StartCheckout => {
        context.log.info("Starting checkout.")
        selectingDelivery(checkoutTimer(context))
      }
    }
  )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, message) => message match {
      case SelectDeliveryMethod(deliveryMethod: String) => {
        context.log.info(s"Selected delivery method '${deliveryMethod}'.")
        timer.cancel()
        selectingPaymentMethod(checkoutTimer(context))
      }
      case CancelCheckout => {
        context.log.info("Checkout cancelled.")
        timer.cancel()
        cancelled
      }
      case ExpireCheckout => {
        context.log.warn(s"Your checkout wasn't updated for ${checkoutTimerDuration} and it expired.")
        timer.cancel()
        cancelled
      }
    }
  )

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, message) => message match {
      case SelectPayment(paymentMethod: String) => {
        context.log.info(s"Selected payment method '${paymentMethod}'.")
        timer.cancel()
        processingPayment(paymentTimer(context))
      }
      case CancelCheckout => {
        context.log.info("Checkout cancelled.")
        timer.cancel()
        cancelled
      }
      case ExpireCheckout => {
        context.log.warn(s"Your checkout wasn't updated for ${checkoutTimerDuration} and it expired.")
        timer.cancel()
        cancelled
      }
    }
  )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, message) => message match {
      case ConfirmPaymentReceived => {
        context.log.info("Confirmed payment.")
        timer.cancel()
        closed
      }
      case CancelCheckout => {
        context.log.info("Checkout cancelled.")
        timer.cancel()
        cancelled
      }
      case ExpirePayment => {
        context.log.warn("Your payment wasn't updated for ${paymentTimerDuration} and it expired.")
        timer.cancel()
        cancelled
      }
    }
  )

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (_, _) => {
      Behaviors.same
    }
  )

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (_, _) => {
      Behaviors.same
    }
  )

}
