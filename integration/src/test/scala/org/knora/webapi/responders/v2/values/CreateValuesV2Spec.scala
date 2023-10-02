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
import org.knora.webapi.CoreSpec
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.searchmessages.GravsearchRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.CreateValueResponseV2
import org.knora.webapi.messages.v2.responder.valuemessages.CreateValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataV2
import org.knora.webapi.messages.util.PermissionUtilADM
import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import java.time.Instant

class CreateValuesV2Spec extends CoreSpec with ImplicitSender {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

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

  private def createValueOrThrow(createValue: CreateValueV2, user: UserADM, uuid: UUID): CreateValueResponseV2 =
    UnsafeZioRun.runOrThrow(ValuesResponderV2.createValueV2(createValue, user, uuid))

  private def doNotCreate[A <: Throwable: ClassTag](createValue: CreateValueV2, user: UserADM, uuid: UUID): Unit = {
    val res = UnsafeZioRun.run(ValuesResponderV2.createValueV2(createValue, user, uuid))
    assertFailsWithA[A](res)
  }

  private def getValue(resourceIri: IRI, propertyIri: SmartIri, valueIri: IRI, requestingUser: UserADM): ReadValueV2 = {
    val resource = getResourceWithValues(
      resourceIri = resourceIri,
      propertyIrisForGravsearch = Seq(propertyIri),
      requestingUser = requestingUser
    )
    getValueFromResource(
      resource = resource,
      propertyIriInResult = propertyIri,
      expectedValueIri = valueIri
    )
  }

  private def assertFailsWithA[T <: Throwable: ClassTag](actual: Exit[Throwable, _]) = actual match {
    case Exit.Failure(err) => err.squash shouldBe a[T]
    case _                 => fail(s"Expected Exit.Failure with specific T.")
  }

  // private def createUnformattedTextValue(
  //   valueHasString: String,
  //   propertyIri: SmartIri,
  //   resourceIri: IRI,
  //   resourceClassIri: SmartIri,
  //   user: UserADM,
  //   comment: Option[String] = None
  // ) =
  //   ValuesResponderV2.createValueV2(
  //     CreateValueV2(
  //       resourceIri = resourceIri,
  //       resourceClassIri = resourceClassIri,
  //       propertyIri = propertyIri,
  //       valueContent = UnformattedTextValueContentV2(
  //         ontologySchema = ApiV2Complex,
  //         valueHasString = valueHasString,
  //         comment = comment
  //       )
  //     ),
  //     requestingUser = user,
  //     apiRequestID = UUID.randomUUID
  //   )

  // private def createFormattedTextValue(
  //   valueHasString: String,
  //   standoff: Seq[StandoffTagV2],
  //   propertyIri: SmartIri,
  //   resourceIri: IRI,
  //   resourceClassIri: SmartIri,
  //   user: UserADM,
  //   comment: Option[String] = None
  // ) = ValuesResponderV2.createValueV2(
  //   CreateValueV2(
  //     resourceIri = resourceIri,
  //     resourceClassIri = resourceClassIri,
  //     propertyIri = propertyIri,
  //     valueContent = FormattedTextValueContentV2(
  //       ontologySchema = ApiV2Complex,
  //       valueHasString = valueHasString,
  //       comment = comment,
  //       mappingIri = OntologyConstants.KnoraBase.StandardMapping,
  //       mapping = Some(StandoffConstants.standardMapping),
  //       standoff = standoff
  //     )
  //   ),
  //   requestingUser = user,
  //   apiRequestID = UUID.randomUUID
  // )

  private val anythingUser1 = SharedTestDataADM.anythingUser1

  "The values responder" when {

    "provided custom permissions" should {

      "create an value with custom permissions" in {
        val propertyIri: SmartIri      = SharedTestDataV2.Values.Ontology.hasIntegerPropIriExternal
        val resourceClassIri: SmartIri = SharedTestDataV2.Values.Ontology.resourceClassIriExternal
        val resourceIri: IRI           = SharedTestDataV2.Values.Data.Resource1.resourceIri
        val intValue                   = 123
        val permissions                = "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher"

        val valueContent = IntegerValueContentV2(ApiV2Complex, intValue)
        val create =
          CreateValueV2(resourceIri, resourceClassIri, propertyIri, valueContent, permissions = Some(permissions))
        val response             = createValueOrThrow(create, anythingUser1, randomUUID)
        val valueFromTriplestore = getValue(resourceIri, propertyIri, response.valueIri, anythingUser1)

        val expected = PermissionUtilADM.parsePermissions(permissions)
        val actual   = PermissionUtilADM.parsePermissions(valueFromTriplestore.permissions)
        actual should ===(expected)
      }

      "not create a value with syntactically invalid custom permissions" in {
        val propertyIri: SmartIri      = SharedTestDataV2.Values.Ontology.hasIntegerPropIriExternal
        val resourceClassIri: SmartIri = SharedTestDataV2.Values.Ontology.resourceClassIriExternal
        val resourceIri: IRI           = SharedTestDataV2.Values.Data.Resource1.resourceIri
        val intValue                   = 124
        val permissions                = "M knora-admin:Creator,V knora-admin:KnownUser"

        val valueContent = IntegerValueContentV2(ApiV2Complex, intValue)
        val create =
          CreateValueV2(resourceIri, resourceClassIri, propertyIri, valueContent, permissions = Some(permissions))
        doNotCreate[BadRequestException](create, anythingUser1, randomUUID)
      }

      "not create a value with custom permissions referring to a nonexistent group" in {
        val propertyIri: SmartIri      = SharedTestDataV2.Values.Ontology.hasIntegerPropIriExternal
        val resourceClassIri: SmartIri = SharedTestDataV2.Values.Ontology.resourceClassIriExternal
        val resourceIri: IRI           = SharedTestDataV2.Values.Data.Resource1.resourceIri
        val intValue                   = 125
        val permissions                = "M knora-admin:Creator|V http://rdfh.ch/groups/0001/nonexistent-group"

        val valueContent = IntegerValueContentV2(ApiV2Complex, intValue)
        val create =
          CreateValueV2(resourceIri, resourceClassIri, propertyIri, valueContent, permissions = Some(permissions))
        doNotCreate[NotFoundException](create, anythingUser1, randomUUID)
      }
    }

    "provided custom UUIDs" should {

      "create a value with a custom UUID" in {
        val propertyIri: SmartIri      = SharedTestDataV2.Values.Ontology.hasIntegerPropIriExternal
        val resourceClassIri: SmartIri = SharedTestDataV2.Values.Ontology.resourceClassIriExternal
        val resourceIri: IRI           = SharedTestDataV2.Values.Data.Resource1.resourceIri
        val intValue                   = 126
        val valueUuid                  = randomUUID

        val valueContent = IntegerValueContentV2(ApiV2Complex, intValue)
        val create =
          CreateValueV2(resourceIri, resourceClassIri, propertyIri, valueContent, valueUUID = Some(valueUuid))
        val response = createValueOrThrow(create, anythingUser1, randomUUID)

        val valueFromTriplestore = getValue(resourceIri, propertyIri, response.valueIri, anythingUser1)
        valueFromTriplestore.valueHasUUID should ===(valueUuid)
      }
    }

    "provided custom custom creation dates" should {

      "create a value with a custom creation date" in {
        val propertyIri: SmartIri      = SharedTestDataV2.Values.Ontology.hasIntegerPropIriExternal
        val resourceClassIri: SmartIri = SharedTestDataV2.Values.Ontology.resourceClassIriExternal
        val resourceIri: IRI           = SharedTestDataV2.Values.Data.Resource1.resourceIri
        val intValue                   = 127
        val valueCreationDate          = Instant.now

        val valueContent = IntegerValueContentV2(ApiV2Complex, intValue)
        val create =
          CreateValueV2(
            resourceIri,
            resourceClassIri,
            propertyIri,
            valueContent,
            valueCreationDate = Some(valueCreationDate)
          )
        val response = createValueOrThrow(create, anythingUser1, randomUUID)

        val valueFromTriplestore = getValue(resourceIri, propertyIri, response.valueIri, anythingUser1)
        valueFromTriplestore.valueCreationDate should ===(valueCreationDate)
      }

    }

    "creating integer values" should {

      "create an integer value" in {
        val propertyIri: SmartIri      = SharedTestDataV2.Values.Ontology.hasIntegerPropIriExternal
        val resourceClassIri: SmartIri = SharedTestDataV2.Values.Ontology.resourceClassIriExternal
        val resourceIri: IRI           = SharedTestDataV2.Values.Data.Resource1.resourceIri
        val intValue                   = 42

        val valueContent = IntegerValueContentV2(ApiV2Complex, intValue)
        val create       = CreateValueV2(resourceIri, resourceClassIri, propertyIri, valueContent)
        val response     = createValueOrThrow(create, anythingUser1, randomUUID)

        val valueFromTriplestore = getValue(resourceIri, propertyIri, response.valueIri, anythingUser1)
        valueFromTriplestore.valueContent match {
          case savedValue: IntegerValueContentV2 => savedValue.valueHasInteger should ===(intValue)
          case _                                 => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
        }
      }

      "create an integer value with a comment" in {
        val propertyIri: SmartIri      = SharedTestDataV2.Values.Ontology.hasIntegerPropIriExternal
        val resourceClassIri: SmartIri = SharedTestDataV2.Values.Ontology.resourceClassIriExternal
        val resourceIri: IRI           = SharedTestDataV2.Values.Data.Resource1.resourceIri
        val intValue                   = 43
        val comment                    = Some("A Comment")

        val valueContent        = IntegerValueContentV2(ApiV2Complex, intValue, comment)
        val create              = CreateValueV2(resourceIri, resourceClassIri, propertyIri, valueContent)
        val createValueResponse = createValueOrThrow(create, anythingUser1, randomUUID)

        val valueFromTriplestore = getValue(resourceIri, propertyIri, createValueResponse.valueIri, anythingUser1)
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
        val resourceClassIri =
          "http://0.0.0.0:3333/ontology/0001/freetest/v2#FreetestWithAPropertyFromAnythingOntology".toSmartIri
        val valueUuid = randomUUID()
        val intValue  = 40

        val valueContent        = IntegerValueContentV2(ApiV2Complex, intValue)
        val create              = CreateValueV2(resourceIri, resourceClassIri, propertyIri, valueContent)
        val createValueResponse = createValueOrThrow(create, anythingUser1, valueUuid)

        val intValueIriForFreetestIri = createValueResponse.valueIri
        val valueFromTriplestore      = getValue(resourceIri, propertyIri, intValueIriForFreetestIri, anythingUser1)
        valueFromTriplestore.valueContent match {
          case savedValue: IntegerValueContentV2 => savedValue.valueHasInteger should ===(intValue)
          case _                                 => throw AssertionException(s"Expected integer value, got $valueFromTriplestore")
        }
      }

    }

  }

}
