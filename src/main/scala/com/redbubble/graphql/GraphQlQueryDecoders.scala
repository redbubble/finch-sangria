package com.redbubble.graphql

import cats.syntax.either._
import com.redbubble.util.io.BufOps._
import com.redbubble.util.json.CodecOps._
import com.twitter.io.Buf
import io.circe.Decoder.{decodeOption, _}
import io.circe._
import sangria.parser.QueryParser

trait GraphQlQueryDecoders {
  type JsonCleaner = (Buf) => Buf

  // GraphQL queries can contain line breaks, which are not valid JSON, so before we parse, we:
  // 1) Remove comments
  // 2) Replace newlines with spaces
  //
  // See: http://facebook.github.io/graphql/#sec-Line-Terminators
  final val cleanJson: JsonCleaner = { buf =>
    stringToBuf(bufToString(buf).split('\u000a').map(_.trim).filterNot(s => s.isEmpty || s.startsWith("#")).mkString(" "))
  }

  final val queryDecoder: Decoder[GraphQlQuery] = Decoder.instance { c =>
    val query = for {
      query <- c.downField("query").as[String]
      variables <- c.downField("variables").as[Option[Json]](decodeOption(variablesDecoder))
      operation <- c.downField("operationName").as[Option[String]]
      parsedQuery <- Either.fromTry(QueryParser.parse(query.trim)).leftMap(t => parseQueryError(t))
    } yield GraphQlQuery(parsedQuery, variables, operation)
    query.leftMap(e => decodeQueryError(e, c))
  }

  final val variablesDecoder: Decoder[Json] = Decoder.instance { c =>
    c.focus.map { json =>
      json.fold(
        decodeFailResult("Null"),
        _ => decodeFailResult("Boolean"),
        _ => decodeFailResult("Number"),
        jsonString => parseVariables(jsonString).flatMap(handleParsedJsonString),
        _ => decodeFailResult("Array"),
        jsonObject => Right(Json.fromJsonObject(jsonObject))
      )
    }.getOrElse(Left(DecodingFailure(s"Unable to get focus for cursor $c while decoding variables", c.history)))
  }

  final def buildGraphQlQuery(query: String, variables: Option[String], operation: Option[String]): Either[DecodingFailure, GraphQlQuery] = {
    val decodedVars = variables.map(decodeVariables).getOrElse(Right(emptyJsonObject))
    for {
      vs <- decodedVars
      parsedQuery <- Either.fromTry(QueryParser.parse(query.trim)).leftMap(e => parseQueryError(e))
    } yield GraphQlQuery(parsedQuery, Some(vs), operation)
  }

  private def handleParsedJsonString(json: Json): Decoder.Result[Json] = {
    json.fold(
      decodeFailResult("Null"),
      _ => decodeFailResult("Boolean"),
      _ => decodeFailResult("Number"),
      _ => decodeFailResult("String"),
      _ => decodeFailResult("Array"),
      jsonObject => Right(Json.fromJsonObject(jsonObject))
    )
  }

  final def parseQueryError(t: Throwable): DecodingFailure = DecodingFailure(t.getMessage, Nil)

  final def decodeVariablesError(t: Throwable): DecodingFailure =
    DecodingFailure(s"Unable to decode GraphQL variables: ${t.getMessage}", Nil)

  final def parseVariables(variablesJson: String): Decoder.Result[Json] =
    parse(stringToBuf(variablesJson)).leftMap(e => decodeVariablesError(e))

  final def decodeVariables(variables: String): Decoder.Result[Json] =
    parseVariables(variables).flatMap(variablesDecoder.decodeJson).leftMap(e => decodeVariablesError(e))

  private def decodeQueryError(t: Throwable, cursor: HCursor): DecodingFailure =
    DecodingFailure(t.getMessage, cursor.history)

  private def decodeFailResult(`type`: String): Result[Json] =
    Left(DecodingFailure(s"Unable to decode GraphQL variables: ${`type`} is not supported", Nil))
}

object GraphQlQueryDecoders extends GraphQlQueryDecoders
