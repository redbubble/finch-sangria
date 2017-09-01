package com.redbubble.util

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors.newSingleThreadExecutor

import com.redbubble.util.async.AsyncOps.shutdownExecutorService
import com.twitter.util.FuturePool

package object async {
  lazy val singleThreadedExecutor: ExecutorService = newSingleThreadExecutor
  lazy val singleThreadedFuturePool: FuturePool = FuturePool.interruptible(singleThreadedExecutor)

  sys.addShutdownHook(shutdownExecutorService(singleThreadedExecutor))
}
