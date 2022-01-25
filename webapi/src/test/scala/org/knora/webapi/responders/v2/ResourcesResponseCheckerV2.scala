/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.valuemessages._

object ResourcesResponseCheckerV2 {

  /**
   * Compares the response to a full resource request with the expected response.
   *
   * @param received the response returned by the resource responder.
   * @param expected the expected response.
   */
  def compareReadResourcesSequenceV2Response(
    expected: ReadResourcesSequenceV2,
    received: ReadResourcesSequenceV2
  ): Unit = {
    assert(expected.resources.size == received.resources.size, "number of resources is not equal")
    assert(expected.resources.size == received.resources.size, "number of resources are not equal")

    // compare the resources one by one: resources have to returned in the correct order
    expected.resources.zip(received.resources).foreach {
      case (expectedResource: ReadResourceV2, receivedResource: ReadResourceV2) =>
        // compare resource information
        assert(expectedResource.resourceIri == receivedResource.resourceIri, "resource Iri does not match")
        assert(expectedResource.label == receivedResource.label, "label does not match")
        assert(expectedResource.resourceClassIri == receivedResource.resourceClassIri, "resource class does not match")
        assert(
          expectedResource.userPermission == receivedResource.userPermission,
          s"expected user permission ${expectedResource.userPermission} on resource ${receivedResource.resourceIri}, received ${receivedResource.userPermission}"
        )

        // this check is necessary because zip returns a sequence of the length of the smaller of the two lists to be combined.
        // https://www.scala-lang.org/api/current/scala/collection/Seq.html#zip[B](that:scala.collection.GenIterable[B]):Seq[(A,B)]
        assert(expectedResource.values.size == receivedResource.values.size, "number of values is not equal")

        // compare the properties
        // convert Map to a sequence of tuples and sort by property Iri)
        expectedResource.values.toSeq.sortBy(_._1).zip(receivedResource.values.toSeq.sortBy(_._1)).foreach {
          case (
                (expectedPropIri: SmartIri, expectedPropValues: Seq[ReadValueV2]),
                (receivedPropIri: SmartIri, receivedPropValues: Seq[ReadValueV2])
              ) =>
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
