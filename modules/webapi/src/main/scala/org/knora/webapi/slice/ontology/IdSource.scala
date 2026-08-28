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
 * Supplies the freshly-minted identifiers the standoff transformation needs and that are otherwise random: the IRIs of
 * the system `hasStandoffLinkTo` LinkValues minted for salsah-links, and the per-standoff-tag UUIDs. Standoff-tag IRIs
 * are deterministic (`<valueIri>/standoff/<startIndex>`) and are minted directly, not through this service.
 *
 * The production layer delegates to the existing random generators. A deterministic test double lives at
 * `IdSourceInMemory` so `Model.isIsomorphicWith` assertions can pin exact identifiers.
 */
trait IdSource {

  /** A fresh IRI for a standoff-link `LinkValue` on the given resource. Its value id becomes `valueHasUUID`. */
  def freshLinkValueIri(resourceIri: ResourceIri): UIO[ValueIri]

  /** A fresh UUID for a standoff tag, emitted as `standoffTagHasUUID` after base64 encoding. */
  def freshStandoffTagUuid: UIO[UUID]
}

final class IdSourceLive extends IdSource {

  // ValueIri.makeNew and UUID.randomUUID are impure, so suspend each call in ZIO.succeed rather than computing once:
  // a hoisted value would reuse the same id on every mint.
  override def freshLinkValueIri(resourceIri: ResourceIri): UIO[ValueIri] =
    ZIO.succeed(ValueIri.makeNew(resourceIri))

  override def freshStandoffTagUuid: UIO[UUID] =
    ZIO.succeed(UUID.randomUUID())
}

object IdSourceLive {
  val layer: ULayer[IdSource] = ZLayer.succeed(new IdSourceLive)
}
