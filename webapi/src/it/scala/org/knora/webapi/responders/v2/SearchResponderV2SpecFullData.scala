package org.knora.webapi.responders.v2

import akka.actor.ActorSystem
import java.time.Instant

import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.PermissionUtilADM._
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.sharedtestdata.SharedTestDataADM

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
    userPermission = ChangeRightsPermission,
    attachedToUser = testUser1,
    resourceClassIri = booksBookIri.toSmartIri,
    projectADM = SharedTestDataADM.anythingProject,
    creationDate = Instant.parse("2019-11-29T10:00:00.673298Z"),
    values = Map(),
    lastModificationDate = None,
    versionDate = None,
    deletionInfo = None
  )
  val listValueTemplateReadOtherValue: ReadOtherValueV2 = ReadOtherValueV2(
    valueContent = HierarchicalListValueContentV2(
      ontologySchema = InternalSchema,
      valueHasListNode = ""
    ),
    valueIri = "",
    valueHasUUID = stringFormatter.decodeUuid("d34d34d3-4d34-d34d-3496-2b2dfef6a5b9"),
    permissions =
      "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
    userPermission = ModifyPermission,
    previousValueIri = None,
    valueCreationDate = Instant.parse("2018-05-29T16:42:04.381Z"),
    attachedToUser = testUser2,
    deletionInfo = None
  )

  val constructQueryForBooksWithTitleZeitgloecklein: ConstructQuery = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
          XsdLiteral("true", "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri),
          None
        ),
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
          QueryVariable("title"),
          None
        )
      ),
      querySchema = Some(ApiV2Simple)
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri, None),
          None
        ),
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
          None
        ),
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
          QueryVariable("title"),
          None
        ),
        StatementPattern(
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri, None),
          IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
          None
        ),
        StatementPattern(
          QueryVariable("title"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
          None
        ),
        FilterPattern(
          CompareExpression(
            QueryVariable("title"),
            CompareExpressionOperator.EQUALS,
            XsdLiteral(
              "Zeitglöcklein des Lebens und Leidens Christi",
              "http://www.w3.org/2001/XMLSchema#string".toSmartIri
            )
          )
        )
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
        QueryVariable("title")
      ),
      querySchema = Some(ApiV2Simple)
    ),
    querySchema = Some(ApiV2Simple)
  )

  val booksWithTitleZeitgloeckleinResponse: ReadResourcesSequenceV2 = ReadResourcesSequenceV2(
    resources = Vector(
      ReadResourceV2(
        label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
        resourceIri = "http://rdfh.ch/0803/c5058f3a",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
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
                maybeValueHasString = Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi")
              ),
              valueHasMaxStandoffStartIndex = None,
              valueIri = "http://rdfh.ch/0803/c5058f3a/values/c3295339",
              valueHasUUID = stringFormatter.decodeUuid("c3295339"),
              permissions =
                "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
              userPermission = ViewPermission,
              previousValueIri = None,
              valueCreationDate = Instant.parse("2016-03-02T15:05:10Z"),
              attachedToUser = "http://rdfh.ch/users/91e19f1e01",
              deletionInfo = None
            )
          )
        ),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      ),
      ReadResourceV2(
        label = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
        resourceIri = "http://rdfh.ch/0803/ff17e5ef9601",
        permissions =
          "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
        userPermission = RestrictedViewPermission,
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
                maybeValueHasString = Some("Zeitgl\u00F6cklein des Lebens und Leidens Christi")
              ),
              valueHasMaxStandoffStartIndex = None,
              valueIri = "http://rdfh.ch/0803/ff17e5ef9601/values/d9a522845006",
              valueHasUUID = stringFormatter.decodeUuid("d9a522845006"),
              permissions =
                "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
              userPermission = ViewPermission,
              previousValueIri = None,
              valueCreationDate = Instant.parse("2016-03-02T15:05:23Z"),
              attachedToUser = "http://rdfh.ch/users/91e19f1e01",
              deletionInfo = None
            )
          )
        ),
        lastModificationDate = None,
        versionDate = None,
        deletionInfo = None
      )
    )
  )

  val constructQueryForBooksWithoutTitleZeitgloecklein: ConstructQuery = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
          XsdLiteral("true", "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri),
          None
        ),
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
          QueryVariable("title"),
          None
        )
      ),
      querySchema = Some(ApiV2Simple)
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri, None),
          None
        ),
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
          None
        ),
        StatementPattern(
          QueryVariable("book"),
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
          QueryVariable("title"),
          None
        ),
        StatementPattern(
          IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri, None),
          IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
          None
        ),
        StatementPattern(
          QueryVariable("title"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri, None),
          None
        ),
        FilterPattern(
          CompareExpression(
            QueryVariable("title"),
            CompareExpressionOperator.NOT_EQUALS,
            XsdLiteral(
              "Zeitglöcklein des Lebens und Leidens Christi",
              "http://www.w3.org/2001/XMLSchema#string".toSmartIri
            )
          )
        )
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
        QueryVariable("title")
      ),
      querySchema = Some(ApiV2Simple)
    ),
    querySchema = Some(ApiV2Simple)
  )

  val expectedResultFulltextSearchForListNodeLabel: ReadResourcesSequenceV2 = ReadResourcesSequenceV2(
    resources = Vector(
      bookTemplateReadResource.copy(
        label = "instance of a book with a list value",
        resourceIri = "http://rdfh.ch/0001/book-instance-02",
        values = Map(
          booksHasTextType.toSmartIri -> Vector(
            listValueTemplateReadOtherValue.copy(
              valueContent = HierarchicalListValueContentV2(
                ontologySchema = InternalSchema,
                valueHasListNode = "http://rdfh.ch/lists/0001/ynm02-03"
              ),
              valueIri = "http://rdfh.ch/0001/book-instance-02/values/has-list-value-01",
              valueHasUUID = stringFormatter.decodeUuid("d34d34d3-4d34-d34d-3496-2b2dfef6a5b9")
            )
          )
        )
      )
    )
  )

  val expectedResultFulltextSearchForListNodeLabelWithSubnodes: ReadResourcesSequenceV2 = ReadResourcesSequenceV2(
    resources = Vector(
      bookTemplateReadResource.copy(
        label = "Lord of the Rings",
        resourceIri = "http://rdfh.ch/0001/book-instance-03",
        values = Map(
          booksHasTextType.toSmartIri -> Vector(
            listValueTemplateReadOtherValue.copy(
              valueContent = HierarchicalListValueContentV2(
                ontologySchema = InternalSchema,
                valueHasListNode = "http://rdfh.ch/lists/0001/ynm02-04"
              ),
              valueIri = "http://rdfh.ch/0001/book-instance-03/values/has-list-value-02",
              valueHasUUID = stringFormatter.decodeUuid("d34d3496-2b2d-fef6-a5b9-efdf6a7b5ab3")
            )
          )
        )
      ),
      bookTemplateReadResource.copy(
        label = "Treasure Island",
        resourceIri = "http://rdfh.ch/0001/book-instance-04",
        values = Map(
          booksHasTextType.toSmartIri -> Vector(
            listValueTemplateReadOtherValue.copy(
              valueContent = HierarchicalListValueContentV2(
                ontologySchema = InternalSchema,
                valueHasListNode = "http://rdfh.ch/lists/0001/ynm02-05"
              ),
              valueIri = "http://rdfh.ch/0001/book-instance-04/values/has-list-value-03",
              valueHasUUID = stringFormatter.decodeUuid("d34962b2-dfef-6a5b-9efd-a76f7a7b6ead")
            )
          )
        )
      )
    )
  )

  val constructQueryForIncunabulaCompundObject: ConstructQuery = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri, None),
          XsdLiteral("true", "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri),
          None
        ),
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#seqnum".toSmartIri, None),
          QueryVariable("seqnum"),
          None
        ),
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#hasStillImageFile".toSmartIri, None),
          QueryVariable("file"),
          None
        )
      ),
      querySchema = Some(ApiV2Simple)
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#StillImageRepresentation".toSmartIri, None),
          None
        ),
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
          None
        ),
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isPartOf".toSmartIri, None),
          IriRef("http://rdfh.ch/0803/861b5644b302".toSmartIri, None),
          None
        ),
        StatementPattern(
          IriRef("http://rdfh.ch/0803/861b5644b302".toSmartIri, None),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri, None),
          None
        ),
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#seqnum".toSmartIri, None),
          QueryVariable("seqnum"),
          None
        ),
        StatementPattern(
          QueryVariable("seqnum"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://www.w3.org/2001/XMLSchema#integer".toSmartIri, None),
          None
        ),
        StatementPattern(
          QueryVariable("page"),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#hasStillImageFile".toSmartIri, None),
          QueryVariable("file"),
          None
        ),
        StatementPattern(
          QueryVariable("file"),
          IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri, None),
          IriRef("http://api.knora.org/ontology/knora-api/simple/v2#File".toSmartIri, None),
          None
        )
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
        IriRef("http://api.knora.org/ontology/knora-api/simple/v2#File".toSmartIri, None)
      ),
      querySchema = Some(ApiV2Simple)
    ),
    querySchema = Some(ApiV2Simple)
  )

}
