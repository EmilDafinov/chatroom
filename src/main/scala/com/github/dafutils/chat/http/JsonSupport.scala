package com.github.dafutils.chat.http

import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.JsonAST.JString
import org.json4s.jackson.Serialization
import org.json4s.{CustomSerializer, DefaultFormats, Formats, Serialization}
import uk.gov.hmrc.emailaddress.EmailAddress

object EmailSerializer extends CustomSerializer[EmailAddress](_ =>
  ( 
    {
      case JString(s) => EmailAddress(s)
    }, 
    {
      case emailAddress: EmailAddress => JString(emailAddress.value)
    }
  )
)

object JsonSupport extends Json4sSupport {

  implicit lazy val serialization: Serialization = Serialization

  implicit val formats: Formats = new DefaultFormats {
    override val allowNull: Boolean = false
  } + EmailSerializer
}
