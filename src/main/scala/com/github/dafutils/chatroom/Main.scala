package com.github.dafutils.chatroom

import akka.event.Logging
import com.github.dafutils.chatroom.http.HttpRoute
import com.github.dafutils.chatroom.service.Services

import scala.util.Failure

object Main extends App
  with Configuration
  with AkkaDependencies
  with Services
  with HttpRoute
  with Application {

  private val log = Logging(actorSystem, this.getClass)
  log.info("Starting HTTP server")
  server.start
    .flatMap { _ =>
      log.info("Initializing HBase ...")
      chatroomMessageRepository.initializeDatastore
    }

    .andThen {
      case Failure(exception) =>
        log.error(cause = exception, message = "Service startup failed")
        actorSystem.terminate()
        System.exit(1)
    }
}
