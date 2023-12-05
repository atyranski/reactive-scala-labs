package EShop.lab5

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object PaymentService {

  sealed trait Response
  case object PaymentSucceeded extends Response

  case class PaymentClientError() extends Exception
  case class PaymentServerError() extends Exception

  // actor behavior which needs to be supervised
  // use akka.http.scaladsl.Http to make http based payment request
  // use getUri method to obtain url
  def apply(
    method: String,
    payment: ActorRef[Response]
  ): Behavior[HttpResponse] = Behaviors.setup { context =>
    implicit val system: ActorSystem[Nothing] = context.system
    implicit val executionContext: ExecutionContextExecutor = context.system.executionContext

    val uri = getURI(method)

    Http()
      .singleRequest(HttpRequest(uri = uri))
      .onComplete {
        case Success(response)    => context.self ! response
        case Failure(exception)   => throw exception
      }

    Behaviors.receiveMessage {
      case HttpResponse(StatusCodes.OK, _, _, _)              => {
        payment ! PaymentSucceeded
        Behaviors.stopped
      }
      case HttpResponse(StatusCodes.NotFound, _, _, _)        => throw PaymentClientError()
      case HttpResponse(StatusCodes.RequestTimeout, _, _, _)  => throw PaymentServerError()
      case _                                                  => throw new Exception("Unsupported response received")
    }
  }

  // remember running PymentServiceServer() before trying payu based payments
  private def getURI(method: String) = method match {
    case "payu"   => "http://127.0.0.1:8080"
    case "paypal" => s"http://httpbin.org/status/408"
    case "visa"   => s"http://httpbin.org/status/200"
    case _        => s"http://httpbin.org/status/404"
  }
}
