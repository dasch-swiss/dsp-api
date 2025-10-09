/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import zio.LogLevel
import zio.Task
import zio.ZIO

object ZioHelper {

  def sequence[K, R, A](x: Map[K, ZIO[R, Throwable, A]]): ZIO[R, Throwable, Map[K, A]] =
    ZIO.foreach(x) { case (k, v) => v.map(k -> _) }.map(_.toMap)

  def sequence[A](x: Seq[Task[A]]): Task[List[A]] =
    x.map(_.map(x => List[A](x)))
      .fold(ZIO.succeed(List.empty[A]))((x, y) => x.flatMap(a => y.map(b => a ++ b)))

  def addLogTiming[R, E, A](msg: String, logLevel: LogLevel = LogLevel.Debug)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.logLevel(logLevel) {
      zio.timed.flatMap { case (duration, res) =>
        ZIO.log(s"$msg took: ${duration.toMillis} ms").as(res)
      }
    }
}
