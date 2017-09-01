package com.redbubble.util.io

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.charset.{Charset => NioCharset}

object Charset {
  val DefaultCharset: NioCharset = UTF_8
}
