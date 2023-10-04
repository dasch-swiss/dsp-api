/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

import dsp.valueobjects.UuidUtil
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadOtherValueV2
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response
import org.knora.webapi.sharedtestdata.SharedTestDataADM

class ResourcesResponseCheckerV2Spec extends AnyWordSpec {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getInitializedTestInstance

  val value1 = ReadOtherValueV2(
    valueIri = "http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9mg/values/c8zmKe-eRjWMOGIOw-5GyA",
    attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
    permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
    userPermission = PermissionUtilADM.ChangeRightsPermission,
    valueCreationDate = Instant.parse("2019-11-29T10:00:00.673298Z"),
    valueHasUUID = UuidUtil.decode("c8zmKe-eRjWMOGIOw-5GyA"),
    valueContent = IntegerValueContentV2(InternalSchema, 1, None),
    previousValueIri = None,
    deletionInfo = None
  )
  val value2 = ReadOtherValueV2(
    valueIri = "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/SZyeLLmOTcCCuS3B0VksHQ",
    attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
    permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
    userPermission = PermissionUtilADM.ChangeRightsPermission,
    valueCreationDate = Instant.parse("2019-11-29T10:00:00.673298Z"),
    valueHasUUID = UuidUtil.decode("SZyeLLmOTcCCuS3B0VksHQ"),
    valueContent = IntegerValueContentV2(InternalSchema, 42, None),
    previousValueIri = None,
    deletionInfo = None
  )
  val values  = Map("http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri -> Seq(value1))
  val values2 = Map("http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri -> Seq(value1, value2))
  val resource = ReadResourceV2(
    resourceIri = "http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9mg",
    label = "test resource",
    resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
    attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
    projectADM = SharedTestDataADM.anythingProject,
    permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
    userPermission = PermissionUtilADM.ChangeRightsPermission,
    values = values,
    creationDate = Instant.parse("2019-11-29T10:00:00.673298Z"),
    lastModificationDate = None,
    versionDate = None,
    deletionInfo = None
  )
  val resource2                  = resource.copy()
  val resourceSequence           = ReadResourcesSequenceV2(Seq(resource))
  val resourceSequenceIdentical  = ReadResourcesSequenceV2(Seq(resource.copy()))
  val resourceSequenceDifferent  = ReadResourcesSequenceV2(Seq(resource, resource2))
  val resourceSequencePreview    = ReadResourcesSequenceV2(Seq(resource.copy(values = Map.empty)))
  val resourceSequenceMoreValues = ReadResourcesSequenceV2(Seq(resource.copy(values = values2)))

  "The ResourcesResponseCheckerV2" should {
    "not throw an exception if received and expected resource responses are the same" in {

      compareReadResourcesSequenceV2Response(
        expected = resourceSequence,
        received = resourceSequenceIdentical
      )

    }

    "throw an exception if received and expected resource responses are different" in {
      assertThrows[AssertionError] {
        compareReadResourcesSequenceV2Response(
          expected = resourceSequence,
          received = resourceSequenceDifferent
        )
      }
    }

    "throw an exception when comparing a full response to a preview response of the same resource" in {
      assertThrows[AssertionError] {
        compareReadResourcesSequenceV2Response(
          expected = resourceSequence,
          received = resourceSequencePreview
        )
      }
    }

    "throw an exception when comparing a full response to a full response with a different number of values for a property" in {
      assertThrows[AssertionError] {
        compareReadResourcesSequenceV2Response(
          expected = resourceSequence,
          received = resourceSequenceMoreValues
        )
      }
    }
  }

}
