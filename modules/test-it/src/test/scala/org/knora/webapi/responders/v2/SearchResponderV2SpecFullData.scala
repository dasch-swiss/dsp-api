/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.apache.pekko

import java.time.Instant

import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.Permission

import pekko.actor.ActorSystem

class SearchResponderV2SpecFullData(implicit stringFormatter: StringFormatter) {

  implicit lazy val system: ActorSystem = ActorSystem("webapi")

  val booksBookIri: String     = "http://www.knora.org/ontology/0001/books#Book"
  val booksHasTextType: String = "http://www.knora.org/ontology/0001/books#hasTextType"
  val testUser1: String        = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q"
  val testUser2: String        = "http://rdfh.ch/users/BhkfBc3hTeS_IDo-JgXRbQ"
  val bookTemplateReadResource: ReadResourceV2 = ReadResourceV2(
    label = "",
    resourceIri = "",
    permissions =
      "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    userPermission = Permission.ObjectAccess.ChangeRights,
    attachedToUser = testUser1,
    resourceClassIri = booksBookIri.toSmartIri,
    projectADM = SharedTestDataADM.anythingProject,
    creationDate = Instant.parse("2019-11-29T10:00:00.673298Z"),
    values = Map(),
    lastModificationDate = None,
    versionDate = None,
    deletionInfo = None,
  )
  val listValueTemplateReadOtherValue: ReadOtherValueV2 = ReadOtherValueV2(
    valueContent = HierarchicalListValueContentV2(InternalSchema, "", None, None),
    valueIri = "",
    valueHasUUID = UuidUtil.decode("d34d34d3-4d34-d34d-3496-2b2dfef6a5b9"),
    permissions =
      "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    userPermission = Permission.ObjectAccess.Modify,
    previousValueIri = None,
    valueCreationDate = Instant.parse("2018-05-29T16:42:04.381Z"),
    attachedToUser = testUser2,
    deletionInfo = None,
  )

  val constructQueryForBooksWithTitleZeitgloecklein: ConstructQuery = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
          XsdLiteral("true", "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri),
        ),
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
          QueryVariable("title"),
        ),
      ),
      querySchema = Some(ApiV2Simple),
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri, None),
        ),
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
        ),
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
          QueryVariable("title"),
        ),
        StatementPattern(
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri, None),
          IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
        ),
        StatementPattern(
          QueryVariable("title"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
        ),
        FilterPattern(
          CompareExpression(
            QueryVariable("title"),
            CompareExpressionOperator.EQUALS,
            XsdLiteral(
              "Zeitglöcklein des Lebens und Leidens Christi",
              "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
            ),
          ),
        ),
      ),
      positiveEntities = Set(
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri, None),
        IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri, None),
        IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
        IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
        IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
        QueryVariable("book"),
        QueryVariable("title"),
      ),
      querySchema = Some(ApiV2Simple),
    ),
    querySchema = Some(ApiV2Simple),
  )

  val booksWithTitleZeitgloeckleinResponse: ReadResourcesSequenceV2 = ReadResourcesSequenceV2(
    resources = Vector(
      ReadResourceV2(
        label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
        resourceIri = "http://rdfh.ch/0803/c5058f3a",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = Permission.ObjectAccess.RestrictedView,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:10Z"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Vector(
            ReadTextValueV2(
              valueContent = TextValueContentV2(
                ontologySchema = InternalSchema,
                valueHasLanguage = None,
                comment = None,
                maybeValueHasString = Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi"),
                textValueType = TextValueType.UnformattedText,
              ),
              valueHasMaxStandoffStartIndex = None,
              valueIri = "http://rdfh.ch/0803/c5058f3a/values/c3295339",
              valueHasUUID = UuidUtil.decode("c3295339"),
              permissions =
                "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
              userPermission = Permission.ObjectAccess.View,
              previousValueIri = None,
              valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
              attachedToUser = "http://rdfh.ch/users/91e19f1e01",
              deletionInfo = None,
            ),
          ),
        ),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None,
      ),
      ReadResourceV2(
        label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
        resourceIri = "http://rdfh.ch/0803/ff17e5ef9601",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = Permission.ObjectAccess.RestrictedView,
        attachedToUser = "http://rdfh.ch/users/91e19f1e01",
        resourceClassIri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
        projectADM = SharedTestDataADM.incunabulaProject,
        creationDate = Instant.parse("2016-03-02T15:05:23Z"),
        values = Map(
          "http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Vector(
            ReadTextValueV2(
              valueContent = TextValueContentV2(
                ontologySchema = InternalSchema,
                valueHasLanguage = None,
                comment = None,
                maybeValueHasString = Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi"),
                textValueType = TextValueType.UnformattedText,
              ),
              valueHasMaxStandoffStartIndex = None,
              valueIri = "http://rdfh.ch/0803/ff17e5ef9601/values/d9a522845006",
              valueHasUUID = UuidUtil.decode("d9a522845006"),
              permissions =
                "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
              userPermission = Permission.ObjectAccess.View,
              previousValueIri = None,
              valueCreationDate = Instant.parse("2016-03-02T15:05:23Z"),
              attachedToUser = "http://rdfh.ch/users/91e19f1e01",
              deletionInfo = None,
            ),
          ),
        ),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None,
      ),
    ),
  )

  val constructQueryForBooksWithoutTitleZeitgloecklein: ConstructQuery = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
          XsdLiteral("true", "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri),
        ),
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
          QueryVariable("title"),
        ),
      ),
      querySchema = Some(ApiV2Simple),
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri, None),
        ),
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
        ),
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
          QueryVariable("title"),
        ),
        StatementPattern(
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri, None),
          IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
        ),
        StatementPattern(
          QueryVariable("title"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
        ),
        FilterPattern(
          CompareExpression(
            QueryVariable("title"),
            CompareExpressionOperator.NOT_EQUALS,
            XsdLiteral(
              "Zeitglöcklein des Lebens und Leidens Christi",
              "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
            ),
          ),
        ),
      ),
      positiveEntities = Set(
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri, None),
        IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri, None),
        IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
        IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
        IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
        QueryVariable("book"),
        QueryVariable("title"),
      ),
      querySchema = Some(ApiV2Simple),
    ),
    querySchema = Some(ApiV2Simple),
  )

  val expectedResultFulltextSearchForListNodeLabel: ReadResourcesSequenceV2 = ReadResourcesSequenceV2(
    resources = Vector(
      bookTemplateReadResource.copy(
        label = "instance of a book with a list value",
        resourceIri = "http://rdfh.ch/0001/book-instance-02",
        values = Map(
          booksHasTextType.toSmartIri -> Vector(
            listValueTemplateReadOtherValue.copy(
              valueContent =
                HierarchicalListValueContentV2(InternalSchema, "http://rdfh.ch/lists/0001/ynm02-03", None, None),
              valueIri = "http://rdfh.ch/0001/book-instance-02/values/has-list-value-01",
              valueHasUUID = UuidUtil.decode("d34d34d3-4d34-d34d-3496-2b2dfef6a5b9"),
            ),
          ),
        ),
      ),
    ),
  )

  val expectedResultFulltextSearchForListNodeLabelWithSubnodes: ReadResourcesSequenceV2 = ReadResourcesSequenceV2(
    resources = Vector(
      bookTemplateReadResource.copy(
        label = "Lord of the Rings",
        resourceIri = "http://rdfh.ch/0001/book-instance-03",
        values = Map(
          booksHasTextType.toSmartIri -> Vector(
            listValueTemplateReadOtherValue.copy(
              valueContent =
                HierarchicalListValueContentV2(InternalSchema, "http://rdfh.ch/lists/0001/ynm02-04", None, None),
              valueIri = "http://rdfh.ch/0001/book-instance-03/values/has-list-value-02",
              valueHasUUID = UuidUtil.decode("d34d3496-2b2d-fef6-a5b9-efdf6a7b5ab3"),
            ),
          ),
        ),
      ),
      bookTemplateReadResource.copy(
        label = "Treasure Island",
        resourceIri = "http://rdfh.ch/0001/book-instance-04",
        values = Map(
          booksHasTextType.toSmartIri -> Vector(
            listValueTemplateReadOtherValue.copy(
              valueContent =
                HierarchicalListValueContentV2(InternalSchema, "http://rdfh.ch/lists/0001/ynm02-05", None, None),
              valueIri = "http://rdfh.ch/0001/book-instance-04/values/has-list-value-03",
              valueHasUUID = UuidUtil.decode("d34962b2-dfef-6a5b-9efd-a76f7a7b6ead"),
            ),
          ),
        ),
      ),
    ),
  )

  val constructQueryForIncunabulaCompundObject: ConstructQuery = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
          XsdLiteral("true", "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri),
        ),
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#seqnum".toSmartIri, None),
          QueryVariable("seqnum"),
        ),
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#hasStillImageFile".toSmartIri, None),
          QueryVariable("file"),
        ),
      ),
      querySchema = Some(ApiV2Simple),
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#StillImageRepresentation".toSmartIri, None),
        ),
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
        ),
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isPartOf".toSmartIri, None),
          IriRef("http://rdfh.ch/0803/861b5644b302".toSmartIri, None),
        ),
        StatementPattern(
          IriRef("http://rdfh.ch/0803/861b5644b302".toSmartIri, None),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
        ),
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#seqnum".toSmartIri, None),
          QueryVariable("seqnum"),
        ),
        StatementPattern(
          QueryVariable("seqnum"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://www.w3.org/2001/XMLSchema#integer".toSmartIri, None),
        ),
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#hasStillImageFile".toSmartIri, None),
          QueryVariable("file"),
        ),
        StatementPattern(
          QueryVariable("file"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#File".toSmartIri, None),
        ),
      ),
      positiveEntities = Set(
        QueryVariable("page"),
        QueryVariable("seqnum"),
        QueryVariable("file"),
        IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#StillImageRepresentation".toSmartIri, None),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isPartOf".toSmartIri, None),
        IriRef("http://rdfh.ch/0803/861b5644b302".toSmartIri, None),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#seqnum".toSmartIri, None),
        IriRef("http://www.w3.org/2001/XMLSchema#integer".toSmartIri, None),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#hasStillImageFile".toSmartIri, None),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#File".toSmartIri, None),
      ),
      querySchema = Some(ApiV2Simple),
    ),
    querySchema = Some(ApiV2Simple),
  )

  val constructQuerySortByLabel: ConstructQuery = ConstructQuery(
    constructClause = ConstructClause(
      Vector(
        StatementPattern(
          QueryVariable("r"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
          XsdLiteral("true", "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri),
        ),
      ),
      querySchema = Some(ApiV2Simple),
    ),
    whereClause = WhereClause(
      Vector(
        StatementPattern(
          QueryVariable("r"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri, None),
        ),
        StatementPattern(
          QueryVariable("r"),
          IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri, None),
          QueryVariable("l"),
        ),
      ),
      Set(
        QueryVariable("r"),
        QueryVariable("l"),
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
        IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
        IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri, None),
        IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri, None),
      ),
      querySchema = Some(ApiV2Simple),
    ),
    orderBy = Vector(OrderCriterion(QueryVariable("l"), true)),
    querySchema = Some(ApiV2Simple),
  )

  val constructQuerySortByLabelDesc: ConstructQuery = constructQuerySortByLabel.copy(
    orderBy = Vector(OrderCriterion(QueryVariable("l"), false)),
  )

}
