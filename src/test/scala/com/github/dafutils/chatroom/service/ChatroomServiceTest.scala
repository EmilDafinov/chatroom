package com.github.dafutils.chatroom.service

import java.time.Instant

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import com.github.dafutils.chatroom.UnitTestSpec
import com.github.dafutils.chatroom.http.model.{AddMessages, ChatroomMessage, ChatroomMessageWithStats}
import com.github.dafutils.chatroom.service.hbase.ChatroomMessageRepository
import org.mockito.ArgumentMatchers.{eq => mockEq, _}
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers._
import uk.gov.hmrc.emailaddress.EmailAddress

import scala.collection.immutable.Seq
import scala.concurrent.Future

class ChatroomServiceTest extends UnitTestSpec {

  val mockRepository: ChatroomMessageRepository = mock[ChatroomMessageRepository]
  val tested = new ChatroomService(mockRepository)

  "ChatroomService" should {
    "persist all messages of the first batch in a chatroom" in {

      //Given
      val firstMessageTimestamp = Instant.now().toEpochMilli

      val requestedMessage1 = ChatroomMessage(
        index = 1,
        timestamp = firstMessageTimestamp,
        author = EmailAddress("me@example.com"),
        message = "Hello World"
      )
      val requestedMessage2 = ChatroomMessage(
        index = 2,
        timestamp = firstMessageTimestamp + 1000,
        author = EmailAddress("you@example.com"),
        message = "Hello Again World"
      )
      val requestedMessage3 = ChatroomMessage(
        index = 3,
        timestamp = firstMessageTimestamp + 2500,
        author = EmailAddress("him@example.com"),
        message = "Goodbye World"
      )
      val testRequest = AddMessages(
        chatRoomId = 1,
        messages = Seq(
          requestedMessage1,
          requestedMessage2,
          requestedMessage3,
        )
      )

      val expectedPersistedMessages = Seq(
        ChatroomMessageWithStats(
          chatroomId = testRequest.chatRoomId,
          timeSincePreviousMessage = -1,
          message = requestedMessage1
        ),
        ChatroomMessageWithStats(
          chatroomId = testRequest.chatRoomId,
          timeSincePreviousMessage = 1000,
          message = requestedMessage2
        ),
        ChatroomMessageWithStats(
          chatroomId = testRequest.chatRoomId,
          timeSincePreviousMessage = 1500,
          message = requestedMessage3
        )
      )
      when {
        mockRepository.chatroomMessagesInPeriod(
          chatroomId =any(),
          from = any(),
          to = any()
        )(any())
      } thenReturn {
        Future.successful(Seq.empty[ChatroomMessageWithStats])
      }

      when {
        mockRepository.timestampOfPreviousMessageInChatroom(
          chatroomId = mockEq(testRequest.chatRoomId),
          messageIndex = mockEq(requestedMessage1.index),
          messageTimestamp = mockEq(requestedMessage1.timestamp)
        )(any[Materializer])
      } thenReturn {
        Source.single(-1L)
      }
      
      when {
        mockRepository.persistMessages(any[Materializer])
      } thenReturn {
        Flow[ChatroomMessageWithStats]
      }

      when {
        mockRepository.updateChatroomMetrics(any[Materializer])
      } thenReturn {
        Flow[ChatroomMessageWithStats]
      }

      //When
      val eventualResult = tested.storeMessages(addMessagesRequest = testRequest)

      //Then
      whenReady(eventualResult) { actualPersistedChatrooms =>
        actualPersistedChatrooms should contain theSameElementsAs expectedPersistedMessages
      }
    }
  }
}
