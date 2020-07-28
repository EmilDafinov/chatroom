package com.github.dafutils.chatroom.service

import akka.stream.Materializer
import com.github.dafutils.chatroom.service.hbase.ChatroomMessageRepository

import scala.concurrent.{ExecutionContext, Future}

class PausesService(chatroomMessageRepository: ChatroomMessageRepository) {

  def countLongPauses(chatroomId: Long, from: Long, to: Long)(implicit mat: Materializer, ec: ExecutionContext): Future[Long] = {
    for {
      maybeAveragePauseLength <- chatroomMessageRepository.averagePause(chatroomId)
      eventualLongPausesCount = maybeAveragePauseLength.map { averagePauseLength =>
        chatroomMessageRepository.countLongPauses(chatroomId, from, to, averagePauseLength)
      }.getOrElse(Future.successful(0))
      longPausesCount <- eventualLongPausesCount
    } yield longPausesCount
  }
}
