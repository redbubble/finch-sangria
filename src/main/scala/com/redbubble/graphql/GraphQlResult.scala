package com.redbubble.graphql

import io.circe.Json
import sangria.execution.RegisteredError

sealed trait GraphQlResult {
  def underlying: Json
  def context: Option[Any]
}

/**
  * The result of a successful GraphQL query execution.
  */
final case class SuccessfulGraphQlResult(underlying: Json, context: Option[Any] = None) extends GraphQlResult

/**
  * The result of a failed GraphQL query execution, where the failure was prior to the execution (i.e. bad query).
  */
final case class ClientErrorGraphQlResult(underlying: Json, errors: Seq[RegisteredError] = Seq.empty, context: Option[Any] = None)
    extends GraphQlResult

/**
  * The result of a failed GraphQL query execution, where the failure was during the execution (e.g. internal,
  * downstream service, etc.).
  */
final case class BackendErrorGraphQlResult(underlying: Json, errors: Seq[RegisteredError] = Seq.empty, context: Option[Any] = None)
    extends GraphQlResult
