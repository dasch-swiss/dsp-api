/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.ports

import zio.*

import org.knora.webapi.slice.common.ResourceIri

/** `calls` counts every read, including failing ones. */
final case class FairResourcesInMemory(
  resources: Map[ResourceIri, FairResource],
  failWith: Option[Throwable],
  calls: Ref[Int],
) extends FairResources {

  override def readAsAnonymous(iri: ResourceIri): Task[Option[FairResource]] =
    calls.update(_ + 1) *> failWith.fold(ZIO.succeed(resources.get(iri)))(ZIO.fail(_))
}

object FairResourcesInMemory {
  def make(resources: FairResource*): UIO[FairResourcesInMemory] =
    Ref.make(0).map(FairResourcesInMemory(resources.map(r => r.iri -> r).toMap, None, _))

  def failing(error: Throwable): UIO[FairResourcesInMemory] =
    Ref.make(0).map(FairResourcesInMemory(Map.empty, Some(error), _))
}
