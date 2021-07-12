/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

import java.time.Instant
import java.util.UUID

import akka.actor.{ActorRef, Props}
import akka.testkit.ImplicitSender
import org.knora.webapi._
import org.knora.webapi.app.ApplicationActor
import org.knora.webapi.exceptions._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.util.{
  CalendarNameGregorian,
  DatePrecisionYear,
  KnoraSystemInstances,
  PermissionUtilADM
}
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.settings.{KnoraDispatchers, _}
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.store.iiif.MockSipiConnector
import org.knora.webapi.util.MutableTestIri

import scala.concurrent.duration._

/**
  * Tests [[ValuesResponderV2]].
  */
class ValuesResponderV2Spec extends CoreSpec() with ImplicitSender {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val zeitglöckleinIri = "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw"
  private val generationeIri = "http://rdfh.ch/resources/pxA6iorNSWOIiIWpkT_pAQ"
  private val aThingIri = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA"
  private val aThingPictureIri = "http://rdfh.ch/resources/h2TEa725XPK7CfG15Bk0bA"
  private val sierraIri = "http://rdfh.ch/resources/0C-0L1kORryKzJAJxxRyRQ"

  private val incunabulaUser = SharedTestDataADM.incunabulaMemberUser
  private val incunabulaCreatorUser = SharedTestDataADM.incunabulaCreatorUser
  private val anythingUser1 = SharedTestDataADM.anythingUser1
  private val anythingUser2 = SharedTestDataADM.anythingUser2

  /* we need to run our app with the mocked sipi actor */
  override lazy val appActor: ActorRef = system.actorOf(
    Props(new ApplicationActor with ManagersWithMockedSipi).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    name = APPLICATION_MANAGER_ACTOR_NAME)

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/responders.v2.ValuesResponderV2Spec/incunabula-data.ttl",
                  name = "http://www.knora.org/data/0803/incunabula"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  // The default timeout for receiving reply messages from actors.
  private val timeout = 30.seconds

  private val firstIntValueVersionIri = new MutableTestIri
  private val intValueIri = new MutableTestIri
  private val intValueIriWithCustomPermissions = new MutableTestIri
  private val intValueForRsyncIri = new MutableTestIri
  private val zeitglöckleinCommentWithoutStandoffIri = new MutableTestIri
  private val zeitglöckleinCommentWithStandoffIri = new MutableTestIri
  private val zeitglöckleinCommentWithCommentIri = new MutableTestIri
  private val zeitglöckleinSecondCommentWithStandoffIri = new MutableTestIri
  private val lobComment1Iri = new MutableTestIri
  private val lobComment2Iri = new MutableTestIri
  private val decimalValueIri = new MutableTestIri
  private val timeValueIri = new MutableTestIri
  private val dateValueIri = new MutableTestIri
  private val booleanValueIri = new MutableTestIri
  private val geometryValueIri = new MutableTestIri
  private val intervalValueIri = new MutableTestIri
  private val listValueIri = new MutableTestIri
  private val colorValueIri = new MutableTestIri
  private val uriValueIri = new MutableTestIri
  private val geonameValueIri = new MutableTestIri
  private val linkValueIri = new MutableTestIri
  private val standoffLinkValueIri = new MutableTestIri
  private val stillImageFileValueIri = new MutableTestIri

  private var integerValueUUID = UUID.randomUUID
  private var linkValueUUID = UUID.randomUUID

  private val sampleStandoff: Vector[StandoffTagV2] = Vector(
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag.toSmartIri,
      startPosition = 0,
      endPosition = 7,
      uuid = UUID.randomUUID(),
      originalXMLID = None,
      startIndex = 0
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag.toSmartIri,
      startPosition = 0,
      endPosition = 10,
      uuid = UUID.randomUUID(),
      originalXMLID = None,
      startIndex = 1
    )
  )

  private val sampleStandoffModified: Vector[StandoffTagV2] = Vector(
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag.toSmartIri,
      startPosition = 1,
      endPosition = 7,
      uuid = UUID.randomUUID(),
      originalXMLID = None,
      startIndex = 0
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag.toSmartIri,
      startPosition = 0,
      endPosition = 10,
      uuid = UUID.randomUUID(),
      originalXMLID = None,
      startIndex = 1
    )
  )

  private val sampleStandoffWithLink: Vector[StandoffTagV2] = Vector(
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag.toSmartIri,
      dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
      startPosition = 0,
      endPosition = 7,
      uuid = UUID.randomUUID(),
      originalXMLID = None,
      startIndex = 0,
      attributes = Vector(
        StandoffTagIriAttributeV2(standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink.toSmartIri,
                                  value = aThingIri)),
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag.toSmartIri,
      startPosition = 0,
      endPosition = 10,
      uuid = UUID.randomUUID(),
      originalXMLID = None,
      startIndex = 1
    )
  )

  private var standardMapping: Option[MappingXMLtoStandoff] = None

  private def getResourceWithValues(resourceIri: IRI,
                                    propertyIrisForGravsearch: Seq[SmartIri],
                                    requestingUser: UserADM): ReadResourceV2 = {
    // Make a Gravsearch query from a template.
    val gravsearchQuery: String = org.knora.webapi.messages.twirl.queries.gravsearch.txt
      .getResourceWithSpecifiedProperties(
        resourceIri = resourceIri,
        propertyIris = propertyIrisForGravsearch
      )
      .toString()

    // Run the query.

    val parsedGravsearchQuery = GravsearchParser.parseQuery(gravsearchQuery)

    responderManager ! GravsearchRequestV2(
      constructQuery = parsedGravsearchQuery,
      targetSchema = ApiV2Complex,
      schemaOptions = SchemaOptions.ForStandoffWithTextValues,
      featureFactoryConfig = defaultFeatureFactoryConfig,
      requestingUser = requestingUser
    )

    expectMsgPF(timeout) {
      case searchResponse: ReadResourcesSequenceV2 =>
        searchResponse.toResource(resourceIri).toOntologySchema(ApiV2Complex)
    }
  }

  private def getValuesFromResource(resource: ReadResourceV2, propertyIriInResult: SmartIri): Seq[ReadValueV2] = {
    resource.values.getOrElse(
      propertyIriInResult,
      throw AssertionException(s"Resource <${resource.resourceIri}> does not have property <$propertyIriInResult>"))
  }

  private def getValueFromResource(resource: ReadResourceV2,
                                   propertyIriInResult: SmartIri,
                                   expectedValueIri: IRI): ReadValueV2 = {
    val propertyValues: Seq[ReadValueV2] =
      getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult)
    propertyValues
      .find(_.valueIri == expectedValueIri)
      .getOrElse(throw AssertionException(
        s"Property <$propertyIriInResult> of resource <${resource.resourceIri}> does not have value <$expectedValueIri>"))
  }

  private def checkValueIsDeleted(resourceIri: IRI,
                                  maybePreviousLastModDate: Option[Instant],
                                  propertyIriForGravsearch: SmartIri,
                                  propertyIriInResult: SmartIri,
                                  valueIri: IRI,
                                  customDeleteDate: Option[Instant] = None,
                                  requestingUser: UserADM): Unit = {
    val resource = getResourceWithValues(
      resourceIri = resourceIri,
      propertyIrisForGravsearch = Seq(propertyIriForGravsearch),
      requestingUser = requestingUser
    )

    checkLastModDate(
      resourceIri = resourceIri,
      maybePreviousLastModDate = maybePreviousLastModDate,
      maybeUpdatedLastModDate = resource.lastModificationDate
    )

    val propertyValues: Seq[ReadValueV2] =
      getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult)

    propertyValues.find(_.valueIri == valueIri) match {
      case Some(_) => throw AssertionException(s"Value <$valueIri was not deleted>")
      case None    => ()
    }

    // If a custom delete date was used, check that it was saved correctly.
    customDeleteDate match {
      case Some(deleteDate) =>
        val sparqlQuery: String = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
          .getDeleteDate(
            triplestore = settings.triplestoreType,
            entityIri = valueIri
          )
          .toString()

        storeManager ! SparqlSelectRequest(sparqlQuery)

        expectMsgPF(timeout) {
          case sparqlSelectResponse: SparqlSelectResult =>
            val savedDeleteDateStr = sparqlSelectResponse.getFirstRow.rowMap("deleteDate")

            val savedDeleteDate: Instant = stringFormatter.xsdDateTimeStampToInstant(
              savedDeleteDateStr,
              throw AssertionException(s"Couldn't parse delete date from triplestore: $savedDeleteDateStr")
            )

            assert(savedDeleteDate == deleteDate)
        }

      case None => ()
    }
  }

  private def checkLastModDate(resourceIri: IRI,
                               maybePreviousLastModDate: Option[Instant],
                               maybeUpdatedLastModDate: Option[Instant]): Unit = {
    maybeUpdatedLastModDate match {
      case Some(updatedLastModDate) =>
        maybePreviousLastModDate match {
          case Some(previousLastModDate) => assert(updatedLastModDate.isAfter(previousLastModDate))
          case None                      => ()
        }

      case None => throw AssertionException(s"Resource $resourceIri has no knora-base:lastModificationDate")
    }
  }

  private def getValue(resourceIri: IRI,
                       maybePreviousLastModDate: Option[Instant],
                       propertyIriForGravsearch: SmartIri,
                       propertyIriInResult: SmartIri,
                       expectedValueIri: IRI,
                       requestingUser: UserADM,
                       checkLastModDateChanged: Boolean = true): ReadValueV2 = {
    val resource = getResourceWithValues(
      resourceIri = resourceIri,
      propertyIrisForGravsearch = Seq(propertyIriForGravsearch),
      requestingUser = requestingUser
    )

    if (checkLastModDateChanged) {
      checkLastModDate(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybePreviousLastModDate,
        maybeUpdatedLastModDate = resource.lastModificationDate
      )
    }

    getValueFromResource(
      resource = resource,
      propertyIriInResult = propertyIriInResult,
      expectedValueIri = expectedValueIri
    )
  }

  private def getResourceLastModificationDate(resourceIri: IRI, requestingUser: UserADM): Option[Instant] = {
    responderManager ! ResourcesPreviewGetRequestV2(
      resourceIris = Seq(resourceIri),
      targetSchema = ApiV2Complex,
      featureFactoryConfig = defaultFeatureFactoryConfig,
      requestingUser = requestingUser
    )

    expectMsgPF(timeout) {
      case previewResponse: ReadResourcesSequenceV2 =>
        val resourcePreview: ReadResourceV2 = previewResponse.toResource(resourceIri)
        resourcePreview.lastModificationDate
    }
  }

  private def getValueUUID(valueIri: IRI): Option[UUID] = {
    val sparqlQuery =
      s"""
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT ?valueUUID WHERE {
               |    <$valueIri> knora-base:valueHasUUID ?valueUUID .
               |}
             """.stripMargin

    storeManager ! SparqlSelectRequest(sparqlQuery)

    expectMsgPF(timeout) {
      case response: SparqlSelectResult =>
        val rows = response.results.bindings

        if (rows.isEmpty) {
          None
        } else if (rows.size > 1) {
          throw AssertionException(s"Expected one knora-base:valueHasUUID, got ${rows.size}")
        } else {
          Some(stringFormatter.base64DecodeUuid(rows.head.rowMap("valueUUID")))
        }
    }
  }

  private def getValuePermissions(valueIri: IRI): Option[UUID] = {
    val sparqlQuery =
      s"""
               |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT ?valuePermissions WHERE {
               |    <$valueIri> knora-base:hasPermissions ?valuePermissions .
               |}
             """.stripMargin

    storeManager ! SparqlSelectRequest(sparqlQuery)

    expectMsgPF(timeout) {
      case response: SparqlSelectResult =>
        val rows = response.results.bindings

        if (rows.isEmpty) {
          None
        } else if (rows.size > 1) {
          throw AssertionException(s"Expected one knora-base:hasPermissions, got ${rows.size}")
        } else {
          Some(stringFormatter.base64DecodeUuid(rows.head.rowMap("valuePermissions")))
        }
    }
  }

  "Load test data" in {
    responderManager ! GetMappingRequestV2(
      mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
      featureFactoryConfig = defaultFeatureFactoryConfig,
      requestingUser = KnoraSystemInstances.Users.SystemUser
    )

    expectMsgPF(timeout) {
      case mappingResponse: GetMappingResponseV2 =>
        standardMapping = Some(mappingResponse.mapping)
    }
  }

  "The values responder" should {
    "create an integer value" in {
      // Add the value.

      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 4
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 =>
          intValueIri.set(createValueResponse.valueIri)
          firstIntValueVersionIri.set(createValueResponse.valueIri)
          integerValueUUID = createValueResponse.valueUUID
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 => savedValue.valueHasInteger should ===(intValue)
        case _                                 => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate integer value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 4

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update an integer value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      // Get the value before update.
      val previousValueFromTriplestore: ReadValueV2 = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1,
        checkLastModDateChanged = false
      )

      // Update the value.

      val intValue = 5

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 =>
          intValueIri.set(updateValueResponse.valueIri)
          assert(updateValueResponse.valueUUID == integerValueUUID)
      }

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1
      )

      updatedValueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          updatedValueFromTriplestore.permissions should ===(previousValueFromTriplestore.permissions)
          updatedValueFromTriplestore.valueHasUUID should ===(previousValueFromTriplestore.valueHasUUID)

        case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
      }

      // Check that the permissions and UUID were deleted from the previous version of the value.
      assert(getValueUUID(previousValueFromTriplestore.valueIri).isEmpty)
      assert(getValuePermissions(previousValueFromTriplestore.valueIri).isEmpty)
    }

    "not update an integer value without a comment without changing it" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 5

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update an integer value, adding a comment" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)
      val comment = "Added a comment"

      // Get the value before update.
      val previousValueFromTriplestore: ReadValueV2 = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1,
        checkLastModDateChanged = false
      )

      // Update the value.

      val intValue = 5

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue,
            comment = Some(comment)
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => intValueIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1
      )

      updatedValueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          updatedValueFromTriplestore.permissions should ===(previousValueFromTriplestore.permissions)
          updatedValueFromTriplestore.valueHasUUID should ===(previousValueFromTriplestore.valueHasUUID)
          assert(updatedValueFromTriplestore.valueContent.comment.contains(comment))

        case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
      }

      // Check that the permissions and UUID were deleted from the previous version of the value.
      assert(getValueUUID(previousValueFromTriplestore.valueIri).isEmpty)
      assert(getValuePermissions(previousValueFromTriplestore.valueIri).isEmpty)
    }

    "not update an integer value with a comment without changing it" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 5
      val comment = "Added a comment"

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue,
            comment = Some(comment)
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update an integer value with a comment, changing only the comment" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)
      val comment = "An updated comment"

      // Get the value before update.
      val previousValueFromTriplestore: ReadValueV2 = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1,
        checkLastModDateChanged = false
      )

      // Update the value.

      val intValue = 5

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue,
            comment = Some(comment)
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => intValueIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1
      )

      updatedValueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          updatedValueFromTriplestore.permissions should ===(previousValueFromTriplestore.permissions)
          updatedValueFromTriplestore.valueHasUUID should ===(previousValueFromTriplestore.valueHasUUID)
          assert(updatedValueFromTriplestore.valueContent.comment.contains(comment))

        case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
      }

      // Check that the permissions and UUID were deleted from the previous version of the value.
      assert(getValueUUID(previousValueFromTriplestore.valueIri).isEmpty)
      assert(getValuePermissions(previousValueFromTriplestore.valueIri).isEmpty)
    }

    "create an integer value with a comment" in {
      // Add the value.

      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 8
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)
      val comment = "Initial comment"

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue,
            comment = Some(comment)
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 =>
          intValueIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          assert(savedValue.comment.contains(comment))

        case _ => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
      }
    }

    "create an integer value with custom permissions" in {
      // Add the value.

      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 1
      val permissions = "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          ),
          permissions = Some(permissions)
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 =>
          intValueIriWithCustomPermissions.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIriWithCustomPermissions.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          PermissionUtilADM.parsePermissions(valueFromTriplestore.permissions) should ===(
            PermissionUtilADM.parsePermissions(permissions))

        case _ => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
      }
    }

    "not create an integer value with syntactically invalid custom permissions" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 1024
      val permissions = "M knora-admin:Creator,V knora-admin:KnownUser"

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          ),
          permissions = Some(permissions)
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[BadRequestException])
      }

    }

    "not create an integer value with custom permissions referring to a nonexistent group" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 1024
      val permissions = "M knora-admin:Creator|V http://rdfh.ch/groups/0001/nonexistent-group"

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          ),
          permissions = Some(permissions)
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[NotFoundException])
      }
    }

    "create an integer value with custom UUID and creation date" in {
      // Add the value.

      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 987
      val valueUUID = UUID.randomUUID
      val valueCreationDate = Instant.now
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          ),
          valueUUID = Some(valueUUID),
          valueCreationDate = Some(valueCreationDate)
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => intValueForRsyncIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueForRsyncIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          valueFromTriplestore.valueHasUUID should ===(valueUUID)
          valueFromTriplestore.valueCreationDate should ===(valueCreationDate)

        case _ => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
      }
    }

    "not update an integer value with a custom creation date that is earlier than the date of the current version" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 989
      val valueCreationDate = Instant.parse("2019-11-29T10:00:00Z")

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueForRsyncIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          ),
          valueCreationDate = Some(valueCreationDate)
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[BadRequestException])
      }
    }

    "update an integer value with a custom creation date" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      // Update the value.

      val intValue = 988
      val valueCreationDate = Instant.now

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueForRsyncIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          ),
          valueCreationDate = Some(valueCreationDate)
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 =>
          intValueForRsyncIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueForRsyncIri.get,
        requestingUser = anythingUser1
      )

      updatedValueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          updatedValueFromTriplestore.valueCreationDate should ===(valueCreationDate)

        case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
      }
    }

    "update an integer value with a custom new version IRI" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      // Update the value.

      val intValue = 1000
      val newValueVersionIri: IRI = stringFormatter.makeValueIri(resourceIri)

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueForRsyncIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          ),
          newValueVersionIri = Some(newValueVersionIri.toSmartIri)
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 =>
          intValueForRsyncIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueForRsyncIri.get,
        requestingUser = anythingUser1
      )

      updatedValueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          updatedValueFromTriplestore.valueIri should ===(newValueVersionIri)

        case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
      }
    }

    "not create a value if the user does not have modify permission on the resource" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 5

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[ForbiddenException])
      }
    }

    "create a text value without standoff" in {
      val valueHasString = "Comment 1a"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = zeitglöckleinIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString)
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 =>
          zeitglöckleinCommentWithoutStandoffIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = zeitglöckleinIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = zeitglöckleinCommentWithoutStandoffIri.get,
        requestingUser = incunabulaUser
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TextValueContentV2 => assert(savedValue.valueHasString.contains(valueHasString))
        case _                              => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate text value without standoff" in {
      val valueHasString = "Comment 1a"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = zeitglöckleinIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString)
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "create a text value with a comment" in {
      val valueHasString = "this is a text value that has a comment"
      val valueHasComment = "this is a comment"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = zeitglöckleinIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString),
            comment = Some(valueHasComment)
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 =>
          zeitglöckleinCommentWithCommentIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = zeitglöckleinIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = zeitglöckleinCommentWithCommentIri.get,
        requestingUser = incunabulaUser
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TextValueContentV2 =>
          assert(savedValue.valueHasString.contains(valueHasString))
          savedValue.comment should ===(Some(valueHasComment))

        case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
      }
    }

    "create a text value with standoff" in {
      val valueHasString = "Comment 1aa"

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = zeitglöckleinIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString),
            standoff = sampleStandoff,
            mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
            mapping = standardMapping
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 =>
          zeitglöckleinCommentWithStandoffIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = zeitglöckleinIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = zeitglöckleinCommentWithStandoffIri.get,
        requestingUser = incunabulaUser
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TextValueContentV2 =>
          assert(savedValue.valueHasString.contains(valueHasString))
          savedValue.standoff should ===(sampleStandoff)
          assert(savedValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"))
          assert(savedValue.mapping == standardMapping)

        case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate text value with standoff (even if the standoff is different)" in {
      val valueHasString = "Comment 1aa"

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = zeitglöckleinIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString),
            standoff = sampleStandoffModified,
            mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
            mapping = standardMapping
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "create a decimal value" in {
      // Add the value.

      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
      val valueHasDecimal = BigDecimal("4.3")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = DecimalValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasDecimal = valueHasDecimal
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => decimalValueIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = decimalValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: DecimalValueContentV2 => savedValue.valueHasDecimal should ===(valueHasDecimal)
        case _                                 => throw AssertionException(s"Expected decimal value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate decimal value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
      val valueHasDecimal = BigDecimal("4.3")

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = DecimalValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasDecimal = valueHasDecimal
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "create a time value" in {
      // Add the value.

      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasTimeStamp".toSmartIri
      val valueHasTimeStamp = Instant.parse("2019-08-28T15:59:12.725007Z")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = TimeValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasTimeStamp = valueHasTimeStamp
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => timeValueIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = timeValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TimeValueContentV2 => savedValue.valueHasTimeStamp should ===(valueHasTimeStamp)
        case _                              => throw AssertionException(s"Expected time value, got $valueFromTriplestore")
      }
    }

    "create a date value" in {
      // Add the value.

      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val submittedValueContent = DateValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasCalendar = CalendarNameGregorian,
        valueHasStartJDN = 2264907,
        valueHasStartPrecision = DatePrecisionYear,
        valueHasEndJDN = 2265271,
        valueHasEndPrecision = DatePrecisionYear
      )

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = submittedValueContent
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => dateValueIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = dateValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: DateValueContentV2 =>
          savedValue.valueHasCalendar should ===(submittedValueContent.valueHasCalendar)
          savedValue.valueHasStartJDN should ===(submittedValueContent.valueHasStartJDN)
          savedValue.valueHasStartPrecision should ===(submittedValueContent.valueHasStartPrecision)
          savedValue.valueHasEndJDN should ===(submittedValueContent.valueHasEndJDN)
          savedValue.valueHasEndPrecision should ===(submittedValueContent.valueHasEndPrecision)

        case _ => throw AssertionException(s"Expected date value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate date value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri

      val submittedValueContent = DateValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasCalendar = CalendarNameGregorian,
        valueHasStartJDN = 2264907,
        valueHasStartPrecision = DatePrecisionYear,
        valueHasEndJDN = 2265271,
        valueHasEndPrecision = DatePrecisionYear
      )

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = submittedValueContent
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "create a boolean value" in {
      // Add the value.

      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
      val valueHasBoolean = true
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = BooleanValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasBoolean = valueHasBoolean
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => booleanValueIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = booleanValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: BooleanValueContentV2 => savedValue.valueHasBoolean should ===(valueHasBoolean)
        case _                                 => throw AssertionException(s"Expected boolean value, got $valueFromTriplestore")
      }
    }

    "create a geometry value" in {
      // Add the value.

      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
      val valueHasGeometry =
        """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = GeomValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasGeometry = valueHasGeometry
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => geometryValueIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = geometryValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: GeomValueContentV2 => savedValue.valueHasGeometry should ===(valueHasGeometry)
        case _                              => throw AssertionException(s"Expected geometry value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate geometry value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
      val valueHasGeometry =
        """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = GeomValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasGeometry = valueHasGeometry
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "create an interval value" in {
      // Add the value.

      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
      val valueHasIntervalStart = BigDecimal("1.2")
      val valueHasIntervalEnd = BigDecimal("3")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = IntervalValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasIntervalStart = valueHasIntervalStart,
            valueHasIntervalEnd = valueHasIntervalEnd
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => intervalValueIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intervalValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: IntervalValueContentV2 =>
          savedValue.valueHasIntervalStart should ===(valueHasIntervalStart)
          savedValue.valueHasIntervalEnd should ===(valueHasIntervalEnd)

        case _ => throw AssertionException(s"Expected interval value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate interval value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
      val valueHasIntervalStart = BigDecimal("1.2")
      val valueHasIntervalEnd = BigDecimal("3")

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = IntervalValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasIntervalStart = valueHasIntervalStart,
            valueHasIntervalEnd = valueHasIntervalEnd
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "create a list value" in {
      // Add the value.

      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode = "http://rdfh.ch/lists/0001/treeList03"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = HierarchicalListValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasListNode = valueHasListNode
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => listValueIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = listValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: HierarchicalListValueContentV2 =>
          savedValue.valueHasListNode should ===(valueHasListNode)

        case _ => throw AssertionException(s"Expected list value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate list value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode = "http://rdfh.ch/lists/0001/treeList03"

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = HierarchicalListValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasListNode = valueHasListNode
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "not create a list value referring to a nonexistent list node" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode = "http://rdfh.ch/lists/0001/nonexistent"

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = HierarchicalListValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasListNode = valueHasListNode
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[NotFoundException])
      }
    }

    "create a color value" in {
      // Add the value.

      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
      val valueHasColor = "#ff3333"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = ColorValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasColor = valueHasColor
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => colorValueIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = colorValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: ColorValueContentV2 =>
          savedValue.valueHasColor should ===(valueHasColor)

        case _ => throw AssertionException(s"Expected color value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate color value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
      val valueHasColor = "#ff3333"

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = ColorValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasColor = valueHasColor
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "create a URI value" in {
      // Add the value.

      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
      val valueHasUri = "https://www.knora.org"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = UriValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasUri = valueHasUri
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => uriValueIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = uriValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: UriValueContentV2 =>
          savedValue.valueHasUri should ===(valueHasUri)

        case _ => throw AssertionException(s"Expected URI value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate URI value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
      val valueHasUri = "https://www.knora.org"

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = UriValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasUri = valueHasUri
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "create a geoname value" in {
      // Add the value.

      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
      val valueHasGeonameCode = "2661604"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = GeonameValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasGeonameCode = valueHasGeonameCode
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => geonameValueIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = geonameValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: GeonameValueContentV2 =>
          savedValue.valueHasGeonameCode should ===(valueHasGeonameCode)

        case _ => throw AssertionException(s"Expected GeoNames value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate geoname value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
      val valueHasGeonameCode = "2661604"

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = GeonameValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasGeonameCode = valueHasGeonameCode
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "create a link between two resources" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/iPS4V8KMQtCTcufBSCkcLw"
      val linkPropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri
      val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

      val createValueRequest = CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          propertyIri = linkValuePropertyIri,
          resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
          valueContent = LinkValueContentV2(
            ontologySchema = ApiV2Complex,
            referredResourceIri = zeitglöckleinIri
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! createValueRequest

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 =>
          linkValueIri.set(createValueResponse.valueIri)
          linkValueUUID = createValueResponse.valueUUID
      }

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = linkPropertyIri,
        propertyIriInResult = linkValuePropertyIri,
        expectedValueIri = linkValueIri.get,
        requestingUser = incunabulaUser
      )

      valueFromTriplestore match {
        case readLinkValueV2: ReadLinkValueV2 =>
          readLinkValueV2.valueContent.referredResourceIri should ===(zeitglöckleinIri)
          readLinkValueV2.valueHasRefCount should ===(1)

        case _ => throw AssertionException(s"Expected link value, got $valueFromTriplestore")
      }
    }

    "not create a duplicate link" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/iPS4V8KMQtCTcufBSCkcLw"
      val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri

      val createValueRequest = CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
          propertyIri = linkValuePropertyIri,
          valueContent = LinkValueContentV2(
            ontologySchema = ApiV2Complex,
            referredResourceIri = zeitglöckleinIri
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! createValueRequest

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "not accept a link property in a request to create a link between two resources" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/iPS4V8KMQtCTcufBSCkcLw"
      val linkPropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri

      val createValueRequest = CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
          propertyIri = linkPropertyIri,
          valueContent = LinkValueContentV2(
            ontologySchema = ApiV2Complex,
            referredResourceIri = zeitglöckleinIri
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! createValueRequest

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[BadRequestException])
      }
    }

    "not create a standoff link directly" in {
      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = zeitglöckleinIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
          valueContent = LinkValueContentV2(
            ontologySchema = ApiV2Complex,
            referredResourceIri = generationeIri
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.superUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[BadRequestException])
      }
    }

    "not add a new value to a nonexistent resource" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/kRBtpcBUj9sieZ0MLLaciA"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 6

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[NotFoundException])
      }
    }

    "not add a new value to a deleted resource" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/60QF3uJ-SdKOj6Awfb5XIg"
      val valueHasString = "Comment 2"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString)
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[NotFoundException])
      }
    }

    "not add a new value if the resource's rdf:type is not correctly given" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 2048

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[BadRequestException])
      }
    }

    "not add a new value of the wrong type" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/86jhp31vTQ2KZra6E-cjQA"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#pubdate".toSmartIri

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some("this is not a date")
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[OntologyConstraintException])
      }
    }

    "not add a new value that would violate a cardinality restriction" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/1Yz4_KI4SaKkuh5aHuF2tA"

      // The cardinality of incunabula:partOf in incunabula:page is 1, and page http://rdfh.ch/resources/1Yz4_KI4SaKkuh5aHuF2tA is already part of a book.

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page".toSmartIri,
          propertyIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#partOfValue".toSmartIri,
          valueContent = LinkValueContentV2(
            ontologySchema = ApiV2Complex,
            referredResourceIri = "http://rdfh.ch/resources/x8s_IggiQ3upWwRQZK0trw"
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[OntologyConstraintException])
      }

      // The cardinality of incunabula:seqnum in incunabula:page is 0-1, and page http://rdfh.ch/resources/1Yz4_KI4SaKkuh5aHuF2tA already has a seqnum.

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = "http://rdfh.ch/resources/1Yz4_KI4SaKkuh5aHuF2tA",
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#page".toSmartIri,
          propertyIri = "http://www.knora.org/ontology/0803/incunabula#seqnum".toSmartIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = 1
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[OntologyConstraintException])
      }
    }

    "add a new text value containing a Standoff resource reference, and create a hasStandoffLinkTo direct link and a corresponding LinkValue" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/86jhp31vTQ2KZra6E-cjQA"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val valueHasString = "This comment refers to another resource"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

      val standoff = Seq(
        StandoffTagV2(
          dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
          standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag.toSmartIri,
          startPosition = 31,
          endPosition = 39,
          attributes = Vector(
            StandoffTagIriAttributeV2(standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink.toSmartIri,
                                      value = zeitglöckleinIri)),
          uuid = UUID.randomUUID(),
          originalXMLID = None,
          startIndex = 0
        ))

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString),
            standoff = standoff,
            mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
            mapping = standardMapping
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => lobComment1Iri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val updatedResource = getResourceWithValues(
        resourceIri = resourceIri,
        propertyIrisForGravsearch = Seq(propertyIri, OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri),
        requestingUser = incunabulaUser
      )

      checkLastModDate(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        maybeUpdatedLastModDate = updatedResource.lastModificationDate
      )

      val textValueFromTriplestore: ReadValueV2 = getValueFromResource(
        resource = updatedResource,
        propertyIriInResult = propertyIri,
        expectedValueIri = lobComment1Iri.get
      )

      textValueFromTriplestore.valueContent match {
        case savedTextValue: TextValueContentV2 =>
          assert(savedTextValue.valueHasString.contains(valueHasString))
          savedTextValue.standoff should ===(standoff)
          assert(savedTextValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"))
          savedTextValue.mapping should ===(standardMapping)

        case _ => throw AssertionException(s"Expected text value, got $textValueFromTriplestore")
      }

      // Since this is the first Standoff resource reference between the source and target resources, we should
      // now have version 1 of a LinkValue (it should have no previous version), with a reference count of 1.

      val linkValuesFromTripletore: Seq[ReadValueV2] = getValuesFromResource(
        resource = updatedResource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri
      )

      assert(linkValuesFromTripletore.size == 1)

      val linkValueFromTriplestore: ReadLinkValueV2 = linkValuesFromTripletore.head match {
        case readLinkValueV2: ReadLinkValueV2 => readLinkValueV2
        case other                            => throw AssertionException(s"Expected link value, got $other")
      }

      linkValueFromTriplestore.previousValueIri.isEmpty should ===(true)
      linkValueFromTriplestore.valueHasRefCount should ===(1)
      linkValueFromTriplestore.valueContent.referredResourceIri should ===(zeitglöckleinIri)
      standoffLinkValueIri.set(linkValueFromTriplestore.valueIri)
    }

    "add another new text value containing a Standoff resource reference, and make a new version of the LinkValue" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/86jhp31vTQ2KZra6E-cjQA"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val valueHasString = "This remark refers to another resource"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

      val standoff = Seq(
        StandoffTagV2(
          dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
          standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag.toSmartIri,
          startPosition = 30,
          endPosition = 38,
          attributes = Vector(
            StandoffTagIriAttributeV2(standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink.toSmartIri,
                                      value = zeitglöckleinIri)),
          uuid = UUID.randomUUID(),
          originalXMLID = None,
          startIndex = 0
        ))

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString),
            standoff = standoff,
            mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
            mapping = standardMapping
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => lobComment2Iri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val updatedResource = getResourceWithValues(
        resourceIri = resourceIri,
        propertyIrisForGravsearch = Seq(propertyIri, OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri),
        requestingUser = incunabulaUser
      )

      checkLastModDate(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        maybeUpdatedLastModDate = updatedResource.lastModificationDate
      )

      val textValueFromTriplestore: ReadValueV2 = getValueFromResource(
        resource = updatedResource,
        propertyIriInResult = propertyIri,
        expectedValueIri = lobComment2Iri.get
      )

      textValueFromTriplestore.valueContent match {
        case savedTextValue: TextValueContentV2 =>
          assert(savedTextValue.valueHasString.contains(valueHasString))
          savedTextValue.standoff should ===(standoff)
          assert(savedTextValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"))
          savedTextValue.mapping should ===(standardMapping)

        case _ => throw AssertionException(s"Expected text value, got $textValueFromTriplestore")
      }

      // Now that we've added a different TextValue that refers to the same resource, we should have version 2
      // of the LinkValue, with a reference count of 2. It should have a previousValue pointing to the previous
      // version.

      val linkValuesFromTripletore: Seq[ReadValueV2] = getValuesFromResource(
        resource = updatedResource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri
      )

      assert(linkValuesFromTripletore.size == 1)

      val linkValueFromTriplestore: ReadLinkValueV2 = linkValuesFromTripletore.head match {
        case readLinkValueV2: ReadLinkValueV2 => readLinkValueV2
        case other                            => throw AssertionException(s"Expected link value, got $other")
      }

      linkValueFromTriplestore.previousValueIri.contains(standoffLinkValueIri.get) should ===(true)
      linkValueFromTriplestore.valueHasRefCount should ===(2)
      linkValueFromTriplestore.valueContent.referredResourceIri should ===(zeitglöckleinIri)
      standoffLinkValueIri.set(linkValueFromTriplestore.valueIri)
    }

    "not update a value if an outdated value IRI is given" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 3

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = firstIntValueVersionIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[NotFoundException])
      }
    }

    "not update a value if the user does not have modify permission on the value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 9

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[ForbiddenException])
      }
    }

    "update a value with custom permissions" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions = "CR knora-admin:Creator|V knora-admin:ProjectMember"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)
      val intValue = 6

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          ),
          permissions = Some(permissions)
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => intValueIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1
      )

      updatedValueFromTriplestore.valueContent match {
        case savedValue: IntegerValueContentV2 =>
          savedValue.valueHasInteger should ===(intValue)
          updatedValueFromTriplestore.permissions should ===(permissions)

        case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
      }
    }

    "not update a value with custom permissions if the requesting user does not have ChangeRightsPermission on the value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions = "CR knora-admin:Creator"
      val intValue = 10

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          ),
          permissions = Some(permissions)
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser2,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[ForbiddenException])
      }
    }

    "not update a value with syntactically invalid custom permissions" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions = "M knora-admin:Creator,V knora-admin:KnownUser"
      val intValue = 7

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          ),
          permissions = Some(permissions)
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[BadRequestException])
      }
    }

    "not update a value with custom permissions referring to a nonexistent group" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions = "M knora-admin:Creator|V http://rdfh.ch/groups/0001/nonexistent-group"
      val intValue = 8

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          ),
          permissions = Some(permissions)
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[NotFoundException])
      }
    }

    "update a value, changing only its permissions" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions = "CR knora-admin:Creator|V knora-admin:KnownUser"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val oldValueFromTriplestore: ReadValueV2 = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = None,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1,
        checkLastModDateChanged = false
      )

      responderManager ! UpdateValueRequestV2(
        UpdateValuePermissionsV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueType = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
          permissions = permissions
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => intValueIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intValueIri.get,
        requestingUser = anythingUser1
      )

      updatedValueFromTriplestore.valueContent should ===(oldValueFromTriplestore.valueContent)
      updatedValueFromTriplestore.permissions should ===(permissions)
    }

    "not update a value, changing only its permissions, if the requesting user does not have ChangeRightsPermission on the value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions = "CR knora-admin:Creator"

      responderManager ! UpdateValueRequestV2(
        UpdateValuePermissionsV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueType = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
          permissions = permissions
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser2,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[ForbiddenException])
      }
    }

    "not update a value, changing only its permissions, with syntactically invalid custom permissions" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions = "M knora-admin:Creator,V knora-admin:KnownUser"

      responderManager ! UpdateValueRequestV2(
        UpdateValuePermissionsV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueType = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
          permissions = permissions
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[BadRequestException])
      }
    }

    "not update a value, changing only its permissions, with permissions referring to a nonexistent group" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val permissions = "M knora-admin:Creator|V http://rdfh.ch/groups/0001/nonexistent-group"

      responderManager ! UpdateValueRequestV2(
        UpdateValuePermissionsV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueType = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
          permissions = permissions
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[NotFoundException])
      }
    }

    "not update an integer value, giving it the same value as another integer value of the same property" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val intValue = 1

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intValueIri.get,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update a text value (without submitting standoff)" in {
      val valueHasString = "This updated comment has no standoff"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = zeitglöckleinIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueIri = zeitglöckleinCommentWithoutStandoffIri.get,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString)
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 =>
          zeitglöckleinCommentWithoutStandoffIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = zeitglöckleinIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = zeitglöckleinCommentWithoutStandoffIri.get,
        requestingUser = incunabulaUser
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TextValueContentV2 => assert(savedValue.valueHasString.contains(valueHasString))
        case _                              => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
      }
    }

    "update a text value (submitting standoff)" in {
      val valueHasString = "Comment 1ab"

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = zeitglöckleinIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueIri = zeitglöckleinCommentWithStandoffIri.get,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString),
            standoff = sampleStandoffWithLink,
            mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
            mapping = standardMapping
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 =>
          zeitglöckleinCommentWithStandoffIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = zeitglöckleinIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = zeitglöckleinCommentWithStandoffIri.get,
        requestingUser = incunabulaUser
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TextValueContentV2 =>
          assert(savedValue.valueHasString.contains(valueHasString))
          savedValue.standoff should ===(sampleStandoffWithLink)
          assert(savedValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"))
          savedValue.mapping should ===(standardMapping)

        case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
      }

      // There should be a link value for a standoff link.

      val resource = getResourceWithValues(
        resourceIri = zeitglöckleinIri,
        propertyIrisForGravsearch = Seq(OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri),
        requestingUser = incunabulaUser
      )

      val standoffLinkValues: Seq[ReadValueV2] = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri
      )

      assert(standoffLinkValues.size == 1)
      val standoffLinkValueFromTriplestore = standoffLinkValues.head

      standoffLinkValueFromTriplestore.valueContent match {
        case linkValueContentV2: LinkValueContentV2 =>
          standoffLinkValueIri.set(standoffLinkValueFromTriplestore.valueIri)
          assert(linkValueContentV2.referredResourceIri == aThingIri)

        case _ => throw AssertionException(s"Expected a link value, got $standoffLinkValueFromTriplestore")
      }
    }

    "not update a text value, duplicating an existing text value (without submitting standoff)" in {
      val valueHasString = "this is a text value that has a comment"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = zeitglöckleinIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueIri = zeitglöckleinCommentWithoutStandoffIri.get,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString)
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "create a second text value with standoff" in {
      val valueHasString = "Comment 1ac"

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = zeitglöckleinIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString),
            standoff = sampleStandoff,
            mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
            mapping = standardMapping
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 =>
          zeitglöckleinSecondCommentWithStandoffIri.set(createValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = zeitglöckleinIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = zeitglöckleinSecondCommentWithStandoffIri.get,
        requestingUser = incunabulaUser
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TextValueContentV2 =>
          assert(savedValue.valueHasString.contains(valueHasString))
          savedValue.standoff should ===(sampleStandoff)
          assert(savedValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"))
          savedValue.mapping should ===(standardMapping)

        case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
      }
    }

    "not update a text value, duplicating an existing text value (submitting standoff)" in {
      val valueHasString = "Comment 1ac"

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = zeitglöckleinIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueIri = zeitglöckleinCommentWithStandoffIri.get,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString),
            standoff = sampleStandoff,
            mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
            mapping = standardMapping
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update a text value, changing only the standoff" in {
      val valueHasString = "Comment 1ac"

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = zeitglöckleinIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueIri = zeitglöckleinSecondCommentWithStandoffIri.get,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString),
            standoff = sampleStandoffModified,
            mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
            mapping = standardMapping
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 =>
          zeitglöckleinSecondCommentWithStandoffIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = zeitglöckleinIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = zeitglöckleinSecondCommentWithStandoffIri.get,
        requestingUser = incunabulaUser
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TextValueContentV2 =>
          assert(savedValue.valueHasString.contains(valueHasString))
          savedValue.standoff should ===(sampleStandoffModified)
          assert(savedValue.mappingIri.contains("http://rdfh.ch/standoff/mappings/StandardMapping"))
          savedValue.mapping should ===(standardMapping)

        case _ => throw AssertionException(s"Expected text value, got $valueFromTriplestore")
      }
    }

    "not update a text value so it differs only from an existing value in that it has different standoff" in {
      val valueHasString = "Comment 1ac"

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = zeitglöckleinIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueIri = zeitglöckleinCommentWithStandoffIri.get,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString),
            standoff = sampleStandoffModified,
            mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
            mapping = standardMapping
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "not update a text value without changing it (without submitting standoff)" in {
      val valueHasString = "This updated comment has no standoff"
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = zeitglöckleinIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = propertyIri,
          valueIri = zeitglöckleinCommentWithoutStandoffIri.get,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(valueHasString)
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update a decimal value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
      val valueHasDecimal = BigDecimal("3.1415926")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = decimalValueIri.get,
          valueContent = DecimalValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasDecimal = valueHasDecimal
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => decimalValueIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = decimalValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: DecimalValueContentV2 => savedValue.valueHasDecimal should ===(valueHasDecimal)
        case _                                 => throw AssertionException(s"Expected decimal value, got $valueFromTriplestore")
      }
    }

    "not update a decimal value without changing it" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
      val valueHasDecimal = BigDecimal("3.1415926")

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = decimalValueIri.get,
          valueContent = DecimalValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasDecimal = valueHasDecimal
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update a time value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasTimeStamp".toSmartIri
      val valueHasTimeStamp = Instant.parse("2019-08-28T16:01:46.952237Z")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = timeValueIri.get,
          valueContent = TimeValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasTimeStamp = valueHasTimeStamp
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => timeValueIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = timeValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: TimeValueContentV2 => savedValue.valueHasTimeStamp should ===(valueHasTimeStamp)
        case _                              => throw AssertionException(s"Expected time value, got $valueFromTriplestore")
      }
    }

    "not update a time value without changing it" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasTimeStamp".toSmartIri
      val valueHasTimeStamp = Instant.parse("2019-08-28T16:01:46.952237Z")

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = timeValueIri.get,
          valueContent = TimeValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasTimeStamp = valueHasTimeStamp
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update a date value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      val submittedValueContent = DateValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasCalendar = CalendarNameGregorian,
        valueHasStartJDN = 2264908,
        valueHasStartPrecision = DatePrecisionYear,
        valueHasEndJDN = 2265272,
        valueHasEndPrecision = DatePrecisionYear
      )

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = dateValueIri.get,
          valueContent = submittedValueContent
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => dateValueIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = dateValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: DateValueContentV2 =>
          savedValue.valueHasCalendar should ===(submittedValueContent.valueHasCalendar)
          savedValue.valueHasStartJDN should ===(submittedValueContent.valueHasStartJDN)
          savedValue.valueHasStartPrecision should ===(submittedValueContent.valueHasStartPrecision)
          savedValue.valueHasEndJDN should ===(submittedValueContent.valueHasEndJDN)
          savedValue.valueHasEndPrecision should ===(submittedValueContent.valueHasEndPrecision)

        case _ => throw AssertionException(s"Expected date value, got $valueFromTriplestore")
      }
    }

    "not update a date value without changing it" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri

      val submittedValueContent = DateValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasCalendar = CalendarNameGregorian,
        valueHasStartJDN = 2264908,
        valueHasStartPrecision = DatePrecisionYear,
        valueHasEndJDN = 2265272,
        valueHasEndPrecision = DatePrecisionYear
      )

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = dateValueIri.get,
          valueContent = submittedValueContent
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update a boolean value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
      val valueHasBoolean = false
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = booleanValueIri.get,
          valueContent = BooleanValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasBoolean = valueHasBoolean
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => booleanValueIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = booleanValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: BooleanValueContentV2 => savedValue.valueHasBoolean should ===(valueHasBoolean)
        case _                                 => throw AssertionException(s"Expected boolean value, got $valueFromTriplestore")
      }
    }

    "not update a boolean value without changing it" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri
      val valueHasBoolean = false

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = booleanValueIri.get,
          valueContent = BooleanValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasBoolean = valueHasBoolean
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update a geometry value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
      val valueHasGeometry =
        """{"status":"active","lineColor":"#ff3334","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = geometryValueIri.get,
          valueContent = GeomValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasGeometry = valueHasGeometry
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => geometryValueIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = geometryValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: GeomValueContentV2 => savedValue.valueHasGeometry should ===(valueHasGeometry)
        case _                              => throw AssertionException(s"Expected geometry value, got $valueFromTriplestore")
      }
    }

    "not update a geometry value without changing it" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri
      val valueHasGeometry =
        """{"status":"active","lineColor":"#ff3334","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = geometryValueIri.get,
          valueContent = GeomValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasGeometry = valueHasGeometry
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update an interval value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
      val valueHasIntervalStart = BigDecimal("1.23")
      val valueHasIntervalEnd = BigDecimal("3.45")
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intervalValueIri.get,
          valueContent = IntervalValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasIntervalStart = valueHasIntervalStart,
            valueHasIntervalEnd = valueHasIntervalEnd
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => intervalValueIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = intervalValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: IntervalValueContentV2 =>
          savedValue.valueHasIntervalStart should ===(valueHasIntervalStart)
          savedValue.valueHasIntervalEnd should ===(valueHasIntervalEnd)

        case _ => throw AssertionException(s"Expected interval value, got $valueFromTriplestore")
      }
    }

    "not update an interval value without changing it" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri
      val valueHasIntervalStart = BigDecimal("1.23")
      val valueHasIntervalEnd = BigDecimal("3.45")

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = intervalValueIri.get,
          valueContent = IntervalValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasIntervalStart = valueHasIntervalStart,
            valueHasIntervalEnd = valueHasIntervalEnd
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update a list value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode = "http://rdfh.ch/lists/0001/treeList02"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = listValueIri.get,
          valueContent = HierarchicalListValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasListNode = valueHasListNode
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => listValueIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = listValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: HierarchicalListValueContentV2 =>
          savedValue.valueHasListNode should ===(valueHasListNode)

        case _ => throw AssertionException(s"Expected list value, got $valueFromTriplestore")
      }
    }

    "not update a list value without changing it" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode = "http://rdfh.ch/lists/0001/treeList02"

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = listValueIri.get,
          valueContent = HierarchicalListValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasListNode = valueHasListNode
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "not update a list value with the IRI of a nonexistent list node" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri
      val valueHasListNode = "http://rdfh.ch/lists/0001/nonexistent"

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = listValueIri.get,
          valueContent = HierarchicalListValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasListNode = valueHasListNode
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[NotFoundException])
      }
    }

    "update a color value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
      val valueHasColor = "#ff3334"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = colorValueIri.get,
          valueContent = ColorValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasColor = valueHasColor
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => colorValueIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = colorValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: ColorValueContentV2 =>
          savedValue.valueHasColor should ===(valueHasColor)

        case _ => throw AssertionException(s"Expected color value, got $valueFromTriplestore")
      }
    }

    "not update a color value without changing it" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri
      val valueHasColor = "#ff3334"

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = colorValueIri.get,
          valueContent = ColorValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasColor = valueHasColor
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update a URI value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
      val valueHasUri = "https://en.wikipedia.org"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = uriValueIri.get,
          valueContent = UriValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasUri = valueHasUri
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => uriValueIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = uriValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: UriValueContentV2 =>
          savedValue.valueHasUri should ===(valueHasUri)

        case _ => throw AssertionException(s"Expected URI value, got $valueFromTriplestore")
      }
    }

    "not update a URI value without changing it" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri
      val valueHasUri = "https://en.wikipedia.org"

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = uriValueIri.get,
          valueContent = UriValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasUri = valueHasUri
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update a geoname value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
      val valueHasGeonameCode = "2988507"
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = geonameValueIri.get,
          valueContent = GeonameValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasGeonameCode = valueHasGeonameCode
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => geonameValueIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = geonameValueIri.get,
        requestingUser = anythingUser1
      )

      valueFromTriplestore.valueContent match {
        case savedValue: GeonameValueContentV2 =>
          savedValue.valueHasGeonameCode should ===(valueHasGeonameCode)

        case _ => throw AssertionException(s"Expected GeoNames value, got $valueFromTriplestore")
      }
    }

    "not update a geoname value without changing it" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri
      val valueHasGeonameCode = "2988507"

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = propertyIri,
          valueIri = geonameValueIri.get,
          valueContent = GeonameValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasGeonameCode = valueHasGeonameCode
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update a link between two resources" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/iPS4V8KMQtCTcufBSCkcLw"
      val linkPropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri
      val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)

      val updateValueRequest = UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
          propertyIri = linkValuePropertyIri,
          valueIri = linkValueIri.get,
          valueContent = LinkValueContentV2(
            ontologySchema = ApiV2Complex,
            referredResourceIri = generationeIri
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! updateValueRequest

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 =>
          linkValueIri.set(updateValueResponse.valueIri)

          // When you change a link value's target, it gets a new UUID.
          assert(updateValueResponse.valueUUID != linkValueUUID)
          linkValueUUID = updateValueResponse.valueUUID
      }

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = linkPropertyIri,
        propertyIriInResult = linkValuePropertyIri,
        expectedValueIri = linkValueIri.get,
        requestingUser = incunabulaUser
      )

      valueFromTriplestore match {
        case readLinkValueV2: ReadLinkValueV2 =>
          readLinkValueV2.valueContent.referredResourceIri should ===(generationeIri)
          readLinkValueV2.valueHasRefCount should ===(1)

        case _ => throw AssertionException(s"Expected link value, got $valueFromTriplestore")
      }
    }

    "not update a link without a comment without changing it" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/iPS4V8KMQtCTcufBSCkcLw"
      val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri

      val updateValueRequest = UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
          propertyIri = linkValuePropertyIri,
          valueIri = linkValueIri.get,
          valueContent = LinkValueContentV2(
            ontologySchema = ApiV2Complex,
            referredResourceIri = generationeIri
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! updateValueRequest

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update a link, adding a comment" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/iPS4V8KMQtCTcufBSCkcLw"
      val linkPropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri
      val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)
      val comment: String = "Adding a comment"

      val updateValueRequest = UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
          propertyIri = linkValuePropertyIri,
          valueIri = linkValueIri.get,
          valueContent = LinkValueContentV2(
            ontologySchema = ApiV2Complex,
            referredResourceIri = generationeIri,
            comment = Some(comment)
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! updateValueRequest

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 =>
          linkValueIri.set(updateValueResponse.valueIri)

          // Since we only changed metadata, the link should have the same UUID.
          assert(updateValueResponse.valueUUID == linkValueUUID)
      }

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = linkPropertyIri,
        propertyIriInResult = linkValuePropertyIri,
        expectedValueIri = linkValueIri.get,
        requestingUser = incunabulaUser
      )

      valueFromTriplestore match {
        case readLinkValueV2: ReadLinkValueV2 =>
          readLinkValueV2.valueContent.referredResourceIri should ===(generationeIri)
          readLinkValueV2.valueHasRefCount should ===(1)
          assert(readLinkValueV2.valueContent.comment.contains(comment))

        case _ => throw AssertionException(s"Expected link value, got $valueFromTriplestore")
      }
    }

    "not update a link with a comment without changing it" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/iPS4V8KMQtCTcufBSCkcLw"
      val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri
      val comment: String = "Adding a comment"

      val updateValueRequest = UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
          propertyIri = linkValuePropertyIri,
          valueIri = linkValueIri.get,
          valueContent = LinkValueContentV2(
            ontologySchema = ApiV2Complex,
            referredResourceIri = generationeIri,
            comment = Some(comment)
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! updateValueRequest

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update a link with a comment, changing only the comment" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/iPS4V8KMQtCTcufBSCkcLw"
      val linkPropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri
      val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)
      val comment = "An updated comment"

      val updateValueRequest = UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
          propertyIri = linkValuePropertyIri,
          valueIri = linkValueIri.get,
          valueContent = LinkValueContentV2(
            ontologySchema = ApiV2Complex,
            referredResourceIri = generationeIri,
            comment = Some(comment)
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! updateValueRequest

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 =>
          linkValueIri.set(updateValueResponse.valueIri)

          // Since we only changed metadata, the link should have the same UUID.
          assert(updateValueResponse.valueUUID == linkValueUUID)
      }

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = linkPropertyIri,
        propertyIriInResult = linkValuePropertyIri,
        expectedValueIri = linkValueIri.get,
        requestingUser = incunabulaUser
      )

      valueFromTriplestore match {
        case readLinkValueV2: ReadLinkValueV2 =>
          readLinkValueV2.valueContent.referredResourceIri should ===(generationeIri)
          readLinkValueV2.valueHasRefCount should ===(1)
          assert(readLinkValueV2.valueContent.comment.contains(comment))

        case _ => throw AssertionException(s"Expected link value, got $valueFromTriplestore")
      }
    }

    "create a link with a comment" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/iPS4V8KMQtCTcufBSCkcLw"
      val linkPropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri
      val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, incunabulaUser)
      val comment = "Initial comment"

      val createValueRequest = CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          propertyIri = linkValuePropertyIri,
          resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
          valueContent = LinkValueContentV2(
            ontologySchema = ApiV2Complex,
            referredResourceIri = zeitglöckleinIri,
            comment = Some(comment)
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! createValueRequest

      expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => linkValueIri.set(createValueResponse.valueIri)
      }

      val valueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = linkPropertyIri,
        propertyIriInResult = linkValuePropertyIri,
        expectedValueIri = linkValueIri.get,
        requestingUser = incunabulaUser
      )

      valueFromTriplestore match {
        case readLinkValueV2: ReadLinkValueV2 =>
          readLinkValueV2.valueContent.referredResourceIri should ===(zeitglöckleinIri)
          readLinkValueV2.valueHasRefCount should ===(1)
          assert(readLinkValueV2.valueContent.comment.contains(comment))

        case _ => throw AssertionException(s"Expected link value, got $valueFromTriplestore")
      }
    }

    "not update a standoff link directly" in {
      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = zeitglöckleinIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          propertyIri = OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
          valueIri = zeitglöckleinCommentWithStandoffIri.get,
          valueContent = LinkValueContentV2(
            ontologySchema = ApiV2Complex,
            referredResourceIri = generationeIri
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.superUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[BadRequestException])
      }
    }

    "not update a still image file value without changing it" in {
      val resourceIri: IRI = aThingPictureIri
      stillImageFileValueIri.set("http://rdfh.ch/resources/h2TEa725XPK7CfG15Bk0bA/values/goZ7JFRNSeqF-dNxsqAS7Q")

      val valueContent = StillImageFileValueContentV2(
        ontologySchema = ApiV2Complex,
        fileValue = FileValueV2(
          internalFilename = "B1D0OkEgfFp-Cew2Seur7Wi.jp2",
          internalMimeType = "image/jp2",
          originalFilename = Some("test.tiff"),
          originalMimeType = Some("image/tiff")
        ),
        dimX = 512,
        dimY = 256
      )

      val updateValueRequest = UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture".toSmartIri,
          propertyIri = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri,
          valueIri = stillImageFileValueIri.get,
          valueContent = valueContent
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! updateValueRequest

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[DuplicateValueException])
      }
    }

    "update a still image file value" in {
      val resourceIri: IRI = aThingPictureIri
      val propertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      // Get the value before update.
      val previousValueFromTriplestore: ReadValueV2 = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = stillImageFileValueIri.get,
        requestingUser = anythingUser1,
        checkLastModDateChanged = false
      )

      // Update the value.

      val valueContent = StillImageFileValueContentV2(
        ontologySchema = ApiV2Complex,
        fileValue = FileValueV2(
          internalFilename = "updated-filename.jp2",
          internalMimeType = "image/jp2",
          originalFilename = Some("test.tiff"),
          originalMimeType = Some("image/tiff")
        ),
        dimX = 512,
        dimY = 256
      )

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture".toSmartIri,
          propertyIri = propertyIri,
          valueIri = stillImageFileValueIri.get,
          valueContent = valueContent
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => stillImageFileValueIri.set(updateValueResponse.valueIri)
      }

      // Read the value back to check that it was added correctly.

      val updatedValueFromTriplestore = getValue(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        expectedValueIri = stillImageFileValueIri.get,
        requestingUser = anythingUser1
      )

      updatedValueFromTriplestore.valueContent match {
        case savedValue: StillImageFileValueContentV2 =>
          savedValue should ===(valueContent)
          updatedValueFromTriplestore.permissions should ===(previousValueFromTriplestore.permissions)

        case _ => throw AssertionException(s"Expected still image file value, got $updatedValueFromTriplestore")
      }

    }

    "not return a Sipi error if Sipi fails to delete a temporary file when Knora rejects a request" in {
      val resourceIri: IRI = aThingPictureIri
      val propertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri

      val valueContent = StillImageFileValueContentV2(
        ontologySchema = ApiV2Complex,
        fileValue = FileValueV2(
          internalFilename = MockSipiConnector.FAILURE_FILENAME, // tells the mock Sipi responder to simulate failure
          internalMimeType = "image/jp2",
          originalFilename = Some("test.tiff"),
          originalMimeType = Some("image/tiff")
        ),
        dimX = 512,
        dimY = 256
      )

      // Knora will reject this request.
      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture".toSmartIri,
          propertyIri = propertyIri,
          valueIri = stillImageFileValueIri.get,
          valueContent = valueContent
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser, // this user doesn't have the necessary permission
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure =>
          assert(msg.cause.isInstanceOf[ForbiddenException])
      }
    }

    "return a Sipi error if Sipi fails to move a file to permanent storage" in {
      val resourceIri: IRI = aThingPictureIri
      val propertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri

      val valueContent = StillImageFileValueContentV2(
        ontologySchema = ApiV2Complex,
        fileValue = FileValueV2(
          internalFilename = MockSipiConnector.FAILURE_FILENAME, // tells the mock Sipi responder to simulate failure
          internalMimeType = "image/jp2",
          originalFilename = Some("test.tiff"),
          originalMimeType = Some("image/tiff")
        ),
        dimX = 512,
        dimY = 256
      )

      // Knora will accept this request, but the mock Sipi responder will say it failed to move the file to permanent storage.
      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture".toSmartIri,
          propertyIri = propertyIri,
          valueIri = stillImageFileValueIri.get,
          valueContent = valueContent
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[SipiException])
      }
    }

    "not delete a value if the requesting user does not have DeletePermission on the value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri

      responderManager ! DeleteValueRequestV2(
        resourceIri = resourceIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        propertyIri = propertyIri,
        valueIri = intValueIri.get,
        valueTypeIri = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
        deleteComment = Some("this value was incorrect"),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser2,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[ForbiddenException])
      }
    }

    "delete an integer value" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! DeleteValueRequestV2(
        resourceIri = resourceIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        propertyIri = propertyIri,
        valueIri = intValueIri.get,
        valueTypeIri = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
        deleteComment = Some("this value was incorrect"),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgType[SuccessResponseV2](timeout)

      checkValueIsDeleted(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        valueIri = intValueIri.get,
        requestingUser = anythingUser1
      )
    }

    "delete an integer value, specifying a custom delete date" in {
      val resourceIri: IRI = aThingIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)
      val deleteDate: Instant = Instant.now

      responderManager ! DeleteValueRequestV2(
        resourceIri = resourceIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        propertyIri = propertyIri,
        valueIri = intValueForRsyncIri.get,
        valueTypeIri = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
        deleteComment = Some("this value was incorrect"),
        deleteDate = Some(deleteDate),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      expectMsgType[SuccessResponseV2](timeout)

      checkValueIsDeleted(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        valueIri = intValueForRsyncIri.get,
        customDeleteDate = Some(deleteDate),
        requestingUser = anythingUser1
      )
    }

    "not delete a standoff link directly" in {
      responderManager ! DeleteValueRequestV2(
        resourceIri = zeitglöckleinIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
        valueIri = standoffLinkValueIri.get,
        valueTypeIri = OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.superUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[BadRequestException])
      }
    }

    "delete a text value with a standoff link" in {
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book_comment".toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(zeitglöckleinIri, incunabulaUser)

      responderManager ! DeleteValueRequestV2(
        resourceIri = zeitglöckleinIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = propertyIri,
        valueIri = zeitglöckleinCommentWithStandoffIri.get,
        valueTypeIri = OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri,
        deleteComment = Some("this value was incorrect"),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgType[SuccessResponseV2](timeout)

      checkValueIsDeleted(
        resourceIri = zeitglöckleinIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = propertyIri,
        propertyIriInResult = propertyIri,
        valueIri = zeitglöckleinCommentWithStandoffIri.get,
        requestingUser = incunabulaUser
      )

      // There should be no standoff link values left in the resource.

      val resource = getResourceWithValues(
        resourceIri = zeitglöckleinIri,
        propertyIrisForGravsearch = Seq(OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri),
        requestingUser = incunabulaUser
      )

      assert(resource.values.get(OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri).isEmpty)
    }

    "delete a link between two resources" in {
      val resourceIri: IRI = "http://rdfh.ch/resources/iPS4V8KMQtCTcufBSCkcLw"
      val linkPropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri
      val linkValuePropertyIri: SmartIri = OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri
      val maybeResourceLastModDate: Option[Instant] = getResourceLastModificationDate(resourceIri, anythingUser1)

      responderManager ! DeleteValueRequestV2(
        resourceIri = resourceIri,
        resourceClassIri = OntologyConstants.KnoraApiV2Complex.LinkObj.toSmartIri,
        propertyIri = linkValuePropertyIri,
        valueIri = linkValueIri.get,
        valueTypeIri = OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgType[SuccessResponseV2](timeout)

      checkValueIsDeleted(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybeResourceLastModDate,
        propertyIriForGravsearch = linkPropertyIri,
        propertyIriInResult = linkValuePropertyIri,
        valueIri = linkValueIri.get,
        requestingUser = anythingUser1
      )
    }

    "not delete a value if the property's cardinality doesn't allow it" in {
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri

      responderManager ! DeleteValueRequestV2(
        resourceIri = zeitglöckleinIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        propertyIri = propertyIri,
        valueIri = "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw/values/c3295339",
        valueTypeIri = OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaCreatorUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[OntologyConstraintException])
      }
    }

    "not accept custom value permissions that would give the requesting user a higher permission on a value than the default" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
        label = "test bildformat",
        values = Map.empty,
        projectADM = SharedTestDataADM.imagesProject,
        permissions = Some("M knora-admin:ProjectMember")
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.imagesUser01,
        apiRequestID = UUID.randomUUID
      )

      expectMsgClass(timeout, classOf[ReadResourcesSequenceV2])

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#stueckzahl".toSmartIri

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = 5,
            comment = Some("this is the number five")
          ),
          permissions = Some("CR knora-admin:Creator")
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.imagesReviewerUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => assert(msg.cause.isInstanceOf[ForbiddenException])
      }
    }

    "accept custom value permissions that would give the requesting user a higher permission on a value than the default if the user is a system admin" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
        label = "test bildformat",
        values = Map.empty,
        projectADM = SharedTestDataADM.imagesProject,
        permissions = Some("M knora-admin:ProjectMember")
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.imagesUser01,
        apiRequestID = UUID.randomUUID
      )

      expectMsgClass(timeout, classOf[ReadResourcesSequenceV2])

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#stueckzahl".toSmartIri

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = 5,
            comment = Some("this is the number five")
          ),
          permissions = Some("CR knora-admin:Creator")
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.rootUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgClass(classOf[CreateValueResponseV2])
    }

    "accept custom value permissions that would give the requesting user a higher permission on a value than the default if the user is a project admin" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
        label = "test bildformat",
        values = Map.empty,
        projectADM = SharedTestDataADM.imagesProject,
        permissions = Some("M knora-admin:ProjectMember")
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.imagesUser01,
        apiRequestID = UUID.randomUUID
      )

      expectMsgClass(timeout, classOf[ReadResourcesSequenceV2])

      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#stueckzahl".toSmartIri

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
          propertyIri = propertyIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = 5,
            comment = Some("this is the number five")
          ),
          permissions = Some("CR knora-admin:Creator")
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.imagesUser01,
        apiRequestID = UUID.randomUUID
      )

      expectMsgClass(classOf[CreateValueResponseV2])
    }

    "create and update text values with standoff links, managing value UUIDs correctly" in {
      val resourceClassIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext".toSmartIri

      // Create a text value with a standoff link.

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = sierraIri,
          resourceClassIri = resourceClassIri,
          propertyIri = propertyIri,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some("Comment 1 for UUID checking"),
            standoff = sampleStandoffWithLink,
            mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
            mapping = standardMapping
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      val textValue1Iri: IRI = expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => createValueResponse.valueIri
      }

      val resourceVersion1: ReadResourceV2 = getResourceWithValues(
        resourceIri = sierraIri,
        propertyIrisForGravsearch = Seq(
          propertyIri,
          OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri
        ),
        requestingUser = anythingUser1
      )

      // Get the UUIDs of the text value and of the standoff link value.
      val textValue1: ReadValueV2 = resourceVersion1.values(propertyIri).head
      assert(textValue1.valueIri == textValue1Iri)
      assert(getValueUUID(textValue1.valueIri).contains(textValue1.valueHasUUID))
      val standoffLinkValueVersion1: ReadLinkValueV2 = resourceVersion1
        .values(OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri)
        .head
        .asInstanceOf[ReadLinkValueV2]
      assert(getValueUUID(standoffLinkValueVersion1.valueIri).contains(standoffLinkValueVersion1.valueHasUUID))
      assert(standoffLinkValueVersion1.valueHasRefCount == 1)

      // Create a second text value with the same standoff link.

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = sierraIri,
          resourceClassIri = resourceClassIri,
          propertyIri = propertyIri,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some("Comment 2 for UUID checking"),
            standoff = sampleStandoffWithLink,
            mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
            mapping = standardMapping
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      val textValue2Version1Iri: IRI = expectMsgPF(timeout) {
        case createValueResponse: CreateValueResponseV2 => createValueResponse.valueIri
      }

      val resourceVersion2: ReadResourceV2 = getResourceWithValues(
        resourceIri = sierraIri,
        propertyIrisForGravsearch = Seq(
          propertyIri,
          OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri
        ),
        requestingUser = anythingUser1
      )

      // Get the second text value's UUID.
      val textValue2Version1: ReadValueV2 = resourceVersion2
        .values(propertyIri)
        .find(_.valueIri == textValue2Version1Iri)
        .getOrElse(throw AssertionException("Value not found"))
      assert(getValueUUID(textValue2Version1.valueIri).contains(textValue2Version1.valueHasUUID))

      // We should have a new version of the standoff link value, containing the UUID that was in the previous version.
      val standoffLinkValueVersion2: ReadLinkValueV2 = resourceVersion2
        .values(OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri)
        .head
        .asInstanceOf[ReadLinkValueV2]
      assert(standoffLinkValueVersion2.previousValueIri.contains(standoffLinkValueVersion1.valueIri))
      assert(standoffLinkValueVersion2.valueHasUUID == standoffLinkValueVersion1.valueHasUUID)
      assert(getValueUUID(standoffLinkValueVersion2.valueIri).contains(standoffLinkValueVersion2.valueHasUUID))
      assert(standoffLinkValueVersion2.valueHasRefCount == 2)

      // The previous version of the standoff link value should have no UUID.
      assert(getValueUUID(standoffLinkValueVersion1.valueIri).isEmpty)

      // Update the second text value.

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = sierraIri,
          resourceClassIri = resourceClassIri,
          propertyIri = propertyIri,
          valueIri = textValue2Version1Iri,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some("Comment 3 for UUID checking"),
            standoff = sampleStandoffWithLink,
            mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
            mapping = standardMapping
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      val textValue2Version2Iri = expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 => updateValueResponse.valueIri
      }

      val resourceVersion3: ReadResourceV2 = getResourceWithValues(
        resourceIri = sierraIri,
        propertyIrisForGravsearch = Seq(
          propertyIri,
          OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri
        ),
        requestingUser = anythingUser1
      )

      // We should now have a new version of the second text value, containing the UUID that was in the previous version.
      val textValue2Version2: ReadValueV2 = resourceVersion3
        .values(propertyIri)
        .find(_.valueIri == textValue2Version2Iri)
        .getOrElse(throw AssertionException("Value not found"))
      assert(getValueUUID(textValue2Version2.valueIri).contains(textValue2Version2.valueHasUUID))
      assert(textValue2Version2.previousValueIri.contains(textValue2Version1.valueIri))

      // The previous version of the second text value should have no UUID.
      assert(getValueUUID(textValue2Version1.valueIri).isEmpty)

      // We should not have a new version of the standoff link value.
      assert(
        resourceVersion3
          .values(OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri)
          .head
          .valueIri == standoffLinkValueVersion2.valueIri)
    }
  }
}
