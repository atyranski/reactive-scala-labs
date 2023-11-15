package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor, TypedCheckout}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object OrderManager {

  sealed trait Command
  case class AddItem(id: String, sender: ActorRef[Ack])                                               extends Command
  case class RemoveItem(id: String, sender: ActorRef[Ack])                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command
  case class Buy(sender: ActorRef[Ack])                                                               extends Command
  case class Pay(sender: ActorRef[Ack])                                                               extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef[Payment.Command])                             extends Command
  case object ConfirmPaymentReceived                                                                  extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK
}

class OrderManager {

  import OrderManager._

  def start: Behavior[OrderManager.Command] = {
    Behaviors.setup { context =>
      val cartActor = context.spawn(new TypedCartActor().empty, "cartActor")

      open(cartActor)
    }
  }

  def uninitialized: Behavior[OrderManager.Command] = start

  def open(
    cartActor: ActorRef[TypedCartActor.Command]
  ): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, message) => message match {
      case AddItem(id, sender) => {
        context.log.info(s"Received request to add item (id='${id}').")

        cartActor ! TypedCartActor.AddItem(id)
        sender ! Done

        Behaviors.same
      }
      case RemoveItem(id, sender) => {
        context.log.info(s"Received request to remove item (id='${id}').")

        cartActor ! TypedCartActor.AddItem(id)
        sender ! Done

        Behaviors.same
      }
      case Buy(sender) => {
        context.log.info(s"Received request to start checkout.")

        cartActor ! TypedCartActor.StartCheckout(context.self)

        inCheckout(cartActor, sender)
      }
    }
  )

  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, message) => message match {
      case ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) => {
        context.log.info("Received request to confirm that checkout has been started.")

        cartActorRef ! TypedCartActor.StartCheckout(context.self)
        senderRef ! Done

        inCheckout(checkoutRef)
      }
    }
  )

  def inCheckout(
    checkoutActorRef: ActorRef[TypedCheckout.Command]
  ): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, message) => message match {
      case SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) => {
        context.log.info(s"Received request with delivery and payment selection: {'delivery':'${delivery}', 'payment':'${payment}'}")

        checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
        checkoutActorRef ! TypedCheckout.SelectPayment(payment, context.self)

        inPayment(sender)
      }
    }
  )

  def inPayment(
   senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, message) => message match {
      case ConfirmPaymentStarted(paymentRef) => {
        context.log.info("Received request to confirm that payment has been started.")

        senderRef ! Done

        inPayment(paymentRef, senderRef)
      }
      case ConfirmPaymentReceived => {
        context.log.info("Received request to confirm that payment has been received.")

        senderRef ! Done

        finished
      }
      case _ => {
        Behaviors.same
      }
    }
  )

  def inPayment(
    paymentActorRef: ActorRef[Payment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.receive(
    (context, message) => message match {
      case Pay(sender) => {
        context.log.info("Received payment request.")

        paymentActorRef ! Payment.DoPayment

        sender ! Done

        inPayment(sender)
      }
    }
  )

  def finished: Behavior[OrderManager.Command] = Behaviors.receive(
    (_, _) => {
      Behaviors.stopped
    }
  )
}
