package com.redbubble.graphql

import com.redbubble.graphql.GraphQlRequestDecoders._
import com.redbubble.graphql.spec.{GraphQlGenerators, GraphQlSampleQueries, SpecHelper}
import com.redbubble.util.io.Charset.DefaultCharset
import com.twitter.io.Buf
import org.scalacheck.Prop._
import org.scalacheck.Properties
import org.specs2.mutable.Specification

final class GraphQlRequestDecodersSpec extends Specification with SpecHelper with GraphQlSampleQueries with GraphQlGenerators {
  val decodingProp = new Properties("Parsing JSON GraphQL queries") {
    property("valid queries") = forAll(genValidQueryString) { (q: Buf) =>
      val decodedQuery = graphQlQueryDecode.apply(q, DefaultCharset)
      decodedQuery.toOption must beSome
    }
    property("invalid queries") = forAll(genInvalidQueryStrings) { (q: Buf) =>
      val decodedQuery = graphQlQueryDecode.apply(q, DefaultCharset)
      decodedQuery.toOption must beNone
    }
  }

  s2"Valid GraphQL requests can be parsed$decodingProp"

  "Known GraphQL queries" >> {
    import com.redbubble.util.twitter._
    import io.circe.syntax._

    "just a query" >> {
      val parsed = graphQlQueryDecode.apply(queryJsonPayload("{ user { name } }"), DefaultCharset).toEither
      val result: org.specs2.execute.Result = parsed match {
        case Left(e) => fail("Expected query parsing to succeed", e)
        case Right(query) => {
          query.document must not(beNull)
          query.variables must beEqualTo(None)
          query.operationName must beNone
        }
      }
      result
    }

    "a query and variables (encoded as strings)" >> {
      val parsed = graphQlQueryDecode.apply(queryJsonPayloadStrings("{ user { name } }", """{"name":"fred"}"""), DefaultCharset).toEither
      val result: org.specs2.execute.Result = parsed match {
        case Left(e) => fail("Expected query parsing to succeed", e)
        case Right(query) => {
          query.document must not(beNull)
          query.variables must beEqualTo(Some(Map("name" -> "fred").asJson))
          query.operationName must beNone
        }
      }
      result
    }

    "a query and variables (encoded as an object)" >> {
      val parsed = graphQlQueryDecode.apply(queryJsonPayloadObject("{ user { name } }", """{"name":"fred"}"""), DefaultCharset).toEither
      val result: org.specs2.execute.Result = parsed match {
        case Left(e) => fail("Expected query parsing to succeed", e)
        case Right(query) => {
          query.document must not(beNull)
          query.variables must beEqualTo(Some(Map("name" -> "fred").asJson))
          query.operationName must beNone
        }
      }
      result
    }

    "a query, variables, and operation name" >> {
      val parsed = graphQlQueryDecode.apply(queryJsonPayload("{ user { name } }", """{"name":"fred"}""", "queryName"), DefaultCharset).toEither
      val result: org.specs2.execute.Result = parsed match {
        case Left(e) => fail("Expected query parsing to succeed", e)
        case Right(query) => {
          query.document must not(beNull)
          query.variables must beEqualTo(Some(Map("name" -> "fred").asJson))
          query.operationName must beEqualTo(Some("queryName"))
        }
      }
      result
    }
  }
}
