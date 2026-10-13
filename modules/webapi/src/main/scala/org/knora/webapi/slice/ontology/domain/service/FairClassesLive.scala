/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service

import zio.*

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.fair.ports.FairClass
import org.knora.webapi.slice.fair.ports.FairClasses

/**
 * Serves the labels and file requirement of a resource class to the FAIR landing pages.
 */
final case class FairClassesLive(ontologyRepo: OntologyRepo, iriConverter: IriConverter) extends FairClasses {

  private val empty = FairClass(Map.empty, requiresFile = false)

  override def describe(classIri: String): Task[FairClass] =
    iriConverter.asResourceClassIriApiV2Complex(classIri).option.flatMap {
      case None           => ZIO.succeed(empty)
      case Some(classIri) =>
        for {
          labelIri <- iriConverter.asSmartIri(OntologyConstants.Rdfs.Label)
          info     <- ontologyRepo.findClassBy(classIri)
        } yield info.fold(empty)(i =>
          FairClass(i.entityInfoContent.getPredicateObjectsWithLangs(labelIri), FairClassesLive.requiresFile(i)),
        )
    }
}

object FairClassesLive {

  /** A class requires a file when a file-value property has a minimum cardinality of at least one. */
  def requiresFile(info: ReadClassInfoV2): Boolean =
    info.allCardinalities.exists { case (property, c) =>
      info.fileValueProperties.contains(property) && c.cardinality.min >= 1
    }

  val layer = ZLayer.derive[FairClassesLive]
}
