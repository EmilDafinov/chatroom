package com.github.dafutils.chatroom.service.hbase.families
import com.github.dafutils.chatroom.service.hbase.HbaseImplicits._

object MessagesColumnFamily {
  val contentColumnFamily = "content"
  val metricsColumnFamily = "metrics"

  val chatroomIdColumnName = "chatroom_id"
  val indexColumnName = "index"
  val timestampColumnName = "timestamp"
  val authorColumnName = "author"
  val messageContentColumnName = "message"
  val previousMessageTimestampColumnName = "previous_message_timestamp"

  def rowKey(chatroomId: Int, messageTimestamp: Long): Array[Byte] = s"${chatroomId}:${messageTimestamp}"
  
}
