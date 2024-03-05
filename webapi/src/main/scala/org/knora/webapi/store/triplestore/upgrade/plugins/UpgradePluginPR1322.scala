/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import dsp.errors.InconsistentRepositoryDataException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 1322.
 */
class UpgradePluginPR1322() extends UpgradePlugin {
  // IRI objects representing the IRIs used in this transformation.
  private val ValueHasUUIDIri: IriNode      = JenaNodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueHasUUID)
  private val ValueCreationDateIri: IriNode = JenaNodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueCreationDate)
  private val PreviousValueIri: IriNode     = JenaNodeFactory.makeIriNode(OntologyConstants.KnoraBase.PreviousValue)

  override def transform(model: RdfModel): Unit =
    // Add a random UUID to each current value version.
    for (valueIri: IriNode <- collectCurrentValueIris(model)) {
      model.add(
        subj = valueIri,
        pred = ValueHasUUIDIri,
        obj = JenaNodeFactory.makeStringLiteral(UuidUtil.makeRandomBase64EncodedUuid),
      )
    }

  /**
   * Collects the IRIs of all values that are current value versions.
   */
  private def collectCurrentValueIris(model: RdfModel): Iterator[IriNode] =
    model
      .find(None, Some(ValueCreationDateIri), None)
      .map(_.subj)
      .filter { (resource: RdfResource) =>
        model
          .find(
            subj = None,
            pred = Some(PreviousValueIri),
            obj = Some(resource),
          )
          .isEmpty
      }
      .map {
        case iriNode: IriNode => iriNode
        case other            => throw InconsistentRepositoryDataException(s"Unexpected subject for $ValueCreationDateIri: $other")
      }
}
