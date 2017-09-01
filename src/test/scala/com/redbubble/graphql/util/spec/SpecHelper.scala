package com.redbubble.graphql.util.spec

import com.redbubble.graphql.util.json.CodecOps
import org.slf4j.{Logger, LoggerFactory}
import org.specs2.ScalaCheck
import org.specs2.execute.{Failure, Result}
import org.specs2.mutable.Specification

trait SpecLogging {
  final lazy val log: Logger = LoggerFactory.getLogger("finch-sangria-test")
}

object SpecLogging extends SpecLogging

trait SpecHelper extends SpecLogging with ScalaCheck with CodecOps { self: Specification =>
  final def fail(message: String, t: Throwable): Result = Failure(message, "", t.getStackTrace.toList)
}
