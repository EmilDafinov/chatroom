package com.github.dafutils.chatroom.service.hbase

import org.apache.hadoop.hbase.util.Bytes

object HbaseImplicits {

  implicit def longToBytes(s: Long) = Bytes.toBytes(s)
  implicit def bytesToLong(a: Array[Byte]): Long = Bytes.toLong(a)
  implicit def strToBytes(s: String) = Bytes.toBytes(s)
  implicit def bytesToString(a: Array[Byte]): String = Bytes.toString(a)
}
