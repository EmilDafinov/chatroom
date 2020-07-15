package com.github.dafutils.chatroom.http.model

import uk.gov.hmrc.emailaddress.EmailAddress

case class ChatroomMessage(
  index: Int,
  timestamp: Long,
  author: EmailAddress,
  message: String                        
)