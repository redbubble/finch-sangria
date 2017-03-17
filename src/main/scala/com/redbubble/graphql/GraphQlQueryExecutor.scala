package com.redbubble.graphql

import com.redbubble.util.async.syntax._
import com.redbubble.util.error.ErrorReporter
import com.redbubble.util.json.CodecOps._
import com.redbubble.util.json.JsonPrinter
import com.redbubble.util.metrics.StatsReceiver
import com.twitter.util.Future
import io.circe.Json
import mouse.option._
import sangria.execution._
import sangria.marshalling.ResultMarshaller
import sangria.marshalling.circe.CirceResultMarshaller.Node
import sangria.marshalling.circe.{CirceInputUnmarshaller, CirceResultMarshaller}
import sangria.schema.Schema

import scala.concurrent.ExecutionContext

trait GraphQlQueryExecutor {
  /**
    * Executes a GraphQL query, or mutation.
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
  def executor[C](schema: Schema[C, Unit], rootContext: C, maxQueryDepth: Int)
      (implicit er: ErrorReporter, statsReceiver: StatsReceiver): GraphQlQueryExecutor =
    new GraphQlQueryExecutor_(schema, rootContext, maxQueryDepth)(er, statsReceiver)
}

private final class GraphQlQueryExecutor_[C](schema: Schema[C, Unit], rootContext: C, maxQueryDepth: Int)
    (implicit er: ErrorReporter, statsReceiver: StatsReceiver) extends GraphQlQueryExecutor {
  private val resultMarshaller = CirceResultMarshaller
  private val inputMarshaller = CirceInputUnmarshaller
  private val executionScheme = ExecutionScheme.Extended
  private val ExecutionPrefix = "graphql_execution"
  private val stats = statsReceiver.scope(ExecutionPrefix)
  private val errorCounter = stats.counter("reported_errors")

  override def execute(q: GraphQlQuery)(implicit ec: ExecutionContext): Future[GraphQlResult] = {
    val executionResult = Executor.execute[C, Unit, Json](
      schema = schema,
      queryAst = q.document,
      userContext = rootContext,
      operationName = q.operationName,
      variables = q.variables.getOrElse(emptyJsonObject),
      exceptionHandler = PartialFunction((handleException(Some(q)) _).tupled),
      maxQueryDepth = Some(maxQueryDepth)
    )(ec, resultMarshaller, inputMarshaller, executionScheme)
    executionResult.asTwitter(ec).map { er =>
      if (er.errors.isEmpty) {
        SuccessfulGraphQlResult(er.result)
      } else {
        BackendErrorGraphQlResult(er.result, er.errors)
      }
    }.rescue(rescueCatastrophicError)
  }

  /**
    * We use this to process exceptions, these are errors that we want to handle, thus designating them as errors that
    * we'd expect a caller (i.e. a GraphQL client) to handle (i.e. they are not catastrophic).
    *
    * We get a `ResultMarshaller` for creating results, and the underlying error. We add in the type (class name) and
    * if it has one, the underlying cause.
    */
  private def handleException(query: Option[GraphQlQuery])(marshaller: ResultMarshaller, error: Throwable): HandledException = {
    errorCounter.incr()
    er.error(error, query.map(rollbarExtraData))
    val commonFields = Map("type" -> marshaller.scalarNode(error.getClass.getName, "String", Set.empty))
    val additionalFields = Option(error.getCause).cata(
      cause => commonFields ++ Map("cause" -> marshaller.scalarNode(errorMessage(cause), "String", Set.empty)),
      commonFields
    )
    HandledException(errorMessage(error), additionalFields)
  }

  /**
    * In the advent of a catastrophic error, do our best to figure out if that error is caused by a bad query, or a
    * problem in the server or downstream service.
    */
  private def rescueCatastrophicError: PartialFunction[Throwable, Future[GraphQlResult]] = {
    case e: QueryAnalysisError =>
      er.error(e)
      Future.value(ClientErrorGraphQlResult(e.resolveError))
    case e: ErrorWithResolver =>
      er.error(e)
      Future.value(BackendErrorGraphQlResult(e.resolveError))
    case e =>
      er.error(e)
      val errorNode = new ResultResolver(
        resultMarshaller, PartialFunction((handleException(None) _).tupled), preserveOriginalErrors = true).resolveError(e).asInstanceOf[Node]
      Future.value(BackendErrorGraphQlResult(errorNode))
  }

  private def rollbarExtraData(q: GraphQlQuery) =
    Map(
      s"$ExecutionPrefix.query.document" -> q.document.renderCompact,
      s"$ExecutionPrefix.query.variable" -> q.variables.map(j => JsonPrinter.jsonToString(j)).getOrElse("<Empty>"),
      s"$ExecutionPrefix.query.operation_name" -> q.operationName.getOrElse("<Empty>")
    )

  private def errorMessage(t: Throwable): String = s"${t.getClass.getName}: ${t.getMessage}"
}
