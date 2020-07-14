package com.github.dafutils.chatroom.service

import com.github.dafutils.chatroom.AkkaDependencies

trait Services {  
  this: AkkaDependencies =>

  val chatroomService = new ChatroomMessageService
}
