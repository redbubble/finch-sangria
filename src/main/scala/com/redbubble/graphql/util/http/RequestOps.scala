package com.redbubble.graphql.util.http

import com.redbubble.graphql.util.json.CodecOps
import com.twitter.io.Buf
import com.twitter.util.{Return, Throw, Try}
import io.circe.Decoder
import io.finch.Decode

trait RequestOps {
  type JsonCleaner = (Buf) => Buf

  /**
    * Decodes a payload, where the data to be decoded is an object at the root level of the request body. For
    * example: `{ ... }`.
    */
  final def decodeRootJson[A](d: Decoder[A], c: JsonCleaner = identity): Decode.Json[A] =
    Decode.json((payload, _) => decodePayload(c(payload), rootObjectDecoder(d)))

  private def decodePayload[A](payload: Buf, decoder: Decoder[A]): Try[A] = {
    val decodedPayload = CodecOps.decode(payload)(decoder)
    decodedPayload.fold(
      error => Throw(new Exception(s"Unable to decode JSON payload: ${error.getMessage}", error)),
      value => Return(value)
    )
  }

  private def rootObjectDecoder[A](implicit d: Decoder[A]): Decoder[A] = Decoder.instance(c => c.as[A](d))
}

object RequestOps extends RequestOps
