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

  val idColumnName = "id"
  val nameColumnName = "name"
  val createdColumnName = "created"
  val participantsColumnName = "participants"
}

object Hbase {
  //TODO: That's bound to hurt performance wise: everything is stored as a string !
  implicit def intToBytes(s: Int) = Bytes.toBytes(s) 
  implicit def longToBytes(s: Long) = Bytes.toBytes(s) 
  implicit def strToBytes(s: String) = Bytes.toBytes(s)
}

trait Hbase {
  import Hbase._
  
  val chatroomConverter: NewChatroom => Seq[Mutation] = { chatroom =>
    import ChatroomsColumnFamily._
    val put = new Put(chatroom.name)
    
    put.addColumn(columnFamilyName, idColumnName, chatroom.id)
    put.addColumn(columnFamilyName, nameColumnName, chatroom.name)
    put.addColumn(columnFamilyName, createdColumnName, chatroom.created)
    put.addColumn(columnFamilyName, participantsColumnName, chatroom.participants.mkString(","))
    List(put)
  }
  
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
