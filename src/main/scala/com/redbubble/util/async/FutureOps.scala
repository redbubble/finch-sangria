package com.redbubble.util.async

import com.twitter.util.{Return, Throw, Future => TwitterFuture, Promise => TwitterPromise}

import scala.concurrent.{ExecutionContext, Future => ScalaFuture, Promise => ScalaPromise}
import scala.language.reflectiveCalls
import scala.util.{Failure, Success}

trait FutureOps {
  type FoldableWithError[T <: Throwable, A] = {def fold[B](fa: T => B, fb: A => B): B}

  /**
    * Flattens a `Future[M[Throwable, A]]` to a `Future[A]`, where Throwable becomes a failing Future. You should only use
    * this for code where you need to integrate with a library that expects a failing Future. In regular code, where you
    * might reasonably expecte a caller to handle the "failure", prefer a `Future` where the success case contains an
    * `Either` (or equivalent), and keep the failing case for errors that you wouldn't expect or want a caller to handle.
    */
  final def flattenFuture[T <: Throwable, A](f: TwitterFuture[FoldableWithError[T, A]]): TwitterFuture[A] =
    f.flatMap { foldable =>
      foldable.fold(e => TwitterFuture.exception(e), a => TwitterFuture.value(a))
    }

  final def flattenFuture[T <: Throwable, A](f: ScalaFuture[FoldableWithError[T, A]])(implicit ec: ExecutionContext): ScalaFuture[A] =
    f.flatMap { foldable =>
      foldable.fold(e => ScalaFuture.failed(e), a => ScalaFuture.successful(a))
    }

  final def scalaToTwitterFuture[A](f: ScalaFuture[A])(implicit ec: ExecutionContext): TwitterFuture[A] = {
    val p = new TwitterPromise[A]()
    f.onComplete {
      case Success(value) => p.setValue(value)
      case Failure(exception) => p.setException(exception)
    }
    p
  }

  final def twitterToScalaFuture[A](f: TwitterFuture[A]): ScalaFuture[A] = {
    val p = ScalaPromise[A]()
    f.respond {
      case Return(value) => {
        p.success(value)
        ()
      }
      case Throw(exception) => {
        p.failure(exception)
        ()
      }
    }
    p.future
  }
}

object FutureOps extends FutureOps
