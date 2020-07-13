package com.github.dafutils.chatroom

import com.github.dafutils.chatroom.http.HttpRoute

import scala.util.Failure

object Main extends App with AkkaDependencies with HttpRoute with Application {

  server.start.andThen {
    case Failure(_) =>
      actorSystem.terminate()
  }
}
