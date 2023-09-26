/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.values

import akka.testkit.ImplicitSender
import zio.Exit

import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID
import scala.reflect.ClassTag

import dsp.errors.AssertionException
import dsp.errors.DuplicateValueException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.CoreSpec
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StandoffConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.SparqlSelectRequest
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesGetRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesPreviewGetRequestV2
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffTagV2
import org.knora.webapi.messages.v2.responder.valuemessages.CreateValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.FormattedTextValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.UnformattedTextValueContentV2
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM

class CreateValuesV2Spec extends CoreSpec with ImplicitSender {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /* we need to run our app with the mocked sipi implementation */
  override type Environment = core.LayersTest.DefaultTestEnvironmentWithoutSipi
  override lazy val effectLayers = core.LayersTest.integrationTestsWithFusekiTestcontainers()

  override lazy val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/project_ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest"
    ),
    RdfDataObject(path = "test_data/project_data/freetest-data.ttl", name = "http://www.knora.org/data/0001/freetest"),
    RdfDataObject(
      path = "test_data/generated_test_data/responders.v2.ValuesResponderV2Spec/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula"
    ),
    RdfDataObject(path = "test_data/project_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(
      path = "test_data/project_ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything"
    )
  )

  private def getResourceWithValues(
    resourceIri: IRI,
    propertyIrisForGravsearch: Seq[SmartIri],
    requestingUser: UserADM
  ): ReadResourceV2 = {
    // Make a Gravsearch query from a template.
    val gravsearchQuery: String = org.knora.webapi.messages.twirl.queries.gravsearch.txt
      .getResourceWithSpecifiedProperties(
        resourceIri = resourceIri,
        propertyIris = propertyIrisForGravsearch
      )
      .toString()

    // Run the query.

    val parsedGravsearchQuery = GravsearchParser.parseQuery(gravsearchQuery)

    appActor ! GravsearchRequestV2(
      constructQuery = parsedGravsearchQuery,
      targetSchema = ApiV2Complex,
      schemaOptions = SchemaOptions.ForStandoffWithTextValues,
      requestingUser = requestingUser
    )

    expectMsgPF(timeout) { case searchResponse: ReadResourcesSequenceV2 =>
      searchResponse.toResource(resourceIri).toOntologySchema(ApiV2Complex)
    }
  }

  private def getValuesFromResource(resource: ReadResourceV2, propertyIriInResult: SmartIri): Seq[ReadValueV2] =
    resource.values.getOrElse(
      propertyIriInResult,
      throw AssertionException(s"Resource <${resource.resourceIri}> does not have property <$propertyIriInResult>")
    )

  private def getValueFromResource(
    resource: ReadResourceV2,
    propertyIriInResult: SmartIri,
    expectedValueIri: IRI
  ): ReadValueV2 = {
    val propertyValues: Seq[ReadValueV2] =
      getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult)
    propertyValues
      .find(_.valueIri == expectedValueIri)
      .getOrElse(
        throw AssertionException(
          s"Property <$propertyIriInResult> of resource <${resource.resourceIri}> does not have value <$expectedValueIri>"
        )
      )
  }

  private def checkValueIsDeleted(
    resourceIri: IRI,
    maybePreviousLastModDate: Option[Instant],
    valueIri: IRI,
    customDeleteDate: Option[Instant] = None,
    deleteComment: Option[String] = None,
    requestingUser: UserADM,
    isLinkValue: Boolean = false
  ): Unit = {
    appActor ! ResourcesGetRequestV2(
      resourceIris = Seq(resourceIri),
      targetSchema = ApiV2Complex,
      requestingUser = requestingUser
    )

    val resource = expectMsgPF(timeout) { case getResponse: ReadResourcesSequenceV2 =>
      getResponse.toResource(resourceIri)
    }
    //  ensure the resource was not deleted
    resource.deletionInfo should be(None)

    val deletedValues = resource.values.getOrElse(
      OntologyConstants.KnoraBase.DeletedValue.toSmartIri,
      throw AssertionException(
        s"Resource <$resourceIri> does not have any deleted values, even though value <$valueIri> should be deleted."
      )
    )

    if (!isLinkValue) {
      // not a LinkValue, so the value should be a DeletedValue of the resource
      val deletedValue = deletedValues.collectFirst { case v if v.valueIri == valueIri => v }
        .getOrElse(throw AssertionException(s"Value <$valueIri> was not among the deleted resources"))

      checkLastModDate(
        resourceIri = resourceIri,
        maybePreviousLastModDate = maybePreviousLastModDate,
        maybeUpdatedLastModDate = resource.lastModificationDate
      )

      val deletionInfo = deletedValue.deletionInfo.getOrElse(
        throw AssertionException(s"Value <$valueIri> does not have deletion information")
      )

      customDeleteDate match {
        case Some(deleteDate) => deletionInfo.deleteDate should equal(deleteDate)
        case None             => ()
      }

      deleteComment match {
        case Some(comment) => deletionInfo.maybeDeleteComment.get should equal(comment)
        case None          => ()
      }
    } else {
      // The value is a LinkValue, so there should be a DeletedValue having a PreviousValue with the IRI of the value.
      if (
        !deletedValues.exists(v =>
          v.previousValueIri match {
            case Some(previousValueIRI) => previousValueIRI == valueIri
            case None                   => false
          }
        )
      ) throw AssertionException(s"ListValue <$valueIri> was not deleted correctly.")
    }
  }

  private def checkLastModDate(
    resourceIri: IRI,
    maybePreviousLastModDate: Option[Instant],
    maybeUpdatedLastModDate: Option[Instant]
  ): Unit =
    maybeUpdatedLastModDate match {
      case Some(updatedLastModDate) =>
        maybePreviousLastModDate match {
          case Some(previousLastModDate) => assert(updatedLastModDate.isAfter(previousLastModDate))
          case None                      => ()
        }

      case None => throw AssertionException(s"Resource $resourceIri has no knora-base:lastModificationDate")
    }

  private def getValue(
    resourceIri: IRI,
    maybePreviousLastModDate: Option[Instant] = None,
    propertyIriForGravsearch: SmartIri,
    propertyIriInResult: SmartIri,
    expectedValueIri: IRI,
    requestingUser: UserADM,
    checkLastModDateChanged: Boolean = true
  ): ReadValueV2 = {
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
    appActor ! ResourcesPreviewGetRequestV2(
      resourceIris = Seq(resourceIri),
      targetSchema = ApiV2Complex,
      requestingUser = requestingUser
    )

    expectMsgPF(timeout) { case previewResponse: ReadResourcesSequenceV2 =>
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

    appActor ! SparqlSelectRequest(sparqlQuery)

    expectMsgPF(timeout) { case response: SparqlSelectResult =>
      val rows = response.results.bindings

      if (rows.isEmpty) {
        None
      } else if (rows.size > 1) {
        throw AssertionException(s"Expected one knora-base:valueHasUUID, got ${rows.size}")
      } else {
        Some(UuidUtil.base64Decode(rows.head.rowMap("valueUUID")).get)
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

    appActor ! SparqlSelectRequest(sparqlQuery)

    expectMsgPF(timeout) { case response: SparqlSelectResult =>
      val rows = response.results.bindings

      if (rows.isEmpty) {
        None
      } else if (rows.size > 1) {
        throw AssertionException(s"Expected one knora-base:hasPermissions, got ${rows.size}")
      } else {
        Some(UuidUtil.base64Decode(rows.head.rowMap("valuePermissions")).get)
      }
    }
  }

  private def assertFailsWithA[T <: Throwable: ClassTag](actual: Exit[Throwable, _]) = actual match {
    case Exit.Failure(err) => err.squash shouldBe a[T]
    case _                 => fail(s"Expected Exit.Failure with specific T.")
  }

  private def createUnformattedTextValue(
    valueHasString: String,
    propertyIri: SmartIri,
    resourceIri: IRI,
    resourceClassIri: SmartIri,
    user: UserADM,
    comment: Option[String] = None
  ) =
    ValuesResponderV2.createValueV2(
      CreateValueV2(
        resourceIri = resourceIri,
        resourceClassIri = resourceClassIri,
        propertyIri = propertyIri,
        valueContent = UnformattedTextValueContentV2(
          ontologySchema = ApiV2Complex,
          valueHasString = valueHasString,
          comment = comment
        )
      ),
      requestingUser = user,
      apiRequestID = UUID.randomUUID
    )

  private def createFormattedTextValue(
    valueHasString: String,
    standoff: Seq[StandoffTagV2],
    propertyIri: SmartIri,
    resourceIri: IRI,
    resourceClassIri: SmartIri,
    user: UserADM,
    comment: Option[String] = None
  ) = ValuesResponderV2.createValueV2(
    CreateValueV2(
      resourceIri = resourceIri,
      resourceClassIri = resourceClassIri,
      propertyIri = propertyIri,
      valueContent = FormattedTextValueContentV2(
        ontologySchema = ApiV2Complex,
        valueHasString = valueHasString,
        comment = comment,
        mappingIri = OntologyConstants.KnoraBase.StandardMapping,
        mapping = Some(StandoffConstants.standardMapping),
        standoff = standoff
      )
    ),
    requestingUser = user,
    apiRequestID = UUID.randomUUID
  )

  private val aThingIri = "http://rdfh.ch/0001/a-thing"

  private val anythingUser1 = SharedTestDataADM.anythingUser1
  private val anythingUser2 = SharedTestDataADM.anythingUser2

  "The values responder" when {

    "creating integer values" should {

      "create an integer value" in {
        // Add the value.
        val resourceIri: IRI      = aThingIri
        val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
        val intValue              = 4

        val createValueResponse = UnsafeZioRun.runOrThrow(
          ValuesResponderV2.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue
              )
            ),
            requestingUser = anythingUser1,
            apiRequestID = randomUUID
          )
        )

        val valueFromTriplestore = getValue(
          resourceIri = resourceIri,
          propertyIriForGravsearch = propertyIri,
          propertyIriInResult = propertyIri,
          expectedValueIri = createValueResponse.valueIri,
          requestingUser = anythingUser1
        )

        valueFromTriplestore.valueContent match {
          case savedValue: IntegerValueContentV2 => savedValue.valueHasInteger should ===(intValue)
          case _                                 => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
        }
      }

      "not create a duplicate integer value" in {
        val resourceIri      = aThingIri
        val resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri
        val propertyIri      = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
        val intVal           = IntegerValueContentV2(ApiV2Complex, 4)
        val duplicateValue   = CreateValueV2(resourceIri, resourceClassIri, propertyIri, intVal)

        val actual = UnsafeZioRun.run(ValuesResponderV2.createValueV2(duplicateValue, anythingUser1, randomUUID))
        assertFailsWithA[DuplicateValueException](actual)
      }

      "create an integer value that belongs to a property of another ontology" in {
        val resourceIri: IRI = "http://rdfh.ch/0001/freetest-with-a-property-from-anything-ontology"
        val propertyIri: SmartIri =
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasIntegerUsedByOtherOntologies".toSmartIri
        val valueUuid = randomUUID()

        // Create the value.
        val intValue = 40

        val createValueResponse = UnsafeZioRun.runOrThrow(
          ValuesResponderV2.createValueV2(
            CreateValueV2(
              resourceIri = resourceIri,
              resourceClassIri =
                "http://0.0.0.0:3333/ontology/0001/freetest/v2#FreetestWithAPropertyFromAnythingOntology".toSmartIri,
              propertyIri = propertyIri,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue
              )
            ),
            requestingUser = anythingUser1,
            apiRequestID = valueUuid
          )
        )

        val intValueIriForFreetestIri = createValueResponse.valueIri

        // Read the value back to check that it was added correctly.

        val valueFromTriplestore = getValue(
          resourceIri = resourceIri,
          propertyIriForGravsearch = propertyIri,
          propertyIriInResult = propertyIri,
          expectedValueIri = intValueIriForFreetestIri,
          requestingUser = anythingUser1
        )

        valueFromTriplestore.valueContent match {
          case savedValue: IntegerValueContentV2 => savedValue.valueHasInteger should ===(intValue)
          case _                                 => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
        }
      }

    }

  }

}
