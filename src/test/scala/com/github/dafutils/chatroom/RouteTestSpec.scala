package com.github.dafutils.chatroom

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.time.SpanSugar
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

trait RouteTestSpec extends AnyWordSpec 
  with ScalaFutures 
  with MockitoSugar 
  with SpanSugar
  with BeforeAndAfterAll {

  this: PatienceConfiguration =>
  
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = 1 minute,
    interval = 500 millis
  )
}
