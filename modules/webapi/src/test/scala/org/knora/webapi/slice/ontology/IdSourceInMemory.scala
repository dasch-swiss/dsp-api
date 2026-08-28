/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology

import zio.*

import java.util.UUID

import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri

/**
 * Deterministic [[IdSource]] test double: two independent counters produce stable, sequential link-value IRIs
 * (`<resourceIri>/values/<n>`) and standoff-tag UUIDs (`UUID(0, n)`), so transformer output is reproducible and
 * `Model.isIsomorphicWith` can assert exact identifiers. The counters are independent so reordering the emit code
 * does not shift the other kind's numbering.
 */
final class IdSourceInMemory(linkCounter: Ref[Int], tagCounter: Ref[Int]) extends IdSource {

  override def freshLinkValueIri(resourceIri: ResourceIri): UIO[ValueIri] =
    linkCounter.getAndUpdate(_ + 1).map(n => ValueIri.unsafeFrom(s"${resourceIri.value}/values/$n"))

  override def freshStandoffTagUuid: UIO[UUID] =
    tagCounter.getAndUpdate(_ + 1).map(n => new UUID(0L, n.toLong))
}

object IdSourceInMemory {

  // The Refs are built inside the layer effect so each non-shared `.provide` gets fresh counters (deterministic per
  // test); `.provideShared` would accumulate them across tests and make every fixed expected id order-dependent.
  val layer: ULayer[IdSource] =
    ZLayer {
      for {
        linkCounter <- Ref.make(1)
        tagCounter  <- Ref.make(1)
      } yield new IdSourceInMemory(linkCounter, tagCounter): IdSource
    }
}
