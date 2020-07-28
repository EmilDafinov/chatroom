package com.github.dafutils.chatroom.service

import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.github.dafutils.chatroom.http.exception.MissingPreviousBatchException
import com.github.dafutils.chatroom.http.model.{AddMessages, ChatroomMessage, ChatroomMessageWithStats}
import com.github.dafutils.chatroom.service.hbase.ChatroomMessageRepository

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}

class ChatroomService(chatroomMessageRepository: ChatroomMessageRepository) {

  /**
   * Stores the requested messages. A few notes: 
   * - Messages are stored only if they do not already exist in the datastore (reinserting the same message is idempotent, however incrementing the pause stats is NOT)
   * - The head message in the batch requires a datastore lookup in order to determine the length of the pause since the previous message. If
   * the lookup fails (for example due to the previous message not having been stored yet), this method should throw. However, all messages
   * other than the head should be persisted even in the case
   *
   * @throws MissingPreviousBatchException if the last message of the previous batch is required but not found. All messages from the batch other
   *                                       than the head would nevertheless be persisted to unblok storing of subsequent batches
   **/
  def storeMessages(addMessagesRequest: AddMessages)(implicit mat: Materializer, ec: ExecutionContext): Future[Seq[ChatroomMessageWithStats]] = {
    //This could be redundant if we assume the messages are already sorted in the request
    val sortedMessages = addMessagesRequest.messages.sortBy(_.index)

    for {
      //We check which messages from the batch are already persisted in order to avoid updating the chatroom metrics twice for them
      //Possible improvement: If performance is a concern, we could use a dedicated read method that only retrieves the message indices.
      knownMessages <- chatroomMessageRepository.chatroomMessagesInPeriod(
        chatroomId = addMessagesRequest.chatRoomId,
        from = sortedMessages.head.timestamp,
        to = sortedMessages.last.timestamp + 1 //include the last message from the batch
      ).map(_.map(_.message))

      headMessageSource = enrichHeadMessage(
        chatRoomId = addMessagesRequest.chatRoomId,
        messagesFromBatchAlreadyPersisted = knownMessages,
        headMessage = sortedMessages.head
      )

      tailMessagesSource = enrichTailMessages(
        chatRoomId = addMessagesRequest.chatRoomId,
        messagesFromBatchAlreadyPersisted = knownMessages,
        sortedMessagesInBatch = sortedMessages
      )

      tailMessagesPersisted <- tailMessagesSource
        .via(chatroomMessageRepository.persistMessages)
        //This is a potential source if inconsistency: if a message gets persisted, but updating the metrics fails,
        //then our average would be wrong !
        .via(chatroomMessageRepository.updateChatroomMetrics)
        .runWith(Sink.seq)

      headMessagePersisted <- headMessageSource
        .via(chatroomMessageRepository.persistMessages)
        //This is a potential source if inconsistency: if a message gets persisted, but updating the metrics fails,
        //then our average would be wrong !
        .via(chatroomMessageRepository.updateChatroomMetrics)
        .runWith(Sink.head)
    } yield tailMessagesPersisted :+ headMessagePersisted
  }


  //Enrich the head of the batch with the length of the pause before it.
  //This requires looking up the las message from the previous batch fro HBase in most cases.
  private def enrichHeadMessage(chatRoomId: Int,
                                messagesFromBatchAlreadyPersisted: Seq[ChatroomMessage],
                                headMessage: ChatroomMessage)
                               (implicit mat: Materializer, ex: ExecutionContext) = {

    headMessage match {
      //Message already persisted, nothing to do  
      case messageAlreadyPersisted if messagesFromBatchAlreadyPersisted.contains(messageAlreadyPersisted) =>
        Source.empty[ChatroomMessageWithStats]
      //First message in the batch, no pause before it  
      case firstMessageInChatroom if firstMessageInChatroom.index == 1 =>
        Source.single(
          ChatroomMessageWithStats(
            chatroomId = chatRoomId,
            timeSincePreviousMessage = -1,
            message = headMessage
          )
        )
      case _ =>
        //We have a method returning a future here in order to trigger the lookup of the previous message as soon as the 
        //source is created, and not only when it starts executing. Allows the lookup to run in parallel with the 
        //persisting of the tail messages
        val eventualPreviousMessage = chatroomMessageRepository.previousMessageInChatroom(
          chatroomId = chatRoomId,
          messageTimestamp = headMessage.timestamp,
          messageIndex = headMessage.index
        ).map {
          _.getOrElse(
            throw new MissingPreviousBatchException(
              s"The message before the message with chatrooId $chatRoomId and index ${headMessage.index} was not found"
            )
          )
        }

        Source.fromFuture(eventualPreviousMessage)
          .map { lastMessageOfPreviousBatch =>
            ChatroomMessageWithStats(
              chatroomId = chatRoomId,
              timeSincePreviousMessage = headMessage.timestamp - lastMessageOfPreviousBatch.message.timestamp,
              message = headMessage
            )
          }
    }
  }


  private def enrichTailMessages(chatRoomId: Int,
                                 messagesFromBatchAlreadyPersisted: Seq[ChatroomMessage],
                                 sortedMessagesInBatch: Seq[ChatroomMessage])
                                (implicit mat: Materializer, ex: ExecutionContext) = {

    val messagesInBatchByIndex = sortedMessagesInBatch.map(msg => msg.index -> msg).toMap

    Source
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
  }
}
