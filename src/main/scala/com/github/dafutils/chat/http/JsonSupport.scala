package com.github.dafutils.chat.http

import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, Formats, Serialization}

object JsonSupport extends Json4sSupport {

  implicit lazy val serialization: Serialization = Serialization
  
  implicit val formats: Formats = new DefaultFormats {
    override val allowNull: Boolean = false
  }
}
