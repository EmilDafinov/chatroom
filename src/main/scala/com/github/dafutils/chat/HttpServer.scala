package com.github.dafutils.chat

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server._
import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class HttpServer(baseRoute: Route, httpPort: Int, httpInterface: String)(implicit as: ActorSystem, mat: Materializer, ex: ExecutionContext) {
  private val log = Logging(as, this.getClass)

  def start: Future[Http.ServerBinding] = {
    Http()
      .bindAndHandle(baseRoute, httpInterface, httpPort)
      .andThen {
        case Success(_) => log.info("Http server started at port {}", httpPort)
        case Failure(ex) => log.error(ex, "Http server failed to start at port {}", httpPort)
      }
  }
}
