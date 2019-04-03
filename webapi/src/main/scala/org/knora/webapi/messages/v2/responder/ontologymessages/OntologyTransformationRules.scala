/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.v2.responder.ontologymessages

import org.knora.webapi.{ApiV2Complex, ApiV2Schema, ApiV2Simple, BadRequestException, OntologyConstants}
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.util.SmartIri

// #OntologyTransformationRules
/**
  * A trait for objects that provide rules for converting an ontology from the internal schema to an external schema.
  */
trait OntologyTransformationRules {
    /**
      * The metadata to be used for the transformed ontology.
      */
    val ontologyMetadata: OntologyMetadataV2

    /**
      * Properties to remove from the ontology before converting it to the target schema.
      */
    val internalPropertiesToRemove: Set[SmartIri]

    /**
      * Classes to remove from the ontology before converting it to the target schema.
      */
    val internalClassesToRemove: Set[SmartIri]

    /**
      * After the ontology has been converted to the target schema, these cardinalities must be
      * added to the specified classes.
      */
    val externalCardinalitiesToAdd: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]]

    /**
      * Classes that need to be added to the ontology after converting it to the target schema.
      */
    val externalClassesToAdd: Map[SmartIri, ReadClassInfoV2]

    /**
      * Properties that need to be added to the ontology after converting it to the target schema.
      */
    val externalPropertiesToAdd: Map[SmartIri, ReadPropertyInfoV2]
}

// #OntologyTransformationRules

/**
  * Finds the appropriate [[OntologyTransformationRules]] for an ontology and a target schema.
  */
object OntologyTransformationRules {
    /**
      * Given an ontology IRI and a target ontology schema, returns the [[OntologyTransformationRules]] describing how to
      * convert the ontology to the target schema.
      *
      * @param ontologyIri  the IRI of the ontology being transformed.
      * @param targetSchema the target ontology schema.
      * @return the appropriate [[OntologyTransformationRules]].
      */
    def getTransformationRules(ontologyIri: SmartIri, targetSchema: ApiV2Schema): OntologyTransformationRules = {
        // If this is the admin ontology, use its transformation rules.
        if (ontologyIri.toString == OntologyConstants.KnoraAdminV2.KnoraAdminOntologyIri) {
            targetSchema match {
                case ApiV2Simple => throw BadRequestException(s"The knora-admin API is not available in the simple schema")
                case ApiV2Complex => KnoraAdminToApiV2ComplexTransformationRules
            }
        } else {
            // Otherwise, use the knora-base transformation rules.
            targetSchema match {
                case ApiV2Simple => KnoraBaseToApiV2SimpleTransformationRules
                case ApiV2Complex => KnoraBaseToApiV2ComplexTransformationRules
            }
        }
    }
}
