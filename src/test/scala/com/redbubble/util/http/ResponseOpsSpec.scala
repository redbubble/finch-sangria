package com.redbubble.util.http

import com.redbubble.util.spec.SpecHelper
import io.circe.syntax._
import io.circe.{Encoder, JsonObject}
import org.scalacheck.Prop.forAll
import org.scalacheck.{Gen, Properties}
import org.specs2.mutable.Specification

final class ResponseOpsSpec extends Specification with SpecHelper {

  case class Foo(bar: String)

  implicit val fooEncoder = Encoder.instance[Foo](s => Map("foo" -> Map("bar" -> s.bar)).asJson)
  implicit val dataEncode = com.redbubble.util.http.ResponseOps.dataJsonEncode[Foo]

  val objectResponseProp = new Properties("Object response encoding") {
    property("encode") = forAll(Gen.alphaStr) { (s: String) =>
      val f = Foo(s)
      val expected = parse(s"""{"data":${f.asJson.noSpaces}}""").getOrElse(JsonObject.empty.asJson)
      parse(ResponseOps.jsonBuf(f)) must beRight(expected)
    }
  }

  s2"Objects with an Encoder instance can be encoded into a response JSON$objectResponseProp"
}
