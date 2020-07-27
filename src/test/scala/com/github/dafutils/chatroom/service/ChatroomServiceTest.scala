package com.github.dafutils.chatroom.service

import java.time.Instant

import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.github.dafutils.chatroom.http.exception.MissingPreviousBatchException
import com.github.dafutils.chatroom.http.model.{AddMessages, ChatroomMessage, ChatroomMessageWithStats}
import com.github.dafutils.chatroom.service.hbase.ChatroomMessageRepository
import com.github.dafutils.chatroom.{SpyingFlow, UnitTestSpec}
import org.mockito.ArgumentMatchers.{eq => mockEq, _}
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.matchers.should.Matchers._
import uk.gov.hmrc.emailaddress.EmailAddress

import scala.collection.immutable.Seq
import scala.collection.mutable
import scala.concurrent.Future

class ChatroomServiceTest extends UnitTestSpec {

  val mockRepository: ChatroomMessageRepository = mock[ChatroomMessageRepository]
  val tested = new ChatroomService(mockRepository)

  "ChatroomService" should {
    "persist all messages of the first batch in a chatroom and update the metrics for all" in {

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
          chatroomId = testRequest.chatRoomId,
          from = testRequest.messages.head.timestamp,
          to = testRequest.messages.last.timestamp + 1
        )
      } thenReturn {
        Future.successful(Seq.empty[ChatroomMessageWithStats])
      }

      val actualMessagesPersisted = mutable.Buffer[ChatroomMessageWithStats]()
      val actualMessageStatsPersisted = mutable.Buffer[ChatroomMessageWithStats]()

      when {
        mockRepository.persistMessages(any[Materializer])
      } thenReturn {
        SpyingFlow(actualMessagesPersisted)
      }

      when {
        mockRepository.updateChatroomMetrics(any[Materializer])
      } thenReturn {
        SpyingFlow(actualMessageStatsPersisted)
      }

      //When
      val eventualResult = tested.storeMessages(addMessagesRequest = testRequest)

      //Then
      whenReady(eventualResult) { actualPersistedChatrooms =>
        actualPersistedChatrooms should contain theSameElementsAs expectedPersistedMessages
        actualMessagesPersisted should contain theSameElementsAs expectedPersistedMessages
        actualMessageStatsPersisted should contain theSameElementsAs expectedPersistedMessages
        verify(mockRepository, never()).previousMessageInChatroom(any[Int], any[Int], any[Long])(any[Materializer])
      }
    }

    """persist all messages but the first one in a batch, 
      |if the message before the first is not yet persisted""".stripMargin in {

      //Given
      val firstMessageTimestamp = Instant.now().toEpochMilli

      val requestedMessage1 = ChatroomMessage(
        index = 2,
        timestamp = firstMessageTimestamp,
        author = EmailAddress("me@example.com"),
        message = "Hello World"
      )
      val requestedMessage2 = ChatroomMessage(
        index = 3,
        timestamp = firstMessageTimestamp + 1000,
        author = EmailAddress("you@example.com"),
        message = "Hello Again World"
      )
      val requestedMessage3 = ChatroomMessage(
        index = 4,
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
          chatroomId = testRequest.chatRoomId,
          from = testRequest.messages.head.timestamp,
          to = testRequest.messages.last.timestamp + 1
        )
      } thenReturn {
        Future.successful(Seq.empty[ChatroomMessageWithStats])
      }

      when {
        mockRepository.previousMessageInChatroom(
          chatroomId = mockEq(testRequest.chatRoomId),
          messageIndex = mockEq(requestedMessage1.index),
          messageTimestamp = mockEq(requestedMessage1.timestamp)
        )(any[Materializer])
      } thenReturn {
        Future.successful(None)
      }

      val actualMessagesPersisted = mutable.Buffer[ChatroomMessageWithStats]()
      val actualMessageStatsPersisted = mutable.Buffer[ChatroomMessageWithStats]()

      when {
        mockRepository.persistMessages(any[Materializer])
      } thenReturn {
        SpyingFlow(actualMessagesPersisted)
      }

      when {
        mockRepository.updateChatroomMetrics(any[Materializer])
      } thenReturn {
        SpyingFlow(actualMessageStatsPersisted)
      }

      //When
      val eventualResult = tested.storeMessages(addMessagesRequest = testRequest)

      //Then
      whenReady(eventualResult.failed) { error =>
        error shouldBe an[MissingPreviousBatchException]
        actualMessagesPersisted should contain theSameElementsAs expectedPersistedMessages
        actualMessageStatsPersisted should contain theSameElementsAs expectedPersistedMessages
      }
    }

    "persist all messages if the last message from the previous batch is known " in {
      //Given
      val firstMessageTimestamp = Instant.now().toEpochMilli

      val requestedMessage1 = ChatroomMessage(
        index = 2,
        timestamp = firstMessageTimestamp,
        author = EmailAddress("me@example.com"),
        message = "Hello World"
      )
      val requestedMessage2 = ChatroomMessage(
        index = 3,
        timestamp = firstMessageTimestamp + 1000,
        author = EmailAddress("you@example.com"),
        message = "Hello Again World"
      )
      val requestedMessage3 = ChatroomMessage(
        index = 4,
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
          timeSincePreviousMessage = 500,
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
          chatroomId = testRequest.chatRoomId,
          from = testRequest.messages.head.timestamp,
          to = testRequest.messages.last.timestamp + 1
        )
      } thenReturn {
        Future.successful(Seq.empty[ChatroomMessageWithStats])
      }

      when {
        mockRepository.previousMessageInChatroom(
          chatroomId = mockEq(testRequest.chatRoomId),
          messageIndex = mockEq(requestedMessage1.index),
          messageTimestamp = mockEq(requestedMessage1.timestamp)
        )(any[Materializer])
      } thenReturn {
        Future.successful(
          Some(
            ChatroomMessageWithStats(
              chatroomId = 1,
              timeSincePreviousMessage = -1,
              message = ChatroomMessage(
                index = 1,
                timestamp = requestedMessage1.timestamp - 500,
                author = EmailAddress("theDude@example.com"),
                message = "Yo"
              )
            )
          )
        )
      }

      val actualMessagesPersisted = mutable.Buffer[ChatroomMessageWithStats]()
      val actualMessageStatsPersisted = mutable.Buffer[ChatroomMessageWithStats]()

      when {
        mockRepository.persistMessages(any[Materializer])
      } thenReturn {
        SpyingFlow(actualMessagesPersisted)
      }

      when {
        mockRepository.updateChatroomMetrics(any[Materializer])
      } thenReturn {
        SpyingFlow(actualMessageStatsPersisted)
      }

      //When
      val eventualResult = tested.storeMessages(addMessagesRequest = testRequest)

      //Then
      whenReady(eventualResult) { actualMessagesReturned =>

        actualMessagesReturned should contain theSameElementsAs expectedPersistedMessages
        actualMessagesPersisted should contain theSameElementsAs expectedPersistedMessages
        actualMessageStatsPersisted should contain theSameElementsAs expectedPersistedMessages
      }
    }

    """persist only the first message if the last message from the previous batch is known 
      |and the rest of the batch is already persisted""".stripMargin in {

      //Given
      val firstMessageTimestamp = Instant.now().toEpochMilli

      val requestedMessage1 = ChatroomMessage(
        index = 2,
        timestamp = firstMessageTimestamp,
        author = EmailAddress("me@example.com"),
        message = "Hello World"
      )
      val requestedMessage2 = ChatroomMessage(
        index = 3,
        timestamp = firstMessageTimestamp + 1000,
        author = EmailAddress("you@example.com"),
        message = "Hello Again World"
      )
      val requestedMessage3 = ChatroomMessage(
        index = 4,
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

      val requestedMessage1WithStats = ChatroomMessageWithStats(
        chatroomId = testRequest.chatRoomId,
        timeSincePreviousMessage = 500,
        message = requestedMessage1
      )
      val requestedMessage2WithStats = ChatroomMessageWithStats(
        chatroomId = testRequest.chatRoomId,
        timeSincePreviousMessage = 1000,
        message = requestedMessage2
      )

      val requestedMessage3WithStats = ChatroomMessageWithStats(
        chatroomId = testRequest.chatRoomId,
        timeSincePreviousMessage = 1500,
        message = requestedMessage3
      )
      val expectedPersistedMessages = Seq(
        requestedMessage1WithStats,
      )

      when {
        mockRepository.chatroomMessagesInPeriod(
          chatroomId = testRequest.chatRoomId,
          from = testRequest.messages.head.timestamp,
          to = testRequest.messages.last.timestamp + 1
        )
      } thenReturn {
        Future.successful(Seq(requestedMessage2WithStats, requestedMessage3WithStats))
      }

      when {
        mockRepository.previousMessageInChatroom(
          chatroomId = mockEq(testRequest.chatRoomId),
          messageIndex = mockEq(requestedMessage1.index),
          messageTimestamp = mockEq(requestedMessage1.timestamp)
        )(any[Materializer])
      } thenReturn {
        Future.successful(
          Some(
            ChatroomMessageWithStats(
              chatroomId = 1,
              timeSincePreviousMessage = -1,
              message = ChatroomMessage(
                index = 1,
                timestamp = requestedMessage1.timestamp - 500,
                author = EmailAddress("theDude@example.com"),
                message = "Yo"
              )
            )
          )
        )
      }

      val actualMessagesPersisted = mutable.Buffer[ChatroomMessageWithStats]()
      val actualMessageStatsPersisted = mutable.Buffer[ChatroomMessageWithStats]()

      when {
        mockRepository.persistMessages(any[Materializer])
      } thenReturn {
        SpyingFlow(actualMessagesPersisted)
      }

      when {
        mockRepository.updateChatroomMetrics(any[Materializer])
      } thenReturn {
        SpyingFlow(actualMessageStatsPersisted)
      }

      //When
      val eventualResult = tested.storeMessages(addMessagesRequest = testRequest)

      //Then
      whenReady(eventualResult) { actualMessagesReturned =>

        actualMessagesReturned should contain theSameElementsAs expectedPersistedMessages
        actualMessagesPersisted should contain theSameElementsAs expectedPersistedMessages
        actualMessageStatsPersisted should contain theSameElementsAs expectedPersistedMessages
      }
    }

    "return a failure if persistence fails" in {
      //Given
      val firstMessageTimestamp = Instant.now().toEpochMilli

      val requestedMessage1 = ChatroomMessage(
        index = 2,
        timestamp = firstMessageTimestamp,
        author = EmailAddress("me@example.com"),
        message = "Hello World"
      )
      val requestedMessage2 = ChatroomMessage(
        index = 3,
        timestamp = firstMessageTimestamp + 1000,
        author = EmailAddress("you@example.com"),
        message = "Hello Again World"
      )
      val requestedMessage3 = ChatroomMessage(
        index = 4,
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

      when {
        mockRepository.chatroomMessagesInPeriod(
          chatroomId = testRequest.chatRoomId,
          from = testRequest.messages.head.timestamp,
          to = testRequest.messages.last.timestamp + 1
        )
      } thenReturn {
        Future.successful(Seq.empty[ChatroomMessageWithStats])
      }

      when {
        mockRepository.previousMessageInChatroom(
          chatroomId = mockEq(testRequest.chatRoomId),
          messageIndex = mockEq(requestedMessage1.index),
          messageTimestamp = mockEq(requestedMessage1.timestamp)
        )(any[Materializer])
      } thenReturn {
        Future.successful(
          Some(
            ChatroomMessageWithStats(
              chatroomId = 1,
              timeSincePreviousMessage = -1,
              message = ChatroomMessage(
                index = 1,
                timestamp = requestedMessage1.timestamp - 500,
                author = EmailAddress("theDude@example.com"),
                message = "Yo"
              )
            )
          )
        )
      }

      val actualMessagesPersisted = mutable.Buffer[ChatroomMessageWithStats]()
      val actualMessageStatsPersisted = mutable.Buffer[ChatroomMessageWithStats]()

      when {
        mockRepository.persistMessages(any[Materializer])
      } thenReturn {
        Flow[ChatroomMessageWithStats].map(_ => throw new RuntimeException("Kaboom"))
      }

      when {
        mockRepository.updateChatroomMetrics(any[Materializer])
      } thenReturn {
        SpyingFlow(actualMessageStatsPersisted)
      }

      //When
      val eventualResult = tested.storeMessages(addMessagesRequest = testRequest)

      //Then
      whenReady(eventualResult.failed) { exception =>

        exception shouldBe a[RuntimeException]

        actualMessagesPersisted shouldBe 'empty
        actualMessageStatsPersisted shouldBe 'empty
      }
    }
  }
}
