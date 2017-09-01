package com.redbubble.util.io

import java.nio.charset.StandardCharsets._

import com.redbubble.util.spec.SpecHelper
import org.scalacheck.Prop._
import org.scalacheck.Properties
import org.specs2.mutable.Specification

final class BufOpsSpec extends Specification with SpecHelper {
  val conversionsProp = new Properties("Buf to String conversions") {
    property("roundtrip conversions") = forAll { (s: String) =>
      BufOps.bufToString(BufOps.stringToBuf(s)) must beEqualTo(s)
    }
    property("roundtrip conversions UTF-8") = forAll { (s: String) =>
      BufOps.bufToString(BufOps.stringToBuf(s, UTF_8), UTF_8) must beEqualTo(s)
    }
    property("roundtrip conversions UTF-16") = forAll { (s: String) =>
      BufOps.bufToString(BufOps.stringToBuf(s, UTF_16), UTF_16) must beEqualTo(s)
    }
  }

  s2"Buf to String conversions$conversionsProp"
}
