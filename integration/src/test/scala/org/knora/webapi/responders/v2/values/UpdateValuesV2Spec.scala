/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.values

import akka.testkit.ImplicitSender
import zio.Exit

import java.util.UUID
import java.util.UUID.randomUUID
import scala.reflect.ClassTag

import dsp.errors.AssertionException
import dsp.errors.DuplicateValueException
import dsp.valueobjects.UuidUtil
import org.knora.webapi.CoreSpec
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.SparqlSelectRequest
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.UpdateValueContentV2
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataV2

class UpdateValuesV2Spec extends CoreSpec with ImplicitSender {
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

  private def getValue(
    resourceIri: IRI,
    propertyIriForGravsearch: SmartIri,
    propertyIriInResult: SmartIri,
    expectedValueIri: IRI,
    requestingUser: UserADM
  ): ReadValueV2 = {
    val resource = getResourceWithValues(
      resourceIri = resourceIri,
      propertyIrisForGravsearch = Seq(propertyIriForGravsearch),
      requestingUser = requestingUser
    )
    getValueFromResource(
      resource = resource,
      propertyIriInResult = propertyIriInResult,
      expectedValueIri = expectedValueIri
    )
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

  private val anythingUser1 = SharedTestDataADM.anythingUser1
  private val anythingUser2 = SharedTestDataADM.anythingUser2

  "The values responder" when {

    "updating values" should {

      "remove UUID and permissions from the previous value" in {
        val resourceClassIri: SmartIri = SharedTestDataV2.Values.Ontology.resourceClassIriExternal
        val propertyIri: SmartIri      = SharedTestDataV2.Values.Ontology.hasIntegerPropIriExternal
        val resourceIri: IRI           = SharedTestDataV2.Values.Data.Resource1.resourceIri
        val valueIri: IRI              = SharedTestDataV2.Values.Data.Resource1.IntValue1.valueIri
        val valueUuid: UUID            = SharedTestDataV2.Values.Data.Resource1.IntValue1.valueUuid
        val valuePermissions: String   = SharedTestDataV2.Values.Data.Resource1.IntValue1.permissions

        val newValue    = IntegerValueContentV2(ApiV2Complex, -1)
        val updateValue = UpdateValueContentV2(resourceIri, resourceClassIri, propertyIri, valueIri, newValue)
        val response = UnsafeZioRun.runOrThrow(
          ValuesResponderV2.updateValueV2(updateValue, SharedTestDataADM.anythingUser1, randomUUID)
        )
        val newValueIri  = response.valueIri
        val updatedValue = getValue(resourceIri, propertyIri, propertyIri, newValueIri, anythingUser1)

        assert(valueIri != newValueIri)
        assert(updatedValue.valueHasUUID == valueUuid)
        assert(getValueUUID(valueIri).isEmpty)
        assert(updatedValue.permissions == valuePermissions)
        assert(getValuePermissions(valueIri).isEmpty)
      }

    }

    "updating integer values" should {

      "update an integer value" in {
        val resourceIri: IRI      = SharedTestDataV2.Anything.resource1.resourceIri
        val propertyIri: SmartIri = SharedTestDataV2.AnythingOntology.hasIntegerPropIriExternal
        val intValueIri: IRI      = SharedTestDataV2.Anything.resouce1value1.valueIri

        // Update the value.

        val intValue = 5

        val updateValueResponse = UnsafeZioRun.runOrThrow(
          ValuesResponderV2.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
              propertyIri = propertyIri,
              valueIri = intValueIri,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue
              )
            ),
            anythingUser1,
            randomUUID
          )
        )
        val updatedValueIri = updateValueResponse.valueIri

        // Read the value back to check that it was added correctly.

        val updatedValueFromTriplestore = getValue(
          resourceIri = resourceIri,
          propertyIriForGravsearch = propertyIri,
          propertyIriInResult = propertyIri,
          expectedValueIri = updatedValueIri,
          requestingUser = anythingUser1
        )

        updatedValueFromTriplestore.valueContent match {
          case savedValue: IntegerValueContentV2 =>
            savedValue.valueHasInteger should ===(intValue)
            updatedValueFromTriplestore.permissions should ===(SharedTestDataV2.Anything.resouce1value1.permissions)
            updatedValueFromTriplestore.valueHasUUID should ===(SharedTestDataV2.Anything.resouce1value1.valueHasUUID)

          case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
        }

        // Check that the permissions and UUID were deleted from the previous version of the value.
        assert(getValueUUID(intValueIri).isEmpty)
        assert(getValuePermissions(intValueIri).isEmpty)
      }

      "update an integer value that belongs to a property of another ontology" in {
        val resourceIri: IRI = "http://rdfh.ch/0001/freetest-with-a-property-from-anything-ontology"
        val valueIri: IRI =
          "http://rdfh.ch/0001/freetest-with-a-property-from-anything-ontology/values/CYWRc1iuQ3-pKgIZ1RPasA"
        val propertyIri: SmartIri = SharedTestDataV2.AnythingOntology.hasIntegerUsedByOtherOntologiesPropIriExternal

        // Get the value before update.
        val previousValueFromTriplestore: ReadValueV2 = getValue(
          resourceIri = resourceIri,
          propertyIriForGravsearch = propertyIri,
          propertyIriInResult = propertyIri,
          expectedValueIri = valueIri,
          requestingUser = anythingUser2
        )

        // Update the value.
        val intValue = 50
        val updateValueContent: UpdateValueContentV2 = UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri =
            "http://0.0.0.0:3333/ontology/0001/freetest/v2#FreetestWithAPropertyFromAnythingOntology".toSmartIri,
          propertyIri = propertyIri,
          valueIri = valueIri,
          valueContent = IntegerValueContentV2(
            ontologySchema = ApiV2Complex,
            valueHasInteger = intValue
          )
        )

        val updateValueResponse =
          UnsafeZioRun.runOrThrow(ValuesResponderV2.updateValueV2(updateValueContent, anythingUser2, randomUUID))

        val updatedValueIri = updateValueResponse.valueIri
        assert(updateValueResponse.valueUUID == previousValueFromTriplestore.valueHasUUID)

        // Read the value back to check that it was added correctly.
        val updatedValueFromTriplestore = getValue(
          resourceIri = resourceIri,
          propertyIriForGravsearch = propertyIri,
          propertyIriInResult = propertyIri,
          expectedValueIri = updatedValueIri,
          requestingUser = anythingUser2
        )

        updatedValueFromTriplestore.valueContent match {
          case savedValue: IntegerValueContentV2 =>
            savedValue.valueHasInteger should ===(intValue)
            updatedValueFromTriplestore.permissions should ===(previousValueFromTriplestore.permissions)
            updatedValueFromTriplestore.valueHasUUID should ===(previousValueFromTriplestore.valueHasUUID)

          case _ => throw AssertionException(s"Expected integer value, got $updatedValueFromTriplestore")
        }

        // Check that the permissions and UUID were deleted from the previous version of the value.
        assert(getValueUUID(valueIri).isEmpty)
        assert(getValuePermissions(valueIri).isEmpty)
      }

      "not update an integer value without changing it" in {
        val resourceIri: IRI      = SharedTestDataV2.Anything.Resource3.resourceIri
        val propertyIri: SmartIri = SharedTestDataV2.AnythingOntology.hasIntegerPropIriExternal
        val classIri: SmartIri    = SharedTestDataV2.AnythingOntology.thingClassIriExternal
        val intValue              = 123454321
        val intValueIri: IRI      = SharedTestDataV2.Anything.Resource3.intValueIri

        val actual = UnsafeZioRun.run(
          ValuesResponderV2.updateValueV2(
            UpdateValueContentV2(
              resourceIri = resourceIri,
              resourceClassIri = classIri,
              propertyIri = propertyIri,
              valueIri = intValueIri,
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = intValue,
                comment = Some("visible int value in main resource")
              )
            ),
            anythingUser1,
            randomUUID
          )
        )
        assertFailsWithA[DuplicateValueException](actual)
      }

      "update an integer value adding a comment" in {}
      "update an integer value updating a comment" in {}
      "update an integer value removing a comment" in {}

    }

    "updating link values" should {

      "..." in {}

    }

  }

}
