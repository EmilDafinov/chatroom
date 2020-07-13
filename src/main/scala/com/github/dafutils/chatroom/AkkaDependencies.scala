package com.github.dafutils.chatroom

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContext

trait AkkaDependencies {
  implicit val actorSystem: ActorSystem = {
    val system = ActorSystem()
    system.registerOnTermination {
      System.exit(1)
    }
    system
  }
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()
}
