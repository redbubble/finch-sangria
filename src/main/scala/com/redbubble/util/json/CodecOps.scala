package com.redbubble.util.json

import com.redbubble.util.io.BufOps
import com.twitter.io.Buf
import io.circe._

trait CodecOps {
  val emptyJsonObject: Json = Json.fromJsonObject(JsonObject.empty)

  /**
    * @note Do not use this for production code, prefer `parse(Buf)`.
    */
  final def parse(input: String): Either[ParsingFailure, Json] = io.circe.jawn.parse(input)

  final def parse(input: Buf): Either[ParsingFailure, Json] =
    io.circe.jawn.parseByteBuffer(BufOps.bufToByteBuffer(input))

  final def encode[A](a: A)(implicit encoder: Encoder[A]): Json = encoder.apply(a)

  /**
    * @note Do not use this for production code, prefer `parse(Buf)`.
    */
  final def decode[A](input: String)(implicit decoder: Decoder[A]): Either[Error, A] =
    parse(input).flatMap(decoder.decodeJson)

  final def decode[A](input: Buf)(implicit decoder: Decoder[A]): Either[Error, A] =
    parse(input).flatMap(decoder.decodeJson)
}

object CodecOps extends CodecOps
