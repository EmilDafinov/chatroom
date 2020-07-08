package com.github.dafutils.chat

import akka.event.Logging
import akka.event.Logging.InfoLevel
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.{BadRequest, Forbidden, InternalServerError}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._

import scala.util.control.NonFatal

trait HttpRoute {
  this: AkkaDependencies =>
  private val log = Logging(actorSystem, classOf[HttpRoute])
  
  val exceptionHandler: ExceptionHandler = ExceptionHandler {
    
    case e: IllegalArgumentException =>
      log.error(e, "HTTP request failed due to an illegal argument exception.")
      complete(
        HttpResponse(status = BadRequest, entity = e.getMessage)
      )
    case NonFatal(exception) =>
      log.error(exception, "HTTP request failed due to an internal error.")
      complete(
        HttpResponse(status = InternalServerError)
      )
  }
  
  val route: Route = 
    (path("health") & get ) {
      complete(getClass.getPackage.getImplementationVersion)
    } ~ 
      (pathPrefix("api") 
        & logRequestResult("requests", InfoLevel) 
        & handleExceptions(exceptionHandler)) {
        
        complete(HttpResponse())
      }
}
