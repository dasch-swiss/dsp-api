/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.Logger

import java.time.Instant

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.SystemProject
import org.knora.webapi.messages.OntologyConstants.KnoraBase.AttachedToProject
import org.knora.webapi.messages.OntologyConstants.KnoraBase.LastModificationDate
import org.knora.webapi.messages.OntologyConstants.Owl.Ontology
import org.knora.webapi.messages.OntologyConstants.Xsd.DateTime
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.upgrade.UpgradePlugin

/**
 * Transforms a repository for DSP-API PR 2018.
 */
class UpgradePluginPR2018(log: Logger) extends UpgradePlugin {
  private val newModificationDate   = Instant.now.toString
  private val ontologyType: IriNode = JenaNodeFactory.makeIriNode(Ontology)

  override def transform(model: RdfModel): Unit =
    for (ontology: IriNode <- getOntologiesToTransform(model)) {
      model.add(
        subj = ontology,
        pred = JenaNodeFactory.makeIriNode(LastModificationDate),
        obj = JenaNodeFactory.makeDatatypeLiteral(
          value = newModificationDate,
          datatype = DateTime,
        ),
        context = Some(ontology.iri),
      )

      log.info(s"Updated ontology: ${ontology.iri} with LastModificationDate")
    }

  private def getOntologiesToTransform(model: RdfModel): Iterator[IriNode] = {
    val triplesWithoutLastModificationDate: Set[RdfResource] = model
      .find(
        subj = None,
        pred = Some(JenaNodeFactory.makeIriNode(LastModificationDate)),
        obj = None,
      )
      .map(_.subj)
      .toSet

    val triplesInOntologyType: Set[RdfResource] = model
      .find(
        subj = None,
        pred = None,
        obj = Some(ontologyType),
      )
      .map(_.subj)
      .toSet

    val onotologiesWithoutLastModificationDate: Set[RdfResource] =
      triplesInOntologyType -- triplesWithoutLastModificationDate

    val triplesAttachedToSystemProject: Set[RdfResource] = model
      .find(
        subj = None,
        pred = Some(JenaNodeFactory.makeIriNode(AttachedToProject)),
        obj = None,
      )
      .filter(triple => (triple.obj == JenaNodeFactory.makeIriNode(SystemProject)))
      .map(_.subj)
      .toSet

    val ontologiesWithoutLastModificationDateAndNotAttachedToSystemProject: Set[RdfResource] =
      onotologiesWithoutLastModificationDate -- triplesAttachedToSystemProject

    ontologiesWithoutLastModificationDateAndNotAttachedToSystemProject.map {
      case iriNode: IriNode => iriNode
      case other            => throw InconsistentRepositoryDataException(s"Unexpected subject for $ontologyType: $other")
    }.iterator
  }
}
