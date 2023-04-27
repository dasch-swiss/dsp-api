/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.model
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.PropertyInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2

/**
 * The in-memory cache of ontologies.
 *
 * @param ontologies                a map of ontology IRIs to ontologies.
 * @param classToSuperClassLookup   a lookup table of class IRIs to their potential super-classes.
 * @param classToSubclassLookup     a lookup table of class IRIs to their potential subclasses.
 * @param subPropertyOfRelations    a map of subproperties to their base properties.
 * @param superPropertyOfRelations  a map of base classes to their subproperties.
 * @param classDefinedInOntology    a map of class IRIs to the ontology where the class is defined
 * @param propertyDefinedInOntology a map of property IRIs to the ontology where the property is defined
 * @param entityDefinedInOntology   a map of entity IRIs (property or class) to the ontology where the entity is defined
 * @param standoffProperties        a set of standoff properties.
 */
case class OntologyCacheData(
  ontologies: Map[SmartIri, ReadOntologyV2],
  classToSuperClassLookup: Map[SmartIri, Seq[SmartIri]],
  classToSubclassLookup: Map[SmartIri, Set[SmartIri]],
  subPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
  superPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
  classDefinedInOntology: Map[SmartIri, SmartIri],
  propertyDefinedInOntology: Map[SmartIri, SmartIri],
  entityDefinedInOntology: Map[SmartIri, SmartIri],
  standoffProperties: Set[SmartIri]
) {
  lazy val allPropertyDefs: Map[SmartIri, PropertyInfoContentV2] = ontologies.values
    .flatMap(_.properties.map { case (propertyIri, readPropertyInfo) =>
      propertyIri -> readPropertyInfo.entityInfoContent
    })
    .toMap

  def getOntologiesOfProject(projectIri: SmartIri): Set[SmartIri] =
    ontologies.values
      .filter(_.ontologyMetadata.projectIri.contains(projectIri))
      .map(_.ontologyMetadata.ontologyIri)
      .toSet
}
