package com.github.dafutils.chatroom.service

import akka.stream.Materializer
import akka.stream.alpakka.hbase.HTableSettings
import akka.stream.alpakka.hbase.javadsl.HTableStage
import akka.stream.scaladsl.{Sink, Source}
import com.github.dafutils.chatroom.http.model.NewChatroom

class ChatroomMessageService(chatroomSettings: HTableSettings[NewChatroom]) {

  def createChatroom(chatroom: NewChatroom)(implicit mat: Materializer) = {
    Source
      .single(chatroom)
      .via(HTableStage.flow(chatroomSettings))
      .runWith(Sink.head)
  }
}
