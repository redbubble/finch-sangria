package com.redbubble.graphql

import com.redbubble.graphql.GraphQlQueryDecoders._
import com.redbubble.util.spec.SpecHelper
import io.circe.Json
import org.scalacheck.Prop._
import org.scalacheck.{Gen, Properties}
import org.specs2.mutable.Specification

final class GraphQlQueryDecodersSpec extends Specification with SpecHelper with GraphQlSampleQueries {
  private val genValidVariableStrings = Gen.oneOf(variableStrings.map(buildJson))
  private val genValidVariableJsons = Gen.oneOf(variableJsons)
  private val genInvalidVariables = Gen.oneOf(invalidVariableStrings.map(buildJson))

  val graphQlVariableStringProps = new Properties("String decoding") {
    property("valid variable strings") = forAll(genValidVariableStrings) { (json: String) =>
      val decoded = decode(json)(variablesDecoder)
      decoded must beXorRight
    }
    property("invalid variable strings") = forAll(genInvalidVariables) { (json: String) =>
      val decoded = decode(json)(variablesDecoder)
      decoded must beXorLeft
    }
  }

  s2"GraphQL variables within an encoded JSON string can be decoded$graphQlVariableStringProps"

  val graphQlVariableProps = new Properties("JSON decoding") {
    property("valid variable JSON objects") = forAll(genValidVariableJsons) { (json: Json) =>
      val decoded = variablesDecoder.decodeJson(json)
      decoded must beXorRight
    }
  }

  s2"GraphQL variables (encoded as JSON) can be decoded$graphQlVariableProps"

  // Note. The JSON we generate here is explicitly wrapped in quotes, so that it mirrors what we'd get when parsing the
  // actual JSON sent through.
  private def buildJson(s: String): String =
  s""""${cleanJson(s).escapeQuotes}""""
}
