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
 * The production layer mints through ZIO's `Random`. A deterministic test double lives at `IdSourceInMemory` so
 * `Model.isIsomorphicWith` assertions can pin exact identifiers.
 */
trait IdSource {

  /** A fresh IRI for a standoff-link `LinkValue` on the given resource. Its value id becomes `valueHasUUID`. */
  def makeLinkValueIri(resourceIri: ResourceIri): UIO[ValueIri]

  /** A fresh UUID for a standoff tag, emitted as `standoffTagHasUUID` after base64 encoding. */
  def makeStandoffTagUuid: UIO[UUID]
}

final class IdSourceLive extends IdSource {

  // Mint through ZIO's Random rather than UUID.randomUUID, so a seeded Random makes the output reproducible in tests.
  //
  // No triplestore uniqueness check (unlike the create path's IriService.makeUnusedIri): import builds a fresh graph
  // from scratch, so a random 128-bit UUID cannot collide with an existing value IRI. Deliberate, not an oversight.
  override def makeLinkValueIri(resourceIri: ResourceIri): UIO[ValueIri] =
    Random.nextUUID.map(uuid => ValueIri.from(resourceIri, uuid))

  override def makeStandoffTagUuid: UIO[UUID] =
    Random.nextUUID
}

object IdSourceLive {
  val layer: ULayer[IdSource] = ZLayer.succeed(new IdSourceLive)
}
