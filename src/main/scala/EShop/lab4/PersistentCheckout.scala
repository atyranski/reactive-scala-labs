package EShop.lab4

import EShop.lab2.TypedCartActor
import EShop.lab3.{OrderManager, Payment}
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import scala.concurrent.duration._

class PersistentCheckout {

  import EShop.lab2.TypedCheckout._

  val timerDuration: FiniteDuration = 1.seconds

  def schedule(
    context: ActorContext[Command]
  ): Cancellable = context.scheduleOnce(timerDuration, context.self, CancelCheckout)

  def apply(
     cartActor: ActorRef[TypedCartActor.Command],
     persistenceId: PersistenceId
  ): Behavior[Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior(
        persistenceId,
        WaitingForStart,
        commandHandler(context, cartActor),
        eventHandler(context)
      )
    }

  def commandHandler(
    context: ActorContext[Command],
    cartActor: ActorRef[TypedCartActor.Command]
  ): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case WaitingForStart => {
        command match {
          case StartCheckout =>       Effect.persist(CheckoutStarted)
          case _ =>                   Effect.unhandled
        }
      }
      case SelectingDelivery(timer) => {
        command match {
          case SelectDeliveryMethod(deliveryMethod) => {
            Effect.persist(DeliveryMethodSelected(deliveryMethod))
              .thenRun(_ => timer.cancel())
          }
          case CancelCheckout => {
            cartActor ! TypedCartActor.ConfirmCheckoutCancelled

            Effect.persist(CheckoutCancelled)
              .thenRun(_ => timer.cancel())
          }
          case ExpireCheckout => {
            cartActor ! TypedCartActor.ConfirmCheckoutCancelled

            Effect.persist(CheckoutCancelled)
              .thenRun(_ => timer.cancel())
          }
          case _ => Effect.unhandled
        }
      }
      case SelectingPaymentMethod(timer) => {
        command match {
          case SelectPayment(paymentMethod, orderManagerRef) => {
            val payment = context.spawn(new Payment(paymentMethod, orderManagerRef, context.self).start, "paymentActor")

            orderManagerRef ! OrderManager.ConfirmPaymentStarted(payment)

            Effect.persist(PaymentStarted(payment.ref))
              .thenRun(_ => timer.cancel())
          }
          case CancelCheckout => {
            cartActor ! TypedCartActor.ConfirmCheckoutCancelled

            Effect.persist(CheckoutCancelled)
              .thenRun(_ => timer.cancel())
          }
          case ExpireCheckout => {
            cartActor ! TypedCartActor.ConfirmCheckoutCancelled

            Effect.persist(CheckoutCancelled)
              .thenRun(_ => timer.cancel())
          }
          case _ => Effect.unhandled
        }
      }
      case ProcessingPayment(timer) => {
        command match {
          case ConfirmPaymentReceived => {
            cartActor ! TypedCartActor.ConfirmCheckoutClosed

            Effect.persist(CheckOutClosed)
              .thenRun(_ => timer.cancel())
          }
          case CancelCheckout => {
            cartActor ! TypedCartActor.ConfirmCheckoutCancelled

            Effect.persist(CheckoutCancelled)
              .thenRun(_ => timer.cancel())
          }
          case ExpirePayment => {
            cartActor ! TypedCartActor.ConfirmCheckoutCancelled

            Effect.persist(CheckOutClosed)
              .thenRun(_ => timer.cancel())
          }
          case _ =>         Effect.unhandled
        }
      }
      case Cancelled =>     Effect.unhandled
      case Closed =>        Effect.unhandled
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    state match {
      case WaitingForStart           => {
        event match {
          case CheckoutStarted => SelectingDelivery(schedule(context))
          case _               => state
        }
      }
      case SelectingDelivery(_) => {
        event match {
          case DeliveryMethodSelected(_) => SelectingPaymentMethod(schedule(context))
          case CheckoutCancelled => Cancelled
          case _ => state
        }

      }
      case SelectingPaymentMethod(_) => {
        event match {
          case PaymentStarted(_) => ProcessingPayment(schedule(context))
          case CheckoutCancelled => Cancelled
          case _ => state
        }
      }
      case ProcessingPayment(_) => {
        event match {
          case CheckOutClosed                 => Closed
          case CheckoutCancelled              => Cancelled
          case _                              => state
        }
      }
      case Closed                             => state
      case Cancelled                          => state
    }
  }
}
