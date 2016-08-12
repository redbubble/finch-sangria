package com.redbubble.graphql

import com.redbubble.graphql.GraphQlError._
import com.redbubble.util.json.JsonCodecOps
import com.redbubble.util.spec.SpecHelper
import io.circe.syntax._
import org.scalacheck.Prop._
import org.scalacheck.{Arbitrary, Gen, Properties}
import org.specs2.mutable.Specification

final class GraphQlErrorSpec extends Specification with SpecHelper with GraphQlJsonOps {
  implicit val arbString: Arbitrary[String] = Arbitrary(Gen.alphaStr)

  val decodeProp = new Properties("Sangria error decoding") {
    property("decode with field") = forAll { (message: String, field: String, line: Int, column: Int) =>
      val error = errorWithField(message, field, Seq(ErrorLocation(line, column)), "SingleGraphQlError")
      val decodedError = JsonCodecOps.decode(error)(graphQlErrorDecoder)
      decodedError.toOption must beSome(graphQlFieldError(message, field, Seq(ErrorLocation(line, column))))
    }

    property("decode with path") = forAll { (message: String, field: String, line: Int, column: Int) =>
      val error = errorWithPath("This is a message", Seq.empty, Seq(ErrorLocation(line, column)))
      val decodedError = JsonCodecOps.decode(error)(graphQlErrorDecoder)
      decodedError.toOption must beSome(graphQlPathError("This is a message", Seq(ErrorLocation(line, column))))
    }
  }

  s2"Sangria errors can be decoded into GraphQlErrors$decodeProp"

  implicit val decoder = graphQlErrorEncoder

  val encodeProp = new Properties("GraphQlError encoding") {
    property("encode") =
        forAll { (message: String, field: String, line: Int, column: Int) =>
          val locations = Seq(ErrorLocation(line, column))
          val expected = parse(errorWithField(message, field, locations, "SingleGraphQlError"))
          expected must beXorRight(graphQlFieldError(message, field, locations).asJson)
        }
  }

  s2"A GraphQlError can be encoded into JSON$encodeProp"
}
