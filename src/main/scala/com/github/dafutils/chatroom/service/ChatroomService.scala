package com.github.dafutils.chatroom.service

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Merge, Sink, Source}
import com.github.dafutils.chatroom.http.model.{AddMessages, ChatroomMessage, ChatroomMessageWithStats}
import com.github.dafutils.chatroom.service.hbase.ChatroomMessageRepository

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext

class ChatroomService(chatroomMessageRepository: ChatroomMessageRepository) {

  def storeMessages(addMessagesRequest: AddMessages)(implicit mat: Materializer, ec: ExecutionContext) = {
    val sortedMessages = addMessagesRequest.messages.sortBy(_.index)

    for {
      knownMessages <- chatroomMessageRepository.scanMessages(
        chatroomId = addMessagesRequest.chatRoomId,
        from = sortedMessages.head.timestamp,
        to = sortedMessages.last.timestamp + 1 //include the last message
      ).runWith(Sink.seq)
      
      messagesPersistedForThisRequest <- toMessagesWithStats(
        chatRoomId = addMessagesRequest.chatRoomId,
        messagesFromBatchAlreadyPersisted = knownMessages,
        sortedMessagesInBatch = sortedMessages
      )
        .via(chatroomMessageRepository.persistMessages)
        .via(chatroomMessageRepository.updateChatroomMetrics)
        .runWith(Sink.seq) 
    } yield messagesPersistedForThisRequest
  }

  //TODO: extract and test
  private def toMessagesWithStats(chatRoomId: Int,
                                  messagesFromBatchAlreadyPersisted: Seq[ChatroomMessage],
                                  sortedMessagesInBatch: Seq[ChatroomMessage])(implicit mat: Materializer) = {
    
    val messagesInBatchByIndex = sortedMessagesInBatch.map(msg => msg.index -> msg).toMap

    val firstMessageSource: Source[ChatroomMessageWithStats, NotUsed] =
      if (messagesFromBatchAlreadyPersisted.contains(sortedMessagesInBatch.head))
        Source.empty[ChatroomMessageWithStats]
      else
        chatroomMessageRepository.timestampOfPreviousMessageInChatroom(
          chatroomId = chatRoomId,
          messageTimestamp = sortedMessagesInBatch.head.timestamp,
          messageIndex = sortedMessagesInBatch.head.index
        )
        .map { timestampOfLastMessageOfPreviousBatch =>
          ChatroomMessageWithStats(
            chatroomId = chatRoomId,
            previousMessageTimestamp = timestampOfLastMessageOfPreviousBatch,
            message = sortedMessagesInBatch.head
          )
        }

    val tailMessagesSource = Source
      .fromIterator(() => sortedMessagesInBatch.tail.iterator)
      .filterNot(messagesFromBatchAlreadyPersisted.contains)
      .map { nonPersistedMessage =>

        val previousMessage = messagesInBatchByIndex(nonPersistedMessage.index - 1)

        ChatroomMessageWithStats(
          chatroomId = chatRoomId,
          previousMessageTimestamp = previousMessage.timestamp,
          message = nonPersistedMessage
        )
      }

    Source.combine(tailMessagesSource, firstMessageSource)(Merge(_))
  }
}
