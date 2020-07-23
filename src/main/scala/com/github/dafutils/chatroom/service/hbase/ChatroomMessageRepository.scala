package com.github.dafutils.chatroom.service.hbase

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.alpakka.hbase.HTableSettings
import akka.stream.alpakka.hbase.scaladsl.HTableStage
import akka.stream.scaladsl.Source
import com.github.dafutils.chatroom.http.model.{ChatroomMessage, ChatroomMessageWithStats, NewChatroom}
import com.github.dafutils.chatroom.service.hbase.HbaseImplicits._
import com.github.dafutils.chatroom.service.hbase.families.{ChatroomsColumnFamily, MessagesColumnFamily}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.{Mutation, Put, Scan}
import uk.gov.hmrc.emailaddress.EmailAddress

import scala.collection.immutable.Seq

class ChatroomMessageRepository(configuration: Configuration) {

  private val chatroomConverter: NewChatroom => Seq[Mutation] = { chatroom =>
    import com.github.dafutils.chatroom.service.hbase.families.ChatroomsColumnFamily._
    val put = new Put(chatroom.name)

    put.addColumn(columnFamilyName, idColumnName, chatroom.id)
    put.addColumn(columnFamilyName, nameColumnName, chatroom.name)
    put.addColumn(columnFamilyName, createdColumnName, chatroom.created)
    put.addColumn(columnFamilyName, participantsColumnName, chatroom.participants.mkString(","))
    List(put)
  }

  private val createChatroomSettings: HTableSettings[NewChatroom] = HTableSettings(
    conf = configuration,
    tableName = TableName.valueOf("chatrooms"),
    columnFamilies = Seq(ChatroomsColumnFamily.columnFamilyName),
    converter = chatroomConverter
  )

  private val messagesConverter: ChatroomMessageWithStats => Seq[Mutation] = { message =>

    import MessagesColumnFamily._

    val put = new Put(rowKey(message.chatroomId, message.message.timestamp))

    put.addColumn(contentColumnFamily, chatroomIdColumnName, message.chatroomId)
    put.addColumn(contentColumnFamily, indexColumnName, message.message.index)
    put.addColumn(contentColumnFamily, timestampColumnName, message.message.timestamp)
    put.addColumn(contentColumnFamily, authorColumnName, message.message.author.value)
    put.addColumn(contentColumnFamily, messageContentColumnName, message.message.message) // Tipping my hat to Joseph Heller ;)


    put.addColumn(metricsColumnFamily, previousMessageTimestampColumnName, message.previousMessageTimestamp)
    List(put)
  }

  private val messagesSettings: HTableSettings[ChatroomMessageWithStats] = HTableSettings(
    conf = configuration,
    tableName = TableName.valueOf("messages"),
    columnFamilies = Seq(MessagesColumnFamily.contentColumnFamily, MessagesColumnFamily.metricsColumnFamily),
    converter = messagesConverter
  )

  def createChatroom(chatroom: NewChatroom)(implicit mat: Materializer) = {
    Source
      .single(chatroom)
      .via(HTableStage.flow(createChatroomSettings))
  }

  def addMessages(addMessagesRequest: Seq[ChatroomMessageWithStats])(implicit mat: Materializer) = {
    Source
      .fromIterator(() => addMessagesRequest.iterator)
      .via(HTableStage.flow(messagesSettings))
  }

  def timestampOfPreviousMessageInChatroom(chatroomId: Int, messageIndex: Int, messageTimestamp: Long)(implicit mat: Materializer): Source[Long, NotUsed] = {
    import MessagesColumnFamily._

    require(messageIndex >= 1, "The indices of messages in a chatroom start from 1.")

    val previousMessageScan = new Scan(rowKey(chatroomId, messageTimestamp))
    previousMessageScan.setReversed(true)
    previousMessageScan.setMaxResultSize(1)

    messageIndex match {
      case 1 =>
        Source.single(-1)
      case index if index > 1 =>
        HTableStage
          .source(previousMessageScan, messagesSettings)
          .map { result =>

            val previousMessageTimestamp: Long = result.getValue(contentColumnFamily, timestampColumnName)
            val previousMessageIndex: Int = result.getValue(contentColumnFamily, indexColumnName)
            val previousMessageChatroomId: Int = result.getValue(contentColumnFamily, chatroomIdColumnName)

            (previousMessageChatroomId, previousMessageIndex, previousMessageTimestamp)
          }
          .filter { case (previousMessageChatroomId, previousMessageIndex, _) =>
            //Make sure that what we got is indeed the previous message in the chatroom (by index)
            previousMessageChatroomId == chatroomId && previousMessageIndex == messageIndex - 1
          }
          .map(_._3)
    }
  }


  def scanMessages(chatroomId: Int, from: Long, to: Long)(implicit mat: Materializer) = {
    import MessagesColumnFamily._
    val scan = new Scan(s"$chatroomId:$from", s"$chatroomId:$to")
    HTableStage
      .source(scan, messagesSettings)
      .map { result =>
        ChatroomMessage(
          index = result.getValue(contentColumnFamily, indexColumnName),
          timestamp = result.getValue(contentColumnFamily, timestampColumnName),
          author = EmailAddress(result.getValue(contentColumnFamily, authorColumnName)),
          message = result.getValue(contentColumnFamily, messageContentColumnName)
        )
      }
  }
}
