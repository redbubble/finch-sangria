package com.redbubble.util.json

import com.redbubble.util.io.BufOps._
import com.redbubble.util.spec.SpecHelper
import io.circe.Json
import io.circe.syntax._
import org.specs2.mutable.Specification

final class JsonPrinterSpec extends Specification with SpecHelper {
  "String encoding" >> {
    "Objects with non-null keys" >> {
      "have all fields serialised" >> {
        val obj = Map("non_null_value" -> "foo")
        val encoded = JsonPrinter.jsonToString(obj.asJson)
        CodecOps.parse(encoded) must beRight(Map("non_null_value" -> "foo").asJson)
      }
    }

    "Objects with null keys" >> {
      "do not have fields with null values serialised" >> {
        val obj = Json.obj(("non_null_value", Json.fromString("foo")), ("null_value", Json.Null))
        val encoded = JsonPrinter.jsonToString(obj)
        CodecOps.parse(encoded) must beRight(Map("non_null_value" -> "foo").asJson)
      }
    }

    "Objects with nested null keys" >> {
      "do not have fields with null values serialised" >> {
        val obj = Json.obj(("non_null_value", Json.fromString("foo")), ("non_null_value_2", Json.obj(("null_value", Json.Null))))
        val encoded = JsonPrinter.jsonToString(obj)
        val expected = Json.obj(("non_null_value", Json.fromString("foo")), ("non_null_value_2", CodecOps.emptyJsonObject))
        CodecOps.parse(encoded) must beRight(expected)
      }
    }
  }

  "Byte buffer encoding" >> {
    "Objects with non-null keys" >> {
      "have all fields serialised" >> {
        val obj = Map("non_null_value" -> "foo")
        val encoded = byteBufferToBuf(JsonPrinter.jsonToByteBuffer(obj.asJson))
        CodecOps.parse(encoded) must beRight(Map("non_null_value" -> "foo").asJson)
      }
    }

    "Objects with null keys" >> {
      "do not have fields with null values serialised" >> {
        val obj = Json.obj(("non_null_value", Json.fromString("foo")), ("null_value", Json.Null))
        val encoded = byteBufferToBuf(JsonPrinter.jsonToByteBuffer(obj))
        CodecOps.parse(encoded) must beRight(Map("non_null_value" -> "foo").asJson)
      }
    }

    "Objects with nested null keys" >> {
      "do not have fields with null values serialised" >> {
        val obj = Json.obj(("non_null_value", Json.fromString("foo")), ("non_null_value_2", Json.obj(("null_value", Json.Null))))
        val encoded = byteBufferToBuf(JsonPrinter.jsonToByteBuffer(obj))
        val expected = Json.obj(("non_null_value", Json.fromString("foo")), ("non_null_value_2", CodecOps.emptyJsonObject))
        CodecOps.parse(encoded) must beRight(expected)
      }
    }
  }
}
