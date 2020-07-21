package com.github.dafutils.chatroom.service.hbase

import akka.stream.Materializer
import akka.stream.alpakka.hbase.HTableSettings
import akka.stream.alpakka.hbase.scaladsl.HTableStage
import akka.stream.scaladsl.Source
import com.github.dafutils.chatroom.http.model.{AddMessages, ChatroomMessage, NewChatroom}
import com.github.dafutils.chatroom.service.hbase.HbaseImplicits._
import com.github.dafutils.chatroom.service.hbase.families.{ChatroomsColumnFamily, MessagesColumnFamily}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.{Mutation, Put, Scan}
import org.apache.hadoop.hbase.util.Bytes
import uk.gov.hmrc.emailaddress.EmailAddress

import scala.collection.immutable.Seq

class ChatroomMessageRepository(configuration: Configuration) {

  val chatroomConverter: NewChatroom => Seq[Mutation] = { chatroom =>
    import com.github.dafutils.chatroom.service.hbase.families.ChatroomsColumnFamily._
    val put = new Put(chatroom.name)

    put.addColumn(columnFamilyName, idColumnName, chatroom.id)
    put.addColumn(columnFamilyName, nameColumnName, chatroom.name)
    put.addColumn(columnFamilyName, createdColumnName, chatroom.created)
    put.addColumn(columnFamilyName, participantsColumnName, chatroom.participants.mkString(","))
    List(put)
  }

  val createChatroomSettings: HTableSettings[NewChatroom] = HTableSettings(
    conf = configuration,
    tableName = TableName.valueOf("chatrooms"),
    columnFamilies = Seq(ChatroomsColumnFamily.columnFamilyName),
    converter = chatroomConverter
  )
  
  val messagesConverter: AddMessages => Seq[Mutation] = { addMessagesRequest =>

    addMessagesRequest.messages.map { message =>
      import MessagesColumnFamily._
      val put = new Put(s"${addMessagesRequest.chatRoomId}:${message.timestamp}")
      put.addColumn(columnFamilyName, indexColumnName, message.index)
      put.addColumn(columnFamilyName, timestampColumnName, message.timestamp)
      put.addColumn(columnFamilyName, authorColumnName, message.author.value)
      put.addColumn(columnFamilyName, messageContentColumnName, message.message)
      put
    }
  }
  
  val messagesSettings: HTableSettings[AddMessages] = HTableSettings(
    conf = configuration,
    tableName = TableName.valueOf("messages"),
    columnFamilies = Seq(MessagesColumnFamily.columnFamilyName),
    converter = messagesConverter
  )
  
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
