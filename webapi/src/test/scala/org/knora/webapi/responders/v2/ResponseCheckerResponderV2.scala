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

package org.knora.webapi.responders.v2

import org.knora.webapi.IRI
import org.knora.webapi.messages.v2.responder.{ReadResourceV2, ReadResourcesSequenceV2, ReadValueV2}

object ResponseCheckerResponderV2 {

    /**
      * Compares the response to a full resource request with the expected response.
      *
      * @param received the response returned by the resource responder.
      * @param expected the expected response.
      */
    def compareReadResourcesSequenceV2Response(expected: ReadResourcesSequenceV2, received: ReadResourcesSequenceV2): Unit = {
        assert(expected.numberOfResources == received.numberOfResources, "number of resources are not equal")

        // compare the resources one by one: resources have to returned in the correct order
        expected.resources.zip(received.resources).foreach {
            case (expectedResource: ReadResourceV2, receivedResource: ReadResourceV2) =>

                // compare resource information
                assert(expectedResource.resourceIri == receivedResource.resourceIri, "resource Iri does not match")
                assert(expectedResource.label == receivedResource.label, "label does not match")
                assert(expectedResource.resourceClass == receivedResource.resourceClass, "resource class does not match")

                // compare the properties
                // convert Map to a sequence of tuples and sort by property Iri)
                expectedResource.values.toSeq.sortBy(_._1).zip(receivedResource.values.toSeq.sortBy(_._1)).foreach {
                    case ((expectedPropIri: IRI, expectedPropValues: Seq[ReadValueV2]), (receivedPropIri: IRI, receivedPropValues: Seq[ReadValueV2])) =>

                        assert(expectedPropIri == receivedPropIri)

                        expectedPropValues.sortBy(_.valueIri).zip(receivedPropValues.sortBy(_.valueIri)).foreach {
                            case (expectedVal: ReadValueV2, receivedVal: ReadValueV2) =>

                                assert(expectedVal == receivedVal,  s"value objects does not match: ${expectedVal} != ${receivedVal}")
                        }



                }

        }
    }

}