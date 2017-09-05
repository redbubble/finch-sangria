package com.redbubble.graphql.spec

import io.circe.Json
import io.circe.syntax._
import org.scalacheck.{Arbitrary, Gen}

trait JsonGenerators {
  implicit def arbJson: Arbitrary[Json] = Arbitrary(genJson)

  val genJsonTuple: Gen[(String, Json)] = for {
    key <- Arbitrary.arbitrary[String]
    value <- Gen.choose(0, key.length)
  } yield (key, value.asJson)
  val genJsonNull: Gen[Json] = Gen.const(Json.Null)
  val genJsonBoolean: Gen[Json] = Gen.oneOf(List(Json.True, Json.False))
  val genJsonNumber: Gen[Json] = Arbitrary.arbInt.arbitrary.map(_.asJson)
  val genJsonString: Gen[Json] = Arbitrary.arbString.arbitrary.map(_.asJson)
  val genSimpleJsonValue: Gen[Json] = genJsonBoolean.flatMap(b => genJsonNumber.flatMap(n => genJsonString.flatMap(s => Gen.oneOf(b, n, s))))
  val genJsonArray: Gen[Json] = Gen.containerOf[List, Json](genSimpleJsonValue).map(l => Json.fromValues(l))
  val genJsonObject: Gen[Json] = Gen.mapOf(genJsonTuple).map(_.asJson)
  val genJson: Gen[Json] = Gen.oneOf(genJsonNull, genJsonBoolean, genJsonNumber, genJsonString, genJsonObject, genJsonArray)
}
