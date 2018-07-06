/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.util.SmartIri

object ResourcesResponseCheckerV2 {

    /**
      * Compares the response to a full resource request with the expected response.
      *
      * @param received the response returned by the resource responder.
      * @param expected the expected response.
      */
    def compareReadResourcesSequenceV2Response(expected: ReadResourcesSequenceV2, received: ReadResourcesSequenceV2): Unit = {
        assert(expected.numberOfResources == received.numberOfResources, "number of resources is not equal")
        assert(expected.resources.size == received.resources.size, "number of resources are not equal")

        // compare the resources one by one: resources have to returned in the correct order
        expected.resources.zip(received.resources).foreach {
            case (expectedResource: ReadResourceV2, receivedResource: ReadResourceV2) =>

                // compare resource information
                assert(expectedResource.resourceIri == receivedResource.resourceIri, "resource Iri does not match")
                assert(expectedResource.label == receivedResource.label, "label does not match")
                assert(expectedResource.resourceClass == receivedResource.resourceClass, "resource class does not match")

                // this check is necessary because zip returns a sequence of the length of the smaller of the two lists to be combined.
                // https://www.scala-lang.org/api/current/scala/collection/Seq.html#zip[B](that:scala.collection.GenIterable[B]):Seq[(A,B)]
                assert(expectedResource.values.size == receivedResource.values.size, "number of values is not equal")

                // compare the properties
                // convert Map to a sequence of tuples and sort by property Iri)
                expectedResource.values.toSeq.sortBy(_._1).zip(receivedResource.values.toSeq.sortBy(_._1)).foreach {
                    case ((expectedPropIri: SmartIri, expectedPropValues: Seq[ReadValueV2]), (receivedPropIri: SmartIri, receivedPropValues: Seq[ReadValueV2])) =>

                        assert(expectedPropIri == receivedPropIri)

                        // this check is necessary because zip returns a sequence of the length of the smaller of the two lists to be combined.
                        // https://www.scala-lang.org/api/current/scala/collection/Seq.html#zip[B](that:scala.collection.GenIterable[B]):Seq[(A,B)]
                        assert(expectedPropValues.size == receivedPropValues.size, "number of value instances is not equal")

                        expectedPropValues.sortBy(_.valueIri).zip(receivedPropValues.sortBy(_.valueIri)).foreach {
                            case (expectedVal: ReadValueV2, receivedVal: ReadValueV2) =>

                                assert(expectedVal == receivedVal, s"value objects does not match: $expectedVal != $receivedVal")
                        }


                }

        }
    }

}