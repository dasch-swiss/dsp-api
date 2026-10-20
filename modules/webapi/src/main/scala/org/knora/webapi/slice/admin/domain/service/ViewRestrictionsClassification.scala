/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

/**
 * Classification of ontology entities for the view-restrictions report.
 *
 * Decides which properties the property report lists and which classes both reports show, from the flags the
 * ontology cache already carries, without any triplestore access. The counts classify properties in SPARQL.
 * [[ProjectOntologyIndex.load]] is the one effectful entry point; everything else is pure.
 */
object ViewRestrictionsClassification {

  /** The kind of value a counted property holds. */
  enum ValueKind {

    /** An ordinary value, such as text, integer or date. */
    case Plain

    /** A file value, such as a still image or document. */
    case File

    /** A user-defined link value, the permissioned object that accompanies a link property. */
    case Link
  }

  object ValueKind {

    /**
     * The kind of value the property holds, or `None` when the report must not count the property.
     *
     * Not counted: non-resource properties, link properties (they point at a resource and carry no value of
     * their own), standoff internal references, and `knora-base:hasStandoffLinkToValue`, which records
     * references inside text rather than a link a user created.
     */
    def of(info: ReadPropertyInfoV2): Option[ValueKind] =
      if !isCounted(info) then None
      else if info.isLinkValueProp then Some(Link)
      else if info.isFileValueProp then Some(File)
      else Some(Plain)

    private def isCounted(info: ReadPropertyInfoV2): Boolean =
      info.isResourceProp && !info.isLinkProp && !info.isStandoffInternalReferenceProperty && !isStandoffLinkValue(info)

    private def isStandoffLinkValue(info: ReadPropertyInfoV2): Boolean =
      info.entityInfoContent.propertyIri.toString == OntologyConstants.KnoraBase.HasStandoffLinkToValue
  }

  /**
   * The resource classes of every ontology in the cache, keyed by internal-schema IRI.
   *
   * @param resourceClasses  the IRIs of the resource classes.
   */
  final case class ProjectOntologyIndex(resourceClasses: Set[String])

  object ProjectOntologyIndex {

    /** Indexes every ontology in the cache, so classes of any ontology are found. */
    def load(ontologyRepo: OntologyRepo): Task[ProjectOntologyIndex] =
      ontologyRepo.findAll().map(ontologies => from(ontologies.flatMap(_.classes.values)))

    def from(classes: Iterable[ReadClassInfoV2]): ProjectOntologyIndex =
      ProjectOntologyIndex(classes.filter(_.isResourceClass).map(_.entityInfoContent.classIri.toString).toSet)
  }
}
