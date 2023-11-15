package EShop.lab3

import EShop.lab2.{TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  import TypedCheckout._

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    val givenDeliveryMethod = "EXAMPLE_DELIVERY_METHOD"
    val givenPaymentMethod = "EXAMPLE_PAYMENT_METHOD"
    val cartProbe = testKit.createTestProbe[TypedCartActor.Command]()
    val orderManagerProbe = testKit.createTestProbe[OrderManager.Command]()
    val checkoutActor = testKit.spawn(new TypedCheckout(cartProbe.ref).start)

    checkoutActor ! TypedCheckout.StartCheckout
    checkoutActor ! SelectDeliveryMethod(givenDeliveryMethod)
    checkoutActor ! SelectPayment(givenPaymentMethod, orderManagerProbe.ref)
    checkoutActor ! ConfirmPaymentReceived

    cartProbe.expectMessage(TypedCartActor.ConfirmCheckoutClosed)
  }

}
