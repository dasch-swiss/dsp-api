package org.knora.webapi.responders.v2

import java.time.Instant

import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.PermissionUtilADM._
import org.knora.webapi.messages.util.{CalendarNameJulian, DatePrecisionYear}
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.sharedtestdata.SharedTestDataADM

// FIXME: Rename to something without spec in the name since it is not a spec
class ResourcesResponseCheckerV2SpecFullData(implicit stringFormatter: StringFormatter) {

  // one title is missing
  val expectedReadResourceV2ForReiseInsHeiligelandWrong: ReadResourceV2 = ReadResourceV2(
    label = "Reise ins Heilige Land",
    resourceIri = "http://rdfh.ch/2a6221216701",
    permissions =
      "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    userPermission = ChangeRightsPermission,
    attachedToUser = "http://rdfh.ch/users/91e19f1e01",
    resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
    projectADM = SharedTestDataADM.incunabulaProject,
    creationDate = Instant.parse("2016-03-02T15:05:21Z"),
    resourceUUID = stringFormatter.decodeUuid("w1JN2mMZam7F1_eiyvz6pw"),
    values = Map(
      "http://www.knora.org/ontology/0803/incunabula#physical_desc".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Extent: 1 Bd.; Dimensions: f\u00B0")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/e94fa8a09205",
          valueHasUUID = stringFormatter.decodeUuid("e94fa8a09205"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        )),
      "http://www.knora.org/ontology/0803/incunabula#citation".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Van der Haegen I: 9,14")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/7b4a9bf89305",
          valueHasUUID = stringFormatter.decodeUuid("7b4a9bf89305"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Goff M165")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/3e74ee319405",
          valueHasUUID = stringFormatter.decodeUuid("3e74ee319405"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("C 3833")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/019e416b9405",
          valueHasUUID = stringFormatter.decodeUuid("019e416b9405"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Klebs 651.2")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/c4c794a49405",
          valueHasUUID = stringFormatter.decodeUuid("c4c794a49405"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Schr 4799")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/87f1e7dd9405",
          valueHasUUID = stringFormatter.decodeUuid("87f1e7dd9405"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Schramm XXI p. 9 & 26")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/4a1b3b179505",
          valueHasUUID = stringFormatter.decodeUuid("4a1b3b179505"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("FairMur(G) 283")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/0d458e509505",
          valueHasUUID = stringFormatter.decodeUuid("0d458e509505"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("IBP 3556")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/d06ee1899505",
          valueHasUUID = stringFormatter.decodeUuid("d06ee1899505"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Borm 1751")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/939834c39505",
          valueHasUUID = stringFormatter.decodeUuid("939834c39505"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        )
      ),
      "http://www.knora.org/ontology/0803/incunabula#publisher".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Bernhard Richel")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/dda85bbb9105",
          valueHasUUID = stringFormatter.decodeUuid("dda85bbb9105"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        )),
      "http://www.knora.org/ontology/0803/incunabula#hasAuthor".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Jean Mandeville")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/1a7f08829105",
          valueHasUUID = stringFormatter.decodeUuid("1a7f08829105"),
          permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        )),
      "http://www.knora.org/ontology/0803/incunabula#book_comment".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Katalogaufnahme anhand ISTC und v.d.Haegen")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/56c287fc9505",
          valueHasUUID = stringFormatter.decodeUuid("56c287fc9505"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:21Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        )),
      "http://www.knora.org/ontology/0803/incunabula#url".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString =
              Some("http://aleph.unibas.ch/F/?local_base=DSV01&con_lng=GER&func=find-b&find_code=SYS&request=002610320")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/f89173afca2704",
          valueHasUUID = stringFormatter.decodeUuid("f89173afca2704"),
          permissions =
            "CR knora-admin:Creator|D knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        )),
      "http://www.knora.org/ontology/0803/incunabula#note".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("deutsch von Otto von Diemeringen")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/ac79fbd99205",
          valueHasUUID = stringFormatter.decodeUuid("ac79fbd99205"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Rubr. mit Init. J zu Beginn")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/6fa34e139305",
          valueHasUUID = stringFormatter.decodeUuid("6fa34e139305"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Holzschnitte nicht koloriert")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/32cda14c9305",
          valueHasUUID = stringFormatter.decodeUuid("32cda14c9305"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Besitzervermerke: Kartause, H. Zscheckenb\u00FCrlin")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/f5f6f4859305",
          valueHasUUID = stringFormatter.decodeUuid("f5f6f4859305"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some(
              "Zusammengebunden mit: Die zehen Gebote ; Was und wie man beten soll und Auslegung des hlg. Pater nosters / Hans von Warmont. Strassburg, 1516")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/b82048bf9305",
          valueHasUUID = stringFormatter.decodeUuid("b82048bf9305"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        )
      ),
      "http://www.knora.org/ontology/0803/incunabula#location".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Basel UB, Sign: Aleph D III 13:1")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/262655679205",
          valueHasUUID = stringFormatter.decodeUuid("262655679205"),
          permissions =
            "CR knora-admin:Creator|D knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        )),
      "http://www.knora.org/ontology/0803/incunabula#publoc".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Basel")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/a0d2aef49105",
          valueHasUUID = stringFormatter.decodeUuid("a0d2aef49105"),
          permissions =
            "CR knora-admin:Creator|V knora-admin:ProjectMember,knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        )),
      "http://www.knora.org/ontology/0803/incunabula#pubdate".toSmartIri -> Vector(
        ReadOtherValueV2(
          valueContent = DateValueContentV2(
            valueHasEndJDN = 2262358,
            valueHasStartJDN = 2261994,
            ontologySchema = InternalSchema,
            valueHasStartPrecision = DatePrecisionYear,
            valueHasCalendar = CalendarNameJulian,
            comment = None,
            valueHasEndPrecision = DatePrecisionYear
          ),
          valueIri = "http://rdfh.ch/2a6221216701/values/63fc012e9205",
          valueHasUUID = stringFormatter.decodeUuid("63fc012e9205"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        )),
      "http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Vector(
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Reise ins Heilige Land")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/d1010fd69005",
          valueHasUUID = stringFormatter.decodeUuid("d1010fd69005"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        ),
        ReadTextValueV2(
          valueContent = TextValueContentV2(
            ontologySchema = InternalSchema,
            valueHasLanguage = None,
            comment = None,
            maybeValueHasString = Some("Reysen und wanderschafften durch das Gelobte Land")
          ),
          valueHasMaxStandoffStartIndex = None,
          valueIri = "http://rdfh.ch/2a6221216701/values/942b620f9105",
          valueHasUUID = stringFormatter.decodeUuid("942b620f9105"),
          permissions =
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          userPermission = ChangeRightsPermission,
          valueCreationDate = Instant.parse("2016-03-02T15:05:20Z"),
          attachedToUser = "http://rdfh.ch/users/91e19f1e01",
          previousValueIri = None,
          deletionInfo = None
        )
      )
    ),
    lastModificationDate = None,
    versionDate = None,
    deletionInfo = None
  )

  val expectedFullResourceResponseForReiseWrong: ReadResourcesSequenceV2 = ReadResourcesSequenceV2(
    resources = Vector(expectedReadResourceV2ForReiseInsHeiligelandWrong)
  )

}
