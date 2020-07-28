package com.github.dafutils.chatroom.http.model

import scala.collection.immutable.Seq

case class AddMessages(
  chatRoomId: Long,
  messages: Seq[ChatroomMessage]                      
)
