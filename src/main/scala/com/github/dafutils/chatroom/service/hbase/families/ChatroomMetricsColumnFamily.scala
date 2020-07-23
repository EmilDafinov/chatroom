package com.github.dafutils.chatroom.service.hbase.families

object ChatroomMetricsColumnFamily {
  val columnFamilyName = "metrics"

  val totalPausesCount = "total_pauses_count"
  val totalPausesDuration = "total_pauses_duration"
  
}
