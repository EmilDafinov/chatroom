package com.github.dafutils.chatroom.http.model

import uk.gov.hmrc.emailaddress.EmailAddress

case class NewChatroom(
  id: Int, 
  name: String, 
  created: Long, 
  participants: Seq[EmailAddress]
)
