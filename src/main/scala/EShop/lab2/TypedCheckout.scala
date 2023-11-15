package EShop.lab2

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.language.postfixOps
import scala.concurrent.duration._
import EShop.lab3.{OrderManager, Payment}

object TypedCheckout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                                                                  extends Command
  case class SelectDeliveryMethod(method: String)                                            extends Command
  case object CancelCheckout                                                                 extends Command
  case object ExpireCheckout                                                                 extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ExpirePayment                                                                  extends Command
  case object ConfirmPaymentReceived                                                         extends Command

  sealed trait Event
  case object CheckOutClosed                           extends Event
  case class PaymentStarted(paymentRef: ActorRef[Any]) extends Event
}

class TypedCheckout(
  cartActor: ActorRef[TypedCartActor.Command]
) {
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
        context.log.info("Checkout started.")
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

        cartActor ! TypedCartActor.ConfirmCheckoutCancelled

        cancelled
      }
      case ExpireCheckout => {
        context.log.warn(s"Your checkout wasn't updated for ${checkoutTimerDuration} and it expired.")
        timer.cancel()

        cartActor ! TypedCartActor.ConfirmCheckoutCancelled

        cancelled
      }
    }
  )

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, message) => message match {
      case SelectPayment(paymentMethod: String, orderManagerRef: ActorRef[OrderManager.Command]) => {
        context.log.info(s"Selected payment method '${paymentMethod}'.")
        timer.cancel()

        val payment = context.spawn(
          new Payment(paymentMethod, orderManagerRef, context.self).start, "paymentActor")

        orderManagerRef ! OrderManager.ConfirmPaymentStarted(payment)

        processingPayment(paymentTimer(context))
      }
      case CancelCheckout => {
        context.log.info("Checkout cancelled.")
        timer.cancel()

        cartActor ! TypedCartActor.ConfirmCheckoutCancelled

        cancelled
      }
      case ExpireCheckout => {
        context.log.warn(s"Your checkout wasn't updated for ${checkoutTimerDuration} and it expired.")
        timer.cancel()

        cartActor ! TypedCartActor.ConfirmCheckoutCancelled

        cancelled
      }
    }
  )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, message) => message match {
      case ConfirmPaymentReceived => {
        context.log.info("Confirmed payment.")
        timer.cancel()

        cartActor ! TypedCartActor.ConfirmCheckoutClosed

        closed
      }
      case CancelCheckout => {
        context.log.info("Checkout cancelled.")
        timer.cancel()

        cartActor ! TypedCartActor.ConfirmCheckoutCancelled

        cancelled
      }
      case ExpirePayment => {
        context.log.warn("Your payment wasn't updated for ${paymentTimerDuration} and it expired.")
        timer.cancel()

        cartActor ! TypedCartActor.ConfirmCheckoutCancelled

        cancelled
      }
    }
  )

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.stopped

  def closed: Behavior[TypedCheckout.Command] = Behaviors.stopped

}
