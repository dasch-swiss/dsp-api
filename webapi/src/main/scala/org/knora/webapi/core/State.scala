/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio._

import org.knora.webapi.core.domain.AppState

final case class State private (private val state: Ref[AppState]) {
  def set(v: AppState): UIO[Unit] = state.set(v) *> ZIO.logInfo(s"AppState set to ${v.toString()}")
  def getAppState: UIO[AppState]  = state.get
}

object State {
  val layer: ZLayer[Any, Nothing, State] =
    ZLayer(Ref.make[AppState](AppState.Stopped).map(State.apply))
      .tap(_ => ZIO.logInfo(">>> AppStateService initialized <<<"))

}
