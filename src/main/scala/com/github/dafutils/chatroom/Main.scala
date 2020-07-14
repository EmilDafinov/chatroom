package com.github.dafutils.chatroom

import com.github.dafutils.chatroom.http.HttpRoute
import com.github.dafutils.chatroom.service.Services

import scala.util.Failure

object Main extends App 
  with Configuration 
  with AkkaDependencies 
  with Services
  with HttpRoute 
  with Application {

  server.start.andThen {
    case Failure(_) =>
      actorSystem.terminate()
  }
}
