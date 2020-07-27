package com.github.dafutils.chatroom.service

import com.github.dafutils.chatroom.UnitTestSpec
import com.github.dafutils.chatroom.service.hbase.ChatroomMessageRepository
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers._

import scala.concurrent.Future

class PausesServiceTest extends UnitTestSpec {

  val mockRepository: ChatroomMessageRepository = mock[ChatroomMessageRepository]
  val tested = new PausesService(mockRepository)

  "PausesService" should {
    
    //This occurs when we attempt to get the long pauses list of a chatroom for which no messages have
    //been stored
    "return 0 if the average pause length could not be determined" in {
      //Given
      val testChatroomId = 1
      val testPeriodStart = 0
      val testPeriodEnd = 10000

      when {
        mockRepository.averagePause(testChatroomId)
      } thenReturn {
        Future.successful(None)
      }

      //When
      val eventualResult = tested.countLongPauses(
        chatroomId = testChatroomId,
        from = testPeriodStart,
        to = testPeriodEnd
      )

      //Then
      whenReady(eventualResult) { actualLongPauseCount =>
        actualLongPauseCount shouldEqual 0
      }
    }
    
    "return the count as determined by the repository" in {
      //Given
      val testChatroomId = 1
      val testPeriodStart = 0
      val testPeriodEnd = 10000
      val testChatroomAveragePauseLength = 10.3
      val expectedLongPausesCount = 15

      when {
        mockRepository.averagePause(testChatroomId)
      } thenReturn {
        Future.successful(Some(testChatroomAveragePauseLength))
      }

      when {
        mockRepository.countLongPauses(
          chatroomId = testChatroomId, 
          from = testPeriodStart, 
          to = testPeriodEnd, 
          averagePauseTime = testChatroomAveragePauseLength
        )
      } thenReturn {
        Future.successful(expectedLongPausesCount)
      }
      
      //When
      val eventualResult = tested.countLongPauses(
        chatroomId = testChatroomId,
        from = testPeriodStart,
        to = testPeriodEnd
      )

      //Then
      whenReady(eventualResult) { actualLongPauseCount =>
        actualLongPauseCount shouldEqual expectedLongPausesCount
      }
    }
  }
}
