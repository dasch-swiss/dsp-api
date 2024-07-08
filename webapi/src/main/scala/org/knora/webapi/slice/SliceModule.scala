/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice
import zio.ZLayer

trait SliceModule[RIn, E, ROut] {
  type Dependencies = RIn
  type Provided     = ROut
  def layer: ZLayer[RIn, E, ROut]
}

type RModule[RIn, ROut]  = SliceModule[RIn, Throwable, ROut]
type URModule[RIn, ROut] = SliceModule[RIn, Nothing, ROut]
type Module[E, ROut]     = SliceModule[Any, E, ROut]
type UModule[ROut]       = SliceModule[Any, Nothing, ROut]
type TaskModule[ROut]    = SliceModule[Any, Throwable, ROut]
