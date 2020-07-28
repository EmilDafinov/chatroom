package com.github.dafutils.chatroom.service

import com.github.dafutils.chatroom.AkkaDependencies
import com.github.dafutils.chatroom.service.hbase.ChatroomMessageRepository
import com.typesafe.config.ConfigFactory
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.client.HBaseAdmin

trait AbstractServices {
  val chatroomMessageRepository: ChatroomMessageRepository
  val chatroomService: ChatroomService
  val pausesService: PausesService
}

trait Services extends AbstractServices {
  this: AkkaDependencies =>

  val applicationConfin = ConfigFactory.load()
  val configuration: Configuration = {
    val conf = HBaseConfiguration.create()
    HBaseAdmin.checkHBaseAvailable(conf)
    conf
  }
  
  
  val chatroomMessageRepository = new ChatroomMessageRepository(
    configuration = configuration,
    modBy = applicationConfin.getInt("mod.by")
  )
  
  val chatroomService = new ChatroomService(chatroomMessageRepository = chatroomMessageRepository)
  
  val pausesService = new PausesService(chatroomMessageRepository = chatroomMessageRepository)
}
