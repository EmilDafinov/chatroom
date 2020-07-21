package com.github.dafutils.chatroom.service.hbase.families

object BatchLastMessageColumnMetricsFamily {
  val columnFamilyName = "batch_last_message"

  val lastMessageTimestamp = "last_message_timestamp"
}
