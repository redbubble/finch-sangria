package com.redbubble.graphql.util.log

import org.slf4j.{Logger, LoggerFactory}

trait GraphQlLogger {
  final lazy val log: Logger = LoggerFactory.getLogger("finch-sangria")
}

object GraphQlLogger extends GraphQlLogger
