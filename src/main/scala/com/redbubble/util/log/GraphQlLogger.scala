package com.redbubble.util.log

import com.redbubble.util.async.singleThreadedFuturePool

trait GraphQlLogger {
  final lazy val log: Logger = new Logger("finch-sangria")(singleThreadedFuturePool)
}

object GraphQlLogger extends GraphQlLogger
