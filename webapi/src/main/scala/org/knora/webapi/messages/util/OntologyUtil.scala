/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
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
