package com.github.dafutils.chatroom.service

import com.github.dafutils.chatroom.AkkaDependencies
import com.github.dafutils.chatroom.hbase.Hbase

trait Services {  
  this: AkkaDependencies with Hbase =>

  val chatroomService = new ChatroomMessageRepository(
    createChatroomSettings = createChatroomSettings,
    messagesSettings = messagesSettings
  )
}
