package com.redbubble.graphql

import com.redbubble.graphql.GraphQlQueryDecoders._
import com.redbubble.util.http.RequestOps
import io.finch.Decode

trait GraphQlRequestDecoders {
  implicit val graphQlQueryDecode: Decode.Json[GraphQlQuery] =
    RequestOps.decodeRootJson[GraphQlQuery](queryDecoder, cleanJson)
}

object GraphQlRequestDecoders extends GraphQlRequestDecoders
