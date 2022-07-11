/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for Knora PR 1372.
 */
class UpgradePluginPR1372() extends UpgradePlugin {
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory()

  // IRI objects representing the IRIs used in this transformation.
  private val ValueCreationDateIri: IriNode = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.ValueCreationDate)
  private val PreviousValueIri: IriNode     = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.PreviousValue)
  private val HasPermissionsIri: IriNode    = nodeFactory.makeIriNode(OntologyConstants.KnoraBase.HasPermissions)

  override def transform(model: RdfModel): Unit =
    // Remove knora-base:hasPermissions from all past value versions.
    for (valueIri: IriNode <- collectPastValueIris(model)) {
      model.remove(
        subj = Some(valueIri),
        pred = Some(HasPermissionsIri),
        obj = None
      )
    }

  /**
   * Collects the IRIs of all values that are past value versions.
   */
  private def collectPastValueIris(model: RdfModel): Iterator[IriNode] =
    model
      .find(None, Some(ValueCreationDateIri), None)
      .map(_.subj)
      .filter { resource: RdfResource =>
        model
          .find(
            subj = None,
            pred = Some(PreviousValueIri),
            obj = Some(resource)
          )
          .nonEmpty
      }
      .map {
        case iriNode: IriNode => iriNode
        case other            => throw InconsistentRepositoryDataException(s"Unexpected subject for $ValueCreationDateIri: $other")
      }
}
