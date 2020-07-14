package com.github.dafutils.chatroom

import com.typesafe.config.ConfigFactory

trait Configuration {
  val rootConfig = ConfigFactory.load()
}
