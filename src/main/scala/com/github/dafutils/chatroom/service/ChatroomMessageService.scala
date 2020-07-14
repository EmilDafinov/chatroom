package com.github.dafutils.chatroom.service

import akka.stream.Materializer
import akka.stream.alpakka.hbase.javadsl.HTableStage
import akka.stream.scaladsl.{Sink, Source}
import com.github.dafutils.chatroom.http.model.NewChatroom
import com.github.dafutils.chatroom.hbase.Hbase._

class ChatroomMessageService {

  def createChatroom(chatroom: NewChatroom)(implicit mat: Materializer) = {
    Source
      .single(chatroom)
      .via(HTableStage.flow(chatroomSettings))
      .runWith(Sink.head)
  }
}
