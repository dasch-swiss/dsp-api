/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

import com.typesafe.scalalogging.Logger
import kamon.Kamon
import kamon.instrumentation.futures.scala.ScalaFutureInstrumentation.{traced, tracedCallback}

import scala.concurrent.{ExecutionContext, Future}

/**
  * A set of methods used for measuring stuff that is happening.
  */
trait InstrumentationSupport {

    /**
      * Measures the time the future needs to complete.
      *
      * Example:
      *
      * val f = Timing.timed {
      *     Future {
      *         work inside the future
      *     }
      * }
      *
      * @param name the name identifying the span.
      * @param future the future we want to instrument.
      * @param logger the logger use to output the message.
      */
    def tracedFuture[A](name: String)(future: => Future[A])(implicit logger: Logger, ec: ExecutionContext): Future[A] = {
        val start = System.currentTimeMillis()
        traced(name)(future).andThen(case completed => logger.info(s"$name: " + (System.currentTimeMillis() - start) + "ms"))
    }

    def counter(name: String) = Kamon.metrics.counter(name)
    def minMaxCounter(name: String) = Kamon.metrics.minMaxCounter(name)
    def time[A](name: String)(thunk: => A) = Latency.measure(Kamon.metrics.histogram(name))(thunk)
//    def traceFuture[A](name:String)(future: => Future[A]):Future[A] =
//        Tracer.withContext(Kamon.tracer.newContext(name)) {
//            future.andThen { case completed ⇒ Tracer.currentContext.finish() }(SameThreadExecutionContext)
//        }

}
