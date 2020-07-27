package com.github.dafutils.chatroom.service.hbase

import akka.stream.Materializer
import akka.stream.alpakka.hbase.HTableSettings
import akka.stream.alpakka.hbase.scaladsl.HTableStage
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.github.dafutils.chatroom.http.model.{ChatroomMessage, ChatroomMessageWithStats, NewChatroom}
import com.github.dafutils.chatroom.service.hbase.HbaseImplicits._
import com.github.dafutils.chatroom.service.hbase.families.ChatroomsTable.{rowKey => _, _}
import com.github.dafutils.chatroom.service.hbase.families.MessagesTable._
import com.github.dafutils.chatroom.service.hbase.families.{ChatroomsTable, MessagesTable}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter
import uk.gov.hmrc.emailaddress.EmailAddress

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}

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

  /**
   * Access both tables so they can be created if they don't exist
   **/
  def initializeDatastore(implicit ec: ExecutionContext, mat: Materializer) = {
    val chatroomsScan = new Scan()
    chatroomsScan.setMaxResultSize(1)

    val messagesScan = new Scan()
    messagesScan.setMaxResultSize(1)

    val pingChatroomsTable = HTableStage
      .source(chatroomsScan, chatroomMetricsSettings)
      .runWith(Sink.ignore)

    val pingMessagesTable = HTableStage
      .source(messagesScan, messagesSettings)
      .runWith(Sink.ignore)

    pingChatroomsTable.flatMap(_ => pingMessagesTable)
  }

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


  def previousMessageInChatroom(chatroomId: Int, messageIndex: Int, messageTimestamp: Long)
                               (implicit mat: Materializer): Future[Option[ChatroomMessageWithStats]] = {

    require(messageIndex > 1, s"Attempted to get the previous message time for message with index $messageIndex in $chatroomId.")

    HTableStage
      .source(
        scan = previousRowInTable(
          currentRow = MessagesTable.rowKey(chatroomId, messageTimestamp)
        ),
        settings = messagesSettings
      )
      .map(extractChatroomMessageWithStats)
      .filter(_.chatroomId == chatroomId)
      .filter(_.message.index == messageIndex - 1)
      .runWith(Sink.headOption)
  }
  
  private def previousRowInTable(currentRow: Array[Byte]): Scan = {
    val previousMessageScan = new Scan(currentRow)
    previousMessageScan.setReversed(true)
    previousMessageScan.setMaxResultSize(1)
    previousMessageScan
  }

  def chatroomMessagesInPeriod(chatroomId: Int, from: Long, to: Long)
                              (implicit mat: Materializer): Future[Seq[ChatroomMessageWithStats]] = {

    val scan = new Scan(MessagesTable.rowKey(chatroomId, from), MessagesTable.rowKey(chatroomId, to))
    HTableStage
      .source(scan, messagesSettings)
      .map(extractChatroomMessageWithStats)
      .runWith(Sink.seq)
  }

  private def extractChatroomMessageWithStats(result: Result): ChatroomMessageWithStats =
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

  def averagePause(chatroomId: Int)(implicit mat: Materializer, ec: ExecutionContext): Future[Option[Double]] = {
    HTableStage
      .source(new Scan(new Get(ChatroomsTable.rowKey(chatroomId, modBy))), chatroomMetricsSettings)
      .map { result =>
        val totalPauseTime: Long = result.getValue(chatroomMetricsColumnFamilyName, totalPausesDurationColumnName)
        val totalPauseCount: Long = result.getValue(chatroomMetricsColumnFamilyName, totalPausesCountColumnName)
        totalPauseTime.toDouble / totalPauseCount.toDouble
      }
      .runWith(Sink.headOption)
  }

  def countLongPauses(chatroomId: Int, from: Long, to: Long, averagePauseTime: Double)(implicit mat: Materializer) = {
    //TODO: large periods...will this attempt to load everything into memory?
    require(averagePauseTime > 0, "The average pause length cannot be negative")

    //Note: first messages in the chatroom have timeSincePreviousMessage == -1 and therefore would be filtered out.
    val scan = new Scan(MessagesTable.rowKey(chatroomId, from), MessagesTable.rowKey(chatroomId, to))
    scan.setFilter(new SingleColumnValueFilter(messagesMetricsColumnFamily, timeSincePreviousMessage, CompareOp.GREATER, averagePauseTime))

    HTableStage
      .source(scan, messagesSettings)
      .runWith(Sink.fold(0) { (count, _) => count + 1 })
  }
}
