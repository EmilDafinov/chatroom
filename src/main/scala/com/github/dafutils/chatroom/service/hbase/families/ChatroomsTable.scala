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

  /**
   * Hashing the chatroom id by adding a modulo of a division by a configurable number. 
   * This should help distribute the keys. One drawback is that once the constant has been set, changing it would
   * be difficult. 
   * 
   * I wonder if it wouldn't be simpler to use a UUID as the chatroom id, that would ensure the rowkeys are randomly distributed.
   * 
   * Since we only access individual chatrooms by id, any good hash function works for generating this key. 
   * The reason we don't want to just use the chatroom id is that I assume that new chatrooms are created consecutively, 
   * so we'd create hotspotting due to the fact that the chatroom ids would be monotonically increasing
   */
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

  /**
   * The rowkey for chatroom messages. Since we are doing scans by time, it is important that rowkeys of messages in the 
   * same chatroom are located close together.
   * 
   * IMPORTANT: The key is generated based on the assumption that the timestamps of consecutive messages in a given chatroom
   * are distinct and monotonically increasing
   * 
   * Also, we are assuming that the incoming write traffic is evenly distributed among the chatrooms, so we'd avoid hotspotting 
   * (writes to the same chatroom are likely to end up on the same server)
   * */
  //left as string for ease if reading
  def rowKey(chatroomId: Int, messageTimestamp: Long): Array[Byte] = s"${chatroomId}:${messageTimestamp}"

}
