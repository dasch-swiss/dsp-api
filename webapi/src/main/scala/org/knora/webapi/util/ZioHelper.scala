/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import zio.Task
import zio.ZIO

object ZioHelper {
  def sequence[K, A](x: Map[K, Task[A]]): Task[Map[K, A]] =
    ZIO.foreach(x) { case (k, v) => v.map(k -> _) }.map(_.toMap)

  def sequence[A](x: Seq[Task[A]]): Task[List[A]] =
    x.map(_.map(x => List[A](x)))
      .fold(ZIO.succeed(List.empty[A]))((x, y) => x.flatMap(a => y.map(b => a ++ b)))

  def sequence[A](x: Set[Task[A]]): Task[Set[A]] =
    x.map(_.map(x => Set[A](x)))
      .fold(ZIO.succeed(Set.empty[A]))((x, y) => x.flatMap(a => y.map(b => a ++ b)))
}
