package com.github.dafutils.chatroom.service

import com.github.dafutils.chatroom.AkkaDependencies
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.client.HBaseAdmin

trait Services {
  this: AkkaDependencies =>

  val configuration: Configuration = {
    val conf = HBaseConfiguration.create()
    HBaseAdmin.checkHBaseAvailable(conf)
    conf
  }
  
  val chatroomService = new ChatroomMessageRepository(configuration = configuration)
}
