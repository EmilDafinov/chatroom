package com.github.dafutils.chatroom.service

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.github.dafutils.chatroom.http.model.{AddMessages, BatchLastMessage}
import com.github.dafutils.chatroom.service.hbase.ChatroomMessageRepository

import scala.concurrent.ExecutionContext

class ChatroomService(chatroomMessageRepository: ChatroomMessageRepository) {

  def storeMessages(addMessagesRequest: AddMessages)(implicit mat: Materializer, ec: ExecutionContext) = {
    val lastMessage = addMessagesRequest.messages.maxBy(_.index)
    
    val storeBatchLatMessageTimestamp = chatroomMessageRepository.storeBatchLastMessage(
      batchLastMessage = BatchLastMessage(
        chatroomId = addMessagesRequest.chatRoomId,
        lastMessageIndex = lastMessage.index,
        lastMessageTimestamp = lastMessage.timestamp
      )
    ).runWith(Sink.ignore)
    
    val storeIndividualMessages = chatroomMessageRepository.addMessages(addMessagesRequest).runWith(Sink.head)
    
    for {
      _ <- storeBatchLatMessageTimestamp
      batchStored <- storeIndividualMessages
    } yield batchStored
  }
}
