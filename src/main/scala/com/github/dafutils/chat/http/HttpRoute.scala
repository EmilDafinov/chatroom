package com.github.dafutils.chat.http

import akka.event.Logging
import akka.event.Logging.InfoLevel
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.github.dafutils.chat.AkkaDependencies
import com.github.dafutils.chat.http.model.{AddMessages, NewChatroom}

trait HttpRoute {
  this: AkkaDependencies =>
  private val log = Logging(actorSystem, classOf[HttpRoute])

  import JsonSupport._

  val route: Route =
    (path("health") & get) {
      complete(getClass.getPackage.getImplementationVersion)
    } ~
      (pathPrefix("chatroom")
        & logRequestResult("requests", InfoLevel)) {

        (post & entity(as[NewChatroom])) { newChatroom =>
          //Create a chatroom
          complete(HttpResponse())
        } ~
          pathPrefix(Segment) { chatroomName =>
            path("messages") {
              (post & entity(as[AddMessages])) { addedMessages =>
                //Add messages to an existing chatroom
                complete(HttpResponse())
              } ~
                (get & parameters("from".as[Long], "to".as[Long])) { (from, to) =>
                  //Request messages in a chatroom by period
                  complete(HttpResponse())
                }
            } ~
              (path("longPauses")
                & get
                & parameters("from".as[Long], "to".as[Long])) { (from, to) =>
                //Long pauses count
                complete(HttpResponse())
              }
          }
      }
}
