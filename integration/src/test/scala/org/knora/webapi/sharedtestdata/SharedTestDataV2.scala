/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.sharedtestdata

import java.time.Instant

import dsp.valueobjects.UuidUtil
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadOtherValueV2

object SharedTestDataV2 {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  object AnythingOntology {
    val ontologyIri: SmartIri         = "http://www.knora.org/ontology/0001/anything".toSmartIri
    val ontologyIriExternal: SmartIri = ontologyIri.toOntologySchema(ApiV2Complex)

    // anything:Thing
    val thingClassIri: SmartIri         = ontologyIri.makeEntityIri("Thing")
    val thingClassIriExternal: SmartIri = thingClassIri.toOntologySchema(ApiV2Complex)

    // anything:BlueThing
    val blueThingClassIri: SmartIri         = ontologyIri.makeEntityIri("BlueThing")
    val blueThingClassIriExternal: SmartIri = blueThingClassIri.toOntologySchema(ApiV2Complex)

    // anythingThingWithMaxCardinality
    val thingWithMaxCardinalityClassIri: SmartIri = ontologyIri.makeEntityIri("ThingWithMaxCardinality")
    val thingWithMaxCardinalityClassIriExternal: SmartIri =
      thingWithMaxCardinalityClassIri.toOntologySchema(ApiV2Complex)

    // anything:hasInteger
    val hasIntegerPropIri: SmartIri         = ontologyIri.makeEntityIri("hasInteger")
    val hasIntegerPropIriExternal: SmartIri = hasIntegerPropIri.toOntologySchema(ApiV2Complex)

    // anything:hasRichtext
    val hasRichtextPropIri: SmartIri         = ontologyIri.makeEntityIri("hasRichtext")
    val hasRichtextPropIriExternal: SmartIri = hasRichtextPropIri.toOntologySchema(ApiV2Complex)

    // anything:hasText
    val hasTextPropIri: SmartIri         = ontologyIri.makeEntityIri("hasText")
    val hasTextPropIriExternal: SmartIri = hasTextPropIri.toOntologySchema(ApiV2Complex)

    // anything:hasUnformattedText
    val hasUnformattedTextPropIri: SmartIri         = ontologyIri.makeEntityIri("hasUnformattedText")
    val hasUnformattedTextPropIriExternal: SmartIri = hasUnformattedTextPropIri.toOntologySchema(ApiV2Complex)

    // anything:hasIntegerUsedByOtherOntologies
    val hasIntegerUsedByOtherOntologiesPropIri: SmartIri = ontologyIri.makeEntityIri("hasIntegerUsedByOtherOntologies")
    val hasIntegerUsedByOtherOntologiesPropIriExternal: SmartIri =
      hasIntegerUsedByOtherOntologiesPropIri.toOntologySchema(ApiV2Complex)

  }

  object Anything {

    /**
     * Simple resource of type anything:Thing with one integer value and restrictive permissions.
     */
    val resouce1value1 = ReadOtherValueV2(
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
    val resource1 = ReadResourceV2(
      resourceIri = "http://rdfh.ch/0001/IwMDbs0KQsaxSRUTl2cAIQ",
      label = "hidden thing",
      resourceClassIri = AnythingOntology.thingClassIri,
      attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
      projectADM = SharedTestDataADM.anythingProject,
      permissions = "M knora-admin:ProjectMember",
      userPermission = PermissionUtilADM.ModifyPermission,
      values = Map(AnythingOntology.hasIntegerPropIri -> Seq(resouce1value1)),
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
      resourceClassIri = AnythingOntology.thingClassIri,
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

    val deletedResourceIri            = "http://rdfh.ch/0001/PHbbrEsVR32q5D_ioKt6pA"
    val resourceWithMaxCardinalityIri = "http://rdfh.ch/0001/t1TgU0hUS3O3DDE9thMqdQ"

    object Resource3 {
      val resourceIri = "http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9mg"
      val intValueIri = "http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9mg/values/c8zmKe-eRjWMOGIOw-5GyA"
    }
  }

  object Values {
    object Ontology {
      // ontology
      val ontologyIri: SmartIri         = "http://www.knora.org/ontology/0001/values".toSmartIri
      val ontologyIriExternal: SmartIri = ontologyIri.toOntologySchema(ApiV2Complex)

      // v:Resource
      val resourceClassIri: SmartIri         = ontologyIri.makeEntityIri("Resource")
      val resourceClassIriExternal: SmartIri = resourceClassIri.toOntologySchema(ApiV2Complex)

      // v:hasInteger
      val hasIntegerPropIri: SmartIri         = ontologyIri.makeEntityIri("hasInteger")
      val hasIntegerPropIriExternal: SmartIri = hasIntegerPropIri.toOntologySchema(ApiV2Complex)

      // v:hasUnformattedText
      val hasUnformattedTextPropIri: SmartIri         = ontologyIri.makeEntityIri("hasUnformattedText")
      val hasUnformattedTextPropIriExternal: SmartIri = hasUnformattedTextPropIri.toOntologySchema(ApiV2Complex)
    }
    object Data {
      object Resource1 {
        val resourceIri = "http://rdfh.ch/0001/sNynjUbwS5eGqAFN3g0R6A"

        object IntValue1 {
          val encodedUuid = "uAdQoNrUR3iCY24IUa9JrA"
          val valueIri    = s"http://rdfh.ch/0001/sNynjUbwS5eGqAFN3g0R6A/values/$encodedUuid"
          val intValue    = 1
          val valueUuid   = UuidUtil.base64Decode(encodedUuid).get
          val permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember"
        }

        object IntValue2 {
          val encodedUuid = "9fB9e0uCSpSQtj8vPmpYSg"
          val valueIri    = s"http://rdfh.ch/0001/sNynjUbwS5eGqAFN3g0R6A/values/$encodedUuid"
          val intValue    = 2
          val valueUuid   = UuidUtil.base64Decode(encodedUuid).get
        }

        object IntValue3 {
          val encodedUuid = "PKh9WhhrSL2mFYLtJkNJDw"
          val valueIri    = s"http://rdfh.ch/0001/sNynjUbwS5eGqAFN3g0R6A/values/$encodedUuid"
          val intValue    = 3
          val valueUuid   = UuidUtil.base64Decode(encodedUuid).get
        }

        object IntValue4 {
          val encodedUuid = "BnrfmiVQRXeTLfK1wJoVAA"
          val valueIri    = s"http://rdfh.ch/0001/sNynjUbwS5eGqAFN3g0R6A/values/$encodedUuid"
          val intValue    = 4
          val valueUuid   = UuidUtil.base64Decode(encodedUuid).get
        }

        object IntValue5 {
          val encodedUuid = "RrFwcpKlR5y2spM4fnwrqw"
          val valueIri    = s"http://rdfh.ch/0001/sNynjUbwS5eGqAFN3g0R6A/values/$encodedUuid"
          val intValue    = 5
          val valueUuid   = UuidUtil.base64Decode(encodedUuid).get
        }

        object IntValue6 {
          val encodedUuid = "px9HxJjER3a3b1gYXiBxAw"
          val valueIri    = s"http://rdfh.ch/0001/sNynjUbwS5eGqAFN3g0R6A/values/$encodedUuid"
          val intValue    = 6
          val valueUuid   = UuidUtil.base64Decode(encodedUuid).get
        }

        object IntValue7 {
          val encodedUuid       = "Z3VJyRLdTAmkQVDND1lCZw"
          val valueIri          = s"http://rdfh.ch/0001/sNynjUbwS5eGqAFN3g0R6A/values/$encodedUuid"
          val intValue          = 7
          val valueUuid         = UuidUtil.base64Decode(encodedUuid).get
          val valueCreationDate = Instant.parse("2019-11-29T10:00:00.673298Z")
        }

        object IntValue8 {
          val encodedUuid       = "06Ab5GjMSzmIozDdxEcP4w"
          val valueIri          = s"http://rdfh.ch/0001/sNynjUbwS5eGqAFN3g0R6A/values/$encodedUuid"
          val intValue          = 8
          val valueUuid         = UuidUtil.base64Decode(encodedUuid).get
          val valueCreationDate = Instant.parse("2019-11-29T10:00:00.673298Z")
        }

        object IntValue9 {
          val encodedUuid = "r4swthLUS4GBhueBdliExg"
          val valueIri    = s"http://rdfh.ch/0001/sNynjUbwS5eGqAFN3g0R6A/values/$encodedUuid"
          val intValue    = 9
        }

        object UnformattedTextValue1 {
          val encodedUuid = "vahL16BGTO2wWsU5ddcyLg"
          val valueIri    = s"http://rdfh.ch/0001/sNynjUbwS5eGqAFN3g0R6A/values/$encodedUuid"
          val textValue   = "unformatted text value 1"
        }
      }
    }
  }
}
