package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor}
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCartTest
  extends ScalaTestWithActorTestKit
  with AnyFlatSpecLike
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit = testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val givenItem = "EXAMPLE_ITEM"
    val expectedResult = Seq(givenItem)
    val cartProbe = testKit.createTestProbe[Cart]()
    val cartActor = testKit.spawn(new TypedCartActor().start)

    cartActor ! TypedCartActor.AddItem(givenItem)
    cartActor ! TypedCartActor.GetItems(cartProbe.ref)

    cartProbe.expectMessage(Cart(expectedResult))
  }

  it should "be empty after adding and removing the same item" in {
    val givenItem = "EXAMPLE_ITEM"
    val expectedResult = Seq()
    val cartProbe = testKit.createTestProbe[Cart]()
    val cartActor = testKit.spawn(new TypedCartActor().start)

    cartActor ! TypedCartActor.AddItem(givenItem)
    cartActor ! TypedCartActor.RemoveItem(givenItem)
    cartActor ! TypedCartActor.GetItems(cartProbe.ref)

    cartProbe.expectMessage(Cart(expectedResult))
  }

  it should "start checkout" in {
    val givenItem = "EXAMPLE_ITEM"
    val cartActor = testKit.spawn(new TypedCartActor().start)
    val orderManagerProbe = testKit.createTestProbe[OrderManager.Command]()

    cartActor ! TypedCartActor.AddItem(givenItem)
    cartActor ! TypedCartActor.StartCheckout(orderManagerProbe.ref)

    orderManagerProbe.expectMessageType[OrderManager.ConfirmCheckoutStarted]

//    val givenItem = "EXAMPLE_ITEM"
//    val cartActor = BehaviorTestKit(new TypedCartActor().start)
//    val orderManagerActor = BehaviorTestKit(new OrderManager().start)
//
//    cartActor.run(TypedCartActor.AddItem(givenItem))
//    cartActor.expectEffectType[Scheduled[String]]
//
//    cartActor.run(TypedCartActor.StartCheckout(orderManagerActor.ref))
//    cartActor.expectEffect(Spawned(new TypedCheckout(cartActor.ref).start, "checkoutActor"))
  }
}
