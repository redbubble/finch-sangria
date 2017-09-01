package com.redbubble.util.http

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import java.util.Locale

object HttpTime {
  private val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")
      .withLocale(Locale.ENGLISH)
      .withZone(ZoneId.of("GMT"))

  private class Last(var millis: Long, var header: String)

  private val last = new ThreadLocal[Last] {
    override def initialValue: Last = new Last(0, "")
  }

  def currentTime(): String = {
    val local = last.get()
    val time = System.currentTimeMillis()

    if (time - local.millis > 1000) {
      local.millis = time
      local.header = formatter.format(Instant.ofEpochMilli(time))
      local.header
    } else {
      local.header
    }
  }
}
