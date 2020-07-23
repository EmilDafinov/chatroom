package com.github.dafutils.chatroom.service

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Merge, Source}
import com.github.dafutils.chatroom.http.model.{AddMessages, ChatroomMessage, ChatroomMessageWithStats}
import com.github.dafutils.chatroom.service.hbase.ChatroomMessageRepository

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext

class ChatroomService(chatroomMessageRepository: ChatroomMessageRepository) {

  //Assuming the indices of the chatroom messages start with 1
  val indexOfFirstMessageInChatroom = 1

  def storeMessages(addMessagesRequest: AddMessages)(implicit mat: Materializer, ec: ExecutionContext) = {
    toMessagesWithStats(
      chatRoomId = addMessagesRequest.chatRoomId,
      messages = addMessagesRequest.messages
    )
      .via(chatroomMessageRepository.persistMessages)
  }

  //TODO: extract and test
  private def toMessagesWithStats(chatRoomId: Int,
                                  messages: Seq[ChatroomMessage])(implicit mat: Materializer) = {

    val sortedMessages = messages.sortBy(_.index)
    val messagesByIndex = sortedMessages.map(msg => msg.index -> msg).toMap

    val firstMessageSource: Source[ChatroomMessageWithStats, NotUsed] = chatroomMessageRepository.timestampOfPreviousMessageInChatroom(
      chatroomId = chatRoomId,
      messageTimestamp = sortedMessages.head.timestamp,
      messageIndex = sortedMessages.head.index
    )
      .map { timestampOfLastMessageOfPreviousBatch =>
        ChatroomMessageWithStats(
          chatroomId = chatRoomId,
          previousMessageTimestamp = timestampOfLastMessageOfPreviousBatch,
          message = sortedMessages.head
        )
      }

    val tailMessagesSource = Source
      .fromIterator(() => sortedMessages.tail.iterator)
      .map { message =>

        val previousMessage = messagesByIndex(message.index - 1)

        ChatroomMessageWithStats(
          chatroomId = chatRoomId,
          previousMessageTimestamp = previousMessage.timestamp,
          message = message
        )
      }

    Source.combine(tailMessagesSource, firstMessageSource)(Merge(_))
  }
}
