/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import org.knora.webapi.exceptions.InconsistentRepositoryDataException
import org.knora.webapi.messages.SmartIri

/**
 * Utility functions for working with ontology entities.
 */
object OntologyUtil {

  /**
   * Recursively walks up an entity hierarchy read from the triplestore, collecting the IRIs of all base entities in
   * an ordered sequence.
   *
   * @param iri             the IRI of an entity.
   * @param directRelations a map of entities to their direct base entities.
   * @return all the base entities of the specified entity hierarchically ordered.
   */
  def getAllBaseDefs(iri: SmartIri, directRelations: Map[SmartIri, Set[SmartIri]]): Seq[SmartIri] = {
    def getAllBaseDefsRec(initialIri: SmartIri, currentIri: SmartIri): Seq[SmartIri] =
      directRelations.get(currentIri) match {
        case Some(baseDefs) =>
          val baseDefsSequence = baseDefs.toSeq
          baseDefsSequence ++ baseDefsSequence.flatMap { baseDef =>
            if (baseDef == initialIri) {
              throw InconsistentRepositoryDataException(
                s"Entity $initialIri has an inheritance cycle with entity $baseDef"
              )
            } else {
              getAllBaseDefsRec(initialIri, baseDef)
            }
          }

        case None => Seq.empty[SmartIri]
      }

    getAllBaseDefsRec(initialIri = iri, currentIri = iri)
  }
}
