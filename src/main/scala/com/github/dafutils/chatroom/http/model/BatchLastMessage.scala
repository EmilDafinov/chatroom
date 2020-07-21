package com.github.dafutils.chatroom.http.model

case class BatchLastMessage(chatroomId: Int, lastMessageIndex: Int, lastMessageTimestamp: Long)
