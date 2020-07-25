package com.github.dafutils.chatroom.service.hbase

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.alpakka.hbase.HTableSettings
import akka.stream.alpakka.hbase.scaladsl.HTableStage
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.github.dafutils.chatroom.http.model.{ChatroomMessage, ChatroomMessageWithStats, NewChatroom}
import com.github.dafutils.chatroom.service.hbase.HbaseImplicits._
import com.github.dafutils.chatroom.service.hbase.families.ChatroomsTable.{chatroomContentColumnFamilyName, chatroomMetricsColumnFamilyName, totalPausesCountColumnName, totalPausesDurationColumnName}
import com.github.dafutils.chatroom.service.hbase.families.MessagesTable._
import com.github.dafutils.chatroom.service.hbase.families.{ChatroomsTable, MessagesTable}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter
import uk.gov.hmrc.emailaddress.EmailAddress

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext

class ChatroomMessageRepository(configuration: Configuration, modBy: Int) {

  private val chatroomConverter: NewChatroom => Seq[Mutation] = { chatroom =>
    import com.github.dafutils.chatroom.service.hbase.families.ChatroomsTable._

    val put = new Put(rowKey(chatroom.id, modBy))

    put.addColumn(chatroomContentColumnFamilyName, idColumnName, chatroom.id)
    put.addColumn(chatroomContentColumnFamilyName, nameColumnName, chatroom.name)
    put.addColumn(chatroomContentColumnFamilyName, createdColumnName, chatroom.created)
    put.addColumn(chatroomContentColumnFamilyName, participantsColumnName, chatroom.participants.mkString(","))

    List(put)
  }

  private val chatroomStatsConverter: ChatroomMessageWithStats => Seq[Mutation] = { message =>
    import com.github.dafutils.chatroom.service.hbase.families.ChatroomsTable._

    val increment = new Increment(rowKey(message.chatroomId, modBy))
    val pauseCountIncrementValue: Long = if (message.message.index > 1) 1 else 0
    val totalPauseTimeIncrementValue: Long = if (message.message.index > 1) message.message.timestamp - message.timeSincePreviousMessage else 0

    increment.addColumn(chatroomMetricsColumnFamilyName, totalPausesCountColumnName, pauseCountIncrementValue)
    increment.addColumn(chatroomMetricsColumnFamilyName, totalPausesDurationColumnName, totalPauseTimeIncrementValue)

    List(increment)
  }

  private val chatroomMetricsSettings: HTableSettings[ChatroomMessageWithStats] = HTableSettings(

    conf = configuration,
    tableName = TableName.valueOf(ChatroomsTable.tableName),
    columnFamilies = Seq(chatroomContentColumnFamilyName, chatroomMetricsColumnFamilyName),
    converter = chatroomStatsConverter
  )

  private val createChatroomSettings: HTableSettings[NewChatroom] = HTableSettings(
    conf = configuration,
    tableName = TableName.valueOf(ChatroomsTable.tableName),
    columnFamilies = Seq(chatroomContentColumnFamilyName, chatroomMetricsColumnFamilyName),
    converter = chatroomConverter
  )

  private val messagesConverter: ChatroomMessageWithStats => Seq[Mutation] = { message =>

    val put = new Put(MessagesTable.rowKey(message.chatroomId, message.message.timestamp))

    put.addColumn(messagesContentColumnFamily, chatroomIdColumnName, message.chatroomId)
    put.addColumn(messagesContentColumnFamily, indexColumnName, message.message.index)
    put.addColumn(messagesContentColumnFamily, timestampColumnName, message.message.timestamp)
    put.addColumn(messagesContentColumnFamily, authorColumnName, message.message.author.value)
    put.addColumn(messagesContentColumnFamily, messageContentColumnName, message.message.message) // Tipping my hat to Joseph Heller ;)

    val pauseSincePreviousMessage: Long = if (message.timeSincePreviousMessage == -1) {
      -1
    } else {
      message.message.timestamp - message.timeSincePreviousMessage
    }
    put.addColumn(messagesMetricsColumnFamily, timeSincePreviousMessage, pauseSincePreviousMessage)

    List(put)
  }

  private val messagesSettings: HTableSettings[ChatroomMessageWithStats] = HTableSettings(
    conf = configuration,
    tableName = TableName.valueOf(MessagesTable.tableName),
    columnFamilies = Seq(messagesContentColumnFamily, messagesMetricsColumnFamily),
    converter = messagesConverter
  )

  def createChatroom(chatroom: NewChatroom)(implicit mat: Materializer) = {
    Source
      .single(chatroom)
      .via(HTableStage.flow(createChatroomSettings))
  }

  def persistMessages(implicit mat: Materializer) = {
    Flow[ChatroomMessageWithStats]
      .via(HTableStage.flow(messagesSettings))
  }

  def updateChatroomMetrics(implicit mat: Materializer) = {
    Flow[ChatroomMessageWithStats]
      .via(HTableStage.flow(chatroomMetricsSettings))
  }

  def timestampOfPreviousMessageInChatroom(chatroomId: Int, messageIndex: Int, messageTimestamp: Long)(implicit mat: Materializer): Source[Long, NotUsed] = {

    require(messageIndex >= 1, s"Attempted to get the previous message time for message with index $messageIndex in $chatroomId.")

    val previousMessageScan = new Scan(MessagesTable.rowKey(chatroomId, messageTimestamp))
    previousMessageScan.setReversed(true)
    previousMessageScan.setMaxResultSize(1)

    messageIndex match {
      case 1 =>
        Source.single(-1)
      case index if index > 1 =>
        HTableStage
          .source(previousMessageScan, messagesSettings)
          .map { result =>

            val previousMessageTimestamp: Long = result.getValue(messagesContentColumnFamily, timestampColumnName)
            val previousMessageIndex: Int = result.getValue(messagesContentColumnFamily, indexColumnName)
            val previousMessageChatroomId: Int = result.getValue(messagesContentColumnFamily, chatroomIdColumnName)

            (previousMessageChatroomId, previousMessageIndex, previousMessageTimestamp)
          }
          .filter { case (previousMessageChatroomId, previousMessageIndex, _) =>
            //Make sure that what we got is indeed the previous message in the chatroom (by index)
            previousMessageChatroomId == chatroomId && previousMessageIndex == messageIndex - 1
          }
          .map(_._3)
    }
  }

  def chatroomMessagesInPeriod(chatroomId: Int, from: Long, to: Long)(implicit mat: Materializer): Source[ChatroomMessageWithStats, NotUsed] = {
    
    val scan = new Scan(MessagesTable.rowKey(chatroomId, from), MessagesTable.rowKey(chatroomId, to))
    HTableStage
      .source(scan, messagesSettings)
      .map { result =>
        ChatroomMessageWithStats(
          chatroomId = result.getValue(messagesContentColumnFamily, chatroomIdColumnName),
          timeSincePreviousMessage = result.getValue(messagesMetricsColumnFamily, timeSincePreviousMessage),
          message = ChatroomMessage(
            index = result.getValue(messagesContentColumnFamily, indexColumnName),
            timestamp = result.getValue(messagesContentColumnFamily, timestampColumnName),
            author = EmailAddress(result.getValue(messagesContentColumnFamily, authorColumnName)),
            message = result.getValue(messagesContentColumnFamily, messageContentColumnName)
          )
        )
      }
  }

  def averagePause(chatroomId: Int)(implicit mat: Materializer, ec: ExecutionContext) = {
    HTableStage
      .source(new Scan(new Get(ChatroomsTable.rowKey(chatroomId, modBy))), chatroomMetricsSettings)
      .map { result =>
        val totalPauseTime: Long = result.getValue(chatroomMetricsColumnFamilyName, totalPausesDurationColumnName)
        val totalPauseCount: Long = result.getValue(chatroomMetricsColumnFamilyName, totalPausesCountColumnName)
        totalPauseTime.toDouble / totalPauseCount.toDouble
      }
      .runWith(Sink.headOption).map(_.getOrElse(Double.MaxValue))
  }

  def countLongPauses(chatroomId: Int, from: Long, to: Long, averagePauseTime: Double)(implicit mat: Materializer) = {
    //TODO: large periods...will this attempt to load everything into memory?
    val scan = new Scan(MessagesTable.rowKey(chatroomId, from), MessagesTable.rowKey(chatroomId, to))
    scan.setFilter(new SingleColumnValueFilter(messagesMetricsColumnFamily, timeSincePreviousMessage, CompareOp.GREATER, averagePauseTime))
    
    HTableStage
      .source(scan, messagesSettings)
      .runWith(Sink.fold(0) { (count, _) => count + 1 })
  }
}
