package com.github.dafutils.chatroom.service.hbase

import org.apache.hadoop.hbase.util.Bytes

object HbaseImplicits {
  //TODO: That's bound to hurt performance wise: everything is stored as a string !
  implicit def intToBytes(s: Int) = Bytes.toBytes(s) 
  implicit def longToBytes(s: Long) = Bytes.toBytes(s) 
  implicit def strToBytes(s: String) = Bytes.toBytes(s)
}
