package com.redbubble.graphql

import com.redbubble.util.json.ExceptionEncoder
import io.circe._
import io.circe.syntax._

// sangria.execution.ExecutionPath
final case class ErrorPath(paths: Seq[String], index: Option[Int])

final case class ErrorLocation(line: Int, column: Int)

final case class SingleGraphQlError(message: String, field: Option[String], path: Option[ErrorPath],
    errorLocations: Seq[ErrorLocation], cause: Option[Throwable]) extends Exception {

  override def getMessage = message

  override def getCause = cause.orNull
}

final case class AggregateGraphQlError(errors: Seq[Throwable]) extends Exception {
  override def getMessage = errors.map(_.getMessage).mkString("; ")

  // If there is one error use it's cause as the cause (not ideal...).
  override def getCause = errors.headOption.flatMap(t => Option(t.getCause)).orNull
}

object GraphQlError {
  implicit private val locationDecoder: Decoder[ErrorLocation] = Decoder.instance { c =>
    for {
      line <- c.downField("line").as[Int]
      column <- c.downField("column").as[Int]
    } yield ErrorLocation(line, column)
  }

  val graphQlErrorDecoder: Decoder[SingleGraphQlError] = Decoder.instance { c =>
    for {
      message <- c.downField("message").as[String]
      field <- c.downField("field").as[Option[String]]
      //path <- c.downField("path").as[Option[ErrorPath]]
      locations <- c.downField("locations").as[Seq[ErrorLocation]]
    } yield SingleGraphQlError(message, field, None, locations, None)
  }

  val graphQlErrorEncoder: Encoder[SingleGraphQlError] = Encoder.instance[SingleGraphQlError] { ge =>
    val commonErrorJson = ExceptionEncoder.exceptionJson(ge)
    val withCustomFields = commonErrorJson.asObject.flatMap { jo =>
      val withLocns = jo.add("locations", ge.errorLocations.map(l => Map("line" -> l.line, "column" -> l.column)).asJson)
      ge.field.map(f => withLocns.add("field", f.asJson))
    }
    Json.fromJsonObject(withCustomFields.getOrElse(JsonObject.empty))
  }

  def graphQlError(message: String): SingleGraphQlError = SingleGraphQlError(message, None, None, Seq.empty, None)

  def graphQlFieldError(message: String, field: String, errorLocations: Seq[ErrorLocation]): SingleGraphQlError =
    SingleGraphQlError(message, Some(field), None, errorLocations, None)

  // Note. We don't currently care about the path, as it comes out in the exception message.
  def graphQlPathError(message: String, errorLocations: Seq[ErrorLocation]): SingleGraphQlError =
  SingleGraphQlError(message, None, None, errorLocations, None)

  def graphQlError(message: String, cause: Throwable): SingleGraphQlError =
    SingleGraphQlError(message, None, None, Seq.empty, Some(cause))

  def graphQlError(f: DecodingFailure): SingleGraphQlError =
    graphQlError("Internal error: unable to decode error payload", f)

  def aggregate(t: Throwable): AggregateGraphQlError = aggregate(Seq(t))

  def aggregate(es: Seq[Throwable]): AggregateGraphQlError = AggregateGraphQlError(es)
}
