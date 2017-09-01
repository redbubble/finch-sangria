package com.redbubble.graphql

import com.redbubble.graphql.GraphQlQueryExecutor.ExecutionPrefix
import com.redbubble.graphql.SimpleQueryRenderer._
import com.redbubble.util.error.ErrorReporter
import com.twitter.finagle.stats.Counter
import com.twitter.util.Future
import mouse.option._
import sangria.execution.{ErrorWithResolver, HandledException, QueryAnalysisError, ResultResolver, ExceptionHandler => SangriaExceptionHandler}
import sangria.marshalling.ResultMarshaller
import sangria.marshalling.circe.CirceResultMarshaller.Node

object GraphQlExceptionHandler {

  def exceptionHandler(query: Option[GraphQlQuery])(implicit errorCounter: Counter, er: ErrorReporter): SangriaExceptionHandler =
    SangriaExceptionHandler(
      onException = PartialFunction((handleException(query) _).tupled)
    )

  /**
    * In the advent of a catastrophic error, do our best to figure out if that error is caused by a bad query, or a
    * problem in the server or downstream service.
    */
  def rescueCatastrophicError(
      implicit marshaller: ResultMarshaller, errorCounter: Counter, er: ErrorReporter
  ): PartialFunction[Throwable, Future[GraphQlResult]] = {
    case e: QueryAnalysisError =>
      errorCounter.incr()
      er.error(e)
      Future.value(ClientErrorGraphQlResult(e.resolveError(marshaller).asInstanceOf[Node]))
    case e: ErrorWithResolver =>
      errorCounter.incr()
      er.error(e)
      Future.value(BackendErrorGraphQlResult(e.resolveError(marshaller).asInstanceOf[Node]))
    case e =>
      errorCounter.incr()
      er.error(e)
      val resolver = new ResultResolver(marshaller, exceptionHandler(None)(errorCounter, er), preserveOriginalErrors = true)
      val errorNode = resolver.resolveError(e).asInstanceOf[Node]
      Future.value(BackendErrorGraphQlResult(errorNode))
  }

  /**
    * We use this to process exceptions, these are errors that we want to handle, thus designating them as errors that
    * we'd expect a caller (i.e. a GraphQL client) to handle (i.e. they are not catastrophic).
    *
    * We get a `ResultMarshaller` for creating results, and the underlying error. We add in the type (class name) and
    * if it has one, the underlying cause.
    */
  private def handleException(query: Option[GraphQlQuery])(marshaller: ResultMarshaller, error: Throwable)(implicit errorCounter: Counter, er: ErrorReporter): HandledException = {
    errorCounter.incr()
    er.error(error, query.map(rollbarExtraData))
    val commonFields = Map("type" -> marshaller.scalarNode(error.getClass.getName, "String", Set.empty))
    val additionalFields = Option(error.getCause).cata(
      cause => commonFields ++ Map("cause" -> marshaller.scalarNode(errorMessage(cause), "String", Set.empty)),
      commonFields
    )
    HandledException(errorMessage(error), additionalFields)
  }

  private def rollbarExtraData(q: GraphQlQuery) =
    Map(
      s"$ExecutionPrefix.query.document" -> graphqlQuery(q),
      s"$ExecutionPrefix.query.variable" -> graphqlVariables(q),
      s"$ExecutionPrefix.query.operation_name" -> graphqlOperation(q)
    )

  private def errorMessage(t: Throwable): String = Option(t.getMessage).getOrElse(t.getClass.getName)
}
