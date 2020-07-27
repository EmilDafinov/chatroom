package com.github.dafutils.chatroom.service

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Concat, Sink, Source}
import com.github.dafutils.chatroom.http.exception.MissingPreviousBatchException
import com.github.dafutils.chatroom.http.model.{AddMessages, ChatroomMessage, ChatroomMessageWithStats}
import com.github.dafutils.chatroom.service.hbase.ChatroomMessageRepository

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext

class ChatroomService(chatroomMessageRepository: ChatroomMessageRepository) {

  def storeMessages(addMessagesRequest: AddMessages)(implicit mat: Materializer, ec: ExecutionContext) = {
    //This could be redundant if we assume the messages are already sorted in the request
    val sortedMessages = addMessagesRequest.messages.sortBy(_.index)

    for {
      //We check which messages from the batch are already persisted in order to avoid updating the chatroom metrics twice for them
      knownMessages <- chatroomMessageRepository.chatroomMessagesInPeriod(
        chatroomId = addMessagesRequest.chatRoomId,
        from = sortedMessages.head.timestamp,
        to = sortedMessages.last.timestamp + 1 //include the last message from the batch
      )

      messagesPersistedForThisRequest <- toMessagesWithStats(
        chatRoomId = addMessagesRequest.chatRoomId,
        messagesFromBatchAlreadyPersisted = knownMessages.map(_.message),
        sortedMessagesInBatch = sortedMessages
      )
        .via(chatroomMessageRepository.persistMessages)
        //This is a potential source if inconsistency: if a message gets persisted, but updating the metrics fails,
        //then our average would be wrong !
        .via(chatroomMessageRepository.updateChatroomMetrics)
        .runWith(Sink.seq)
    } yield messagesPersistedForThisRequest
  }
  
  private def toMessagesWithStats(chatRoomId: Int,
                                  messagesFromBatchAlreadyPersisted: Seq[ChatroomMessage],
                                  sortedMessagesInBatch: Seq[ChatroomMessage])
                                 (implicit mat: Materializer, ex: ExecutionContext): Source[ChatroomMessageWithStats, NotUsed] = {

    val messagesInBatchByIndex = sortedMessagesInBatch.map(msg => msg.index -> msg).toMap

    //For the first message in the batch, we don't know the pause associated, we have to look up the time of the 
    //previous message from the database

    val firstMessageSource = sortedMessagesInBatch.head match {
      case messageAlreadyPersisted if messagesFromBatchAlreadyPersisted.contains(messageAlreadyPersisted) =>
        Source.empty[ChatroomMessageWithStats]
      case firstMessageInChatroom if firstMessageInChatroom.index == 1 =>
        Source.single(
          ChatroomMessageWithStats(
            chatroomId = chatRoomId,
            timeSincePreviousMessage = -1,
            message = sortedMessagesInBatch.head
          )
        )
      case _ =>
        val eventualPreviousMessage = chatroomMessageRepository.previousMessageInChatroom(
          chatroomId = chatRoomId,
          messageTimestamp = sortedMessagesInBatch.head.timestamp,
          messageIndex = sortedMessagesInBatch.head.index
        ).map {
          _.getOrElse(
            throw new MissingPreviousBatchException(
              s"The message before the message with chatrooId $chatRoomId and index ${sortedMessagesInBatch.head.index} was not found"
            )
          )
        }

        Source.fromFuture(eventualPreviousMessage)
          .map { lastMessageOfPreviousBatch =>
            ChatroomMessageWithStats(
              chatroomId = chatRoomId,
              timeSincePreviousMessage = sortedMessagesInBatch.head.timestamp - lastMessageOfPreviousBatch.message.timestamp,
              message = sortedMessagesInBatch.head
            )
          }
    }

    //For all messages but the first, we don't need a database lookup to find out the length of the pause
    val tailMessagesSource = Source
      .fromIterator(() => sortedMessagesInBatch.tail.reverseIterator) //store staring from the last message, it would be needed for the next batch...
      .filterNot(messagesFromBatchAlreadyPersisted.contains)
      .map { currentMessage =>

        val previousMessage = messagesInBatchByIndex(currentMessage.index - 1)

        ChatroomMessageWithStats(
          chatroomId = chatRoomId,
          timeSincePreviousMessage = currentMessage.timestamp - previousMessage.timestamp,
          message = currentMessage
        )
      }

    Source.combine(tailMessagesSource, firstMessageSource)(Concat(_))
  }
}
