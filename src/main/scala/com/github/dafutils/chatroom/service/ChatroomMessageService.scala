package com.github.dafutils.chatroom.service

import java.nio.charset.StandardCharsets.UTF_8

import akka.stream.Materializer
import akka.stream.alpakka.hbase.HTableSettings
import akka.stream.alpakka.hbase.scaladsl.HTableStage
import akka.stream.scaladsl.Source
import com.github.dafutils.chatroom.hbase.Hbase._
import com.github.dafutils.chatroom.hbase.MessagesColumnFamily
import com.github.dafutils.chatroom.http.model.{AddMessages, ChatroomMessage, NewChatroom}
import org.apache.hadoop.hbase.client.Scan
import uk.gov.hmrc.emailaddress.EmailAddress

class ChatroomMessageService(createChatroomSettings: HTableSettings[NewChatroom],
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


    //    val scan = {
    //      val allFilters = new FilterList(
    //        Operator.MUST_PASS_ALL, 
    //        List[Filter](
    //          new RowFilter(CompareOp.GREATER_OR_EQUAL, new LongComparator(from)),
    //          new RowFilter(CompareOp.LESS, new LongComparator(to))
    //        ).asJava
    //      )
    //      
    //      val theScan = new Scan("startRow", allFilters)
    //      
    //      
    //      theScan.setRowPrefixFilter(s"$chatroomId:$from")
    //      theScan
    //    }
    HTableStage
      .source(new Scan(s"$chatroomId:$from", s"$chatroomId:$to"), messagesSettings)
      .map { result =>
        ChatroomMessage(
          index = new String(result.getColumnLatestCell(MessagesColumnFamily.columnFamilyName, MessagesColumnFamily.indexColumnName).getValueArray, UTF_8).toInt,
          timestamp = new String(result.getColumnLatestCell(MessagesColumnFamily.columnFamilyName, MessagesColumnFamily.timestampColumnName).getValueArray, UTF_8).toLong,
          author = EmailAddress(new String(result.getColumnLatestCell(MessagesColumnFamily.columnFamilyName, MessagesColumnFamily.authorColumnName).getValueArray, UTF_8)),
          message = new String(result.getColumnLatestCell(MessagesColumnFamily.columnFamilyName, MessagesColumnFamily.messageContentColumnName).getValueArray, UTF_8),
        )
      }
  }
}
