package com.github.dafutils.chatroom.service

object MessagesColumnFamily {
  val columnFamilyName = "messages"
  val indexColumnName = "index"
  val timestampColumnName = "timestamp"
  val authorColumnName = "author"
  val messageContentColumnName = "message"
}
