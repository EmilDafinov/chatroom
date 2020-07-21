package com.github.dafutils.chatroom.service.hbase.families

object ChatroomsColumnFamily {
  val columnFamilyName = "chatrooms"

  val idColumnName = "id"
  val nameColumnName = "name"
  val createdColumnName = "created"
  val participantsColumnName = "participants"
}
