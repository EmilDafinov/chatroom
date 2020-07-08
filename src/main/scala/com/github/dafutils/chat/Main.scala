package com.github.dafutils.chat

import scala.util.{Failure, Success}

object Main extends App with AkkaDependencies with HttpRoute with Application {

  server.start.andThen {
    case Failure(ex) => actorSystem.terminate()
  }
}
