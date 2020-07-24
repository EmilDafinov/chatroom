package com.github.dafutils.chatroom.http

import akka.event.Logging
import akka.event.Logging.InfoLevel
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import com.github.dafutils.chatroom.AkkaDependencies
import com.github.dafutils.chatroom.http.exception.MissingPreviousBatchException
import com.github.dafutils.chatroom.http.model.{AddMessages, NewChatroom, Pauses}
import com.github.dafutils.chatroom.service.Services

trait HttpRoute {
  this: AkkaDependencies with Services =>
  private val log = Logging(actorSystem, classOf[HttpRoute])

  import JsonSupport._

  val exceptionHandler = ExceptionHandler {
    case ex: MissingPreviousBatchException =>
      log.error(ex, ex.getMessage)
      complete(
        HttpResponse(status = BadRequest, entity = ex.getMessage)
      )
  }

  val route: Route =
    handleExceptions(exceptionHandler) {
      (path("health") & get) {
        complete(getClass.getPackage.getImplementationVersion)
      } ~
        (path("chatroom")
          & logRequestResult("requests", InfoLevel)) {

          (post & entity(as[NewChatroom])) { newChatroom =>
            complete(
              chatroomMessageRepository.createChatroom(newChatroom)
            )
          }
        } ~
        path("messages") {
          (post & entity(as[AddMessages])) { addedMessages =>
            complete(
              chatroomRepository.storeMessages(addedMessages)
            )
          } ~
            (get & parameters("from".as[Long], "to".as[Long], "chatroomId".as[Int])) { (from, to, chatroom) =>
              //Request messages in a chatroom by period
              complete(
                chatroomMessageRepository.scanMessages(chatroomId = chatroom, from = from, to = to)
              )
            }
        } ~
        (path("longPauses")
          & get
          & parameters("from".as[Long], "to".as[Long])) { (from, to) =>
          //Long pauses count
          complete(Pauses(count = 73))
        }
    }
}
