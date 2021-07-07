package org.knora.webapi.util

import java.time.Instant

import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.PermissionUtilADM.{ChangeRightsPermission, ViewPermission}
import org.knora.webapi.messages.v2.responder.resourcemessages.{ReadResourceV2, ReadResourcesSequenceV2}
import org.knora.webapi.messages.v2.responder.standoffmessages.{
  StandoffDataTypeClasses,
  StandoffTagIriAttributeV2,
  StandoffTagV2
}
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.sharedtestdata.SharedTestDataADM

class ConstructResponseUtilV2SpecFullData(implicit stringFormatter: StringFormatter) {

  val expectedReadResourceForAnythingVisibleThingWithHiddenIntValuesAnythingAdmin = ReadResourcesSequenceV2(
    resources = Vector(
      ReadResourceV2(
        versionDate = None,
        label = "visible thing with hidden int values",
        resourceIri = "http://rdfh.ch/resources/F8L7zPp7TI-4MGJQlCO4Zg",
        permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
        attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceUUID = stringFormatter.decodeUuid("F8L7zPp7TI-4MGJQlCO4Zg"),
        creationDate = Instant.parse("2019-11-29T10:00:00.673298Z"),
        userPermission = ChangeRightsPermission,
        values = Map("http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri -> Vector(
          ReadOtherValueV2(
            valueContent = IntegerValueContentV2(
              ontologySchema = InternalSchema,
              valueHasInteger = 543212345,
              comment = Some("second hidden int value in visible resource")
            ),
            valueIri = "http://rdfh.ch/resources/F8L7zPp7TI-4MGJQlCO4Zg/values/F2xCr0S2QfWRQxJDWY9L0g",
            permissions = "M knora-admin:ProjectMember",
            valueCreationDate = Instant.parse("2019-11-29T10:00:00.673298Z"),
            attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
            previousValueIri = None,
            valueHasUUID = stringFormatter.decodeUuid("F2xCr0S2QfWRQxJDWY9L0g"),
            userPermission = ChangeRightsPermission,
            deletionInfo = None
          ),
          ReadOtherValueV2(
            valueContent = IntegerValueContentV2(
              ontologySchema = InternalSchema,
              valueHasInteger = 123454321,
              comment = Some("first hidden int value in visible resource")
            ),
            valueIri = "http://rdfh.ch/resources/F8L7zPp7TI-4MGJQlCO4Zg/values/yVTqO37cRkCSvXbFc3vTyw",
            permissions = "M knora-admin:ProjectMember",
            valueCreationDate = Instant.parse("2019-11-29T10:00:00.673298Z"),
            attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
            previousValueIri = None,
            valueHasUUID = stringFormatter.decodeUuid("yVTqO37cRkCSvXbFc3vTyw"),
            userPermission = ChangeRightsPermission,
            deletionInfo = None
          )
        )),
        projectADM = SharedTestDataADM.anythingProject,
        lastModificationDate = None,
        deletionInfo = None
      )),
    hiddenResourceIris = Set(),
    mayHaveMoreResults = false
  )

  val expectedReadResourceForAnythingVisibleThingWithHiddenIntValuesIncunabulaUser = ReadResourcesSequenceV2(
    resources = Vector(
      ReadResourceV2(
        versionDate = None,
        label = "visible thing with hidden int values",
        resourceIri = "http://rdfh.ch/resources/F8L7zPp7TI-4MGJQlCO4Zg",
        permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
        attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceUUID = stringFormatter.decodeUuid("F8L7zPp7TI-4MGJQlCO4Zg"),
        creationDate = Instant.parse("2019-11-29T10:00:00.673298Z"),
        userPermission = ViewPermission,
        values = Map(),
        projectADM = SharedTestDataADM.anythingProject,
        lastModificationDate = None,
        deletionInfo = None
      )),
    hiddenResourceIris = Set(),
    mayHaveMoreResults = false
  )

  val expectedReadResourceForAnythingThingWithOneHiddenThingAnythingAdmin = ReadResourcesSequenceV2(
    resources = Vector(
      ReadResourceV2(
        versionDate = None,
        label = "thing with one hidden thing",
        resourceIri = "http://rdfh.ch/resources/0JhgKcqoRIeRRG6ownArSw",
        permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
        attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceUUID = stringFormatter.decodeUuid("0JhgKcqoRIeRRG6ownArSw"),
        creationDate = Instant.parse("2020-04-07T09:12:56.710717Z"),
        userPermission = ChangeRightsPermission,
        values = Map(
          "http://www.knora.org/ontology/0001/anything#hasOtherThingValue".toSmartIri -> Vector(ReadLinkValueV2(
            valueContent = LinkValueContentV2(
              isIncomingLink = false,
              referredResourceIri = "http://rdfh.ch/resources/XTxSMt0ySraVmwXD-bD2wQ",
              ontologySchema = InternalSchema,
              comment = Some("link value pointing to hidden resource"),
              referredResourceExists = true,
              nestedResource = Some(ReadResourceV2(
                versionDate = None,
                label = "hidden thing",
                resourceIri = "http://rdfh.ch/resources/XTxSMt0ySraVmwXD-bD2wQ",
                permissions = "V knora-admin:Creator",
                resourceUUID = stringFormatter.decodeUuid("XTxSMt0ySraVmwXD-bD2wQ"),
                attachedToUser = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                creationDate = Instant.parse("2020-04-07T09:12:56.710717Z"),
                userPermission = ChangeRightsPermission,
                values = Map(),
                projectADM = SharedTestDataADM.anythingProject,
                lastModificationDate = None,
                deletionInfo = None
              ))
            ),
            valueHasRefCount = 1,
            valueIri = "http://rdfh.ch/resources/0JhgKcqoRIeRRG6ownArSw/values/UgSp5mXTTSKdI02ZU1KIAA",
            permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
            valueCreationDate = Instant.parse("2020-04-07T09:12:56.710717Z"),
            attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
            previousValueIri = None,
            valueHasUUID = stringFormatter.decodeUuid("UgSp5mXTTSKdI02ZU1KIAA"),
            userPermission = ChangeRightsPermission,
            deletionInfo = None
          )),
          "http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri -> Vector(ReadOtherValueV2(
            valueContent = IntegerValueContentV2(
              ontologySchema = InternalSchema,
              valueHasInteger = 123454321,
              comment = Some("visible int value in main resource")
            ),
            valueIri = "http://rdfh.ch/resources/0JhgKcqoRIeRRG6ownArSw/values/U1PwfNaVRQebbOSFWNdMqQ",
            permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
            valueCreationDate = Instant.parse("2020-04-07T09:12:56.710717Z"),
            attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
            previousValueIri = None,
            valueHasUUID = stringFormatter.decodeUuid("U1PwfNaVRQebbOSFWNdMqQ"),
            userPermission = ChangeRightsPermission,
            deletionInfo = None
          ))
        ),
        projectADM = SharedTestDataADM.anythingProject,
        lastModificationDate = None,
        deletionInfo = None
      )),
    hiddenResourceIris = Set(),
    mayHaveMoreResults = false
  )

  val expectedReadResourceForAnythingThingWithOneHiddenThingAnonymousUser = ReadResourcesSequenceV2(
    resources = Vector(
      ReadResourceV2(
        versionDate = None,
        label = "thing with one hidden thing",
        resourceIri = "http://rdfh.ch/resources/0JhgKcqoRIeRRG6ownArSw",
        permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
        attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceUUID = stringFormatter.decodeUuid("0JhgKcqoRIeRRG6ownArSw"),
        creationDate = Instant.parse("2020-04-07T09:12:56.710717Z"),
        userPermission = ViewPermission,
        values = Map("http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri -> Vector(ReadOtherValueV2(
          valueContent = IntegerValueContentV2(
            ontologySchema = InternalSchema,
            valueHasInteger = 123454321,
            comment = Some("visible int value in main resource")
          ),
          valueIri = "http://rdfh.ch/resources/0JhgKcqoRIeRRG6ownArSw/values/U1PwfNaVRQebbOSFWNdMqQ",
          permissions = "V knora-admin:UnknownUser|M knora-admin:ProjectMember",
          valueCreationDate = Instant.parse("2020-04-07T09:12:56.710717Z"),
          attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
          previousValueIri = None,
          valueHasUUID = stringFormatter.decodeUuid("U1PwfNaVRQebbOSFWNdMqQ"),
          userPermission = ViewPermission,
          deletionInfo = None
        ))),
        projectADM = SharedTestDataADM.anythingProject,
        lastModificationDate = None,
        deletionInfo = None
      )),
    hiddenResourceIris = Set("http://rdfh.ch/resources/XTxSMt0ySraVmwXD-bD2wQ"),
    mayHaveMoreResults = false
  )

  val expectedReadResourceSequenceV2WithStandoffAnythingAdminUser = ReadResourcesSequenceV2(
    resources = Vector(
      ReadResourceV2(
        versionDate = None,
        label = "Ein Ding f\u00FCr jemanden, dem die Dinge gefallen",
        resourceIri = "http://rdfh.ch/resources/jT0UHG9_wtaX23VoYydmGg",
        permissions = "CR knora-admin:Creator|V knora-admin:ProjectMember",
        attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceUUID = stringFormatter.decodeUuid("jT0UHG9_wtaX23VoYydmGg"),
        creationDate = Instant.parse("2016-03-02T15:05:10Z"),
        userPermission = ChangeRightsPermission,
        values = Map(
          "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri -> Vector(
            ReadTextValueV2(
              valueContent = TextValueContentV2(
                standoff = Vector(
                  StandoffTagV2(
                    endParentIndex = None,
                    originalXMLID = None,
                    uuid = stringFormatter.decodeUuid("2e136103-2a4b-4e59-ac8f-79a53f54b496"),
                    endPosition = 45,
                    startParentIndex = None,
                    attributes = Nil,
                    startIndex = 0,
                    endIndex = None,
                    dataType = None,
                    startPosition = 0,
                    standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag".toSmartIri
                  ),
                  StandoffTagV2(
                    endParentIndex = None,
                    originalXMLID = None,
                    uuid = stringFormatter.decodeUuid("80133696-26a1-4941-967b-6bf210d7d5e1"),
                    endPosition = 19,
                    startParentIndex = Some(0),
                    attributes = Vector(StandoffTagIriAttributeV2(
                      standoffPropertyIri = "http://www.knora.org/ontology/knora-base#standoffTagHasLink".toSmartIri,
                      value = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA",
                      targetExists = true
                    )),
                    startIndex = 1,
                    endIndex = None,
                    dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
                    startPosition = 14,
                    standoffTagClassIri = "http://www.knora.org/ontology/knora-base#StandoffLinkTag".toSmartIri
                  )
                ),
                mapping = None,
                valueHasLanguage = None,
                ontologySchema = InternalSchema,
                maybeValueHasString = Some("Ich liebe die Dinge, sie sind alles f\u00FCr mich."),
                comment = None,
                xslt = None,
                mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping")
              ),
              valueIri = "http://rdfh.ch/resources/jT0UHG9_wtaX23VoYydmGg/values/1",
              permissions = "CR knora-admin:Creator",
              valueCreationDate = Instant.parse("2016-03-02T15:05:54Z"),
              valueHasMaxStandoffStartIndex = Some(1),
              attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
              previousValueIri = None,
              valueHasUUID = stringFormatter.decodeUuid("1"),
              userPermission = ChangeRightsPermission,
              deletionInfo = None
            ),
            ReadTextValueV2(
              valueContent = TextValueContentV2(
                standoff = Vector(
                  StandoffTagV2(
                    endParentIndex = None,
                    originalXMLID = None,
                    uuid = stringFormatter.decodeUuid("fd583868-2a3c-4941-a330-990f5a972f71"),
                    endPosition = 25,
                    startParentIndex = None,
                    attributes = Nil,
                    startIndex = 0,
                    endIndex = None,
                    dataType = None,
                    startPosition = 0,
                    standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag".toSmartIri
                  ),
                  StandoffTagV2(
                    endParentIndex = None,
                    originalXMLID = None,
                    uuid = stringFormatter.decodeUuid("59a36237-95a9-4acc-8361-7c8fac311063"),
                    endPosition = 16,
                    startParentIndex = Some(0),
                    attributes = Vector(StandoffTagIriAttributeV2(
                      standoffPropertyIri = "http://www.knora.org/ontology/knora-base#standoffTagHasLink".toSmartIri,
                      value = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA",
                      targetExists = true
                    )),
                    startIndex = 1,
                    endIndex = None,
                    dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
                    startPosition = 11,
                    standoffTagClassIri = "http://www.knora.org/ontology/knora-base#StandoffLinkTag".toSmartIri
                  )
                ),
                mapping = None,
                valueHasLanguage = None,
                ontologySchema = InternalSchema,
                maybeValueHasString = Some("Na ja, die Dinge sind OK."),
                comment = None,
                xslt = None,
                mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
              ),
              valueIri = "http://rdfh.ch/resources/jT0UHG9_wtaX23VoYydmGg/values/2",
              permissions = "CR knora-admin:Creator",
              valueCreationDate = Instant.parse("2016-03-02T15:05:54Z"),
              valueHasMaxStandoffStartIndex = Some(1),
              attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
              previousValueIri = None,
              valueHasUUID = stringFormatter.decodeUuid("2"),
              userPermission = ChangeRightsPermission,
              deletionInfo = None
            )
          ),
          "http://www.knora.org/ontology/knora-base#hasStandoffLinkToValue".toSmartIri -> Vector(ReadLinkValueV2(
            valueContent = LinkValueContentV2(
              isIncomingLink = false,
              referredResourceIri = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA",
              ontologySchema = InternalSchema,
              comment = None,
              referredResourceExists = true,
              nestedResource = Some(ReadResourceV2(
                versionDate = None,
                label = "A thing",
                resourceIri = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA",
                permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:UnknownUser",
                attachedToUser = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceUUID = stringFormatter.decodeUuid("SHnkVt4X2LHAM2nNZVwkoA"),
                creationDate = Instant.parse("2016-03-02T15:05:10Z"),
                userPermission = ChangeRightsPermission,
                values = Map(),
                projectADM = SharedTestDataADM.anythingProject,
                lastModificationDate = None,
                deletionInfo = None
              ))
            ),
            valueHasRefCount = 2,
            valueIri = "http://rdfh.ch/resources/jT0UHG9_wtaX23VoYydmGg/values/0",
            permissions = "CR knora-admin:Creator|V knora-admin:UnknownUser",
            valueCreationDate = Instant.parse("2016-03-02T15:05:54Z"),
            attachedToUser = "http://www.knora.org/ontology/knora-admin#SystemUser",
            previousValueIri = None,
            valueHasUUID = stringFormatter.decodeUuid("0"),
            userPermission = ChangeRightsPermission,
            deletionInfo = None
          ))
        ),
        projectADM = SharedTestDataADM.anythingProject,
        lastModificationDate = None,
        deletionInfo = None
      )),
    hiddenResourceIris = Set(),
    mayHaveMoreResults = false
  )

  val expectedReadResourceSequenceV2ForMainQuery1 = ReadResourcesSequenceV2(
    resources = Vector(
      ReadResourceV2(
        versionDate = None,
        label = "a5v",
        resourceIri = "http://rdfh.ch/resources/I5qjztyxx63BGvfAjr5ZKQ",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceUUID = stringFormatter.decodeUuid("I5qjztyxx63BGvfAjr5ZKQ"),
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        creationDate = Instant.parse("2016-03-02T15:05:23Z"),
        userPermission = ChangeRightsPermission,
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#partOfValue".toSmartIri -> Vector(
            ReadLinkValueV2(
              valueContent = LinkValueContentV2(
                isIncomingLink = false,
                referredResourceIri = "http://rdfh.ch/resources/i4egXDOr2dZR3JRcdlapSQ",
                ontologySchema = InternalSchema,
                comment = None,
                referredResourceExists = true,
                nestedResource = Some(ReadResourceV2(
                  versionDate = None,
                  label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
                  resourceIri = "http://rdfh.ch/resources/i4egXDOr2dZR3JRcdlapSQ",
                  resourceUUID = stringFormatter.decodeUuid("i4egXDOr2dZR3JRcdlapSQ"),
                  permissions =
                    "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
                  attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                  resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
                  creationDate = Instant.parse("2016-03-02T15:05:23Z"),
                  userPermission = ChangeRightsPermission,
                  values =
                    Map("http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Vector(ReadTextValueV2(
                      valueContent = TextValueContentV2(
                        valueHasLanguage = None,
                        ontologySchema = InternalSchema,
                        maybeValueHasString = Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi"),
                        comment = None
                      ),
                      valueIri = "http://rdfh.ch/resources/i4egXDOr2dZR3JRcdlapSQ/values/d9a522845006",
                      permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                      valueCreationDate = Instant.parse("2016-03-02T15:05:23Z"),
                      valueHasMaxStandoffStartIndex = None,
                      attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                      previousValueIri = None,
                      valueHasUUID = stringFormatter.decodeUuid("d9a522845006"),
                      userPermission = ChangeRightsPermission,
                      deletionInfo = None
                    ))),
                  projectADM = SharedTestDataADM.incunabulaProject,
                  lastModificationDate = None,
                  deletionInfo = None
                ))
              ),
              valueHasRefCount = 1,
              valueIri = "http://rdfh.ch/resources/I5qjztyxx63BGvfAjr5ZKQ/values/bbd4d6a9-8b73-4670-b0cd-e851cd0a7c5d",
              permissions =
                "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
              valueCreationDate = Instant.parse("2016-03-02T15:05:23Z"),
              attachedToUser = "http://rdfh.ch/users/91e19f1e01",
              previousValueIri = None,
              valueHasUUID = stringFormatter.decodeUuid("bbd4d6a9-8b73-4670-b0cd-e851cd0a7c5d"),
              userPermission = ChangeRightsPermission,
              deletionInfo = None
            )),
          "http://www.knora.org/ontology/0803/incunabula#seqnum".toSmartIri -> Vector(
            ReadOtherValueV2(
              valueContent = IntegerValueContentV2(
                ontologySchema = InternalSchema,
                valueHasInteger = 10,
                comment = None
              ),
              valueIri = "http://rdfh.ch/resources/I5qjztyxx63BGvfAjr5ZKQ/values/fae17f4f6106",
              permissions =
                "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
              valueCreationDate = Instant.parse("2016-03-02T15:05:23Z"),
              attachedToUser = "http://rdfh.ch/users/91e19f1e01",
              previousValueIri = None,
              valueHasUUID = stringFormatter.decodeUuid("fae17f4f6106"),
              userPermission = ChangeRightsPermission,
              deletionInfo = None
            ))
        ),
        projectADM = SharedTestDataADM.incunabulaProject,
        lastModificationDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        versionDate = None,
        label = "a5v",
        resourceIri = "http://rdfh.ch/resources/aW-UJ8Hd_gEXDoWD1Da5wQ",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceUUID = stringFormatter.decodeUuid("aW-UJ8Hd_gEXDoWD1Da5wQ"),
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
        creationDate = Instant.parse("2016-03-02T15:05:10Z"),
        userPermission = ChangeRightsPermission,
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#partOfValue".toSmartIri -> Vector(
            ReadLinkValueV2(
              valueContent = LinkValueContentV2(
                isIncomingLink = false,
                referredResourceIri = "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw",
                ontologySchema = InternalSchema,
                comment = None,
                referredResourceExists = true,
                nestedResource = Some(ReadResourceV2(
                  versionDate = None,
                  label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
                  resourceIri = "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw",
                  permissions =
                    "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
                  attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                  resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
                  creationDate = Instant.parse("2016-03-02T15:05:10Z"),
                  resourceUUID = stringFormatter.decodeUuid("7dGkt1CLKdZbrxVj324eaw"),
                  userPermission = ChangeRightsPermission,
                  values =
                    Map("http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Vector(ReadTextValueV2(
                      valueContent = TextValueContentV2(
                        valueHasLanguage = None,
                        ontologySchema = InternalSchema,
                        maybeValueHasString = Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi"),
                        comment = None
                      ),
                      valueIri = "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw/values/c3295339",
                      permissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
                      valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
                      valueHasMaxStandoffStartIndex = None,
                      attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                      previousValueIri = None,
                      valueHasUUID = stringFormatter.decodeUuid("c3295339"),
                      userPermission = ChangeRightsPermission,
                      deletionInfo = None
                    ))),
                  projectADM = SharedTestDataADM.incunabulaProject,
                  lastModificationDate = None,
                  deletionInfo = None
                ))
              ),
              valueHasRefCount = 1,
              valueIri = "http://rdfh.ch/resources/aW-UJ8Hd_gEXDoWD1Da5wQ/values/25c5e9fd-2cb2-4350-88bb-882be3373745",
              permissions =
                "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
              valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
              attachedToUser = "http://rdfh.ch/users/91e19f1e01",
              previousValueIri = None,
              valueHasUUID = stringFormatter.decodeUuid("25c5e9fd-2cb2-4350-88bb-882be3373745"),
              userPermission = ChangeRightsPermission,
              deletionInfo = None
            )),
          "http://www.knora.org/ontology/0803/incunabula#seqnum".toSmartIri -> Vector(
            ReadOtherValueV2(
              valueContent = IntegerValueContentV2(
                ontologySchema = InternalSchema,
                valueHasInteger = 10,
                comment = None
              ),
              valueIri = "http://rdfh.ch/resources/aW-UJ8Hd_gEXDoWD1Da5wQ/values/53feeaf80a",
              permissions =
                "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
              valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
              attachedToUser = "http://rdfh.ch/users/91e19f1e01",
              previousValueIri = None,
              valueHasUUID = stringFormatter.decodeUuid("53feeaf80a"),
              userPermission = ChangeRightsPermission,
              deletionInfo = None
            ))
        ),
        projectADM = SharedTestDataADM.incunabulaProject,
        lastModificationDate = None,
        deletionInfo = None
      )
    ),
    hiddenResourceIris = Set(),
    mayHaveMoreResults = false
  )

  val expectedReadResourceSequenceV2ForMainQuery2 = ReadResourcesSequenceV2(
    resources = Vector(
      ReadResourceV2(
        versionDate = None,
        label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
        resourceIri = "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
        creationDate = Instant.parse("2016-03-02T15:05:10Z"),
        resourceUUID = stringFormatter.decodeUuid("7dGkt1CLKdZbrxVj324eaw"),
        userPermission = ChangeRightsPermission,
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Vector(
            ReadTextValueV2(
              valueContent = TextValueContentV2(
                valueHasLanguage = None,
                ontologySchema = InternalSchema,
                maybeValueHasString = Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi"),
                comment = None
              ),
              valueIri = "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw/values/c3295339",
              permissions =
                "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
              valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
              valueHasMaxStandoffStartIndex = None,
              attachedToUser = "http://rdfh.ch/users/91e19f1e01",
              previousValueIri = None,
              valueHasUUID = stringFormatter.decodeUuid("c3295339"),
              userPermission = ChangeRightsPermission,
              deletionInfo = None
            )),
          "http://www.knora.org/ontology/knora-base#hasIncomingLinkValue".toSmartIri -> Vector(
            ReadLinkValueV2(
              valueContent = LinkValueContentV2(
                isIncomingLink = true,
                referredResourceIri = "http://rdfh.ch/resources/aW-UJ8Hd_gEXDoWD1Da5wQ",
                ontologySchema = InternalSchema,
                comment = None,
                referredResourceExists = true,
                nestedResource = Some(ReadResourceV2(
                  versionDate = None,
                  label = "a5v",
                  resourceIri = "http://rdfh.ch/resources/aW-UJ8Hd_gEXDoWD1Da5wQ",
                  permissions =
                    "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
                  attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                  resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                  creationDate = Instant.parse("2016-03-02T15:05:10Z"),
                  resourceUUID = stringFormatter.decodeUuid("aW-UJ8Hd_gEXDoWD1Da5wQ"),
                  userPermission = ChangeRightsPermission,
                  values = Map(
                    "http://www.knora.org/ontology/0803/incunabula#partOfValue".toSmartIri -> Vector(ReadLinkValueV2(
                      valueContent = LinkValueContentV2(
                        isIncomingLink = false,
                        referredResourceIri = "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw",
                        ontologySchema = InternalSchema,
                        comment = None,
                        referredResourceExists = true,
                        nestedResource = None
                      ),
                      valueHasRefCount = 1,
                      valueIri =
                        "http://rdfh.ch/resources/aW-UJ8Hd_gEXDoWD1Da5wQ/values/25c5e9fd-2cb2-4350-88bb-882be3373745",
                      permissions = "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
                      valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
                      attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                      previousValueIri = None,
                      valueHasUUID = stringFormatter.decodeUuid("25c5e9fd-2cb2-4350-88bb-882be3373745"),
                      userPermission = ChangeRightsPermission,
                      deletionInfo = None
                    )),
                    "http://www.knora.org/ontology/0803/incunabula#seqnum".toSmartIri -> Vector(ReadOtherValueV2(
                      valueContent = IntegerValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasInteger = 10,
                        comment = None
                      ),
                      valueIri = "http://rdfh.ch/resources/aW-UJ8Hd_gEXDoWD1Da5wQ/values/53feeaf80a",
                      permissions = "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
                      valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
                      attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                      previousValueIri = None,
                      valueHasUUID = stringFormatter.decodeUuid("53feeaf80a"),
                      userPermission = ChangeRightsPermission,
                      deletionInfo = None
                    ))
                  ),
                  projectADM = SharedTestDataADM.incunabulaProject,
                  lastModificationDate = None,
                  deletionInfo = None
                ))
              ),
              valueHasRefCount = 1,
              valueIri = "http://rdfh.ch/resources/aW-UJ8Hd_gEXDoWD1Da5wQ/values/25c5e9fd-2cb2-4350-88bb-882be3373745",
              permissions =
                "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
              valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
              attachedToUser = "http://rdfh.ch/users/91e19f1e01",
              previousValueIri = None,
              valueHasUUID = stringFormatter.decodeUuid("25c5e9fd-2cb2-4350-88bb-882be3373745"),
              userPermission = ChangeRightsPermission,
              deletionInfo = None
            ))
        ),
        projectADM = SharedTestDataADM.incunabulaProject,
        lastModificationDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        versionDate = None,
        label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
        resourceIri = "http://rdfh.ch/resources/i4egXDOr2dZR3JRcdlapSQ",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
        creationDate = Instant.parse("2016-03-02T15:05:23Z"),
        resourceUUID = stringFormatter.decodeUuid("i4egXDOr2dZR3JRcdlapSQ"),
        userPermission = ChangeRightsPermission,
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Vector(
            ReadTextValueV2(
              valueContent = TextValueContentV2(
                valueHasLanguage = None,
                ontologySchema = InternalSchema,
                maybeValueHasString = Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi"),
                comment = None
              ),
              valueIri = "http://rdfh.ch/resources/i4egXDOr2dZR3JRcdlapSQ/values/d9a522845006",
              permissions =
                "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
              valueCreationDate = Instant.parse("2016-03-02T15:05:23Z"),
              valueHasMaxStandoffStartIndex = None,
              attachedToUser = "http://rdfh.ch/users/91e19f1e01",
              previousValueIri = None,
              valueHasUUID = stringFormatter.decodeUuid("d9a522845006"),
              userPermission = ChangeRightsPermission,
              deletionInfo = None
            )),
          "http://www.knora.org/ontology/knora-base#hasIncomingLinkValue".toSmartIri -> Vector(
            ReadLinkValueV2(
              valueContent = LinkValueContentV2(
                isIncomingLink = true,
                referredResourceIri = "http://rdfh.ch/resources/I5qjztyxx63BGvfAjr5ZKQ",
                ontologySchema = InternalSchema,
                comment = None,
                referredResourceExists = true,
                nestedResource = Some(ReadResourceV2(
                  versionDate = None,
                  label = "a5v",
                  resourceIri = "http://rdfh.ch/resources/I5qjztyxx63BGvfAjr5ZKQ",
                  permissions =
                    "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
                  attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                  resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#page".toSmartIri,
                  creationDate = Instant.parse("2016-03-02T15:05:23Z"),
                  resourceUUID = stringFormatter.decodeUuid("I5qjztyxx63BGvfAjr5ZKQ"),
                  userPermission = ChangeRightsPermission,
                  values = Map(
                    "http://www.knora.org/ontology/0803/incunabula#partOfValue".toSmartIri -> Vector(ReadLinkValueV2(
                      valueContent = LinkValueContentV2(
                        isIncomingLink = false,
                        referredResourceIri = "http://rdfh.ch/resources/i4egXDOr2dZR3JRcdlapSQ",
                        ontologySchema = InternalSchema,
                        comment = None,
                        referredResourceExists = true,
                        nestedResource = None
                      ),
                      valueHasRefCount = 1,
                      valueIri =
                        "http://rdfh.ch/resources/I5qjztyxx63BGvfAjr5ZKQ/values/bbd4d6a9-8b73-4670-b0cd-e851cd0a7c5d",
                      permissions = "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
                      valueCreationDate = Instant.parse("2016-03-02T15:05:23Z"),
                      attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                      previousValueIri = None,
                      valueHasUUID = stringFormatter.decodeUuid("bbd4d6a9-8b73-4670-b0cd-e851cd0a7c5d"),
                      userPermission = ChangeRightsPermission,
                      deletionInfo = None
                    )),
                    "http://www.knora.org/ontology/0803/incunabula#seqnum".toSmartIri -> Vector(ReadOtherValueV2(
                      valueContent = IntegerValueContentV2(
                        ontologySchema = InternalSchema,
                        valueHasInteger = 10,
                        comment = None
                      ),
                      valueIri = "http://rdfh.ch/resources/I5qjztyxx63BGvfAjr5ZKQ/values/fae17f4f6106",
                      permissions = "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
                      valueCreationDate = Instant.parse("2016-03-02T15:05:23Z"),
                      attachedToUser = "http://rdfh.ch/users/91e19f1e01",
                      previousValueIri = None,
                      valueHasUUID = stringFormatter.decodeUuid("fae17f4f6106"),
                      userPermission = ChangeRightsPermission,
                      deletionInfo = None
                    ))
                  ),
                  projectADM = SharedTestDataADM.incunabulaProject,
                  lastModificationDate = None,
                  deletionInfo = None
                ))
              ),
              valueHasRefCount = 1,
              valueIri = "http://rdfh.ch/resources/I5qjztyxx63BGvfAjr5ZKQ/values/bbd4d6a9-8b73-4670-b0cd-e851cd0a7c5d",
              permissions =
                "CR knora-admin:Creator|V knora-admin:UnknownUser,knora-admin:KnownUser,knora-admin:ProjectMember",
              valueCreationDate = Instant.parse("2016-03-02T15:05:23Z"),
              attachedToUser = "http://rdfh.ch/users/91e19f1e01",
              previousValueIri = None,
              valueHasUUID = stringFormatter.decodeUuid("bbd4d6a9-8b73-4670-b0cd-e851cd0a7c5d"),
              userPermission = ChangeRightsPermission,
              deletionInfo = None
            ))
        ),
        projectADM = SharedTestDataADM.incunabulaProject,
        lastModificationDate = None,
        deletionInfo = None
      )
    ),
    hiddenResourceIris = Set(),
    mayHaveMoreResults = false
  )
}
