package com.redbubble.graphql

import io.circe._

final case class GraphQlError(message: String, cause: Option[Throwable]) extends Exception {
  override def getMessage = message

  override def getCause = cause.orNull
}

object GraphQlError {
  def graphQlError(message: String): GraphQlError = GraphQlError(message, None)

  def graphQlError(message: String, cause: Throwable): GraphQlError = GraphQlError(message, Some(cause))

  def graphQlError(f: DecodingFailure): GraphQlError = graphQlError("Internal error: unable to decode error payload", f)
}
