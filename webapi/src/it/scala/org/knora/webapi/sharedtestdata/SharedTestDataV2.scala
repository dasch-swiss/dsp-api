/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.sharedtestdata

import java.time.Instant

import dsp.valueobjects.UuidUtil
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadOtherValueV2

object SharedTestDataV2 {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  object Anything {

    /**
     * Simple resource of type anything:Thing with one integer value and restrictive permissions.
     */
    val resource1 = ReadResourceV2(
      resourceIri = "http://rdfh.ch/0001/IwMDbs0KQsaxSRUTl2cAIQ",
      label = "hidden thing",
      resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
      attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
      projectADM = SharedTestDataADM.anythingProject,
      permissions = "M knora-admin:ProjectMember",
      userPermission = PermissionUtilADM.ModifyPermission,
      values = Map(
        "http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri -> Seq(
          ReadOtherValueV2(
            valueIri = "http://rdfh.ch/0001/IwMDbs0KQsaxSRUTl2cAIQ/values/95r2v2DQSgmID6Kmr2LwHg",
            attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
            permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
            userPermission = PermissionUtilADM.ModifyPermission,
            valueCreationDate = Instant.parse("2019-11-29T10:00:00.673298Z"),
            valueHasUUID = UuidUtil.base64Decode("95r2v2DQSgmID6Kmr2LwHg").get,
            valueContent = IntegerValueContentV2(
              ontologySchema = InternalSchema,
              valueHasInteger = 123454321,
              comment = Some("visible int value in hidden resource")
            ),
            previousValueIri = None,
            deletionInfo = None
          )
        )
      ),
      creationDate = Instant.parse("2019-11-29T10:00:00.673298Z"),
      lastModificationDate = None,
      versionDate = None,
      deletionInfo = None
    )
    val resource1Preview = resource1.copy(values = Map.empty)

    /**
     * Simple resource of type anything:Thing without any values.
     */
    val resource2 = ReadResourceV2(
      resourceIri = "http://rdfh.ch/0001/A67ka6UQRHWf313tbhQBjw",
      label = "Victor",
      resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
      attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
      projectADM = SharedTestDataADM.anythingProject,
      permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
      userPermission = PermissionUtilADM.ModifyPermission,
      values = Map.empty,
      creationDate = Instant.parse("2016-10-17T19:16:04.917+02:00"),
      lastModificationDate = None,
      versionDate = None,
      deletionInfo = None
    )
    val resource2Preview = resource2.copy(values = Map.empty)
  }
}
