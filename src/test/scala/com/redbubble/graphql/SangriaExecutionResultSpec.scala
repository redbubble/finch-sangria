package com.redbubble.graphql

import cats.data.Xor
import com.redbubble.graphql.GraphQlError.graphQlFieldError
import com.redbubble.graphql.SangriaExecutionResult.sangriaResultDecoder
import com.redbubble.util.spec.SpecHelper
import io.circe.Json
import io.circe.syntax._
import org.scalacheck.Prop._
import org.scalacheck.{Arbitrary, Gen, Properties}
import org.specs2.mutable.Specification

final class SangriaExecutionResultSpec extends Specification with SpecHelper {
  implicit val arbString: Arbitrary[String] = Arbitrary(Gen.alphaStr)

  val objectRequestProp = new Properties("Sangria result decoding") {
    property("failed sangria execution result with a field failure") =
        forAll { (message: String, field: String, line: Int, column: Int) =>
          val error = errorWithField(message, field, ErrorLocation(line, column))
          val decodedError = decode(error)(sangriaResultDecoder)
          val expected = Xor.left(AggregateGraphQlError(Seq(graphQlFieldError(message, field, Seq(ErrorLocation(line, column))))))
          decodedError.toOption must beSome(expected)
        }

    property("successful sangria execution result") =
        forAll { (message: String, field: String, line: Int, column: Int) =>
          val success = successJson(Map("foo" -> "bar"))
          val decodedSuccess = decode(success)(sangriaResultDecoder)
          decodedSuccess.toOption must beSome(Xor.right(Json.obj("foo" -> "bar".asJson)))
        }
  }

  s2"Sangria errors can be decoded into GraphQlErrors$objectRequestProp"

  private def successJson[T: io.circe.Encoder](t: T) = s"""{ "data" : ${t.asJson} }"""

  private def errorWithField(message: String, field: String, errorLocation: ErrorLocation) =
    s"""
       |{
       |  "data" : null,
       |  "errors" : [${GraphQlJsonOps.errorWithField(message, field, Seq(errorLocation), "N/A")}]
       |}
      """.stripMargin
}
