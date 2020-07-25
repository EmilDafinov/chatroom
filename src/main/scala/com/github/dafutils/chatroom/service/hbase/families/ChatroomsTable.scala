package com.github.dafutils.chatroom.service.hbase.families
import com.github.dafutils.chatroom.service.hbase.HbaseImplicits._

object ChatroomsTable {
  val tableName = "chatrooms"
  
  //Column families
  val chatroomContentColumnFamilyName = "content"
  val chatroomMetricsColumnFamilyName = "metrics"

  //Columns
  val idColumnName = "id"
  val nameColumnName = "name"
  val createdColumnName = "created"
  val participantsColumnName = "participants"
  
  val totalPausesCountColumnName = "total_pauses_count"
  val totalPausesDurationColumnName = "total_pauses_duration"
  
  def rowKey(chatroomId: Int, modulo: Int) = s"${chatroomId % modulo}:${chatroomId}"
}

object MessagesTable {
  val tableName = "messages"
  
  val messagesContentColumnFamily = "content"
  val messagesMetricsColumnFamily = "metrics"

  val chatroomIdColumnName = "chatroom_id"
  val indexColumnName = "index"
  val timestampColumnName = "timestamp"
  val authorColumnName = "author"
  val messageContentColumnName = "message"
  val timeSincePreviousMessage = "pause_since_previous_message"

  //left as string for ease if reading
  def rowKey(chatroomId: Int, messageTimestamp: Long): Array[Byte] = s"${chatroomId}:${messageTimestamp}"

}
