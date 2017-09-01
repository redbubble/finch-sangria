package com.redbubble.util.io

import com.twitter.io.Buf

package object syntax {

  implicit final class RichBuf(val b: Buf) extends AnyVal {
    def asString: String = BufOps.bufToString(b)
  }

  implicit final class RichString(val s: String) extends AnyVal {
    def asBuf: Buf = BufOps.stringToBuf(s)
  }

}
