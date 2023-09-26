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
import org.knora.webapi.CoreSpec
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StandoffConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
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
import org.knora.webapi.sharedtestdata.SharedTestDataV2

class CreateValuesV2Spec extends CoreSpec with ImplicitSender {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /* we need to run our app with the mocked sipi implementation */
  override type Environment = core.LayersTest.DefaultTestEnvironmentWithoutSipi
  override lazy val effectLayers = core.LayersTest.integrationTestsWithFusekiTestcontainers()

  override lazy val rdfDataObjects = List(
    RdfDataObject("test_data/project_ontologies/freetest-onto.ttl", "http://www.knora.org/ontology/0001/freetest"),
    RdfDataObject("test_data/project_data/freetest-data.ttl", "http://www.knora.org/data/0001/anything"),
    RdfDataObject("test_data/project_data/images-demo-data.ttl", "http://www.knora.org/data/00FF/images"),
    RdfDataObject("test_data/project_data/anything-data.ttl", "http://www.knora.org/data/0001/anything"),
    RdfDataObject("test_data/project_ontologies/anything-onto.ttl", "http://www.knora.org/ontology/0001/anything"),
    RdfDataObject("test_data/project_ontologies/values-onto.ttl", "http://www.knora.org/ontology/0001/values"),
    RdfDataObject("test_data/project_data/values-data.ttl", "http://www.knora.org/data/0001/anything")
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

  private val anythingUser1 = SharedTestDataADM.anythingUser1

  "The values responder" when {

    "creating integer values" should {

      "create an integer value" in {
        // Add the value.
        val propertyIri: SmartIri      = SharedTestDataV2.Values.Ontology.hasIntegerPropIriExternal
        val resourceClassIri: SmartIri = SharedTestDataV2.Values.Ontology.resourceClassIriExternal
        val resourceIri: IRI           = SharedTestDataV2.Values.Data.Resource1.resourceIri
        val intValue                   = 42

        val createValue =
          CreateValueV2(resourceIri, resourceClassIri, propertyIri, IntegerValueContentV2(ApiV2Complex, intValue))
        val createValueResponse =
          UnsafeZioRun.runOrThrow(ValuesResponderV2.createValueV2(createValue, anythingUser1, randomUUID))

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
        val intVal                     = IntegerValueContentV2(ApiV2Complex, 1)
        val propertyIri: SmartIri      = SharedTestDataV2.Values.Ontology.hasIntegerPropIriExternal
        val resourceClassIri: SmartIri = SharedTestDataV2.Values.Ontology.resourceClassIriExternal
        val resourceIri: IRI           = SharedTestDataV2.Values.Data.Resource1.resourceIri
        val duplicateValue             = CreateValueV2(resourceIri, resourceClassIri, propertyIri, intVal)
        val actual                     = UnsafeZioRun.run(ValuesResponderV2.createValueV2(duplicateValue, anythingUser1, randomUUID))
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
