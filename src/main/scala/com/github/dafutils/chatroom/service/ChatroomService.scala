package com.github.dafutils.chatroom.service

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.github.dafutils.chatroom.http.exception.MissingPreviousBatchException
import com.github.dafutils.chatroom.http.model.{AddMessages, ChatroomMessage, ChatroomMessageWithStats}
import com.github.dafutils.chatroom.service.hbase.ChatroomMessageRepository

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class ChatroomService(chatroomMessageRepository: ChatroomMessageRepository) {

  //Assuming the indices of the chatroom messages start with 1
  val indexOfFirstMessageInChatroom = 1

  def storeMessages(addMessagesRequest: AddMessages)(implicit mat: Materializer, ec: ExecutionContext) = {

    val sortedMessages = addMessagesRequest.messages.sortBy(_.index)
    val firstMessageInBatch = sortedMessages.head

    val enrichedMessages = toMessagesWithStats(
      chatRoomId = addMessagesRequest.chatRoomId,
      indexOfFirstMessageInBatch = firstMessageInBatch.index,
      sortedMessages = sortedMessages
    )

    for {
      messagesWithStats <- enrichedMessages
      batchStored <- chatroomMessageRepository.addMessages(messagesWithStats).runWith(Sink.seq)
    } yield batchStored
  }

  //TODO: extract and test
  private def toMessagesWithStats(chatRoomId: Int,
                                  indexOfFirstMessageInBatch: Int,
                                  sortedMessages: Seq[ChatroomMessage])(implicit ec: ExecutionContext, mat: Materializer) = {

    val messagesByIndex = sortedMessages.map(msg => msg.index -> msg).toMap

    chatroomMessageRepository.timestampOfPreviousMessageInChatroom(
      chatroomId = chatRoomId,
      messageTimestamp = sortedMessages.head.timestamp,
      messageIndex = sortedMessages.head.index
    )
      .runWith(Sink.head)
      .recover {
        case ex: NoSuchElementException =>
          throw new MissingPreviousBatchException(s"Could not find the previous message for message ${sortedMessages.head.index} in chatroom $chatRoomId", ex)
        case NonFatal(ex) => throw ex
      }
      .map { timestampOfLastMessageOfPreviousBatch =>
        sortedMessages.map { message =>
          val previousMessageTimestamp: Long = message.index match {
            case messageIndex if messageIndex == indexOfFirstMessageInChatroom =>
              -1
            case messageIndex if messageIndex > indexOfFirstMessageInBatch =>
              messagesByIndex(messageIndex - 1).timestamp
            case messageIndex if messageIndex == indexOfFirstMessageInBatch =>
              //TODO: proper error handling on missing value
              timestampOfLastMessageOfPreviousBatch
          }

          ChatroomMessageWithStats(
            chatroomId = chatRoomId,
            previousMessageTimestamp = previousMessageTimestamp,
            message = message
          )
        }
      }
  }
}
