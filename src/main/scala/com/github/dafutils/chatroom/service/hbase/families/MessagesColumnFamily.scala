package com.github.dafutils.chatroom.service.hbase.families

object MessagesColumnFamily {
  val columnFamilyName = "messages"
  val indexColumnName = "index"
  val timestampColumnName = "timestamp"
  val authorColumnName = "author"
  val messageContentColumnName = "message"
}
