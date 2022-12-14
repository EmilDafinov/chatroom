package com.github.dafutils.chatroom.service.hbase

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.alpakka.hbase.HTableSettings
import akka.stream.alpakka.hbase.scaladsl.HTableStage
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.github.dafutils.chatroom.http.model.{Chatroom, ChatroomMessage, ChatroomMessageWithStats}
import com.github.dafutils.chatroom.service.hbase.HbaseImplicits._
import com.github.dafutils.chatroom.service.hbase.families.ChatroomsTable.{rowKey => _, _}
import com.github.dafutils.chatroom.service.hbase.families.MessagesTable._
import com.github.dafutils.chatroom.service.hbase.families.{ChatroomsTable, MessagesTable}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp
import org.apache.hadoop.hbase.filter.{LongComparator, SingleColumnValueFilter}
import uk.gov.hmrc.emailaddress.EmailAddress

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}

class ChatroomMessageRepository(configuration: Configuration, modBy: Int) {

  // Describes the HBase operation needed to store the information regarding a chatroom
  private val chatroomConverter: Chatroom => Seq[Mutation] = { chatroom =>
    import com.github.dafutils.chatroom.service.hbase.families.ChatroomsTable._

    val put = new Put(rowKey(chatroom.id, modBy))

    put.addColumn(chatroomContentColumnFamilyName, idColumnName, chatroom.id)
    put.addColumn(chatroomContentColumnFamilyName, nameColumnName, chatroom.name)
    put.addColumn(chatroomContentColumnFamilyName, createdColumnName, chatroom.created)
    put.addColumn(chatroomContentColumnFamilyName, participantsColumnName, chatroom.participants.mkString(","))

    List(put)
  }

  //The increment necessary in order to update the chatroom stats following persisting of a message
  private val chatroomStatsConverter: ChatroomMessageWithStats => Seq[Mutation] = { message =>
    import com.github.dafutils.chatroom.service.hbase.families.ChatroomsTable._

    val increment = new Increment(rowKey(message.chatroomId, modBy))
    val pauseCountIncrementValue: Long = if (message.message.index > 1) 1 else 0
    val totalPauseTimeIncrementValue: Long = if (message.message.index > 1) message.timeSincePreviousMessage else 0

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

  private val createChatroomSettings: HTableSettings[Chatroom] = HTableSettings(
    conf = configuration,
    tableName = TableName.valueOf(ChatroomsTable.tableName),
    columnFamilies = Seq(chatroomContentColumnFamilyName, chatroomMetricsColumnFamilyName),
    converter = chatroomConverter
  )

  //Storing a message. Note that for each message we should have resolved the length of the pause
  //since the previous message in the chatroom
  private val messagesConverter: ChatroomMessageWithStats => Seq[Mutation] = { message =>

    val put = new Put(MessagesTable.rowKey(message.chatroomId, message.message.timestamp))

    put.addColumn(messagesContentColumnFamily, chatroomIdColumnName, message.chatroomId)
    put.addColumn(messagesContentColumnFamily, indexColumnName, message.message.index)
    put.addColumn(messagesContentColumnFamily, timestampColumnName, message.message.timestamp)
    put.addColumn(messagesContentColumnFamily, authorColumnName, message.message.author.value)
    put.addColumn(messagesContentColumnFamily, messageContentColumnName, message.message.message) // Tipping my hat to Joseph Heller ;)

    put.addColumn(messagesMetricsColumnFamily, timeSincePreviousMessage, message.timeSincePreviousMessage)

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

  /**
   * Persist a chatroom.
   **/
  def createChatroom(chatroom: Chatroom)(implicit mat: Materializer): Future[Chatroom] = {
    Source
      .single(chatroom)
      .via(HTableStage.flow(createChatroomSettings))
      .runWith(Sink.head)
  }

  /**
   * Persistance flow used to store chatroom messages in HBase. Messages have already been enriched with 
   * the length of the preceding pause.
   **/
  def persistMessages(implicit mat: Materializer): Flow[ChatroomMessageWithStats, ChatroomMessageWithStats, NotUsed] = {
    Flow[ChatroomMessageWithStats]
      .via(HTableStage.flow(messagesSettings))
  }

  /**
   * Update the total pause duration and total pause count for a chatroom
   **/
  def updateChatroomMetrics(implicit mat: Materializer): Flow[ChatroomMessageWithStats, ChatroomMessageWithStats, NotUsed] = {
    Flow[ChatroomMessageWithStats]
      .via(HTableStage.flow(chatroomMetricsSettings))
  }


  /**
   * Retrieve the previous message in a chatroom from HBase, if it already exists in the datastore.
   **/
  def previousMessageInChatroom(chatroomId: Long, messageIndex: Long, messageTimestamp: Long)
                               (implicit mat: Materializer): Future[Option[ChatroomMessageWithStats]] = {

    require(messageIndex > 1, s"Attempted to get the previous message time for message with index $messageIndex in $chatroomId. Only makes sense for the second and later messages.")

    HTableStage
      .source(
        scan = {
          val previousMessageScan = new Scan(MessagesTable.rowKey(chatroomId, messageTimestamp))
          previousMessageScan.setReversed(true)
          previousMessageScan.setMaxResultSize(1)
          //          previousMessageScan.setFilter(
          //            new FilterList(
          //              FilterList.Operator.MUST_PASS_ALL,
          //              new SingleColumnValueFilter(messageContentColumnName, chatroomIdColumnName, CompareOp.EQUAL, new LongComparator(chatroomId)),
          //              new SingleColumnValueFilter(messageContentColumnName, indexColumnName, CompareOp.EQUAL, new LongComparator(messageIndex - 1L)),
          //            )
          //          )
          previousMessageScan
        },
        settings = messagesSettings
      )
      .map(extractChatroomMessageWithStats)
      //Possible improvement: I'm filtering the messages in the application as opposed to HBase :(
      //                      See the commented code above, that was my first attempt. Unfortunately it was still 
      //                      picking up the previous row in the table, even when it was not satisfying the filters.
      //                      Very curious to know what I was missing...
      .filter(_.chatroomId == chatroomId)
      .filter(_.message.index == messageIndex - 1)
      .runWith(Sink.headOption)
  }

  /**
   * Retrieve all messages stored for a given chatroom id for the indicated period (from time included, to time exclusive)
   **/
  def chatroomMessagesInPeriod(chatroomId: Long, from: Long, to: Long)
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

  /**
   * Calculate the average pause for a chatroom. We do this by dividing the total pause time by the total pause
   * count. Both of these are stored in the chatroom row.
   **/
  def averagePause(chatroomId: Long)(implicit mat: Materializer, ec: ExecutionContext): Future[Option[Double]] = {
    HTableStage
      .source(new Scan(new Get(ChatroomsTable.rowKey(chatroomId, modBy))), chatroomMetricsSettings)
      .map { result =>
        val totalPauseTime: Long = result.getValue(chatroomMetricsColumnFamilyName, totalPausesDurationColumnName)
        val totalPauseCount: Long = result.getValue(chatroomMetricsColumnFamilyName, totalPausesCountColumnName)
        totalPauseTime.toDouble / totalPauseCount.toDouble
      }
      .runWith(Sink.headOption)
  }

  /**
   * Count the long pauses in a timerange by iterating over it and counting the messages with a pause before them larger than 
   * the average
   **/
  def countLongPauses(chatroomId: Long, from: Long, to: Long, averagePauseTime: Double)(implicit mat: Materializer) = {
    //TODO: large periods...will this attempt to load everything into memory?
    require(averagePauseTime > 0, "The average pause length cannot be negative")

    //Note: first messages in the chatroom have timeSincePreviousMessage == -1 and therefore would be filtered out.
    val scan = new Scan(MessagesTable.rowKey(chatroomId, from), MessagesTable.rowKey(chatroomId, to))
    scan.setFilter(new SingleColumnValueFilter(
      messagesMetricsColumnFamily,
      timeSincePreviousMessage,
      CompareOp.GREATER,
      //the casting strips away the fractional part of the double. However, since the next smaller long would be greater by a whole unit,
      //the comparison would still hold
      new LongComparator(averagePauseTime.toLong)
    ))

    HTableStage
      .source(scan, messagesSettings)
      .runWith(Sink.fold(0) { (count, _) => count + 1 })
  }
}
