package com.github.dafutils.chatroom.http

import java.time.Instant

import akka.event.Logging
import akka.event.Logging.InfoLevel
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.github.dafutils.chatroom.AkkaDependencies
import com.github.dafutils.chatroom.http.model.{AddMessages, ChatroomMessage, NewChatroom, Pauses}
import com.github.dafutils.chatroom.service.Services
import uk.gov.hmrc.emailaddress.EmailAddress

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
            chatroomService
            .createChatroom(newChatroom)
          )
        } ~
          pathPrefix(Segment) { chatroomName =>
            path("messages") {
              (post & entity(as[AddMessages])) { addedMessages =>
                //Add messages to an existing chatroom
                complete(addedMessages)
              } ~
                (get & parameters("from".as[Long], "to".as[Long])) { (from, to) =>
                  //Request messages in a chatroom by period
                  complete(
                    Seq(
                      ChatroomMessage(
                        index = 4,
                        timestamp = Instant.now().toEpochMilli,
                        author = EmailAddress("me@example.com"),
                        message = "boo"
                      ),
                      ChatroomMessage(
                        index = 5,
                        timestamp = Instant.now().toEpochMilli,
                        author = EmailAddress("me@example.com"),
                        message = "foo-bar boo"
                      )
                    )
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
