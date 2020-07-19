package com.github.dafutils.chatroom.http

import akka.event.Logging
import akka.event.Logging.InfoLevel
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.github.dafutils.chatroom.AkkaDependencies
import com.github.dafutils.chatroom.http.model.{AddMessages, NewChatroom, Pauses}
import com.github.dafutils.chatroom.service.Services

trait HttpRoute {
  this: AkkaDependencies with Services =>
  private val log = Logging(actorSystem, classOf[HttpRoute])

  import JsonSupport._

  val route: Route =
    (path("health") & get) {
      complete(getClass.getPackage.getImplementationVersion)
    } ~
      (pathPrefix("chatroom")
        & logRequestResult("requests", InfoLevel)) {

        (post & entity(as[NewChatroom])) { newChatroom =>
          complete(
            chatroomService.createChatroom(newChatroom)
          )
        } ~
          pathPrefix(Segment) { chatroomName =>
            path("messages") {
              (post & entity(as[AddMessages])) { addedMessages =>
                complete(
                  chatroomService.addMessages(addedMessages)
                )
              } ~
                (get & parameters("from".as[Long], "to".as[Long], "chatroomId".as[Int])) { (from, to, chatroom) =>
                  //Request messages in a chatroom by period
                  complete(
                    chatroomService.scanMessages(chatroomId = chatroom, from = from, to = to)
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
}
