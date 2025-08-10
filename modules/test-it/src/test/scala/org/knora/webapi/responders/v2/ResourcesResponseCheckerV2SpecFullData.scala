/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import java.time.Instant

import dsp.valueobjects.UuidUtil
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.CalendarNameJulian
import org.knora.webapi.messages.util.DatePrecisionYear
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.Permission

class ResourcesResponseCheckerV2SpecFullData(implicit stringFormatter: StringFormatter) {

  // one title is missing
  val expectedReadResourceV2ForReiseInsHeiligelandWrong: ReadResourceV2 = ReadResourceV2(
    label = "Reise ins Heilige Land",
    resourceIri = "http://rdfh.ch/2a6221216701",
    permissions =
      "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    userPermission = Permission.ObjectAccess.ChangeRights,
    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
    resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
    projectADM = SharedTestDataADM.incunabulaProject,
    creationDate = Instant.parse("2016-03-02T15:05:21Z"),
    values = Map(
      "http://www.knora.org/ontology/0803/incunabula#physical_desc".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Extent: 1 Bd.; Dimensions: f\u00B0"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/e94fa8a09205",
          valueHasUUID = UuidUtil.decode("e94fa8a09205"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
      ),
      "http://www.knora.org/ontology/0803/incunabula#citation".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Van der Haegen I: 9,14"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/7b4a9bf89305",
          valueHasUUID = UuidUtil.decode("7b4a9bf89305"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Goff M165"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/3e74ee319405",
          valueHasUUID = UuidUtil.decode("3e74ee319405"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("C 3833"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/019e416b9405",
          valueHasUUID = UuidUtil.decode("019e416b9405"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Klebs 651.2"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/c4c794a49405",
          valueHasUUID = UuidUtil.decode("c4c794a49405"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Schr 4799"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/87f1e7dd9405",
          valueHasUUID = UuidUtil.decode("87f1e7dd9405"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Schramm XXI p. 9 & 26"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/4a1b3b179505",
          valueHasUUID = UuidUtil.decode("4a1b3b179505"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("FairMur(G) 283"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/0d458e509505",
          valueHasUUID = UuidUtil.decode("0d458e509505"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("IBP 3556"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/d06ee1899505",
          valueHasUUID = UuidUtil.decode("d06ee1899505"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Borm 1751"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/939834c39505",
          valueHasUUID = UuidUtil.decode("939834c39505"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
      ),
      "http://www.knora.org/ontology/0803/incunabula#publisher".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Bernhard Richel"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/dda85bbb9105",
          valueHasUUID = UuidUtil.decode("dda85bbb9105"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
      ),
      "http://www.knora.org/ontology/0803/incunabula#hasAuthor".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Jean Mandeville"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/1a7f08829105",
          valueHasUUID = UuidUtil.decode("1a7f08829105"),
          permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
      ),
      "http://www.knora.org/ontology/0803/incunabula#book_comment".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Katalogaufnahme anhand ISTC und v.d.Haegen"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/56c287fc9505",
          valueHasUUID = UuidUtil.decode("56c287fc9505"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
      ),
      "http://www.knora.org/ontology/0803/incunabula#url".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some(
              "http://aleph.unibas.ch/F/?local_base=DSV01&con_lng=GER&func=find-b&find_code=SYS&request=002610320",
            ),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/f89173afca2704",
          valueHasUUID = UuidUtil.decode("f89173afca2704"),
          permissions =
            "CR knora-admin:Creator|D knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
      ),
      "http://www.knora.org/ontology/0803/incunabula#note".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("deutsch von Otto von Diemeringen"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/ac79fbd99205",
          valueHasUUID = UuidUtil.decode("ac79fbd99205"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Rubr. mit Init. J zu Beginn"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/6fa34e139305",
          valueHasUUID = UuidUtil.decode("6fa34e139305"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Holzschnitte nicht koloriert"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/32cda14c9305",
          valueHasUUID = UuidUtil.decode("32cda14c9305"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Besitzervermerke: Kartause, H. Zscheckenb\u00FCrlin"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/f5f6f4859305",
          valueHasUUID = UuidUtil.decode("f5f6f4859305"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some(
              "Zusammengebunden mit: Die zehen Gebote ; Was und wie man beten soll und Auslegung des hlg. Pater nosters / Hans von Warmont. Strassburg, 1516",
            ),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/b82048bf9305",
          valueHasUUID = UuidUtil.decode("b82048bf9305"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
      ),
      "http://www.knora.org/ontology/0803/incunabula#location".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Basel UB, Sign: Aleph D III 13:1"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/262655679205",
          valueHasUUID = UuidUtil.decode("262655679205"),
          permissions =
            "CR knora-admin:Creator|D knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
      ),
      "http://www.knora.org/ontology/0803/incunabula#publoc".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Basel"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/a0d2aef49105",
          valueHasUUID = UuidUtil.decode("a0d2aef49105"),
          permissions =
            "CR knora-admin:Creator|V knora-admin:ProjectMember,knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
      ),
      "http://www.knora.org/ontology/0803/incunabula#pubdate".toSmartIri -> Vector(
        ReadOtherValueV2(
          valueContent = DateValueContentV2(
            valueHasEndJDN = 2262358,
            valueHasStartJDN = 2261994,
            ontologySchema = InternalSchema,
            valueHasStartPrecision = DatePrecisionYear,
            valueHasCalendar = CalendarNameJulian,
            comment = None,
            valueHasEndPrecision = DatePrecisionYear,
          ),
          valueIri = "http://rdfh.ch/2a6221216701/values/63fc012e9205",
          valueHasUUID = UuidUtil.decode("63fc012e9205"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
      ),
      "http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Reise ins Heilige Land"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/d1010fd69005",
          valueHasUUID = UuidUtil.decode("d1010fd69005"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Reysen und wanderschafften durch das Gelobte Land"),
            textValueType = TextValueType.UnformattedText,
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/942b620f9105",
          valueHasUUID = UuidUtil.decode("942b620f9105"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = Permission.ObjectAccess.ChangeRights,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None,
        ),
      ),
    ),
    lastModificationDate = None,
    versionDate = None,
    deletionInfo = None,
  )

  val expectedFullResourceResponseForReiseWrong: ReadResourcesSequenceV2 = ReadResourcesSequenceV2(
    resources = Vector(expectedReadResourceV2ForReiseInsHeiligelandWrong),
  )

}
