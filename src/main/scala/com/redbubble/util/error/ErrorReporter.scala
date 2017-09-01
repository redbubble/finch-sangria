package com.redbubble.util.error

import com.redbubble.util.log.GraphQlLogger

sealed trait ErrorLevel

case object Debug extends ErrorLevel

case object Info extends ErrorLevel

case object Warning extends ErrorLevel

case object Error extends ErrorLevel

case object Critical extends ErrorLevel

trait ErrorReporter extends GraphQlLogger {
  type ExtraData = Map[String, AnyRef]

  /**
    * Note. This should be called on the main thread to have any real effect.
    */
  def registerForUnhandledExceptions(): Unit

  final def debug(t: Throwable, extraData: Option[ExtraData] = None): Unit = {
    log.debug(s"Error: ${t.getMessage}")
    report(Debug, t, extraData)
  }

  final def info(t: Throwable, extraData: Option[ExtraData] = None): Unit = {
    log.info(s"Error: ${t.getMessage}")
    report(Info, t, extraData)
  }

  final def warning(t: Throwable, extraData: Option[ExtraData] = None): Unit = {
    log.warn(s"Error: ${t.getMessage}", t)
    report(Warning, t, extraData)
  }

  final def error(t: Throwable, extraData: Option[ExtraData] = None): Unit = {
    log.error(s"Error: ${t.getMessage}", t)
    report(Error, t, extraData)
  }

  final def critical(t: Throwable, extraData: Option[ExtraData] = None): Unit = {
    log.error(s"Error: ${t.getMessage}", t)
    report(Critical, t, extraData)
  }

  def report(level: ErrorLevel, t: Throwable, extraData: Option[ExtraData]): Unit
}
