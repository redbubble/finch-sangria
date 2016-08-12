package com.redbubble.graphql

import cats.data.Xor
import cats.data.Xor._
import com.redbubble.util.json.JsonCodecOps._
import io.circe.Decoder.{decodeOption, _}
import io.circe._
import sangria.parser.QueryParser

trait GraphQlQueryDecoders {
  type ParseResult = Xor[DecodingFailure, Json]

  // GraphQL queries can contain line breaks, which are not valid JSON, so before we parse, we:
  // 1) Remove comments
  // 2) Replace newlines with spaces
  //
  // See: http://facebook.github.io/graphql/#sec-Line-Terminators
  final val cleanJson = (s: String) => s.split('\u000a').map(_.trim).filterNot(s => s.isEmpty || s.startsWith("#")).mkString(" ")

  final val queryDecoder: Decoder[GraphQlQuery] = Decoder.instance { c =>
    val query = for {
      query <- c.downField("query").as[String]
      variables <- c.downField("variables").as[Option[Json]](decodeOption(variablesDecoder))
      operation <- c.downField("operationName").as[Option[String]]
      parsedQuery <- fromTry(QueryParser.parse(query.trim)).leftMap(t => parseQueryError(t))
    } yield GraphQlQuery(parsedQuery, variables, operation)
    query.fold(e => Xor.left(decodeQueryError(e, c)), q => Xor.right(q))
  }

  final val variablesDecoder: Decoder[Json] = Decoder.instance { c =>
    c.focus.fold(
      decodeFail("Null is not supported"),
      jsonBoolean => decodeFail("Booleans are not supported"),
      jsonNumber => decodeFail("Numbers are not supported"),
      jsonString => parseVariables(jsonString).flatMap(handleParsedJsonString),
      jsonArray => decodeFail("Arrays are not supported"),
      jsonObject => Xor.right(Json.fromJsonObject(jsonObject))
    )
  }

  final def buildGraphQlQuery(query: String, variables: Option[String], operation: Option[String]): Xor[DecodingFailure, GraphQlQuery] = {
    val decodedVars = variables.map(decodeVariables).getOrElse(Xor.right(emptyJsonObject))
    for {
      vs <- decodedVars
      parsedQuery <- fromTry(QueryParser.parse(query.trim)).leftMap(e => parseQueryError(e))
    } yield GraphQlQuery(parsedQuery, Some(vs), operation)
  }

  private def handleParsedJsonString(json: Json): ParseResult = {
    json.fold(
      decodeFail("Null is not supported"),
      jsonBoolean => decodeFail("Booleans are not supported"),
      jsonNumber => decodeFail("Numbers are not supported"),
      jsonString => decodeFail("Strings are not supported"),
      jsonArray => decodeFail("Arrays are not supported"),
      jsonObject => Xor.right(Json.fromJsonObject(jsonObject))
    )
  }

  final def parseQueryError(t: Throwable): DecodingFailure = DecodingFailure(t.getMessage, Nil)

  final def decodeVariablesError(t: Throwable): DecodingFailure = DecodingFailure(s"Unable to decode GraphQL variables: ${t.getMessage}", Nil)

  final def parseVariables(variablesJson: String): ParseResult = parse(variablesJson).leftMap(e => decodeVariablesError(e))

  final def decodeVariables(variables: String): ParseResult =
    parseVariables(variables).flatMap(variablesDecoder.decodeJson).leftMap(e => decodeVariablesError(e))

  private def decodeQueryError(t: Throwable, cursor: HCursor): DecodingFailure = DecodingFailure(t.getMessage, cursor.history)

  private def decodeFail(`type`: String): ParseResult = Xor.left(DecodingFailure(s"Unable to decode GraphQL variables: ${`type`} is not supported", Nil))
}

object GraphQlQueryDecoders extends GraphQlQueryDecoders
