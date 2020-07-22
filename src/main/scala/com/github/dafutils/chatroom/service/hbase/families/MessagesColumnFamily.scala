package com.github.dafutils.chatroom.service.hbase.families

object MessagesColumnFamily {
  val contentColumnFamily = "content"
  val metricsColumnFamily = "metrics"

  val chatroomIdColumnName = "chatroom_id"
  val indexColumnName = "index"
  val timestampColumnName = "timestamp"
  val authorColumnName = "author"
  val messageContentColumnName = "message"
  val previousMessageTimestampColumnName = "previous_message_timestamp"
}
