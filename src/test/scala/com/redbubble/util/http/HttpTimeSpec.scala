package com.redbubble.util.http

import com.redbubble.util.http.HttpTime.currentTime
import com.redbubble.util.spec.SpecHelper
import org.specs2.mutable.Specification

final class HttpTimeSpec extends Specification with SpecHelper {
  "RFC 1123 date/time" >> {
    "Can be generated twice in succession returning the same second precision" >> {
      val time1 = currentTime()
      val time2 = currentTime()
      time1 must beEqualTo(time2)
    }

    "Calling across multiple seconds gives different results" >> {
      val time1 = currentTime()
      Thread.sleep(1000L)
      val time2 = currentTime()
      time1 must not(beEqualTo(time2))
    }
  }
}
