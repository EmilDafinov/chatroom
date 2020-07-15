package com.github.dafutils.chatroom.hbase

import java.nio.charset.StandardCharsets.UTF_8

import akka.stream.alpakka.hbase.HTableSettings
import com.github.dafutils.chatroom.http.model.NewChatroom
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.client.{HBaseAdmin, Mutation, Put}
import org.apache.hadoop.hbase.{HBaseConfiguration, TableName}

import scala.collection.immutable.Seq


trait Hbase {
  val chatroomColumnFamily = "chatroom"

  implicit def strToUtf8Bytes[T](s: T) = s.toString.getBytes(UTF_8)

  val chatroomConverter: NewChatroom => Seq[Mutation] = { chatroom =>
    val put = new Put(s"${chatroom.name}".getBytes(UTF_8))
    put.addColumn(chatroomColumnFamily, "id", chatroom.id)
    put.addColumn(chatroomColumnFamily, "name", chatroom.name)
    put.addColumn(chatroomColumnFamily, "created", chatroom.created)
    put.addColumn(chatroomColumnFamily, "participants", chatroom.participants.mkString(","))
    List(put)
  }


  val configuration: Configuration = {
    val conf = HBaseConfiguration.create()
    HBaseAdmin.checkHBaseAvailable(conf)
    conf
  }

  val chatroomSettings: HTableSettings[NewChatroom] = HTableSettings(
    conf = configuration,
    tableName = TableName.valueOf("chatrooms"),
    columnFamilies = Seq(chatroomColumnFamily),
    chatroomConverter
  )
}
