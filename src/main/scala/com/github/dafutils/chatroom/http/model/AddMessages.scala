package com.github.dafutils.chatroom.http.model

case class AddMessages(
  chatRoomId: Int,
  messages: Seq[ChatroomMessage]                      
)
