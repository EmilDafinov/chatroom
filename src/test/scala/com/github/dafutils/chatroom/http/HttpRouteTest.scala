package com.github.dafutils.chatroom.http

import akka.http.scaladsl.model.StatusCodes.ImATeapot
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.Materializer
import com.github.dafutils.chatroom.http.JsonSupport._
import com.github.dafutils.chatroom.http.exception.MissingPreviousBatchException
import com.github.dafutils.chatroom.http.model.{AddMessages, ChatroomMessage, ChatroomMessageWithStats, NewChatroom}
import com.github.dafutils.chatroom.service.hbase.ChatroomMessageRepository
import com.github.dafutils.chatroom.service.{AbstractServices, ChatroomService, PausesService}
import com.github.dafutils.chatroom.{AkkaDependencies, RouteTestSpec}
import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods._
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers._
import uk.gov.hmrc.emailaddress.EmailAddress

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}

class HttpRouteTest extends RouteTestSpec with ScalatestRouteTest {

  trait ServiceMocks extends AbstractServices {
    override val chatroomMessageRepository = mock[ChatroomMessageRepository]
    override val chatroomService = mock[ChatroomService]
    override val pausesService = mock[PausesService]
  }

  val tested = new AkkaDependencies with HttpRoute with ServiceMocks {

    override val chatroomMessageRepository = mock[ChatroomMessageRepository]
    override val chatroomService = mock[ChatroomService]
    override val pausesService = mock[PausesService]
  }

  "HttpRoute" should {

    "create a new chatroom" in {
      //Given
      val testRequestBody =
        """{ 
          |"id": 1, 
          |"name": "AChatroom",
          |"created": 1595896980976,
          |"participants": ["me@example.com", "you@example.com"]
          | }""".stripMargin

      when {
        tested.chatroomMessageRepository.createChatroom(
          mockEq(parse(testRequestBody).extract[NewChatroom])
        )(any[Materializer])
      } thenReturn {
        Future.successful(parse(testRequestBody).extract[NewChatroom])
      }

      //When
      Post(uri = "/chatrooms",
        content = parse(testRequestBody).extract[JValue]
      ) ~>
        Route.seal(tested.route) ~>
        check {
          //Then
          responseAs[JValue] shouldEqual parse(testRequestBody)
        }
    }

    "store the requested messages" in {
      //Given
      val testRequestBody =
        """{ 
          |"chatRoomId": 1, 
          |"messages": [
          |   {"index": 2, "timestamp": 1595898612517, "author": "me@example.com", "message": "Foo"},
          |   {"index": 3, "timestamp": 1595898612717, "author": "you@example.com", "message": "Bar"}
          |]
          |
          |}""".stripMargin


      val expectedMessages = Seq(
        ChatroomMessageWithStats(
          chatroomId = 1,
          timeSincePreviousMessage = 10L,
          message = ChatroomMessage(
            index = 2,
            timestamp = 1595898612517L,
            author = EmailAddress("me@example.com"),
            message = "Foo"
          )
        ),
        ChatroomMessageWithStats(
          chatroomId = 1,
          timeSincePreviousMessage = 20L,
          message = ChatroomMessage(
            index = 3,
            timestamp = 1595898612717L,
            author = EmailAddress("you@example.com"),
            message = "Bar"
          )
        ),
      )

      when {
        tested.chatroomService.storeMessages(
          addMessagesRequest = mockEq(parse(testRequestBody).extract[AddMessages])
        )(any[Materializer], any[ExecutionContext])
      } thenReturn {
        Future.successful(expectedMessages)
      }

      val expectedResponse =
        """
          |[
          |   { "chatroomId": 1, "timeSincePreviousMessage": 10, "message": {"index": 2, "timestamp": 1595898612517, "author": "me@example.com", "message": "Foo"}},
          |   { "chatroomId": 1, "timeSincePreviousMessage": 20, "message": {"index": 3, "timestamp": 1595898612717, "author": "you@example.com", "message": "Bar"}}
          |]
          |""".stripMargin

      //When
      Post(uri = "/messages", content = parse(testRequestBody)) ~>
        Route.seal(tested.route) ~>
        check {
          responseAs[JValue] shouldEqual parse(expectedResponse)
        }
    }

    "fail storing messages with the appropriate error when previous message could not be resolved" in {
      //Given
      val testRequestBody =
        """{ 
          |"chatRoomId": 1, 
          |"messages": [
          |   {"index": 2, "timestamp": 1595898612517, "author": "me@example.com", "message": "Foo"},
          |   {"index": 3, "timestamp": 1595898612717, "author": "you@example.com", "message": "Bar"}
          |]
          |
          |}""".stripMargin

      when {
        tested.chatroomService.storeMessages(
          addMessagesRequest = mockEq(parse(testRequestBody).extract[AddMessages])
        )(any[Materializer], any[ExecutionContext])
      } thenReturn {
        Future.failed(new MissingPreviousBatchException("Boom"))
      }

      //When
      Post(uri = "/messages", content = parse(testRequestBody)) ~>
        Route.seal(tested.route) ~>
        check {
          //Then
          response.status shouldEqual ImATeapot
        }
    }
  }
}
