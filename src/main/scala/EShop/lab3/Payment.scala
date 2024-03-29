package EShop.lab3

import EShop.lab2.TypedCheckout
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Payment {

  sealed trait Command
  case object DoPayment extends Command
}

class Payment(
  method: String,
  orderManager: ActorRef[OrderManager.Command],
  checkout: ActorRef[TypedCheckout.Command]
) {

  import Payment._

  def start: Behavior[Payment.Command] = Behaviors.receive(
    (context, message) => message match {
      case DoPayment => {
        context.log.info(s"Payment detected with method '${method}'")

        orderManager ! OrderManager.ConfirmPaymentReceived
        checkout ! TypedCheckout.ConfirmPaymentReceived

        Behaviors.stopped
      }
  })

}
