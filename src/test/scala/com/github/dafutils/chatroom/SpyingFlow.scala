package com.github.dafutils.chatroom

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.github.dafutils.chatroom.http.model.ChatroomMessageWithStats

import scala.collection.mutable

/**
 * Simple test flow that records the messages that go through it
 * */
object SpyingFlow {

  def apply[T](buffer: mutable.Buffer[T]): Flow[T, T, NotUsed] = {
    
    Flow[T].map { message =>
      buffer += message
      message
    }
  }
}
