package com.redbubble.graphql

import com.redbubble.graphql.GraphQlQueryExecutor._
import com.redbubble.graphql.SimpleQueryRenderer._
import com.redbubble.util.async.syntax._
import com.redbubble.util.error.ErrorReporter
import com.redbubble.util.json.CodecOps._
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Future
import io.circe.Json
import org.slf4j.Logger
import sangria.execution._
import sangria.marshalling.circe.{CirceInputUnmarshaller, CirceResultMarshaller}
import sangria.schema.Schema

import scala.concurrent.ExecutionContext

trait GraphQlQueryExecutor {
  /**
    * Executes a GraphQL query, or mutation.
    *
    * The resulting `Future` will contain either:
    *
    * - `Return` - Indicates a (best guess) successful execution of a query. Will contain a subtype of `GraphQlResult`,
    * representing whether this was a) `SuccessfulGraphQlResult` a successful result, b) `ClientErrorGraphQlResult` a
    * client error (e.g. bad query) or b) `ServerErrorGraphQlResult` - an internal error (e.g. malformed GraphQL schema).
    * - `Throw` - Indicates a catastrphic failure, that a caller should not be expected to handle.
    *
    * Note. There is still a possibility in the success case that the query could "fail", for example if a downstream
    * service returns an error, and that error is handled by the `exceptionHandler` (see below), the query will be
    * considered a success. The `errors` key within the returns `Json` instance will be non-empty however.
    *
    * More information is in this thread: https://gitter.im/sangria-graphql/sangria?at=57e1e94933c63ba01a1c91e5
    **/
  def execute(q: GraphQlQuery)(implicit ec: ExecutionContext): Future[GraphQlResult]
}

object GraphQlQueryExecutor {
  val ExecutionPrefix = "graphql_execution"

  def executor[C](schema: Schema[C, Unit], rootContext: C, maxQueryDepth: Int)
      (implicit er: ErrorReporter, statsReceiver: StatsReceiver, logger: Logger): GraphQlQueryExecutor =
    new GraphQlQueryExecutor_(schema, rootContext, maxQueryDepth)(er, statsReceiver, logger)
}

private final class GraphQlQueryExecutor_[C](schema: Schema[C, Unit], rootContext: C, maxQueryDepth: Int)
    (implicit er: ErrorReporter, statsReceiver: StatsReceiver, logger: Logger) extends GraphQlQueryExecutor {
  private val resultMarshaller = CirceResultMarshaller
  private val inputMarshaller = CirceInputUnmarshaller
  private val executionScheme = ExecutionScheme.Extended
  private val stats = statsReceiver.scope(ExecutionPrefix)
  private val handledErrorsCounter = stats.counter("errors", "handled")
  private val catastrophicErrorsCounter = stats.counter("errors", "catastrophic")

  override def execute(q: GraphQlQuery)(implicit ec: ExecutionContext): Future[GraphQlResult] = {
    logger.trace(s"GRAPHQL operation ${graphqlOperation(q)}, query ${graphqlQuery(q)} with variables ${graphqlVariables(q)}")
    val result = runQuery(q)(ec)
    handleErrors(result.asTwitter(ec))
  }

  private def runQuery(q: GraphQlQuery)(implicit ec: ExecutionContext) = {
    Executor.execute[C, Unit, Json](
      schema = schema,
      queryAst = q.document,
      userContext = rootContext,
      operationName = q.operationName,
      variables = q.variables.getOrElse(emptyJsonObject),
      exceptionHandler = GraphQlExceptionHandler.exceptionHandler(Some(q))(handledErrorsCounter, er),
      maxQueryDepth = Some(maxQueryDepth)
    )(
      executionContext = ec,
      marshaller = resultMarshaller,
      um = inputMarshaller,
      scheme = executionScheme
    )
  }

  private def handleErrors(result: Future[ExecutionResult[C, Json]]) =
    result.map { er =>
      if (er.errors.isEmpty) {
        SuccessfulGraphQlResult(er.result)
      } else {
        BackendErrorGraphQlResult(er.result, er.errors)
      }
    }.rescue(GraphQlExceptionHandler.rescueCatastrophicError(resultMarshaller, catastrophicErrorsCounter, er))
}
