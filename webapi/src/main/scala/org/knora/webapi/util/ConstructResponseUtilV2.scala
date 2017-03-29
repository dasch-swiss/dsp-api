/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, OntologyConstants}
import org.knora.webapi.messages.v1.store.triplestoremessages.SparqlConstructResponse

object ConstructResponseUtilV2 {

    case class ResourcesAndValueObjects(resources: Map[IRI, Seq[(IRI, String)]], valueObjects: Map[IRI, Seq[(IRI, String)]])

    /**
      * A [[SparqlConstructResponse]] may contain both resources and value objects.
      * This method splits the results and returns them as separate structures.
      *
      * @param constructQueryResults the results of a SPARQL construct query.
      * @return a [[ResourcesAndValueObjects]].
      */
    def splitResourcesAndValueObjects(constructQueryResults: SparqlConstructResponse): ResourcesAndValueObjects = {

        val (valueObjects: Map[IRI, Seq[(IRI, String)]], resources: Map[IRI, Seq[(IRI, String)]]) = constructQueryResults.statements.partition {
            case (subject: IRI, assertions: Seq[(IRI, String)]) =>

                // get the subject's type (it could be a resource or a valueObject)
                val subjectType: Option[String] = assertions.find {
                    case (pred, obj) =>
                        pred == OntologyConstants.Rdf.Type
                }.map(_._2) // get the type

                OntologyConstants.KnoraBase.ValueClasses.contains(subjectType.getOrElse(throw InconsistentTriplestoreDataException(s"no rdf:type given for $subject")))

        }

        ResourcesAndValueObjects(resources = resources, valueObjects = valueObjects)

    }
}