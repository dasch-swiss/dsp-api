/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.values

import akka.testkit.ImplicitSender

import java.time.Instant
import java.util.UUID.randomUUID

import dsp.errors.AssertionException
import org.knora.webapi.CoreSpec
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesGetRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.DeleteValueV2
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataV2

class DeleteValuesV2Spec extends CoreSpec with ImplicitSender {
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

  private def checkValueIsDeleted(
    resourceIri: IRI,
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

  private val anythingUser1 = SharedTestDataADM.anythingUser1
  private val anythingUser2 = SharedTestDataADM.anythingUser2

  "The values responder" when {

    "deleting integer values" should {

      "delete an integer value that belongs to a property of another ontology" in {
        val resourceIri: IRI = "http://rdfh.ch/0001/freetest-with-a-property-from-anything-ontology"
        val valueIri: IRI =
          "http://rdfh.ch/0001/freetest-with-a-property-from-anything-ontology/values/CYWRc1iuQ3-pKgIZ1RPasA"

        val propertyIri: SmartIri = SharedTestDataV2.AnythingOntology.hasIntegerUsedByOtherOntologiesPropIriExternal
        val classIri: SmartIri =
          "http://0.0.0.0:3333/ontology/0001/freetest/v2#FreetestWithAPropertyFromAnythingOntology".toSmartIri
        val valueTypeIri = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri

        val deleteValue =
          DeleteValueV2(resourceIri, classIri, propertyIri, valueIri, valueTypeIri, Some("this value was incorrect"))
        UnsafeZioRun.runOrThrow(ValuesResponderV2.deleteValueV2(deleteValue, anythingUser2, randomUUID))

        checkValueIsDeleted(resourceIri = resourceIri, valueIri = valueIri, requestingUser = anythingUser2)
      }
    }
  }

}
