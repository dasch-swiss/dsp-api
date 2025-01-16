/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service

import zio.*

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import dsp.constants.SalsahGui

trait OntologyService {
  def getProjectIriForOntologyIri(ontologyIri: InternalIri): Task[Option[String]]

  def getPropertyLabelByIri(propertyIri: SmartIri): Task[Option[String]]

  def getGuiOrderForProperty(propertyIri: SmartIri): Task[Option[Int]]
}

final case class OntologyServiceLive(ontologyCache: OntologyCache) extends OntologyService {
  def getProjectIriForOntologyIri(ontologyIri: InternalIri): Task[Option[String]] =
    ontologyCache.getCacheData.map { cacheData =>
      cacheData.ontologies.map { case (k, v) => k.toString() -> v }
        .get(ontologyIri.value)
        .flatMap(_.ontologyMetadata.projectIri.map(_.toString()))
    }

  def getPropertyLabelByIri(propertyIri: SmartIri): Task[Option[String]] =
    (for {
      cacheData <- ontologyCache.getCacheData.option.some
      ontoIri    = propertyIri.getOntologyFromEntity
      onto      <- ZIO.fromOption(cacheData.ontologies.get(ontoIri))
      prop      <- ZIO.fromOption(onto.properties.get(propertyIri))
      literal <- ZIO
                   .fromOption(
                     prop.entityInfoContent.predicates
                       .find(_._1.toIri == OntologyConstants.Rdfs.Label)
                       .map(_._2)
                       .flatMap(_._2.headOption),
                   )
      label <- ZIO.fromOption(literal match {
                 case StringLiteralV2(value, _) => Some(value)
                 case _                         => None
               })
    } yield label).unsome

  def getGuiOrderForProperty(propertyIri: SmartIri): Task[Option[Int]] =
    (for {
      cacheData <- ontologyCache.getCacheData.option.some
      ontoIri    = propertyIri.getOntologyFromEntity
      onto      <- ZIO.fromOption(cacheData.ontologies.get(ontoIri))
      prop      <- ZIO.fromOption(onto.properties.get(propertyIri))
      literal <- ZIO
                   .fromOption(
                     prop.entityInfoContent.predicates
                       .find(_._1.toIri == SalsahGui.GuiOrder)
                       .map(_._2)
                       .flatMap(_._2.headOption),
                   )
      guiOrder <- ZIO.fromOption(literal match {
                    case StringLiteralV2(value, _) => value.toIntOption
                    case _                         => None
                  })
    } yield guiOrder).unsome

}

object OntologyServiceLive {
  def isBuiltInOntology(ontologyIri: InternalIri): Boolean =
    OntologyConstants.BuiltInOntologyLabels.contains(ontologyIri.value.split("/").last)

  def isSharedOntology(ontologyIri: InternalIri): Boolean =
    ontologyIri.value.split("/")(4) == "shared"

  def isBuiltInOrSharedOntology(ontologyIri: InternalIri): Boolean =
    isBuiltInOntology(ontologyIri) || isSharedOntology(ontologyIri)

  val layer = ZLayer.derive[OntologyServiceLive]
}
