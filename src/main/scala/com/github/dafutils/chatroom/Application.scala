package com.github.dafutils.chatroom

import com.github.dafutils.chatroom.http.{HttpRoute, HttpServer}
import com.typesafe.config.ConfigFactory


trait Application {
  this: AkkaDependencies with HttpRoute =>

  val rootConfig = ConfigFactory.load()
  val httpConfig = rootConfig.getConfig("http")
  val httpPort = httpConfig.getInt("port")
  val httpInterface = httpConfig.getString("interface")
  
  val server = new HttpServer(route, httpPort, httpInterface)
}
