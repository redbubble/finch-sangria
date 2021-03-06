package com.redbubble.graphql.spec

import com.redbubble.util.async.singleThreadedFuturePool
import com.redbubble.util.json.CodecOps
import com.redbubble.util.log.Logger
import org.specs2.ScalaCheck
import org.specs2.execute.{Failure, Result}
import org.specs2.mutable.Specification

trait SpecLogging {
  final val log = new Logger("finch-sangria-test")(singleThreadedFuturePool)
}

object SpecLogging extends SpecLogging

trait SpecHelper extends SpecLogging with ScalaCheck with GenHelpers with CodecOps {
  self: Specification =>
  final def fail(message: String, t: Throwable): Result = Failure(message, "", t.getStackTrace.toList)
}
