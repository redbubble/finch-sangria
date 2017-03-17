package com.redbubble.graphql

import java.nio.charset.StandardCharsets.UTF_8

import com.redbubble.graphql.GraphQlSampleQueries._
import com.redbubble.util.io.BufOps
import com.redbubble.util.json.CodecOps
import com.twitter.io.Buf
import org.scalacheck.Gen
import sangria.parser.QueryParser

import scala.util.Success

trait GraphQlGenerators {
  private val genValidQueriesWithVariables = for {
    qs <- Gen.oneOf(validQueries)
    vs <- Gen.oneOf(variableStrings)
  } yield queryJsonPayloadStrings(qs, vs)

  private val genValidQueriesWithoutVariables = Gen.oneOf(validQueries).map(queryJsonPayload)

  val genInvalidQueryStrings: Gen[Buf] = Gen.oneOf(invalidQueries).map(BufOps.stringToBuf(_, UTF_8))

  val genValidQueryString: Gen[Buf] = Gen.oneOf(genValidQueriesWithoutVariables, genValidQueriesWithVariables)

  val genValidQueries: Gen[GraphQlQuery] = for {
    q <- Gen.oneOf(validQueries)
    vs <- Gen.oneOf(variableStrings)
  } yield {
    val Success(parsedQuery) = QueryParser.parse(q.trim)
    val Right(parsedVariables) = CodecOps.parse(vs)
    GraphQlQuery(parsedQuery, Some(parsedVariables))
  }
}
