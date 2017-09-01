package com.redbubble.util.async

import com.twitter.util.{Future => TwitterFuture}

import scala.concurrent.{ExecutionContext, Future => ScalaFuture}

package object syntax {

  implicit final class TwitterToScalaFuture[A](val f: TwitterFuture[A]) extends AnyVal {
    def asScala: ScalaFuture[A] = FutureOps.twitterToScalaFuture(f)
  }

  implicit final class ScalaToTwitterFuture[A](val f: ScalaFuture[A]) extends AnyVal {
    def asTwitter(implicit ec: ExecutionContext): TwitterFuture[A] = FutureOps.scalaToTwitterFuture(f)(ec)
  }

}
