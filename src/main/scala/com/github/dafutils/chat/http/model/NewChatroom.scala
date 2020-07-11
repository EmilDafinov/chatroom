package com.github.dafutils.chat.http.model

import uk.gov.hmrc.emailaddress.EmailAddress

case class NewChatroom(
  id: Int, 
  name: String, 
  created: Long, 
  participants: Seq[EmailAddress]
)
