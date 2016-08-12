package com.redbubble.graphql

import cats.data.Xor
import com.redbubble.graphql.GraphQlError._
import com.redbubble.graphql.GraphQlQueryExecutor.ExecutorResult
import com.redbubble.graphql.SangriaExecutionResult.{SangriaExecutionResult, _}
import com.redbubble.util.async.syntax._
import com.redbubble.util.json.JsonCodecOps._
import com.twitter.util.{Future, Return, Throw}
import io.circe.Json
import sangria.execution.{ExecutionError, Executor, HandledException}
import sangria.marshalling.circe.{CirceInputUnmarshaller, CirceResultMarshaller}
import sangria.schema.Schema

import scala.concurrent.ExecutionContext

trait GraphQlQueryExecutor {
  def execute(q: GraphQlQuery)(implicit ec: ExecutionContext): Future[ExecutorResult]
}

object GraphQlQueryExecutor {
  type ExecutorResult = AggregateGraphQlError Xor GraphQlResult

  def executor[C](schema: Schema[C, Unit], rootContext: C, maxQueryDepth: Int): GraphQlQueryExecutor =
    new GraphQlQueryExecutor_(schema, rootContext, maxQueryDepth)
}

private final class GraphQlQueryExecutor_[C](
    schema: Schema[C, Unit], rootContext: C, maxQueryDepth: Int) extends GraphQlQueryExecutor {
  private val resultMarshaller = CirceResultMarshaller
  private val inputMarshaller = CirceInputUnmarshaller

  private val errorHandler: Executor.ExceptionHandler = {
    case (m, t: Throwable) if t.getCause == null =>
      HandledException(t.getMessage)
    case (m, t: Throwable) if t.getCause != null =>
      HandledException(t.getMessage, Map("cause" -> m.scalarNode(t.getCause.getMessage, "String", Set.empty)))
  }

  // TODO We should differentiate here between a client error & a server error. A client error we should return as a 400,
  // a server error we should return as some form of 500 (e.g. a DownStreamError should be propagated as a proxy error).
  // Proposal:
  // 1) Have a ClientGraphQlError that is for client events.
  // 2) Have an InternalGraphQlError that is for server events. This likely won't be graphql specific.
  // 3) Maybe have them both extend GraphQlExecutionError
  override def execute(q: GraphQlQuery)(implicit ec: ExecutionContext): Future[ExecutorResult] = {
    val executionResult = Executor.execute[C, Unit, Json](
      schema = schema,
      queryAst = q.document,
      userContext = rootContext,
      operationName = q.operationName,
      variables = q.variables.getOrElse(emptyJsonObject),
      exceptionHandler = errorHandler,
      maxQueryDepth = Some(maxQueryDepth)
    )(ec, resultMarshaller, inputMarshaller)
    executionResult.asTwitter(ec).transform {
      case Return(json) =>
        val decodedResult = json.as[SangriaExecutionResult](sangriaResultDecoder).leftMap(df => aggregate(graphQlError(df)))
        Future.value(decodedResult.flatMap(_.map(GraphQlResult)))
      case Throw(e: ExecutionError) => Future.value(Xor.left(aggregate(e)))
      case Throw(e: Throwable) => Future.exception(e)
    }
  }
}
