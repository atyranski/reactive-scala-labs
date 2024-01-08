package EShop.lab5

import akka.Done
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat}

import java.net.URI
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}


object ProductCatalogHttpServer {
  import EShop.lab5.ProductCatalog.Item

  case class GetItems(brand: String, productKeyWords: List[String])
  case class ItemsList(items: List[Item])
}

trait ProductCatalogJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  implicit lazy val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue = JsString(obj.toString)

    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _ => throw new RuntimeException("Parsing exception")
      }
  }

  implicit lazy val itemFormat          = jsonFormat5(ProductCatalog.Item)
  implicit lazy val getItemsFormat      = jsonFormat2(ProductCatalogHttpServer.GetItems)
  implicit lazy val itemsListFormat     = jsonFormat1(ProductCatalogHttpServer.ItemsList)
}

class ProductCatalogHttpServer(productCatalog: ActorRef[ProductCatalog.Query]) extends ProductCatalogJsonSupport {

  implicit val system = ActorSystem[Nothing](Behaviors.empty, "ProductCatalogHttpServer")

  implicit val timeout: Timeout = Timeout(5 seconds)

  private def routes: Route = {
    path("search") {
      post {
        entity(as[ProductCatalogHttpServer.GetItems]) { searchQuery =>
          val result: Future[ProductCatalog.Ack] =
            productCatalog.ask(ref => ProductCatalog.GetItems(searchQuery.brand, searchQuery.productKeyWords, ref))

          onComplete(result) {
            case Success(itemsList: ProductCatalog.Items) => {
              complete(StatusCodes.OK, ProductCatalogHttpServer.ItemsList(itemsList.items))
            }
            case Failure(ex)    => complete(s"Failed to retrieve items: ${ex.getMessage}")
          }
        }
      }
    }
  }

  def start(port: Int): Future[Done] = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)

    Await.ready(system.whenTerminated, Duration.Inf)
  }

}


object ProductCatalogHttpServerApp extends App {

  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "ProductCatalogApi")

  val productCatalog = system.systemActorOf(ProductCatalog(new SearchService()), "productCatalog")

  new ProductCatalogHttpServer(productCatalog).start(8080)
}