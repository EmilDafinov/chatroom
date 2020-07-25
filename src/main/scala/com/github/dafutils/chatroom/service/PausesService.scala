package com.github.dafutils.chatroom.service

import akka.stream.Materializer
import com.github.dafutils.chatroom.service.hbase.ChatroomMessageRepository

import scala.concurrent.ExecutionContext

class PausesService(chatroomMessageRepository: ChatroomMessageRepository) {

  def countLongPauses(chatroomId: Int, from: Long, to: Long)(implicit mat: Materializer, ec: ExecutionContext) = {
    //TODO: handle requests for long periods

    for {
      eventualAverage <- chatroomMessageRepository.averagePause(chatroomId)
      rowCount <- chatroomMessageRepository.countLongPauses(chatroomId, from, to, eventualAverage)
    } yield rowCount
  }
}
