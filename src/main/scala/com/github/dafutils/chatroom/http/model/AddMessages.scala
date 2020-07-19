package com.github.dafutils.chatroom.http.model

import scala.collection.immutable.Seq

case class AddMessages(
  chatRoomId: Int,
  messages: Seq[ChatroomMessage]                      
)
