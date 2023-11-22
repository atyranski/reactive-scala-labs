package EShop.lab4

import EShop.lab2.{Cart, TypedCheckout}
import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}

import scala.concurrent.duration._

class PersistentCartActor {

  import EShop.lab2.TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5.seconds

  private def scheduleTimer(
       context: ActorContext[Command]
  ): Cancellable = context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def apply(
     persistenceId: PersistenceId
  ): Behavior[Command] = Behaviors.setup {
    context => {
      EventSourcedBehavior[Command, Event, State](
        persistenceId,
        Empty,
        commandHandler(context),
        eventHandler(context)
      ).withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))
    }
  }

  def commandHandler(
    context: ActorContext[Command]
  ): (State, Command) => Effect[Event, State] =
    (state, command) => {
      state match {
        case Empty =>
          command match {
            case AddItem(item)            => Effect.persist(ItemAdded(item))
            case _                        => Effect.unhandled
          }

        case NonEmpty(cart, _) =>
          command match {
            case AddItem(item)            => Effect.persist(ItemAdded(item))
            case RemoveItem(item) if cart.contains(item) => {
              if (cart.size == 1) Effect.persist(CartEmptied)
              else Effect.persist(ItemRemoved(item))
            }
            case StartCheckout(replyTo) => {
              val checkout = context.spawn(new TypedCheckout(context.self).start, "checkoutActor")

              Effect.persist(CheckoutStarted(checkout))
                .thenRun(_ => replyTo ! OrderManager.ConfirmCheckoutStarted(checkout.ref))
            }
            case ExpireCart               => Effect.persist(CartExpired)
            case _                        => Effect.unhandled
          }

        case InCheckout(_) =>
          command match {
            case ConfirmCheckoutCancelled => Effect.persist(CheckoutCancelled).thenRun(_ => context.self ! ExpireCart)
            case ConfirmCheckoutClosed    => Effect.persist(CheckoutClosed).thenRun(_ => context.self ! ExpireCart)
            case _                        => Effect.unhandled
          }
      }
  }

  def eventHandler(
    context: ActorContext[Command]
  ): (State, Event) => State =
    (state, event) => {
      state match {
        case Empty =>
          event match {
            case ItemAdded(item)                    => NonEmpty(Cart(Seq(item)), scheduleTimer(context))
            case _                                  => Empty
          }

        case NonEmpty(cart, timer) =>
          event match {
            case ItemAdded(item)                    => NonEmpty(cart.addItem(item), timer)
            case ItemRemoved(_) if cart.size == 1   => Empty
            case ItemRemoved(item) if cart.size > 0 => NonEmpty(cart.removeItem(item), timer)
            case CartEmptied | CartExpired          => Empty
            case CheckoutStarted(_)                 => InCheckout(cart)
            case _                                  => state
          }

        case InCheckout(cart) =>
          event match {
            case CheckoutCancelled                  => NonEmpty(cart, scheduleTimer(context))
            case CheckoutClosed                     => Empty
            case _                                  => state
          }
      }
  }

}
