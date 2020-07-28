package com.github.dafutils.chatroom.http.model

import uk.gov.hmrc.emailaddress.EmailAddress

case class Chatroom(
  id: Long, 
  name: String, 
  created: Long, 
  participants: Seq[EmailAddress]
)
