package com.redbubble.graphql

import cats.data.Xor
import com.redbubble.graphql.GraphQlError.{aggregate, _}
import com.redbubble.util.json.JsonCodecOps._
import io.circe.{Decoder, HCursor, Json}

object SangriaExecutionResult {
  type SangriaExecutionResult = AggregateGraphQlError Xor Json

  implicit val errorDecoder = graphQlErrorDecoder

  // Note. An "error" coming back from Sangria has both "data" and the "errors" fields, so if "errors" is present we want
  // to present this as an error (`AggregatedGraphQlError`). If no "errors" field is present then we return the "data"
  // field untouched in its raw JSON form.
  val sangriaResultDecoder: Decoder[SangriaExecutionResult] = Decoder.instance { c =>
    val errorsOrData = c.focus.asObject.flatMap(_.apply("errors")).map { errorsFieldJson =>
      val parsed = errorsFieldJson.as[Seq[SingleGraphQlError]]
      parsed.fold(df => Xor.left(aggregate(graphQlError(df))), es => Xor.left(aggregate(es)))
    }.getOrElse(Xor.right(dataJson(c)))
    Xor.right(errorsOrData)
  }

  private def dataJson(c: HCursor): Json = c.downField("data").as[Json].getOrElse(emptyJsonObject)
}
