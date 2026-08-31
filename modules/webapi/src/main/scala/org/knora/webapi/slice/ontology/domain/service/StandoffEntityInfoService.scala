/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service

import zio.*

import dsp.errors.NotFoundException
import org.knora.webapi.ApiV2Simple
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetResponseV2
import org.knora.webapi.slice.ontology.repo.service.OntologyCache

/**
 * Reads standoff class and property definitions from the ontology cache.
 *
 * Extracted from `OntologyResponderV2` so that standoff callers depend only on the ontology cache, not on the whole
 * responder. This keeps the layer graph acyclic and lets the module bundle the cache without pulling in the responder.
 */
trait StandoffEntityInfoService {

  /**
   * Given a set of standoff class IRIs and a set of property IRIs, returns a [[StandoffEntityInfoGetResponseV2]] that
   * describes both the class and the property entities.
   *
   * @param standoffClassIris    the IRIs of the standoff class entities to query.
   * @param standoffPropertyIris the IRIs of the property entities to query.
   */
  def getStandoffEntityInfoResponseV2(
    standoffClassIris: Set[SmartIri] = Set.empty[SmartIri],
    standoffPropertyIris: Set[SmartIri] = Set.empty[SmartIri],
  ): Task[StandoffEntityInfoGetResponseV2]
}

final class StandoffEntityInfoServiceLive(ontologyCache: OntologyCache)(implicit val stringFormatter: StringFormatter)
    extends StandoffEntityInfoService {

  override def getStandoffEntityInfoResponseV2(
    standoffClassIris: Set[SmartIri],
    standoffPropertyIris: Set[SmartIri],
  ): Task[StandoffEntityInfoGetResponseV2] =
    for {
      cacheData <- ontologyCache.getCacheData

      entitiesInWrongSchema =
        (standoffClassIris ++ standoffPropertyIris).filter(_.getOntologySchema.contains(ApiV2Simple))

      _ <- ZIO.fail {
             NotFoundException(
               s"Some requested standoff classes were not found: ${entitiesInWrongSchema.mkString(", ")}",
             )
           }.when(entitiesInWrongSchema.nonEmpty)

      classIrisForCache    = standoffClassIris.map(_.toOntologySchema(InternalSchema))
      propertyIrisForCache = standoffPropertyIris.map(_.toOntologySchema(InternalSchema))

      classOntologies =
        cacheData.ontologies.view.filterKeys(classIrisForCache.map(_.getOntologyFromEntity)).values
      propertyOntologies =
        cacheData.ontologies.view.filterKeys(propertyIrisForCache.map(_.getOntologyFromEntity)).values

      classDefsAvailable = classOntologies.flatMap { ontology =>
                             ontology.classes.filter { case (classIri, classDef) =>
                               classDef.isStandoffClass && standoffClassIris.contains(classIri)
                             }
                           }.toMap

      propertyDefsAvailable = propertyOntologies.flatMap { ontology =>
                                ontology.properties.filter { case (propertyIri, _) =>
                                  standoffPropertyIris.contains(propertyIri) && cacheData.standoffProperties.contains(
                                    propertyIri,
                                  )
                                }
                              }.toMap

      missingClassDefs    = classIrisForCache -- classDefsAvailable.keySet
      missingPropertyDefs = propertyIrisForCache -- propertyDefsAvailable.keySet

      _ <- ZIO.fail {
             NotFoundException(s"Some requested standoff classes were not found: ${missingClassDefs.mkString(", ")}")
           }.when(missingClassDefs.nonEmpty)

      _ <- ZIO.fail {
             NotFoundException(
               s"Some requested standoff properties were not found: ${missingPropertyDefs.mkString(", ")}",
             )
           }.when(missingPropertyDefs.nonEmpty)

      response =
        StandoffEntityInfoGetResponseV2(
          standoffClassInfoMap = new ErrorHandlingMap(classDefsAvailable, key => s"Resource class $key not found"),
          standoffPropertyInfoMap = new ErrorHandlingMap(propertyDefsAvailable, key => s"Property $key not found"),
        )
    } yield response
}

object StandoffEntityInfoServiceLive {
  val layer: URLayer[OntologyCache & StringFormatter, StandoffEntityInfoService] =
    ZLayer.derive[StandoffEntityInfoServiceLive]
}
