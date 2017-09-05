package com.redbubble.graphql

import com.redbubble.util.http.ResponseOps.rootJsonEncode
import io.circe.{Encoder, Json}
import io.finch.Encode

trait GraphQlEncoders {
  val graphQlResultEncoder: Encoder[Json] = Encoder.instance[Json](r => r)

  implicit val graphQlResultEncode: Encode.Json[Json] = rootJsonEncode[Json](graphQlResultEncoder)
}

object GraphQlEncoders extends GraphQlEncoders
