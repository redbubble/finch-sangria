package com.redbubble.util.log

import com.redbubble.util.async.AsyncOps.runAsyncUnit
import com.twitter.util.FuturePool
import org.slf4j.LoggerFactory

final class Logger(name: String)(implicit futurePool: FuturePool) {
  private lazy val log = LoggerFactory.getLogger(name)

  def trace(s: => String): Unit =
    if (log.isTraceEnabled) {
      runAsyncUnit(log.trace(s))
    }

  def debug(s: => String): Unit =
    if (log.isDebugEnabled) {
      runAsyncUnit(log.debug(s))
    }

  def info(s: => String): Unit =
    if (log.isInfoEnabled) {
      runAsyncUnit(log.info(s))
    }

  def warn(s: => String): Unit =
    if (log.isWarnEnabled) {
      runAsyncUnit(log.warn(s))
    }

  def warn(s: => String, t: Throwable): Unit =
    if (log.isWarnEnabled) {
      runAsyncUnit(log.warn(s, t))
    }

  def error(s: => String): Unit =
    if (log.isErrorEnabled) {
      runAsyncUnit(log.error(s))
    }

  def error(s: => String, t: Throwable): Unit =
    if (log.isErrorEnabled) {
      runAsyncUnit(log.error(s, t))
    }
}
