package com.github.dafutils.chatroom.http

import akka.event.Logging
import akka.event.Logging.InfoLevel
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.server.Directives.{logRequestResult, _}
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

    (path("health") & get) {
      //Kubernetes requirement
      complete(getClass.getPackage.getImplementationVersion)
    } ~
      (handleExceptions(exceptionHandler)
        & logRequestResult("requests", InfoLevel)) {
        path("chatrooms") {
          (post & entity(as[NewChatroom])) { newChatroom =>
            complete(
              chatroomMessageRepository.createChatroom(newChatroom)
            )
          }
        } ~
          path("messages") {
            (post & entity(as[AddMessages])) { addedMessages =>
              complete(
                chatroomService.storeMessages(addedMessages)
              )
            } ~
              (get & parameters("from".as[Long], "to".as[Long], "chatroomId".as[Int])) { (from, to, chatroom) =>
                //TODO: Perhaps there should be a limit enforced on the lentgh of the period that can be requested?
                //      Since we are returning the result, we will most likely have to load all messages into memory.
                //      Unless we do some streaming HTTP trickery...
                complete(
                  chatroomMessageRepository.chatroomMessagesInPeriod(chatroomId = chatroom, from = from, to = to)
                )
              }
          } ~
          (path("longPauses")
            & get
            & parameters("from".as[Long], "to".as[Long], "chatroomId".as[Int])) { (from, to, chatroom) =>
            //TODO: Same concern about limiting the period: not from a memory point of view as above, but because of 
            //      execution time: we have to scan the entire period to count all pauses that are longer than the 
            //      average
            complete(
              pausesService.countLongPauses(chatroom, from, to).map(Pauses.apply)
            )
          }
      }
}
