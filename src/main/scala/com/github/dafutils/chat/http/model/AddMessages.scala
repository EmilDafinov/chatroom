package com.github.dafutils.chat.http.model

case class AddMessages(
  chatRoomId: Int,
  messages: Seq[ChatroomMessage]                      
)
