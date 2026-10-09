/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service

import zio.*

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.fair.ports.FairClassLabels

/**
 * Serves the labels of a resource class to the FAIR landing pages.
 */
final case class FairClassLabelsLive(ontologyRepo: OntologyRepo, iriConverter: IriConverter) extends FairClassLabels {

  override def labels(classIri: String): Task[Map[String, String]] =
    iriConverter.asResourceClassIriApiV2Complex(classIri).option.flatMap {
      case None           => ZIO.succeed(Map.empty)
      case Some(classIri) =>
        for {
          labelIri <- iriConverter.asSmartIri(OntologyConstants.Rdfs.Label)
          info     <- ontologyRepo.findClassBy(classIri)
        } yield info.map(_.entityInfoContent.getPredicateObjectsWithLangs(labelIri)).getOrElse(Map.empty)
    }
}

object FairClassLabelsLive {
  val layer = ZLayer.derive[FairClassLabelsLive]
}
