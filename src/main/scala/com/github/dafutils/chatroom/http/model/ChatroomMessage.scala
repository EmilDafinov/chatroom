package com.github.dafutils.chatroom.http.model

import uk.gov.hmrc.emailaddress.EmailAddress

case class ChatroomMessage(
  index: Int,
  timestamp: Long,
  author: EmailAddress,
  message: String                        
) {
  require(index >= 1, "The chatroom messages should have indices greater than 1")
}

case class ChatroomMessageWithStats(
  chatroomId: Int,
  timeSincePreviousMessage: Long,
  message: ChatroomMessage,
)
