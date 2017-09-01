package com.redbubble.util.async

import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit._

import com.twitter.util._

trait AsyncOps {
  final def runAsync[T](f: => T)(implicit fp: FuturePool): Future[T] = fp.apply(f)

  final def runAsyncUnit[T](f: => T)(implicit futurePool: FuturePool): Unit = {
    futurePool.apply(f)
    ()
  }

  final def block[T <: Awaitable[_]](awaitable: T): T = Await.ready(awaitable)

  final def blockUnit[T <: Awaitable[_]](awaitable: T): Unit = {
    block(awaitable)
    ()
  }

  final def shutdownExecutorService(executor: ExecutorService): Unit = {
    executor.shutdown()
    try {
      executor.awaitTermination(10L, SECONDS)
    } catch {
      case _: InterruptedException => {
        Console.err.println("Interrupted while waiting for graceful shutdown, forcibly shutting down...")
        executor.shutdownNow()
      }
    }
    ()
  }
}

object AsyncOps extends AsyncOps
