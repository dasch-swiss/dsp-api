/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2

/**
 * Pure classification of ontology entities for the view-restrictions report.
 *
 * Decides which properties the report counts and of what kind, from the flags the ontology cache already
 * carries, without any triplestore access.
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
   * The ontology entities of one project that the report can count, keyed by internal-schema IRI.
   *
   * @param resourceClasses  the IRIs of the resource classes.
   * @param valueProperties  the counted properties, each with its [[ValueKind]].
   * @param knownProperties  every property IRI in the ontologies, counted or not. A row whose property is not
   *                         in this set points at an ontology-cache miss, not at a link target.
   */
  final case class ProjectOntologyIndex(
    resourceClasses: Set[String],
    valueProperties: Map[String, ValueKind],
    knownProperties: Set[String],
  )

  object ProjectOntologyIndex {

    def from(properties: Iterable[ReadPropertyInfoV2], classes: Iterable[ReadClassInfoV2]): ProjectOntologyIndex =
      ProjectOntologyIndex(
        resourceClasses = classes.filter(_.isResourceClass).map(_.entityInfoContent.classIri.toString).toSet,
        valueProperties = properties.flatMap { p =>
          ValueKind.of(p).map(p.entityInfoContent.propertyIri.toString -> _)
        }.toMap,
        knownProperties = properties.map(_.entityInfoContent.propertyIri.toString).toSet,
      )
  }
}
