package com.github.dafutils.chatroom.service

import akka.stream.Materializer
import akka.stream.alpakka.hbase.HTableSettings
import akka.stream.alpakka.hbase.scaladsl.HTableStage
import akka.stream.scaladsl.Source
import com.github.dafutils.chatroom.hbase.Hbase._
import com.github.dafutils.chatroom.hbase.MessagesColumnFamily
import com.github.dafutils.chatroom.http.model.{AddMessages, ChatroomMessage, NewChatroom}
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.util.Bytes
import uk.gov.hmrc.emailaddress.EmailAddress

class ChatroomMessageRepository(createChatroomSettings: HTableSettings[NewChatroom],
                                messagesSettings: HTableSettings[AddMessages]) {

  def createChatroom(chatroom: NewChatroom)(implicit mat: Materializer) = {
    Source
      .single(chatroom)
      .via(HTableStage.flow(createChatroomSettings))
  }

  def addMessages(addMessagesRequest: AddMessages)(implicit mat: Materializer) = {
    Source
      .single(addMessagesRequest)
      .via(HTableStage.flow(messagesSettings))
  }

  def scanMessages(chatroomId: Int, from: Long, to: Long)(implicit mat: Materializer) = {
    import MessagesColumnFamily._
    HTableStage
      .source(new Scan(s"$chatroomId:$from", s"$chatroomId:$to"), messagesSettings)
      .map { result =>
        ChatroomMessage(
          index = Bytes.toInt(result.getValue(columnFamilyName, indexColumnName)),
          timestamp = Bytes.toLong(result.getValue(columnFamilyName, timestampColumnName)),
          author = EmailAddress(Bytes.toString(result.getValue(columnFamilyName, authorColumnName))),
          message = Bytes.toString(result.getValue(columnFamilyName, messageContentColumnName))
        )
      }
  }
}
