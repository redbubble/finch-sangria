package com.redbubble.graphql

import cats.data.Xor
import com.redbubble.graphql.GraphQlSampleQueries._
import com.redbubble.util.json.JsonCodecOps
import org.scalacheck.Gen
import sangria.parser.QueryParser

import scala.util.Success

trait GraphQlGenerators {
  private val genValidQueriesWithVariables = for {
    qs <- Gen.oneOf(validQueries)
    vs <- Gen.oneOf(variableStrings)
  } yield queryJsonPayloadStrings(qs, vs)

  private val genValidQueriesWithoutVariables = Gen.oneOf(validQueries).map(queryJsonPayload)

  val genInvalidQueryStrings: Gen[String] = Gen.oneOf(invalidQueries)

  val genValidQueryString: Gen[String] = Gen.oneOf(genValidQueriesWithoutVariables, genValidQueriesWithVariables)

  val genValidQueries: Gen[GraphQlQuery] = for {
    q <- Gen.oneOf(validQueries)
    vs <- Gen.oneOf(variableStrings)
  } yield {
    val Success(parsedQuery) = QueryParser.parse(q.trim)
    val Xor.Right(parsedVariables) = JsonCodecOps.parse(vs)
    GraphQlQuery(parsedQuery, Some(parsedVariables))
  }
}
