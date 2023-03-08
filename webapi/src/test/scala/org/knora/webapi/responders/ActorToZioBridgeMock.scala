/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders
import zio.Tag
import zio.Task
import zio.URLayer
import zio.ZIO
import zio.ZLayer
import zio.mock
import zio.mock.Mock
import zio.mock.Proxy

import org.knora.webapi.messages.ResponderRequest

/**
 * zio-mock implementation for the [[ActorToZioBridge]]
 *
 * See also the zio-mock documentation:
 * [[https://zio.dev/ecosystem/officials/zio-mock/#encoding-polymorphic-capabilities]]
 */
object ActorToZioBridgeMock extends Mock[ActorToZioBridge] {
  object AskAppActor extends Poly.Effect.Output[ResponderRequest, Throwable]

  val compose: URLayer[mock.Proxy, ActorToZioBridge] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new ActorToZioBridge {
        override def askAppActor[R: Tag](message: ResponderRequest): Task[R] = proxy(AskAppActor.of[R], message)
      }
    }
}
