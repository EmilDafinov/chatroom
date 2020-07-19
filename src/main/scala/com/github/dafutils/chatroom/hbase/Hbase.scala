package com.github.dafutils.chatroom.hbase

import java.nio.charset.StandardCharsets.UTF_8

import akka.stream.alpakka.hbase.HTableSettings
import com.github.dafutils.chatroom.http.model.{AddMessages, NewChatroom}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.client.{HBaseAdmin, Mutation, Put}
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.{HBaseConfiguration, TableName}

import scala.collection.immutable.Seq

object MessagesColumnFamily {
  val columnFamilyName = "messages"
  val indexColumnName = "index"
  val timestampColumnName = "timestamp"
  val authorColumnName = "author"
  val messageContentColumnName = "message"
}

object ChatroomsColumnFamily {
  val columnFamilyName = "chatrooms"
}

object Hbase {
  //TODO: That's bound to hurt performance wise: everything is stored as a string !
  implicit def toUtf8Bytes[T](s: T) = s.toString.getBytes(UTF_8) 
  
}

trait Hbase {
  import Hbase._
  
  val chatroomConverter: NewChatroom => Seq[Mutation] = { chatroom =>
    import ChatroomsColumnFamily._
    val put = new Put(chatroom.name)
    put.addColumn(columnFamilyName, "id", chatroom.id)
    put.addColumn(columnFamilyName, "name", chatroom.name)
    put.addColumn(columnFamilyName, "created", chatroom.created)
    put.addColumn(columnFamilyName, "participants", chatroom.participants.mkString(","))
    List(put)
  }
  
  val messagesConverter: AddMessages => Seq[Mutation] = { addMessagesRequest => 
    
    addMessagesRequest.messages.map { message =>
      import MessagesColumnFamily._
      val put = new Put(s"${addMessagesRequest.chatRoomId}:${message.timestamp}")
      put.addColumn(columnFamilyName, indexColumnName, Bytes.toBytes(message.index))
      put.addColumn(columnFamilyName, timestampColumnName, Bytes.toBytes(message.timestamp))
      put.addColumn(columnFamilyName, authorColumnName, Bytes.toBytes(message.author.value))
      put.addColumn(columnFamilyName, messageContentColumnName, Bytes.toBytes(message.message))
      put
    }
  }


  val configuration: Configuration = {
    val conf = HBaseConfiguration.create()
    HBaseAdmin.checkHBaseAvailable(conf)
    conf
  }

  val createChatroomSettings: HTableSettings[NewChatroom] = HTableSettings(
    conf = configuration,
    tableName = TableName.valueOf("chatrooms"),
    columnFamilies = Seq(ChatroomsColumnFamily.columnFamilyName),
    converter = chatroomConverter
  )
  
  val messagesSettings: HTableSettings[AddMessages] = HTableSettings(
    conf = configuration,
    tableName = TableName.valueOf("messages"),
    columnFamilies = Seq(MessagesColumnFamily.columnFamilyName),
    converter = messagesConverter
  )
}
