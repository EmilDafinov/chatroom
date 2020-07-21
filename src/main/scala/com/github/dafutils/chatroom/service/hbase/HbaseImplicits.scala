package com.github.dafutils.chatroom.service.hbase

import org.apache.hadoop.hbase.util.Bytes

object HbaseImplicits {
  //TODO: That's bound to hurt performance wise: everything is stored as a string !
  implicit def intToBytes(s: Int) = Bytes.toBytes(s) 
  implicit def bytesToInt(a: Array[Byte]): Int = Bytes.toInt(a) 
  implicit def longToBytes(s: Long) = Bytes.toBytes(s)
  implicit def bytesToLong(a: Array[Byte]): Long = Bytes.toLong(a)
  implicit def strToBytes(s: String) = Bytes.toBytes(s)
  implicit def bytesToString(a: Array[Byte]): String = Bytes.toString(a)
}
