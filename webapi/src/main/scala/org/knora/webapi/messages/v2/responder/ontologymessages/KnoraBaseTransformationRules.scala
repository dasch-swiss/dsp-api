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

import org.knora.webapi.{ApiV2Schema, ApiV2Simple, ApiV2WithValueObjects}
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.util.SmartIri

// #KnoraBaseTransformationRules
/**
  * A trait for objects that provide rules for transforming `knora-base` into `knora-api` in some external schema.
  */
trait KnoraBaseTransformationRules {
    /**
      * The metadata to be used for the transformed ontology.
      */
    val ontologyMetadata: OntologyMetadataV2

    /**
      * Properties to remove from `knora-base` before converting it to the target schema.
      */
    val knoraBasePropertiesToRemove: Set[SmartIri]

    /**
      * Classes to remove from `knora-base` before converting it to the target schema.
      */
    val knoraBaseClassesToRemove: Set[SmartIri]

    /**
      * After `knora-base` has been converted to the target schema, these cardinalities must be
      * added to the specified classes to obtain `knora-api`.
      */
    val knoraApiCardinalitiesToAdd: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]]

    /**
      * Classes that need to be added to `knora-base`, after converting it to the target schema, to obtain `knora-api`.
      */
    val knoraApiClassesToAdd: Map[SmartIri, ReadClassInfoV2]

    /**
      * Properties that need to be added to `knora-base`, after converting it to the target schema, to obtain `knora-api`.
      */
    val knoraApiPropertiesToAdd: Map[SmartIri, ReadPropertyInfoV2]
}
// #KnoraBaseTransformationRules

/**
  * Finds the appropriate [[KnoraBaseTransformationRules]] for the target ontology schema.
  */
object KnoraBaseTransformationRules {
    /**
      * Given a target ontology schema, returns a [[KnoraBaseTransformationRules]] describing how to transform
      * `knora-base` into `knora-api` in the target schema.
      *
      * @param targetSchema the target ontology schema.
      * @return the appropriate [[KnoraBaseTransformationRules]].
      */
    def getTransformationRules(targetSchema: ApiV2Schema): KnoraBaseTransformationRules = {
        targetSchema match {
            case ApiV2Simple => KnoraApiV2SimpleTransformationRules
            case ApiV2WithValueObjects => KnoraApiV2WithValueObjectsTransformationRules
        }
    }
}