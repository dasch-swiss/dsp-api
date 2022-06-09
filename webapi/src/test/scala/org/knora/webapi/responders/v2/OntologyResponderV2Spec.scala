/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import akka.testkit.ImplicitSender
import org.knora.webapi._
import dsp.errors._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.v2.responder.CanDoResponseV2
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateValueInNewResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.MutableTestIri

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Tests [[OntologyResponderV2]].
 */
class OntologyResponderV2Spec extends CoreSpec() with ImplicitSender {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  override lazy val rdfDataObjects: List[RdfDataObject] =
    List(exampleSharedOntology, anythingData, freeTestOntology, freeTestData)
  private val imagesUser           = SharedTestDataADM.imagesUser01
  private val imagesProjectIri     = SharedTestDataADM.IMAGES_PROJECT_IRI.toSmartIri
  private val anythingAdminUser    = SharedTestDataADM.anythingAdminUser
  private val anythingNonAdminUser = SharedTestDataADM.anythingUser1
  private val anythingProjectIri   = SharedTestDataADM.ANYTHING_PROJECT_IRI.toSmartIri
  private val exampleSharedOntology = RdfDataObject(
    path = "test_data/ontologies/example-box.ttl",
    name = "http://www.knora.org/ontology/shared/example-box"
  )
  private val anythingData =
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  private val freeTestOntology =
    RdfDataObject(path = "test_data/ontologies/freetest-onto.ttl", name = "http://www.knora.org/ontology/0001/freetest")
  private val freeTestData =
    RdfDataObject(path = "test_data/all_data/freetest-data.ttl", name = "http://www.knora.org/data/0001/freetest")
  // The default timeout for receiving reply messages from actors.
  private val timeout                      = 10.seconds
  private val fooIri                       = new MutableTestIri
  private val barIri                       = new MutableTestIri
  private val chairIri                     = new MutableTestIri
  private val ExampleSharedOntologyIri     = "http://api.knora.org/ontology/shared/example-box/v2".toSmartIri
  private val IncunabulaOntologyIri        = "http://0.0.0.0:3333/ontology/0803/incunabula/v2".toSmartIri
  private val AnythingOntologyIri          = "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri
  private val FreeTestOntologyIri          = "http://0.0.0.0:3333/ontology/0001/freetest/v2".toSmartIri
  private val printErrorMessages           = false
  private var fooLastModDate: Instant      = Instant.now
  private var barLastModDate: Instant      = Instant.now
  private var chairLastModDate: Instant    = Instant.now
  private var anythingLastModDate: Instant = Instant.parse("2017-12-19T15:23:42.166Z")
  private var freetestLastModDate: Instant = Instant.parse("2012-12-12T12:12:12.12Z")

  val anythingOntology                     = "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  val anythingThing: IRI                   = anythingOntology + "Thing"
  val anythingHasBoolean: IRI              = anythingOntology + "hasBoolean"
  val anythingHasColor: IRI                = anythingOntology + "hasColor"
  val anythingHasDate: IRI                 = anythingOntology + "hasDate"
  val anythingHasDecimal: IRI              = anythingOntology + "hasDecimal"
  val anythingHasGeometry: IRI             = anythingOntology + "hasGeometry"
  val anythingHasGeoname: IRI              = anythingOntology + "hasGeoname"
  val anythingHasInteger: IRI              = anythingOntology + "hasInteger"
  val anythingHasInterval: IRI             = anythingOntology + "hasInterval"
  val anythingHasListItem: IRI             = anythingOntology + "hasListItem"
  val anythingHasName: IRI                 = anythingOntology + "hasName"
  val anythingHasOtherListItem: IRI        = anythingOntology + "hasOtherListItem"
  val anythingHasOtherThing: IRI           = anythingOntology + "hasOtherThing"
  val anythingHasOtherThingValue: IRI      = anythingOntology + "hasOtherThingValue"
  val anythingHasRichtext: IRI             = anythingOntology + "hasRichtext"
  val anythingHasText: IRI                 = anythingOntology + "hasText"
  val anythingHasThingDocument: IRI        = anythingOntology + "hasThingDocument"
  val anythingHasThingDocumentValue: IRI   = anythingOntology + "hasThingDocumentValue"
  val anythingHasThingPicture: IRI         = anythingOntology + "hasThingPicture"
  val anythingHasThingPictureValue: IRI    = anythingOntology + "hasThingPictureValue"
  val anythingHasTimeStamp: IRI            = anythingOntology + "hasTimeStamp"
  val anythingHasUri: IRI                  = anythingOntology + "hasUri"
  val anythingIsPartOfOtherThing: IRI      = anythingOntology + "isPartOfOtherThing"
  val anythingIsPartOfOtherThingValue: IRI = anythingOntology + "isPartOfOtherThingValue"
  val anythingHasStandoffLinkTo: IRI       = "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo"
  val anythingHasStandoffLinkToValue: IRI  = "http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkToValue"

  private def loadInvalidTestData(rdfDataObjs: List[RdfDataObject]): Unit = {
    storeManager ! ResetRepositoryContent(rdfDataObjs)
    expectMsg(5 minutes, ResetRepositoryContentACK())

    responderManager ! LoadOntologiesRequestV2(
      requestingUser = KnoraSystemInstances.Users.SystemUser
    )

    val response: SuccessResponseV2 = expectMsgType[SuccessResponseV2](10.seconds)
    assert(response.message.contains("An error occurred when loading ontologies"))
  }

  "The ontology responder v2" should {
    "not allow a user to create an ontology if they are not a sysadmin or an admin in the ontology's project" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "foo",
        projectIri = imagesProjectIri,
        label = "The foo ontology",
        apiRequestID = UUID.randomUUID,
        requestingUser = SharedTestDataADM.imagesUser02
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }
    }

    "create an empty ontology called 'foo' with a project code" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "foo",
        projectIri = imagesProjectIri,
        label = "The foo ontology",
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      val response = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri.toString == "http://www.knora.org/ontology/00FF/foo")
      fooIri.set(metadata.ontologyIri.toString)
      fooLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
      )
    }

    "change the label in the metadata of 'foo'" in {
      val newLabel = "The modified foo ontology"

      responderManager ! ChangeOntologyMetadataRequestV2(
        ontologyIri = fooIri.get.toSmartIri.toOntologySchema(ApiV2Complex),
        label = Some(newLabel),
        lastModificationDate = fooLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      val response = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri == fooIri.get.toSmartIri)
      assert(metadata.label.contains(newLabel))
      val newFooLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
      )
      assert(newFooLastModDate.isAfter(fooLastModDate))
      fooLastModDate = newFooLastModDate
    }

    "add a comment to the metadata of 'foo' ontology" in {
      val aComment = "a comment"

      responderManager ! ChangeOntologyMetadataRequestV2(
        ontologyIri = fooIri.get.toSmartIri.toOntologySchema(ApiV2Complex),
        comment = Some(aComment),
        lastModificationDate = fooLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      val response = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri == fooIri.get.toSmartIri)
      assert(metadata.comment.contains(aComment))
      val newFooLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
      )
      assert(newFooLastModDate.isAfter(fooLastModDate))
      fooLastModDate = newFooLastModDate
    }

    "change both the label and the comment of the 'foo' ontology" in {
      val aLabel   = "a changed label"
      val aComment = "a changed comment"

      responderManager ! ChangeOntologyMetadataRequestV2(
        ontologyIri = fooIri.get.toSmartIri.toOntologySchema(ApiV2Complex),
        label = Some(aLabel),
        comment = Some(aComment),
        lastModificationDate = fooLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      val response = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri == fooIri.get.toSmartIri)
      assert(metadata.label.contains(aLabel))
      assert(metadata.comment.contains(aComment))
      val newFooLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
      )
      assert(newFooLastModDate.isAfter(fooLastModDate))
      fooLastModDate = newFooLastModDate
    }

    "change the label of 'foo' again" in {
      val newLabel = "a label changed again"

      responderManager ! ChangeOntologyMetadataRequestV2(
        ontologyIri = fooIri.get.toSmartIri.toOntologySchema(ApiV2Complex),
        label = Some(newLabel),
        lastModificationDate = fooLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      val response = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri == fooIri.get.toSmartIri)
      assert(metadata.label.contains(newLabel))
      assert(metadata.comment.contains("a changed comment"))
      val newFooLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
      )
      assert(newFooLastModDate.isAfter(fooLastModDate))
      fooLastModDate = newFooLastModDate
    }

    "delete the comment from 'foo'" in {
      responderManager ! DeleteOntologyCommentRequestV2(
        ontologyIri = fooIri.get.toSmartIri.toOntologySchema(ApiV2Complex),
        lastModificationDate = fooLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      val response = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri == fooIri.get.toSmartIri)
      assert(metadata.label.contains("a label changed again"))
      assert(metadata.comment.isEmpty)
      val newFooLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
      )
      assert(newFooLastModDate.isAfter(fooLastModDate))
      fooLastModDate = newFooLastModDate
    }

    "not create an ontology if the given name matches NCName pattern but is not URL safe" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "bär",
        projectIri = imagesProjectIri,
        label = "The bär ontology",
        comment = Some("some comment"),
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "create an empty ontology called 'bar' with a comment" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "bar",
        projectIri = imagesProjectIri,
        label = "The bar ontology",
        comment = Some("some comment"),
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      val response = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri.toString == "http://www.knora.org/ontology/00FF/bar")
      val returnedComment: String =
        metadata.comment.getOrElse(throw AssertionException("The bar ontology has no comment!"))
      assert(returnedComment == "some comment")
      barIri.set(metadata.ontologyIri.toString)
      barLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
      )
    }

    "change the existing comment in the metadata of 'bar' ontology" in {
      val newComment = "a new comment"

      responderManager ! ChangeOntologyMetadataRequestV2(
        ontologyIri = barIri.get.toSmartIri.toOntologySchema(ApiV2Complex),
        comment = Some(newComment),
        lastModificationDate = barLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      val response = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri == barIri.get.toSmartIri)
      assert(metadata.comment.contains(newComment))
      val newBarLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
      )
      assert(newBarLastModDate.isAfter(barLastModDate))
      barLastModDate = newBarLastModDate
    }

    "not create 'foo' again" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "foo",
        projectIri = imagesProjectIri,
        label = "The foo ontology",
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not delete an ontology that doesn't exist" in {
      responderManager ! DeleteOntologyRequestV2(
        ontologyIri = "http://0.0.0.0:3333/ontology/1234/nonexistent/v2".toSmartIri,
        lastModificationDate = fooLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[NotFoundException] should ===(true)
      }
    }

    "not allow a user to delete an ontology if they are not a sysadmin or an admin in the ontology's project" in {
      responderManager ! DeleteOntologyRequestV2(
        ontologyIri = fooIri.get.toSmartIri.toOntologySchema(ApiV2Complex),
        lastModificationDate = fooLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = SharedTestDataADM.imagesUser02
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }
    }

    "delete the 'foo' ontology" in {
      responderManager ! DeleteOntologyRequestV2(
        ontologyIri = fooIri.get.toSmartIri.toOntologySchema(ApiV2Complex),
        lastModificationDate = fooLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      expectMsgType[SuccessResponseV2](timeout)

      // Request the metadata of all ontologies to check that 'foo' isn't listed.

      responderManager ! OntologyMetadataGetByProjectRequestV2(
        requestingUser = imagesUser
      )

      val cachedMetadataResponse = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(!cachedMetadataResponse.ontologies.exists(_.ontologyIri == fooIri.get.toSmartIri))

      // Reload the ontologies from the triplestore and check again.

      responderManager ! LoadOntologiesRequestV2(
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )

      expectMsgType[SuccessResponseV2](10.seconds)

      responderManager ! OntologyMetadataGetByProjectRequestV2(
        requestingUser = imagesUser
      )

      val loadedMetadataResponse = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(!loadedMetadataResponse.ontologies.exists(_.ontologyIri == fooIri.get.toSmartIri))
    }

    "not delete the 'anything' ontology, because it is used in data and in the 'something' ontology" in {
      responderManager ! OntologyMetadataGetByProjectRequestV2(
        projectIris = Set(anythingProjectIri),
        requestingUser = anythingAdminUser
      )

      val metadataResponse = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(metadataResponse.ontologies.size == 3)
      anythingLastModDate = metadataResponse
        .toOntologySchema(ApiV2Complex)
        .ontologies
        .find(_.ontologyIri == AnythingOntologyIri)
        .get
        .lastModificationDate
        .get

      responderManager ! DeleteOntologyRequestV2(
        ontologyIri = AnythingOntologyIri,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        val cause: Throwable = msg.cause
        val errorMsg: String = cause.getMessage
        if (printErrorMessages) println(errorMsg)
        cause.isInstanceOf[BadRequestException] should ===(true)

        val expectedSubjects = Set(
          "<http://rdfh.ch/0001/a-thing>",                                   // rdf:type anything:Thing
          "<http://rdfh.ch/0001/a-blue-thing>",                              // rdf:type anything:BlueThing, a subclass of anything:Thing
          "<http://www.knora.org/ontology/0001/something#Something>",        // a subclass of anything:Thing in another ontology
          "<http://www.knora.org/ontology/0001/something#hasOtherSomething>" // a subproperty of anything:hasOtherThing in another ontology
        )

        expectedSubjects.forall(s => errorMsg.contains(s)) should ===(true)
      }
    }

    "not create an ontology called 'rdfs'" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "rdfs",
        projectIri = imagesProjectIri,
        label = "The rdfs ontology",
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }

    }

    "not create an ontology called '0000'" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "0000",
        projectIri = imagesProjectIri,
        label = "The 0000 ontology",
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }

    }

    "not create an ontology called '-foo'" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "-foo",
        projectIri = imagesProjectIri,
        label = "The -foo ontology",
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }

    }

    "not create an ontology called 'v3'" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "v3",
        projectIri = imagesProjectIri,
        label = "The v3 ontology",
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }

    }

    "not create an ontology called 'ontology'" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "ontology",
        projectIri = imagesProjectIri,
        label = "The ontology ontology",
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }

    }

    "not create an ontology called 'knora'" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "knora",
        projectIri = imagesProjectIri,
        label = "The wrong knora ontology",
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }

    }

    "not create an ontology called 'simple'" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "simple",
        projectIri = imagesProjectIri,
        label = "The simple ontology",
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }

    }

    "not create an ontology called 'shared'" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "shared",
        projectIri = imagesProjectIri,
        label = "The invalid shared ontology",
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }

    }

    "not create a shared ontology in the wrong project" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "misplaced",
        projectIri = imagesProjectIri,
        isShared = true,
        label = "The invalid shared ontology",
        apiRequestID = UUID.randomUUID,
        requestingUser = imagesUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a non-shared ontology in the shared ontologies project" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "misplaced",
        projectIri = OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject.toSmartIri,
        label = "The invalid non-shared ontology",
        apiRequestID = UUID.randomUUID,
        requestingUser = SharedTestDataADM.superUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "create a shared ontology" in {
      responderManager ! CreateOntologyRequestV2(
        ontologyName = "chair",
        projectIri = OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject.toSmartIri,
        isShared = true,
        label = "a chaired ontology",
        apiRequestID = UUID.randomUUID,
        requestingUser = SharedTestDataADM.superUser
      )

      val response = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri.toString == "http://www.knora.org/ontology/shared/chair")
      chairIri.set(metadata.ontologyIri.toOntologySchema(ApiV2Complex).toString)
      chairLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
      )
    }

    "not allow a user to create a property if they are not a sysadmin or an admin in the ontology's project" in {

      responderManager ! OntologyMetadataGetByProjectRequestV2(
        projectIris = Set(anythingProjectIri),
        requestingUser = anythingNonAdminUser
      )

      val metadataResponse = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(metadataResponse.ontologies.size == 3)
      anythingLastModDate = metadataResponse
        .toOntologySchema(ApiV2Complex)
        .ontologies
        .find(_.ontologyIri == AnythingOntologyIri)
        .get
        .lastModificationDate
        .get

      val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("has name", Some("en")),
              StringLiteralV2("hat Namen", Some("de"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("The name of a Thing", Some("en")),
              StringLiteralV2("Der Name eines Dinges", Some("de"))
            )
          )
        ),
        subPropertyOf =
          Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri, OntologyConstants.SchemaOrg.Name.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingNonAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }
    }

    "create a property anything:hasName as a subproperty of knora-api:hasValue and schema:name" in {

      responderManager ! OntologyMetadataGetByProjectRequestV2(
        projectIris = Set(anythingProjectIri),
        requestingUser = anythingAdminUser
      )

      val metadataResponse = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(metadataResponse.ontologies.size == 3)
      anythingLastModDate = metadataResponse
        .toOntologySchema(ApiV2Complex)
        .ontologies
        .find(_.ontologyIri == AnythingOntologyIri)
        .get
        .lastModificationDate
        .get

      val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("has name", Some("en")),
              StringLiteralV2("hat Namen", Some("de"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("The name of a Thing", Some("en")),
              StringLiteralV2("Der Name eines Dinges", Some("de"))
            )
          )
        ),
        subPropertyOf =
          Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri, OntologyConstants.SchemaOrg.Name.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val property         = externalOntology.properties(propertyIri)
        property.entityInfoContent should ===(propertyInfoContent)
        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      // Reload the ontology cache and see if we get the same result.

      responderManager ! LoadOntologiesRequestV2(
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )

      expectMsgType[SuccessResponseV2](10.seconds)

      responderManager ! PropertiesGetRequestV2(
        propertyIris = Set(propertyIri),
        allLanguages = true,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties.values.head
        readPropertyInfo.entityInfoContent should ===(propertyInfoContent)
      }
    }

    "create a link property in the 'anything' ontology, and automatically create the corresponding link value property" in {

      responderManager ! OntologyMetadataGetByProjectRequestV2(
        projectIris = Set(anythingProjectIri),
        requestingUser = anythingAdminUser
      )

      val metadataResponse = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(metadataResponse.ontologies.size == 3)
      anythingLastModDate = metadataResponse
        .toOntologySchema(ApiV2Complex)
        .ontologies
        .find(_.ontologyIri == AnythingOntologyIri)
        .get
        .lastModificationDate
        .get

      val propertyIri = AnythingOntologyIri.makeEntityIri("hasInterestingThing")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("has interesting thing", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("an interesting Thing", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val property         = externalOntology.properties(propertyIri)
        assert(property.isLinkProp)
        assert(!property.isLinkValueProp)
        externalOntology.properties(propertyIri).entityInfoContent should ===(propertyInfoContent)
        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      // Check that the link value property was created.

      val linkValuePropIri = propertyIri.fromLinkPropToLinkValueProp

      responderManager ! PropertiesGetRequestV2(
        propertyIris = Set(linkValuePropIri),
        allLanguages = true,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties.values.head
        assert(readPropertyInfo.entityInfoContent.propertyIri == linkValuePropIri)
        assert(!readPropertyInfo.isLinkProp)
        assert(readPropertyInfo.isLinkValueProp)
      }

      // Reload the ontology cache and see if we get the same result.

      responderManager ! LoadOntologiesRequestV2(
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )

      expectMsgType[SuccessResponseV2](10.seconds)

      responderManager ! PropertiesGetRequestV2(
        propertyIris = Set(propertyIri),
        allLanguages = true,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties.values.head
        assert(readPropertyInfo.isLinkProp)
        assert(!readPropertyInfo.isLinkValueProp)
        readPropertyInfo.entityInfoContent should ===(propertyInfoContent)
      }

      responderManager ! PropertiesGetRequestV2(
        propertyIris = Set(linkValuePropIri),
        allLanguages = true,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties.values.head
        assert(readPropertyInfo.entityInfoContent.propertyIri == linkValuePropIri)
        assert(!readPropertyInfo.isLinkProp)
        assert(readPropertyInfo.isLinkValueProp)
      }

    }

    "create a subproperty of an existing custom link property and add it to a resource class, check if the correct link and link value properties were added to the class" in {

      responderManager ! OntologyMetadataGetByProjectRequestV2(
        projectIris = Set(anythingProjectIri),
        requestingUser = anythingAdminUser
      )

      val metadataResponse = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(metadataResponse.ontologies.size == 3)
      freetestLastModDate = metadataResponse
        .toOntologySchema(ApiV2Complex)
        .ontologies
        .find(_.ontologyIri == FreeTestOntologyIri)
        .get
        .lastModificationDate
        .get

      // Create class freetest:ComicBook which is a subclass of freetest:Book

      val comicBookClassIri = FreeTestOntologyIri.makeEntityIri("ComicBook")

      val comicBookClassInfoContent = ClassInfoContentV2(
        classIri = comicBookClassIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("Comic Book", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("A comic book", Some("en")))
          )
        ),
        directCardinalities = Map(),
        subClassOf = Set(FreeTestOntologyIri.makeEntityIri("Book")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = comicBookClassInfoContent,
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val metadata         = externalOntology.ontologyMetadata
        val newFreetestLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate
      }

      // Create class freetest:ComicAuthor which is a subclass of feetest:Author

      val comicAuthorClassIri = FreeTestOntologyIri.makeEntityIri("ComicAuthor")

      val comicAuthorClassInfoContent = ClassInfoContentV2(
        classIri = comicAuthorClassIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("Comic Author", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("A comic author", Some("en")))
          )
        ),
        directCardinalities = Map(),
        subClassOf = Set(FreeTestOntologyIri.makeEntityIri("Author")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = comicAuthorClassInfoContent,
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val metadata         = externalOntology.ontologyMetadata
        val newFreetestLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate
      }

      // Create property freetest:hasComicBookAuthor which is a subproperty of freetest:hasAuthor and links freetest:ComicBook and freetest:ComicAuthor

      val comicAuthorPropertyIri = FreeTestOntologyIri.makeEntityIri("hasComicAuthor")

      val comicAuthorPropertyInfoContent = PropertyInfoContentV2(
        propertyIri = comicAuthorPropertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(FreeTestOntologyIri.makeEntityIri("ComicBook")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(FreeTestOntologyIri.makeEntityIri("ComicAuthor")))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("Comic author", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("A comic author of a comic book", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(FreeTestOntologyIri.makeEntityIri("hasAuthor")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = comicAuthorPropertyInfoContent,
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val property         = externalOntology.properties(comicAuthorPropertyIri)
        assert(property.isLinkProp)
        assert(!property.isLinkValueProp)
        externalOntology.properties(comicAuthorPropertyIri).entityInfoContent should ===(
          comicAuthorPropertyInfoContent
        )
        val metadata = externalOntology.ontologyMetadata
        val newFreetestLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate
      }

      // Add new subproperty freetest:hasComicBookAuthor to class freetest:ComicBook

      responderManager ! AddCardinalitiesToClassRequestV2(
        classInfoContent = ClassInfoContentV2(
          predicates = Map(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri))
            )
          ),
          classIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#ComicBook".toSmartIri,
          ontologySchema = ApiV2Complex,
          directCardinalities = Map(
            "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasComicAuthor".toSmartIri -> KnoraCardinalityInfo(
              cardinality = Cardinality.MayHaveOne
            )
          )
        ),
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val comicBookClass =
          msg.classes("http://www.knora.org/ontology/0001/freetest#ComicBook".toSmartIri)
        val linkProperties      = comicBookClass.linkProperties
        val linkValueProperties = comicBookClass.linkValueProperties
        assert(
          linkProperties.contains(
            "http://www.knora.org/ontology/0001/freetest#hasComicAuthor".toSmartIri
          )
        )
        assert(
          linkValueProperties.contains(
            "http://www.knora.org/ontology/0001/freetest#hasComicAuthorValue".toSmartIri
          )
        )
        assert(
          !linkProperties.contains(
            "http://www.knora.org/ontology/0001/freetest#hasAuthor".toSmartIri
          )
        )
        assert(
          !linkValueProperties.contains(
            "http://www.knora.org/ontology/0001/freetest#hasAuthorValue".toSmartIri
          )
        )
        val newFreetestLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate
      }

    }

    "not create a property without an rdf:type" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a property with the wrong rdf:type" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a property that already exists" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("hasInteger")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a property with a nonexistent Knora superproperty" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(AnythingOntologyIri.makeEntityIri("nonexistentProperty")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a property that is not a subproperty of knora-api:hasValue or knora-api:hasLinkTo" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set("http://xmlns.com/foaf/0.1/name".toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a property that is a subproperty of both knora-api:hasValue and knora-api:hasLinkTo" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(
          AnythingOntologyIri.makeEntityIri("hasText"),
          AnythingOntologyIri.makeEntityIri("hasOtherThing")
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a property with a knora-base:subjectType that refers to a nonexistent class" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("NonexistentClass")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a property with a knora-base:objectType that refers to a nonexistent class" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(
              SmartIriLiteralV2(
                (OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion + "NonexistentClass").toSmartIri
              )
            )
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a subproperty of anything:hasInteger with a knora-base:subjectType of knora-api:Representation" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.Representation.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(AnythingOntologyIri.makeEntityIri("hasInteger")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a file value property" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.FileValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasFileValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not directly create a link value property" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not directly create a property with a knora-api:objectType of knora-api:LinkValue" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a property with a knora-api:objectType of xsd:string" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Xsd.String.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a property whose object type is knora-api:StillImageFileValue" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.StillImageFileValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a property whose object type is a Knora resource class if the property isn't a subproperty of knora-api:hasLinkValue" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a link property whose object type is knora-api:TextValue" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a subproperty of anything:hasText with a knora-api:objectType of knora-api:IntegerValue" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(AnythingOntologyIri.makeEntityIri("hasText")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }

    }

    "not create a subproperty of anything:hasBlueThing with a knora-api:objectType of anything:Thing" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(AnythingOntologyIri.makeEntityIri("hasBlueThing")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }

    }

    "not create a property with an invalid salsah-gui:guiAttribute (invalid integer)" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          ),
          OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri,
            objects = Seq(SmartIriLiteralV2("http://api.knora.org/ontology/salsah-gui/v2#Textarea".toSmartIri))
          ),
          OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiAttribute.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiAttribute.toSmartIri,
            objects = Seq(StringLiteralV2("rows=10"), StringLiteralV2("cols=80.5"))
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a property with an invalid salsah-gui:guiAttribute (wrong enumerated value)" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          ),
          OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri,
            objects = Seq(SmartIriLiteralV2("http://api.knora.org/ontology/salsah-gui/v2#Textarea".toSmartIri))
          ),
          OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiAttribute.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiAttribute.toSmartIri,
            objects = Seq(StringLiteralV2("rows=10"), StringLiteralV2("cols=80"), StringLiteralV2("wrap=wrong"))
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not allow a user to change the labels of a property if they are not a sysadmin or an admin in the ontology's project" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

      val newObjects = Seq(
        StringLiteralV2("has name", Some("en")),
        StringLiteralV2("a nom", Some("fr")),
        StringLiteralV2("hat Namen", Some("de"))
      )

      responderManager ! ChangePropertyLabelsOrCommentsRequestV2(
        propertyIri = propertyIri,
        predicateToUpdate = OntologyConstants.Rdfs.Label.toSmartIri,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingNonAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }

    }

    "change the labels of a property" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

      val newObjects = Seq(
        StringLiteralV2("has name", Some("en")),
        StringLiteralV2("a nom", Some("fr")),
        StringLiteralV2("hat Namen", Some("de"))
      )

      responderManager ! ChangePropertyLabelsOrCommentsRequestV2(
        propertyIri = propertyIri,
        predicateToUpdate = OntologyConstants.Rdfs.Label.toSmartIri,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo = externalOntology.properties(propertyIri)
        readPropertyInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects should ===(
          newObjects
        )

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the labels of a property, submitting the same labels again" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

      val newObjects = Seq(
        StringLiteralV2("has name", Some("en")),
        StringLiteralV2("a nom", Some("fr")),
        StringLiteralV2("hat Namen", Some("de"))
      )

      responderManager ! ChangePropertyLabelsOrCommentsRequestV2(
        propertyIri = propertyIri,
        predicateToUpdate = OntologyConstants.Rdfs.Label.toSmartIri,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo = externalOntology.properties(propertyIri)
        readPropertyInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects should ===(
          newObjects
        )

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not allow a user to change the comments of a property if they are not a sysadmin or an admin in the ontology's project" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

      val newObjects = Seq(
        StringLiteralV2("The name of a Thing", Some("en")),
        StringLiteralV2(
          "Le nom d\\'une chose",
          Some("fr")
        ), // This is SPARQL-escaped as it would be if taken from a JSON-LD request.
        StringLiteralV2("Der Name eines Dinges", Some("de"))
      )

      responderManager ! ChangePropertyLabelsOrCommentsRequestV2(
        propertyIri = propertyIri,
        predicateToUpdate = OntologyConstants.Rdfs.Comment.toSmartIri,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingNonAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }

    }

    "change the comments of a property" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

      val newObjects = Seq(
        StringLiteralV2("The name of a Thing", Some("en")),
        StringLiteralV2(
          "Le nom d\\'une chose",
          Some("fr")
        ), // This is SPARQL-escaped as it would be if taken from a JSON-LD request.
        StringLiteralV2("Der Name eines Dinges", Some("de"))
      )

      // Make an unescaped copy of the new comments, because this is how we will receive them in the API response.
      val newObjectsUnescaped = newObjects.map { case StringLiteralV2(text, lang) =>
        StringLiteralV2(stringFormatter.fromSparqlEncodedString(text), lang)
      }

      responderManager ! ChangePropertyLabelsOrCommentsRequestV2(
        propertyIri = propertyIri,
        predicateToUpdate = OntologyConstants.Rdfs.Comment.toSmartIri,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo = externalOntology.properties(propertyIri)
        readPropertyInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(
          newObjectsUnescaped
        )

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the comments of a property, submitting the same comments again" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

      val newObjects = Seq(
        StringLiteralV2("The name of a Thing", Some("en")),
        StringLiteralV2(
          "Le nom d\\'une chose",
          Some("fr")
        ), // This is SPARQL-escaped as it would be if taken from a JSON-LD request.
        StringLiteralV2("Der Name eines Dinges", Some("de"))
      )

      // Make an unescaped copy of the new comments, because this is how we will receive them in the API response.
      val newObjectsUnescaped = newObjects.map { case StringLiteralV2(text, lang) =>
        StringLiteralV2(stringFormatter.fromSparqlEncodedString(text), lang)
      }

      responderManager ! ChangePropertyLabelsOrCommentsRequestV2(
        propertyIri = propertyIri,
        predicateToUpdate = OntologyConstants.Rdfs.Comment.toSmartIri,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo = externalOntology.properties(propertyIri)
        readPropertyInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(
          newObjectsUnescaped
        )

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the comment of a property that has a comment" in {
      val propertyIri: SmartIri = FreeTestOntologyIri.makeEntityIri("hasPropertyWithComment")
      responderManager ! DeletePropertyCommentRequestV2(
        propertyIri = propertyIri,
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties(propertyIri)
        readPropertyInfo.entityInfoContent.predicates.contains(
          OntologyConstants.Rdfs.Comment.toSmartIri
        ) should ===(false)
        val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
        val newFreeTestLastModDate: Instant = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newFreeTestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreeTestLastModDate
      }
    }

    "not update the ontology when trying to delete a comment of a property that has no comment" in {
      val propertyIri: SmartIri = FreeTestOntologyIri.makeEntityIri("hasPropertyWithoutComment")
      responderManager ! DeletePropertyCommentRequestV2(
        propertyIri = propertyIri,
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties(propertyIri)
        readPropertyInfo.entityInfoContent.predicates.contains(
          OntologyConstants.Rdfs.Comment.toSmartIri
        ) should ===(false)
        val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
        val newFreeTestLastModDate: Instant = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        // the ontology was not changed and thus should not have a new last modification date
        assert(newFreeTestLastModDate == freetestLastModDate)
        freetestLastModDate = newFreeTestLastModDate
      }
    }

    "delete the comment of a class that has a comment" in {
      val classIri: SmartIri = FreeTestOntologyIri.makeEntityIri("BookWithComment")
      responderManager ! DeleteClassCommentRequestV2(
        classIri = classIri,
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo: ReadClassInfoV2 = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent.predicates.contains(
          OntologyConstants.Rdfs.Comment.toSmartIri
        ) should ===(false)
        val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
        val newFreeTestLastModDate: Instant = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newFreeTestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreeTestLastModDate
      }
    }

    "not update the ontology when trying to delete a comment of a class that has no comment" in {
      val classIri: SmartIri = FreeTestOntologyIri.makeEntityIri("BookWithoutComment")
      responderManager ! DeleteClassCommentRequestV2(
        classIri = classIri,
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo: ReadClassInfoV2 = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent.predicates.contains(
          OntologyConstants.Rdfs.Comment.toSmartIri
        ) should ===(false)
        val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
        val newFreeTestLastModDate: Instant = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        // the ontology was not changed and thus should not have a new last modification date
        assert(newFreeTestLastModDate == freetestLastModDate)
        freetestLastModDate = newFreeTestLastModDate
      }
    }

    "delete the comment of a link property and remove the comment of the link value property as well" in {
      val linkPropertyIri: SmartIri = FreeTestOntologyIri.makeEntityIri("hasLinkPropertyWithComment")
      val linkValueIri: SmartIri    = linkPropertyIri.fromLinkPropToLinkValueProp

      // delete the comment of the link property
      responderManager ! DeletePropertyCommentRequestV2(
        propertyIri = linkPropertyIri,
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)

        val propertyReadPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties(linkPropertyIri)
        propertyReadPropertyInfo.entityInfoContent.predicates.contains(
          OntologyConstants.Rdfs.Comment.toSmartIri
        ) should ===(false)

        val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
        val newFreeTestLastModDate: Instant = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newFreeTestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreeTestLastModDate
      }

      // check that the comment of the link value property was deleted as well
      responderManager ! PropertiesGetRequestV2(
        propertyIris = Set(linkValueIri),
        allLanguages = true,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology: ReadOntologyV2              = msg.toOntologySchema(ApiV2Complex)
        val linkValueReadPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties(linkValueIri)

        linkValueReadPropertyInfo.entityInfoContent.predicates.contains(
          OntologyConstants.Rdfs.Comment.toSmartIri
        ) should ===(false)
      }
    }

    "not allow a user to create a class if they are not a sysadmin or an admin in the ontology's project" in {

      val classIri = AnythingOntologyIri.makeEntityIri("WildThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("wild thing", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("A thing that is wild", Some("en")))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(Cardinality.MayHaveOne),
          AnythingOntologyIri.makeEntityIri("hasInteger") -> KnoraCardinalityInfo(
            cardinality = Cardinality.MayHaveOne,
            guiOrder = Some(20)
          )
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingNonAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }

    }

    "not allow a user to create a class with cardinalities both on property P and on a subproperty of P" in {

      val classIri = AnythingOntologyIri.makeEntityIri("InvalidThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("invalid thing", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("A thing that is invalid", Some("en")))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasOtherThing") -> KnoraCardinalityInfo(Cardinality.MustHaveOne),
          AnythingOntologyIri.makeEntityIri("hasBlueThing") -> KnoraCardinalityInfo(
            cardinality = Cardinality.MustHaveOne
          )
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }

    }

    "not allow the user to submit a direct cardinality on anything:hasInterestingThingValue" in {
      val classIri = AnythingOntologyIri.makeEntityIri("WildThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("wild thing", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("A thing that is wild", Some("en")))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasInterestingThingValue") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "create a class anything:CardinalityThing with cardinalities on anything:hasInterestingThing and anything:hasInterestingThingValue" in {
      val classIri = AnythingOntologyIri.makeEntityIri("CardinalityThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("thing with cardinalities", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("A thing that has cardinalities", Some("en")))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasInterestingThing") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        Set(
          AnythingOntologyIri.makeEntityIri("hasInterestingThing"),
          AnythingOntologyIri.makeEntityIri("hasInterestingThingValue")
        )
          .subsetOf(readClassInfo.allResourcePropertyCardinalities.keySet) should ===(true)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create classes anything:wholeThing and anything:partThing with a isPartOf relation and its corresponding value property" in {

      // Create class partThing

      val partThingClassIri = AnythingOntologyIri.makeEntityIri("partThing")

      val partThingClassInfoContent = ClassInfoContentV2(
        classIri = partThingClassIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("Thing as part", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("Thing that is part of something else", Some("en")))
          )
        ),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = partThingClassInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val metadata         = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        anythingLastModDate = newAnythingLastModDate
      }

      // Create class wholeThing

      val wholeThingClassIri = AnythingOntologyIri.makeEntityIri("wholeThing")

      val wholeThingClassInfoContent = ClassInfoContentV2(
        classIri = wholeThingClassIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("Thing as a whole", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("A thing that has multiple parts", Some("en")))
          )
        ),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = wholeThingClassInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val metadata         = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        anythingLastModDate = newAnythingLastModDate
      }

      // Create property partOf with subject partThing and object wholeThing

      val partOfPropertyIri = AnythingOntologyIri.makeEntityIri("partOf")

      val partOfPropertyInfoContent = PropertyInfoContentV2(
        propertyIri = partOfPropertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("partThing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("wholeThing")))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("is part of", Some("en")),
              StringLiteralV2("ist Teil von", Some("de"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("Represents a part of a whole relation", Some("en")),
              StringLiteralV2("Repräsentiert eine Teil-Ganzes-Beziehung", Some("de"))
            )
          ),
          OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri,
            objects = Seq(SmartIriLiteralV2("http://api.knora.org/ontology/salsah-gui/v2#Searchbox".toSmartIri))
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraBase.IsPartOf.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = partOfPropertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val property = externalOntology.properties(partOfPropertyIri)
        // check that partOf is a subproperty of knora-api:isPartOf
        property.entityInfoContent.subPropertyOf.contains(
          OntologyConstants.KnoraApiV2Complex.IsPartOf.toSmartIri
        ) should ===(true)
        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      // Check that the corresponding partOfValue was created
      val partOfValuePropertyIri = AnythingOntologyIri.makeEntityIri("partOfValue")

      val partOfValuePropGetRequest = PropertiesGetRequestV2(
        propertyIris = Set(partOfValuePropertyIri),
        allLanguages = true,
        requestingUser = anythingAdminUser
      )

      responderManager ! partOfValuePropGetRequest

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val property = externalOntology.properties(partOfValuePropertyIri)
        // check that partOfValue is a subproperty of knora-api:isPartOfValue
        property.entityInfoContent.subPropertyOf.contains(
          OntologyConstants.KnoraApiV2Complex.IsPartOfValue.toSmartIri
        ) should ===(true)
        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the metadata of the 'anything' ontology" in {
      val newLabel = "The modified anything ontology"

      responderManager ! ChangeOntologyMetadataRequestV2(
        ontologyIri = AnythingOntologyIri,
        label = Some(newLabel),
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      val response = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri.toOntologySchema(ApiV2Complex) == AnythingOntologyIri)
      assert(metadata.label.contains(newLabel))
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "delete the class anything:CardinalityThing" in {
      val classIri = AnythingOntologyIri.makeEntityIri("CardinalityThing")

      responderManager ! DeleteClassRequestV2(
        classIri = classIri,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyMetadataV2 =>
        assert(msg.ontologies.size == 1)
        val metadata = msg.ontologies.head
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a class anything:WildThing that is a subclass of anything:Thing, with a direct cardinality for anything:hasName, overriding the cardinality for anything:hasInteger" in {
      val classIri = AnythingOntologyIri.makeEntityIri("WildThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("wild thing", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("A thing that is wild", Some("en")))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(Cardinality.MayHaveOne),
          AnythingOntologyIri.makeEntityIri("hasInteger") -> KnoraCardinalityInfo(
            cardinality = Cardinality.MayHaveOne,
            guiOrder = Some(20)
          )
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      val expectedProperties: Set[SmartIri] = Set(
        anythingHasBoolean,
        anythingHasColor,
        anythingHasDate,
        anythingHasDecimal,
        anythingHasGeometry,
        anythingHasGeoname,
        anythingHasInteger,
        anythingHasInterval,
        anythingHasListItem,
        anythingHasName,
        anythingHasOtherListItem,
        anythingHasOtherThing,
        anythingHasOtherThingValue,
        anythingHasRichtext,
        anythingHasText,
        anythingHasThingDocument,
        anythingHasThingDocumentValue,
        anythingHasThingPicture,
        anythingHasThingPictureValue,
        anythingHasTimeStamp,
        anythingHasUri,
        anythingIsPartOfOtherThing,
        anythingIsPartOfOtherThingValue,
        anythingHasStandoffLinkTo,
        anythingHasStandoffLinkToValue
      ).map(_.toSmartIri)

      val expectedAllBaseClasses: Seq[SmartIri] = Seq(
        (anythingOntology + "WildThing").toSmartIri,
        anythingThing.toSmartIri,
        "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.allBaseClasses should ===(expectedAllBaseClasses)
        readClassInfo.entityInfoContent should ===(classInfoContent)
        readClassInfo.inheritedCardinalities.keySet
          .contains(anythingHasInteger.toSmartIri) should ===(false)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not allow inherited property to be deleted on subclass" in {
      val classIri = AnythingOntologyIri.makeEntityIri("SubThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("sub thing", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("A subclass thing of thing", Some("en")))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      val expectedProperties: Set[SmartIri] = Set(
        anythingHasBoolean,
        anythingHasColor,
        anythingHasDate,
        anythingHasDecimal,
        anythingHasGeometry,
        anythingHasGeoname,
        anythingHasInteger,
        anythingHasInterval,
        anythingHasListItem,
        anythingHasName,
        anythingHasOtherListItem,
        anythingHasOtherThing,
        anythingHasOtherThingValue,
        anythingHasRichtext,
        anythingHasText,
        anythingHasThingDocument,
        anythingHasThingDocumentValue,
        anythingHasThingPicture,
        anythingHasThingPictureValue,
        anythingHasTimeStamp,
        anythingHasUri,
        anythingIsPartOfOtherThing,
        anythingIsPartOfOtherThingValue,
        anythingHasStandoffLinkTo,
        anythingHasStandoffLinkToValue
      ).map(_.toSmartIri)

      val expectedAllBaseClasses: Seq[SmartIri] = Seq(
        (anythingOntology + "SubThing").toSmartIri,
        anythingThing.toSmartIri,
        "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val readClassInfo    = externalOntology.classes(classIri)
        readClassInfo.allBaseClasses should ===(expectedAllBaseClasses)
        readClassInfo.entityInfoContent should ===(classInfoContent)
        readClassInfo.inheritedCardinalities.keySet
          .contains(anythingHasInteger.toSmartIri) should ===(true)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      val classInfoContentWithCardinalityToDeleteDontAllow = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("sub thing", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("A subclass thing of thing", Some("en")))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasInteger") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CanDeleteCardinalitiesFromClassRequestV2(
        classInfoContent = classInfoContentWithCardinalityToDeleteDontAllow,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: CanDoResponseV2 =>
        assert(!msg.canDo)
      }
    }

    "allow direct property to be deleted on subclass" in {
      val classIri = AnythingOntologyIri.makeEntityIri("OtherSubThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("other sub thing", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("Another subclass thing of thing", Some("en")))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      val expectedProperties: Set[SmartIri] = Set(
        anythingHasBoolean,
        anythingHasColor,
        anythingHasDate,
        anythingHasDecimal,
        anythingHasGeometry,
        anythingHasGeoname,
        anythingHasInteger,
        anythingHasInterval,
        anythingHasListItem,
        anythingHasName,
        anythingHasOtherListItem,
        anythingHasOtherThing,
        anythingHasOtherThingValue,
        anythingHasRichtext,
        anythingHasText,
        anythingHasThingDocument,
        anythingHasThingDocumentValue,
        anythingHasThingPicture,
        anythingHasThingPictureValue,
        anythingHasTimeStamp,
        anythingHasUri,
        anythingIsPartOfOtherThing,
        anythingIsPartOfOtherThingValue,
        anythingHasStandoffLinkTo,
        anythingHasStandoffLinkToValue
      ).map(_.toSmartIri)

      val expectedAllBaseClasses: Seq[SmartIri] = Seq(
        (anythingOntology + "OtherSubThing").toSmartIri,
        anythingThing.toSmartIri,
        "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val readClassInfo    = externalOntology.classes(classIri)
        readClassInfo.allBaseClasses should ===(expectedAllBaseClasses)
        readClassInfo.entityInfoContent should ===(classInfoContent)
        readClassInfo.inheritedCardinalities.keySet
          .contains(anythingHasName.toSmartIri) should ===(false)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      val classInfoContentWithCardinalityToDeleteAllow = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("other sub thing", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("Another subclass thing of thing", Some("en")))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CanDeleteCardinalitiesFromClassRequestV2(
        classInfoContent = classInfoContentWithCardinalityToDeleteAllow,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: CanDoResponseV2 =>
        assert(msg.canDo)
      }
    }

    "create a class anything:Nothing with no properties" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("nothing", Some("en")),
              StringLiteralV2("Nichts", Some("de"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("Represents nothing", Some("en")),
              StringLiteralV2("Stellt nichts dar", Some("de"))
            )
          )
        ),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      val expectedProperties = Set(
        anythingHasStandoffLinkTo,
        anythingHasStandoffLinkToValue
      ).map(_.toSmartIri)

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent should ===(classInfoContent)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not allow a user to change the labels of a class if they are not a sysadmin or an admin in the ontology's project" in {

      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val newObjects = Seq(
        StringLiteralV2("nothing", Some("en")),
        StringLiteralV2("rien", Some("fr"))
      )

      responderManager ! ChangeClassLabelsOrCommentsRequestV2(
        classIri = classIri,
        predicateToUpdate = OntologyConstants.Rdfs.Label.toSmartIri,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingNonAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }

    }

    "change the labels of a class" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val newObjects = Seq(
        StringLiteralV2("nothing", Some("en")),
        StringLiteralV2("rien", Some("fr"))
      )

      responderManager ! ChangeClassLabelsOrCommentsRequestV2(
        classIri = classIri,
        predicateToUpdate = OntologyConstants.Rdfs.Label.toSmartIri,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects should ===(
          newObjects
        )

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the labels of a class, submitting the same labels again" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val newObjects = Seq(
        StringLiteralV2("nothing", Some("en")),
        StringLiteralV2("rien", Some("fr"))
      )

      responderManager ! ChangeClassLabelsOrCommentsRequestV2(
        classIri = classIri,
        predicateToUpdate = OntologyConstants.Rdfs.Label.toSmartIri,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects should ===(
          newObjects
        )

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not allow a user to change the comments of a class if they are not a sysadmin or an admin in the ontology's project" in {

      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val newObjects = Seq(
        StringLiteralV2("Represents nothing", Some("en")),
        StringLiteralV2("ne représente rien", Some("fr"))
      )

      responderManager ! ChangeClassLabelsOrCommentsRequestV2(
        classIri = classIri,
        predicateToUpdate = OntologyConstants.Rdfs.Comment.toSmartIri,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingNonAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }

    }

    "change the comments of a class" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val newObjects = Seq(
        StringLiteralV2("Represents nothing", Some("en")),
        StringLiteralV2("ne représente rien", Some("fr"))
      )

      // Make an unescaped copy of the new comments, because this is how we will receive them in the API response.
      val newObjectsUnescaped = newObjects.map { case StringLiteralV2(text, lang) =>
        StringLiteralV2(stringFormatter.fromSparqlEncodedString(text), lang)
      }

      responderManager ! ChangeClassLabelsOrCommentsRequestV2(
        classIri = classIri,
        predicateToUpdate = OntologyConstants.Rdfs.Comment.toSmartIri,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(
          newObjectsUnescaped
        )

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the comments of a class, submitting the same comments again" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val newObjects = Seq(
        StringLiteralV2("Represents nothing", Some("en")),
        StringLiteralV2("ne représente rien", Some("fr"))
      )

      // Make an unescaped copy of the new comments, because this is how we will receive them in the API response.
      val newObjectsUnescaped = newObjects.map { case StringLiteralV2(text, lang) =>
        StringLiteralV2(stringFormatter.fromSparqlEncodedString(text), lang)
      }

      responderManager ! ChangeClassLabelsOrCommentsRequestV2(
        classIri = classIri,
        predicateToUpdate = OntologyConstants.Rdfs.Comment.toSmartIri,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(
          newObjectsUnescaped
        )

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not create a class with the wrong rdf:type" in {
      val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong class", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid class definition", Some("en"))
            )
          )
        ),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a class that already exists" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Thing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong class", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid class definition", Some("en"))
            )
          )
        ),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a class with a nonexistent base class" in {
      val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong class", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid class definition", Some("en"))
            )
          )
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("NonexistentClass")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a class that is not a subclass of knora-api:Resource" in {
      val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong class", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid class definition", Some("en"))
            )
          )
        ),
        subClassOf = Set("http://xmlns.com/foaf/0.1/Person".toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a class with a cardinality for a Knora property that doesn't exist" in {
      val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong class", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid class definition", Some("en"))
            )
          )
        ),
        directCardinalities =
          Map(AnythingOntologyIri.makeEntityIri("nonexistentProperty") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[NotFoundException] should ===(true)
      }
    }

    "not create a class that has a cardinality for anything:hasInteger but is not a subclass of anything:Thing" in {
      val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong class", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid class definition", Some("en"))
            )
          )
        ),
        directCardinalities =
          Map(AnythingOntologyIri.makeEntityIri("hasInteger") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "create a subclass of anything:Thing that has cardinality 1 for anything:hasBoolean" in {
      val classIri = AnythingOntologyIri.makeEntityIri("RestrictiveThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("restrictive thing", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("A more restrictive Thing", Some("en"))
            )
          )
        ),
        directCardinalities =
          Map(AnythingOntologyIri.makeEntityIri("hasBoolean") -> KnoraCardinalityInfo(Cardinality.MustHaveOne)),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent should ===(classInfoContent)
        readClassInfo.allCardinalities(AnythingOntologyIri.makeEntityIri("hasBoolean")).cardinality should ===(
          Cardinality.MustHaveOne
        )

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not create a subclass of anything:Thing that has cardinality 0-n for anything:hasBoolean" in {
      val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong class", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid class definition", Some("en"))
            )
          )
        ),
        directCardinalities =
          Map(AnythingOntologyIri.makeEntityIri("hasBoolean") -> KnoraCardinalityInfo(Cardinality.MayHaveMany)),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "reject a request to delete a link value property directly" in {

      val hasInterestingThingValue = AnythingOntologyIri.makeEntityIri("hasInterestingThingValue")

      responderManager ! DeletePropertyRequestV2(
        propertyIri = hasInterestingThingValue,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }

    }

    "delete a link property and automatically delete the corresponding link value property" in {

      val linkPropIri = AnythingOntologyIri.makeEntityIri("hasInterestingThing")

      responderManager ! DeletePropertyRequestV2(
        propertyIri = linkPropIri,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyMetadataV2 =>
        assert(msg.ontologies.size == 1)
        val metadata = msg.ontologies.head
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      // Check that both properties were deleted.

      val linkPropGetRequest = PropertiesGetRequestV2(
        propertyIris = Set(linkPropIri),
        allLanguages = true,
        requestingUser = anythingAdminUser
      )

      val linkValuePropIri = linkPropIri.fromLinkPropToLinkValueProp

      val linkValuePropGetRequest = PropertiesGetRequestV2(
        propertyIris = Set(linkValuePropIri),
        allLanguages = true,
        requestingUser = anythingAdminUser
      )

      responderManager ! linkPropGetRequest

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[NotFoundException] should ===(true)
      }

      responderManager ! linkValuePropGetRequest

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[NotFoundException] should ===(true)
      }

      // Reload the ontology cache and see if we get the same result.

      responderManager ! LoadOntologiesRequestV2(
        requestingUser = KnoraSystemInstances.Users.SystemUser
      )

      expectMsgType[SuccessResponseV2](10.seconds)

      responderManager ! linkPropGetRequest

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[NotFoundException] should ===(true)
      }

      responderManager ! linkValuePropGetRequest

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[NotFoundException] should ===(true)
      }

    }

    "create a property anything:hasNothingness with knora-api:subjectType anything:Nothing" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasNothingness")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Nothing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.BooleanValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("has nothingness", Some("en")),
              StringLiteralV2("hat Nichtsein", Some("de"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("Indicates whether a Nothing has nothingness", Some("en")),
              StringLiteralV2("Anzeigt, ob ein Nichts Nichtsein hat", Some("de"))
            )
          ),
          OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri,
            objects = Seq(SmartIriLiteralV2("http://api.knora.org/ontology/salsah-gui/v2#Checkbox".toSmartIri))
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val property = externalOntology.properties(propertyIri)
        property.entityInfoContent should ===(propertyInfoContent)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the salsah-gui:guiElement and salsah-gui:guiAttribute of anything:hasNothingness" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasNothingness")

      responderManager ! ChangePropertyGuiElementRequest(
        propertyIri = propertyIri,
        newGuiElement = Some("http://www.knora.org/ontology/salsah-gui#SimpleText".toSmartIri),
        newGuiAttributes = Set("size=80"),
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        msg.properties.head._2.entityInfoContent.predicates
          .get(stringFormatter.toSmartIri(OntologyConstants.SalsahGui.GuiElementProp)) match {
          case Some(predicateInfo) =>
            val guiElementTypeFromMessage = predicateInfo.objects.head.asInstanceOf[SmartIriLiteralV2]
            val guiElementTypeInternal    = guiElementTypeFromMessage.toOntologySchema(InternalSchema)
            guiElementTypeFromMessage should equal(guiElementTypeInternal)
          case None => ()
        }

        // Check that the salsah-gui:guiElement from the message is as expected
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val property = externalOntology.properties(propertyIri)

        val guiElementPropComplex = property.entityInfoContent.predicates(
          OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri
        )

        val guiElementPropComplexExpected = PredicateInfoV2(
          predicateIri = OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri,
          objects = Seq(SmartIriLiteralV2("http://api.knora.org/ontology/salsah-gui/v2#SimpleText".toSmartIri))
        )

        guiElementPropComplex should equal(guiElementPropComplexExpected)

        val guiAttributeComplex = property.entityInfoContent.predicates(
          OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiAttribute.toSmartIri
        )

        val guiAttributeComplexExpected = PredicateInfoV2(
          predicateIri = OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiAttribute.toSmartIri,
          objects = Seq(StringLiteralV2("size=80"))
        )

        guiAttributeComplex should equal(guiAttributeComplexExpected)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the salsah-gui:guiElement and salsah-gui:guiAttribute of anything:hasNothingness" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasNothingness")

      responderManager ! ChangePropertyGuiElementRequest(
        propertyIri = propertyIri,
        newGuiElement = None,
        newGuiAttributes = Set.empty,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val property = externalOntology.properties(propertyIri)

        property.entityInfoContent.predicates
          .get(OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiElementProp.toSmartIri) should ===(None)

        property.entityInfoContent.predicates
          .get(OntologyConstants.SalsahGuiApiV2WithValueObjects.GuiAttribute.toSmartIri) should ===(None)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not create a property called anything:Thing, because that IRI is already used for a class" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("Thing")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Nothing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.BooleanValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a class called anything:hasNothingness, because that IRI is already used for a property" in {
      val classIri = AnythingOntologyIri.makeEntityIri("hasNothingness")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("wrong class", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid class definition", Some("en"))
            )
          )
        ),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "create a class anything:Void as a subclass of anything:Nothing" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Void")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("void", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("Represents a void", Some("en")))
          )
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Nothing")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent should ===(classInfoContent)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "add a cardinality=1 to the class anything:Nothing which has a subclass" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(Cardinality.MustHaveOne)
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! AddCardinalitiesToClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      responderManager ! DeleteCardinalitiesFromClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val metadata         = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not allow a user to delete a class if they are not a sysadmin or an admin in the ontology's project" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Void")

      responderManager ! DeleteClassRequestV2(
        classIri = classIri,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingNonAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }
    }

    "delete the class anything:Void" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Void")

      responderManager ! DeleteClassRequestV2(
        classIri = classIri,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyMetadataV2 =>
        assert(msg.ontologies.size == 1)
        val metadata = msg.ontologies.head
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not allow a user to add a cardinality to a class if they are not a sysadmin or an admin in the user's project" in {

      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
            cardinality = Cardinality.MayHaveOne,
            guiOrder = Some(0)
          )
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! AddCardinalitiesToClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingNonAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }

    }

    "create a link property, anything:hasOtherNothing, and add a cardinality for it to the class anything:Nothing" in {
      val classIri    = AnythingOntologyIri.makeEntityIri("Nothing")
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasOtherNothing")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(classIri))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(classIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("has other nothing", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("Indicates whether a Nothing has another Nothing", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val metadata         = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        directCardinalities = Map(
          propertyIri -> KnoraCardinalityInfo(cardinality = Cardinality.MayHaveOne, guiOrder = Some(0))
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! AddCardinalitiesToClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      val expectedDirectCardinalities = Map(
        propertyIri -> KnoraCardinalityInfo(cardinality = Cardinality.MayHaveOne, guiOrder = Some(0)),
        propertyIri.fromLinkPropToLinkValueProp -> KnoraCardinalityInfo(
          cardinality = Cardinality.MayHaveOne,
          guiOrder = Some(0)
        )
      )

      val expectedProperties = Set(
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
        propertyIri,
        propertyIri.fromLinkPropToLinkValueProp
      )

      val expectedAllBaseClasses: Seq[SmartIri] = Seq(
        (anythingOntology + "Nothing").toSmartIri,
        "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        assert(readClassInfo.allBaseClasses == expectedAllBaseClasses)
        readClassInfo.entityInfoContent.directCardinalities should ===(expectedDirectCardinalities)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not add an 0-n cardinality for a boolean property" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
            cardinality = Cardinality.MayHaveMany,
            guiOrder = Some(0)
          )
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! AddCardinalitiesToClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "add a cardinality for the property anything:hasNothingness to the class anything:Nothing" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
            cardinality = Cardinality.MayHaveOne,
            guiOrder = Some(0)
          )
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! AddCardinalitiesToClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      val expectedDirectCardinalities = Map(
        AnythingOntologyIri.makeEntityIri("hasOtherNothing") -> KnoraCardinalityInfo(
          cardinality = Cardinality.MayHaveOne,
          guiOrder = Some(0)
        ),
        AnythingOntologyIri.makeEntityIri("hasOtherNothingValue") -> KnoraCardinalityInfo(
          cardinality = Cardinality.MayHaveOne,
          guiOrder = Some(0)
        ),
        AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
          cardinality = Cardinality.MayHaveOne,
          guiOrder = Some(0)
        )
      )

      val expectedProperties = Set(
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
        AnythingOntologyIri.makeEntityIri("hasOtherNothing"),
        AnythingOntologyIri.makeEntityIri("hasOtherNothingValue"),
        AnythingOntologyIri.makeEntityIri("hasNothingness")
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent.directCardinalities should ===(expectedDirectCardinalities)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not add a minCardinality>0 for property anything:hasName to class anything:BlueThing, because the class is used in data" in {
      val classIri = AnythingOntologyIri.makeEntityIri("BlueThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(Cardinality.MustHaveSome)
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! AddCardinalitiesToClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "add a maxCardinality=1 for property anything:hasName to class anything:BlueThing even though the class is used in data" in {
      val classIri = AnythingOntologyIri.makeEntityIri("BlueThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! AddCardinalitiesToClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a property anything:hasEmptiness with knora-api:subjectType anything:Nothing" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasEmptiness")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Nothing")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.BooleanValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("has emptiness", Some("en")),
              StringLiteralV2("hat Leerheit", Some("de"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("Indicates whether a Nothing has emptiness", Some("en")),
              StringLiteralV2("Anzeigt, ob ein Nichts Leerheit hat", Some("de"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val property = externalOntology.properties(propertyIri)

        property.entityInfoContent should ===(propertyInfoContent)
        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "add a cardinality for the property anything:hasEmptiness to the class anything:Nothing" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
            cardinality = Cardinality.MayHaveOne,
            guiOrder = Some(1)
          )
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! AddCardinalitiesToClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      val expectedDirectCardinalities = Map(
        AnythingOntologyIri.makeEntityIri("hasOtherNothing") -> KnoraCardinalityInfo(
          cardinality = Cardinality.MayHaveOne,
          guiOrder = Some(0)
        ),
        AnythingOntologyIri.makeEntityIri("hasOtherNothingValue") -> KnoraCardinalityInfo(
          cardinality = Cardinality.MayHaveOne,
          guiOrder = Some(0)
        ),
        AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
          cardinality = Cardinality.MayHaveOne,
          guiOrder = Some(0)
        ),
        AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
          cardinality = Cardinality.MayHaveOne,
          guiOrder = Some(1)
        )
      )

      val expectedProperties = Set(
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
        AnythingOntologyIri.makeEntityIri("hasOtherNothing"),
        AnythingOntologyIri.makeEntityIri("hasOtherNothingValue"),
        AnythingOntologyIri.makeEntityIri("hasNothingness"),
        AnythingOntologyIri.makeEntityIri("hasEmptiness")
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent.directCardinalities should ===(expectedDirectCardinalities)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not allow a user to change the cardinalities of a class if they are not a sysadmin or an admin in the user's project" in {

      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
            cardinality = Cardinality.MayHaveOne,
            guiOrder = Some(0)
          )
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! ChangeCardinalitiesRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingNonAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }

    }

    "change the GUI order of the cardinalities of the class anything:Nothing" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasOtherNothing") -> KnoraCardinalityInfo(
            cardinality = Cardinality.MayHaveOne,
            guiOrder = Some(1)
          ),
          AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
            cardinality = Cardinality.MayHaveOne,
            guiOrder = Some(2)
          ),
          AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
            cardinality = Cardinality.MayHaveOne,
            guiOrder = Some(3)
          )
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! ChangeGuiOrderRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      val expectedCardinalities = Map(
        AnythingOntologyIri.makeEntityIri("hasOtherNothing") -> KnoraCardinalityInfo(
          cardinality = Cardinality.MayHaveOne,
          guiOrder = Some(1)
        ),
        AnythingOntologyIri.makeEntityIri("hasOtherNothingValue") -> KnoraCardinalityInfo(
          cardinality = Cardinality.MayHaveOne,
          guiOrder = Some(1)
        ),
        AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
          cardinality = Cardinality.MayHaveOne,
          guiOrder = Some(2)
        ),
        AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
          cardinality = Cardinality.MayHaveOne,
          guiOrder = Some(3)
        )
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent.directCardinalities should ===(expectedCardinalities)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the cardinalities of the class anything:Nothing, removing anything:hasOtherNothing and anything:hasNothingness and leaving anything:hasEmptiness" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
            cardinality = Cardinality.MayHaveOne,
            guiOrder = Some(0)
          )
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! ChangeCardinalitiesRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      val expectedProperties = Set(
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
        AnythingOntologyIri.makeEntityIri("hasEmptiness")
      )

      val expectedAllBaseClasses: Seq[SmartIri] = Seq(
        (anythingOntology + "Nothing").toSmartIri,
        "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        assert(readClassInfo.allBaseClasses == expectedAllBaseClasses)
        readClassInfo.entityInfoContent.directCardinalities should ===(classInfoContent.directCardinalities)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not delete the class anything:Nothing, because the property anything:hasEmptiness refers to it" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      responderManager ! DeleteClassRequestV2(
        classIri = classIri,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "delete the property anything:hasNothingness" in {
      val hasNothingness = AnythingOntologyIri.makeEntityIri("hasNothingness")

      responderManager ! DeletePropertyRequestV2(
        propertyIri = hasNothingness,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyMetadataV2 =>
        assert(msg.ontologies.size == 1)
        val metadata = msg.ontologies.head
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not delete the property anything:hasEmptiness, because the class anything:Nothing refers to it" in {
      val hasNothingness = AnythingOntologyIri.makeEntityIri("hasEmptiness")

      responderManager ! DeletePropertyRequestV2(
        propertyIri = hasNothingness,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not allow a user to remove all cardinalities from a class if they are not a sysadmin or an admin in the user's project" in {

      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! ChangeCardinalitiesRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingNonAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }
    }

    "remove all cardinalities from the class anything:Nothing" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! ChangeCardinalitiesRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      val expectedProperties = Set(
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent.directCardinalities should ===(classInfoContent.directCardinalities)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not delete the property anything:hasEmptiness with the wrong knora-api:lastModificationDate" in {
      val hasEmptiness = AnythingOntologyIri.makeEntityIri("hasEmptiness")

      responderManager ! DeletePropertyRequestV2(
        propertyIri = hasEmptiness,
        lastModificationDate = anythingLastModDate.minusSeconds(60),
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[EditConflictException] should ===(true)
      }
    }

    "not allow a user to delete a property if they are not a sysadmin or an admin in the ontology's project" in {
      val hasEmptiness = AnythingOntologyIri.makeEntityIri("hasEmptiness")

      responderManager ! DeletePropertyRequestV2(
        propertyIri = hasEmptiness,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingNonAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }
    }

    "delete the properties anything:hasOtherNothing and anything:hasEmptiness" in {
      val hasOtherNothing = AnythingOntologyIri.makeEntityIri("hasOtherNothing")

      responderManager ! DeletePropertyRequestV2(
        propertyIri = hasOtherNothing,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyMetadataV2 =>
        assert(msg.ontologies.size == 1)
        val metadata = msg.ontologies.head
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      val hasEmptiness = AnythingOntologyIri.makeEntityIri("hasEmptiness")

      responderManager ! DeletePropertyRequestV2(
        propertyIri = hasEmptiness,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyMetadataV2 =>
        assert(msg.ontologies.size == 1)
        val metadata = msg.ontologies.head
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the class anything:Nothing" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      responderManager ! DeleteClassRequestV2(
        classIri = classIri,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyMetadataV2 =>
        assert(msg.ontologies.size == 1)
        val metadata = msg.ontologies.head
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not create a class whose base class is in a non-shared ontology in another project" in {
      val classIri = AnythingOntologyIri.makeEntityIri("InvalidClass")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("invalid class", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("Represents an invalid class", Some("en")))
          )
        ),
        subClassOf = Set(IncunabulaOntologyIri.makeEntityIri("book")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a class with a cardinality on a property defined in a non-shared ontology in another project" in {
      val classIri = AnythingOntologyIri.makeEntityIri("InvalidClass")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("invalid class", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("Represents an invalid class", Some("en")))
          )
        ),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        directCardinalities =
          Map(IncunabulaOntologyIri.makeEntityIri("description") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a subproperty of a property defined in a non-shared ontology in another project" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("invalidProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("invalid property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(IncunabulaOntologyIri.makeEntityIri("description")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create property with a subject type defined in a non-shared ontology in another project" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("invalidProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(IncunabulaOntologyIri.makeEntityIri("book")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("invalid property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create property with an object type defined in a non-shared ontology in another project" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("invalidProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(IncunabulaOntologyIri.makeEntityIri("book")))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("invalid property", Some("en"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("An invalid property definition", Some("en"))
            )
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: akka.actor.Status.Failure =>
        if (printErrorMessages) println(msg.cause.getMessage)
        msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "create a class anything:AnyBox1 as a subclass of example-box:Box" in {
      val classIri = AnythingOntologyIri.makeEntityIri("AnyBox1")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("any box", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("Represents any box", Some("en")))
          )
        ),
        subClassOf = Set(ExampleSharedOntologyIri.makeEntityIri("Box")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent should ===(classInfoContent)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the class anything:AnyBox1" in {
      val classIri = AnythingOntologyIri.makeEntityIri("AnyBox1")

      responderManager ! DeleteClassRequestV2(
        classIri = classIri,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyMetadataV2 =>
        assert(msg.ontologies.size == 1)
        val metadata = msg.ontologies.head
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a class anything:AnyBox2 with a cardinality on example-box:hasName" in {
      val classIri = AnythingOntologyIri.makeEntityIri("AnyBox2")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("any box", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("Represents any box", Some("en")))
          )
        ),
        subClassOf = Set(ExampleSharedOntologyIri.makeEntityIri("Box")),
        directCardinalities =
          Map(ExampleSharedOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent should ===(classInfoContent)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the class anything:AnyBox2" in {
      val classIri = AnythingOntologyIri.makeEntityIri("AnyBox2")

      responderManager ! DeleteClassRequestV2(
        classIri = classIri,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyMetadataV2 =>
        assert(msg.ontologies.size == 1)
        val metadata = msg.ontologies.head
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a property anything:hasAnyName with base property example-box:hasName" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasAnyName")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("has any shared name", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("Represents a shared name", Some("en")))
          )
        ),
        subPropertyOf = Set(ExampleSharedOntologyIri.makeEntityIri("hasName")),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val property = externalOntology.properties(propertyIri)

        property.entityInfoContent should ===(propertyInfoContent)
        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the property anything:hasAnyName" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasAnyName")

      responderManager ! DeletePropertyRequestV2(
        propertyIri = propertyIri,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyMetadataV2 =>
        assert(msg.ontologies.size == 1)
        val metadata = msg.ontologies.head
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a property anything:BoxHasBoolean with subject type example-box:Box" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("BoxHasBoolean")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(ExampleSharedOntologyIri.makeEntityIri("Box")))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.BooleanValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("has boolean", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("Represents a boolean", Some("en")))
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val property = externalOntology.properties(propertyIri)

        property.entityInfoContent should ===(propertyInfoContent)
        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the property anything:BoxHasBoolean" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("BoxHasBoolean")

      responderManager ! DeletePropertyRequestV2(
        propertyIri = propertyIri,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyMetadataV2 =>
        assert(msg.ontologies.size == 1)
        val metadata = msg.ontologies.head
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a property anything:hasBox with object type example-box:Box" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasBox")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(ExampleSharedOntologyIri.makeEntityIri("Box")))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("has box", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("Has a box", Some("en")))
          )
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val property = externalOntology.properties(propertyIri)

        property.entityInfoContent should ===(propertyInfoContent)
        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the property anything:hasBox" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasBox")

      responderManager ! DeletePropertyRequestV2(
        propertyIri = propertyIri,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyMetadataV2 =>
        assert(msg.ontologies.size == 1)
        val metadata = msg.ontologies.head
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a class with several cardinalities, then remove one of the cardinalities" in {
      // Create a class with no cardinalities.

      responderManager ! CreateClassRequestV2(
        classInfoContent = ClassInfoContentV2(
          predicates = Map(
            "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
              objects = Vector(
                StringLiteralV2(
                  value = "test class",
                  language = Some("en")
                )
              )
            ),
            "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
              objects = Vector(
                StringLiteralV2(
                  value = "A test class",
                  language = Some("en")
                )
              )
            ),
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri))
            )
          ),
          classIri = (anythingOntology + "TestClass").toSmartIri,
          ontologySchema = ApiV2Complex,
          subClassOf = Set("http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri)
        ),
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val newAnythingLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      // Create a text property.

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = PropertyInfoContentV2(
          propertyIri = (anythingOntology + "testTextProp").toSmartIri,
          predicates = Map(
            "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
              objects = Vector(
                StringLiteralV2(
                  value = "test text property",
                  language = Some("en")
                )
              )
            ),
            "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = (anythingOntology + "TestClass").toSmartIri))
            ),
            "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
              objects = Vector(
                StringLiteralV2(
                  value = "A test text property",
                  language = Some("en")
                )
              )
            ),
            "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri,
              objects =
                Vector(SmartIriLiteralV2(value = "http://api.knora.org/ontology/knora-api/v2#TextValue".toSmartIri))
            ),
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri))
            )
          ),
          subPropertyOf = Set("http://api.knora.org/ontology/knora-api/v2#hasValue".toSmartIri),
          ontologySchema = ApiV2Complex
        ),
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val newAnythingLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      // Create an integer property.

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = PropertyInfoContentV2(
          propertyIri = (anythingOntology + "testIntProp").toSmartIri,
          predicates = Map(
            "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
              objects = Vector(
                StringLiteralV2(
                  value = "test int property",
                  language = Some("en")
                )
              )
            ),
            "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = (anythingOntology + "TestClass").toSmartIri))
            ),
            "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
              objects = Vector(
                StringLiteralV2(
                  value = "A test int property",
                  language = Some("en")
                )
              )
            ),
            "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri,
              objects =
                Vector(SmartIriLiteralV2(value = "http://api.knora.org/ontology/knora-api/v2#IntValue".toSmartIri))
            ),
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri))
            )
          ),
          subPropertyOf = Set("http://api.knora.org/ontology/knora-api/v2#hasValue".toSmartIri),
          ontologySchema = ApiV2Complex
        ),
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val newAnythingLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      // Create a link property.

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = PropertyInfoContentV2(
          propertyIri = (anythingOntology + "testLinkProp").toSmartIri,
          predicates = Map(
            "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
              objects = Vector(
                StringLiteralV2(
                  value = "test link property",
                  language = Some("en")
                )
              )
            ),
            "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = (anythingOntology + "TestClass").toSmartIri))
            ),
            "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
              objects = Vector(
                StringLiteralV2(
                  value = "A test link property",
                  language = Some("en")
                )
              )
            ),
            "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = (anythingOntology + "TestClass").toSmartIri))
            ),
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri))
            )
          ),
          subPropertyOf = Set("http://api.knora.org/ontology/knora-api/v2#hasLinkTo".toSmartIri),
          ontologySchema = ApiV2Complex
        ),
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val newAnythingLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      // Add cardinalities to the class.

      responderManager ! AddCardinalitiesToClassRequestV2(
        classInfoContent = ClassInfoContentV2(
          predicates = Map(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri))
            )
          ),
          classIri = (anythingOntology + "TestClass").toSmartIri,
          ontologySchema = ApiV2Complex,
          directCardinalities = Map(
            (anythingOntology + "testTextProp").toSmartIri -> KnoraCardinalityInfo(
              cardinality = Cardinality.MayHaveOne
            ),
            (anythingOntology + "testIntProp").toSmartIri -> KnoraCardinalityInfo(
              cardinality = Cardinality.MayHaveOne
            ),
            (anythingOntology + "testLinkProp").toSmartIri -> KnoraCardinalityInfo(
              cardinality = Cardinality.MayHaveOne
            )
          )
        ),
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val newAnythingLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      // Remove the link value cardinality from the class.

      responderManager ! ChangeCardinalitiesRequestV2(
        classInfoContent = ClassInfoContentV2(
          predicates = Map(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri))
            )
          ),
          classIri = (anythingOntology + "TestClass").toSmartIri,
          ontologySchema = ApiV2Complex,
          directCardinalities = Map(
            (anythingOntology + "testTextProp").toSmartIri -> KnoraCardinalityInfo(
              cardinality = Cardinality.MayHaveOne
            ),
            (anythingOntology + "testIntProp").toSmartIri -> KnoraCardinalityInfo(
              cardinality = Cardinality.MayHaveOne
            )
          )
        ),
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val newAnythingLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      // Check that the correct blank nodes were stored for the cardinalities.

      storeManager ! SparqlSelectRequest(
        """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |
          |SELECT ?cardinalityProp
          |WHERE {
          |  <http://www.knora.org/ontology/0001/anything#TestClass> rdfs:subClassOf ?restriction .
          |  FILTER isBlank(?restriction)
          |  ?restriction owl:onProperty ?cardinalityProp .
          |}""".stripMargin
      )

      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        assert(
          msg.results.bindings.map(_.rowMap("cardinalityProp")).sorted == Seq(
            "http://www.knora.org/ontology/0001/anything#testIntProp",
            "http://www.knora.org/ontology/0001/anything#testTextProp"
          )
        )
      }
    }

    "create a class with two cardinalities, use one in data, and allow only removal of the cardinality for the property not used in data" in {

      // Create a class with no cardinalities.

      responderManager ! CreateClassRequestV2(
        classInfoContent = ClassInfoContentV2(
          predicates = Map(
            "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
              objects = Vector(
                StringLiteralV2(
                  value = "A Blue Free Test class",
                  language = Some("en")
                )
              )
            ),
            "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
              objects = Vector(
                StringLiteralV2(
                  value = "A Blue Free Test class used for testing cardinalities",
                  language = Some("en")
                )
              )
            ),
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri))
            )
          ),
          classIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#BlueFreeTestClass".toSmartIri,
          ontologySchema = ApiV2Complex,
          subClassOf = Set("http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri)
        ),
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val newFreetestLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate
      }

      // Create a text property.

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = PropertyInfoContentV2(
          propertyIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasBlueTestTextProp".toSmartIri,
          predicates = Map(
            "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
              objects = Vector(
                StringLiteralV2(
                  value = "blue test text property",
                  language = Some("en")
                )
              )
            ),
            "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri,
              objects = Vector(
                SmartIriLiteralV2(value = "http://0.0.0.0:3333/ontology/0001/freetest/v2#BlueFreeTestClass".toSmartIri)
              )
            ),
            "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
              objects = Vector(
                StringLiteralV2(
                  value = "A blue test text property",
                  language = Some("en")
                )
              )
            ),
            "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri,
              objects =
                Vector(SmartIriLiteralV2(value = "http://api.knora.org/ontology/knora-api/v2#TextValue".toSmartIri))
            ),
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri))
            )
          ),
          subPropertyOf = Set("http://api.knora.org/ontology/knora-api/v2#hasValue".toSmartIri),
          ontologySchema = ApiV2Complex
        ),
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val newFreetestLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate
      }

      // Create an integer property.

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = PropertyInfoContentV2(
          propertyIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasBlueTestIntProp".toSmartIri,
          predicates = Map(
            "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
              objects = Vector(
                StringLiteralV2(
                  value = "blue test integer property",
                  language = Some("en")
                )
              )
            ),
            "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri,
              objects = Vector(
                SmartIriLiteralV2(value = "http://0.0.0.0:3333/ontology/0001/freetest/v2#BlueFreeTestClass".toSmartIri)
              )
            ),
            "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
              objects = Vector(
                StringLiteralV2(
                  value = "A blue test integer property",
                  language = Some("en")
                )
              )
            ),
            "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri,
              objects =
                Vector(SmartIriLiteralV2(value = "http://api.knora.org/ontology/knora-api/v2#IntValue".toSmartIri))
            ),
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri))
            )
          ),
          subPropertyOf = Set("http://api.knora.org/ontology/knora-api/v2#hasValue".toSmartIri),
          ontologySchema = ApiV2Complex
        ),
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val newFreetestLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate
      }

      // Add cardinalities to the class.

      responderManager ! AddCardinalitiesToClassRequestV2(
        classInfoContent = ClassInfoContentV2(
          predicates = Map(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri))
            )
          ),
          classIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#BlueFreeTestClass".toSmartIri,
          ontologySchema = ApiV2Complex,
          directCardinalities = Map(
            "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasBlueTestTextProp".toSmartIri -> KnoraCardinalityInfo(
              cardinality = Cardinality.MayHaveOne
            ),
            "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasBlueTestIntProp".toSmartIri -> KnoraCardinalityInfo(
              cardinality = Cardinality.MayHaveOne
            )
          )
        ),
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val newFreetestLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate
      }

      // Create a resource of #BlueTestClass using only #hasBlueTestIntProp.

      val resourceIri: IRI = stringFormatter.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasBlueTestIntProp".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = IntegerValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasInteger = 5,
              comment = Some("this is the number five")
            ),
            permissions = Some("CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher")
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#BlueFreeTestClass".toSmartIri,
        label = "my blue test class thing instance",
        values = inputValues,
        projectADM = SharedTestDataADM.anythingProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        requestingUser = anythingAdminUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgType[ReadResourcesSequenceV2](timeout)

      // Successfully check if the cardinality can be deleted

      responderManager ! CanDeleteCardinalitiesFromClassRequestV2(
        classInfoContent = ClassInfoContentV2(
          predicates = Map(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri))
            )
          ),
          classIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#BlueFreeTestClass".toSmartIri,
          ontologySchema = ApiV2Complex,
          directCardinalities = Map(
            "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasBlueTestTextProp".toSmartIri -> KnoraCardinalityInfo(
              cardinality = Cardinality.MayHaveOne
            )
          )
        ),
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: CanDoResponseV2 =>
        assert(msg.canDo)
      }

      // Successfully remove the (unused) text value cardinality from the class.

      responderManager ! DeleteCardinalitiesFromClassRequestV2(
        classInfoContent = ClassInfoContentV2(
          predicates = Map(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri))
            )
          ),
          classIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#BlueFreeTestClass".toSmartIri,
          ontologySchema = ApiV2Complex,
          directCardinalities = Map(
            "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasBlueTestTextProp".toSmartIri -> KnoraCardinalityInfo(
              cardinality = Cardinality.MayHaveOne
            )
          )
        ),
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val newFreetestLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate
      }

      // Check that the correct blank nodes were stored for the cardinalities.

      storeManager ! SparqlSelectRequest(
        """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX owl: <http://www.w3.org/2002/07/owl#>
          |
          |SELECT ?cardinalityProp
          |WHERE {
          |  <http://www.knora.org/ontology/0001/freetest#BlueFreeTestClass> rdfs:subClassOf ?restriction .
          |  FILTER isBlank(?restriction)
          |  ?restriction owl:onProperty ?cardinalityProp .
          |}""".stripMargin
      )

      expectMsgPF(timeout) { case msg: SparqlSelectResult =>
        assert(
          msg.results.bindings.map(_.rowMap("cardinalityProp")).sorted == Seq(
            "http://www.knora.org/ontology/0001/freetest#hasBlueTestIntProp"
          )
        )
      }
    }

    "create a class anything:FoafPerson as a subclass of foaf:Person" in {
      // create the class anything:FoafPerson
      val classIri: SmartIri = AnythingOntologyIri.makeEntityIri("FoafPerson")

      val classInfoContent: ClassInfoContentV2 = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2("FOAF person", Some("en")))
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2("FOAF person with reference to foaf:Person", Some("en")))
          )
        ),
        subClassOf = Set(
          "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri,
          "http://xmlns.com/foaf/0.1/Person".toSmartIri
        ),
        directCardinalities =
          Map(ExampleSharedOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(Cardinality.MayHaveOne)),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreateClassRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      // check if class was created correctly
      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo: ReadClassInfoV2 = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent should ===(classInfoContent)

        val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
        val newAnythingLastModDate: Instant = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

    }

    "create a property anything:hasFoafName as a subproperty of foaf:name" in {
      // get the class IRI for anything:FoafPerson
      val classIri: SmartIri = AnythingOntologyIri.makeEntityIri("FoafPerson")

      // create the property anything:hasFoafName
      responderManager ! OntologyMetadataGetByProjectRequestV2(
        projectIris = Set(anythingProjectIri),
        requestingUser = anythingAdminUser
      )

      val metadataResponse: ReadOntologyMetadataV2 = expectMsgType[ReadOntologyMetadataV2](timeout)
      assert(metadataResponse.ontologies.size == 3)
      anythingLastModDate = metadataResponse
        .toOntologySchema(ApiV2Complex)
        .ontologies
        .find(_.ontologyIri == AnythingOntologyIri)
        .get
        .lastModificationDate
        .get

      val propertyIri: SmartIri = AnythingOntologyIri.makeEntityIri("hasFoafName")

      val propertyInfoContent: PropertyInfoContentV2 = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri))
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(classIri))
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri))
          ),
          OntologyConstants.Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2("has foaf name", Some("en")),
              StringLiteralV2("hat foaf Namen", Some("de"))
            )
          ),
          OntologyConstants.Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2("The foaf name of something", Some("en")),
              StringLiteralV2("Der foaf Name eines Dinges", Some("de"))
            )
          )
        ),
        subPropertyOf =
          Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri, "http://xmlns.com/foaf/0.1/name".toSmartIri),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CreatePropertyRequestV2(
        propertyInfoContent = propertyInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      // check if property was created correctly
      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
        val property: ReadPropertyInfoV2     = externalOntology.properties(propertyIri)
        property.entityInfoContent should ===(propertyInfoContent)
        val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
        val newAnythingLastModDate: Instant = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate

      }
    }

    "add property anything:hasFoafName to the class anything:FoafPerson" in {
      // get the class IRI for anything:FoafPerson
      val classIri: SmartIri = AnythingOntologyIri.makeEntityIri("FoafPerson")

      val propertyIri: SmartIri = AnythingOntologyIri.makeEntityIri("hasFoafName")

      // add a cardinality for the property anything:hasFoafName to the class anything:FoafPerson
      val classWithNewCardinalityInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        directCardinalities = Map(
          propertyIri -> KnoraCardinalityInfo(
            cardinality = Cardinality.MayHaveOne,
            guiOrder = Some(0)
          )
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! AddCardinalitiesToClassRequestV2(
        classInfoContent = classWithNewCardinalityInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      // check if cardinality was added correctly
      val expectedDirectCardinalities: Map[SmartIri, KnoraCardinalityInfo] = Map(
        propertyIri -> KnoraCardinalityInfo(
          cardinality = Cardinality.MayHaveOne,
          guiOrder = Some(0)
        ),
        ExampleSharedOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(
          cardinality = Cardinality.MayHaveOne
        )
      )

      val expectedProperties: Set[SmartIri] = Set(
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
        ExampleSharedOntologyIri.makeEntityIri("hasName"),
        propertyIri
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo: ReadClassInfoV2 = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent.directCardinalities should ===(expectedDirectCardinalities)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
        val newAnythingLastModDate: Instant = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "remove all properties from class anything:FoafPerson" in {
      // get the class IRI for anything:FoafPerson
      val classIri: SmartIri = AnythingOntologyIri.makeEntityIri("FoafPerson")

      val propertyIri: SmartIri = AnythingOntologyIri.makeEntityIri("hasFoafName")

      // check if cardinalities on class anything:FoafPerson can be removed

      val classInfoContentWithCardinalityToDeleteAllow: ClassInfoContentV2 = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        directCardinalities = Map(
          propertyIri -> KnoraCardinalityInfo(
            cardinality = Cardinality.MayHaveOne,
            guiOrder = Some(0)
          )
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! CanDeleteCardinalitiesFromClassRequestV2(
        classInfoContent = classInfoContentWithCardinalityToDeleteAllow,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: CanDoResponseV2 =>
        assert(msg.canDo)
      }

      // remove cardinalities on the class anything:FoafPerson
      val classChangeInfoContent: ClassInfoContentV2 = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! ChangeCardinalitiesRequestV2(
        classInfoContent = classChangeInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      // check if cardinalities were removed correctly
      val expectedPropertiesAfterDeletion: Set[SmartIri] = Set(
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo: ReadClassInfoV2 = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent.directCardinalities should ===(classChangeInfoContent.directCardinalities)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedPropertiesAfterDeletion)

        val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
        val newAnythingLastModDate: Instant = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the GUI order of a cardinality in a base class, and update its subclass in the ontology cache" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Thing")

      val newCardinality = KnoraCardinalityInfo(cardinality = Cardinality.MayHaveMany, guiOrder = Some(100))

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
          )
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasText") -> newCardinality
        ),
        ontologySchema = ApiV2Complex
      )

      responderManager ! ChangeGuiOrderRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        assert(
          readClassInfo.entityInfoContent
            .directCardinalities(AnythingOntologyIri.makeEntityIri("hasText")) == newCardinality
        )

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date")
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      responderManager ! ClassesGetRequestV2(
        classIris = Set(AnythingOntologyIri.makeEntityIri("ThingWithSeqnum")),
        allLanguages = false,
        requestingUser = anythingAdminUser
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(AnythingOntologyIri.makeEntityIri("ThingWithSeqnum"))
        assert(readClassInfo.inheritedCardinalities(AnythingOntologyIri.makeEntityIri("hasText")) == newCardinality)
      }
    }

    "not load an ontology that has no knora-base:attachedToProject" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/onto-without-project.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a class that's missing a cardinality for a link value property" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/missing-link-value-cardinality-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a class that's missing a cardinality for a link property" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/missing-link-cardinality-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a class with a cardinality whose subject class constraint is incompatible with the class" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/class-incompatible-with-scc-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a resource class without an rdfs:label" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/class-without-label-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a resource property without an rdfs:label" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/property-without-label-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a resource class that is also a standoff class" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/resource-class-is-standoff-class-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a resource class with a cardinality on an undefined property" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/class-with-missing-property-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a resource class with a directly defined cardinality on a non-resource property" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/class-with-non-resource-prop-cardinality-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a resource class with a cardinality on knora-base:resourceProperty" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/class-with-cardinality-on-kbresprop-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a resource class with a cardinality on knora-base:hasValue" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/class-with-cardinality-on-kbhasvalue-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a resource class with a base class that has a Knora IRI but isn't a resource class" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/resource-class-with-invalid-base-class-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a standoff class with a cardinality on a resource property" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/standoff-class-with-resprop-cardinality-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a standoff class with a base class that's not a standoff class" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/standoff-class-with-invalid-base-class-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a property with a subject class constraint of foaf:Person" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/prop-with-non-knora-scc-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a Knora value property with a subject class constraint of knora-base:TextValue" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/prop-with-value-scc-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a property with a subject class constraint of salsah-gui:Guielement" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/prop-with-guielement-scc-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a property with an object class constraint of foaf:Person" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/prop-with-non-knora-occ-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a property whose object class constraint is incompatible with its base property" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/prop-with-incompatible-occ-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a class with cardinalities for a link property and a matching link value property, except that the link property isn't really a link property" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/class-with-misdefined-link-property-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a class with cardinalities for a link property and a matching link value property, except that the link value property isn't really a link value property" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/class-with-misdefined-link-value-property-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a resource property with no rdfs:label" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/resource-prop-without-label-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a property that's a subproperty of both knora-base:hasValue and knora-base:hasLinkTo" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/prop-both-value-and-link-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a property that's a subproperty of knora-base:hasFileValue" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/filevalue-prop-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a resource property with a base property that has a Knora IRI but isn't a resource property" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/resource-prop-wrong-base-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a cardinality that contains salsah-gui:guiElement" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/cardinality-with-guielement-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load an ontology containing a cardinality that contains salsah-gui:guiAttribute" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/cardinality-with-guiattribute-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load a project-specific ontology containing an owl:TransitiveProperty" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/transitive-prop.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load a project-specific ontology with a class that has cardinalities both on property P and on a subproperty of P" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/class-inherits-prop-and-subprop-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load a project-specific ontology containing mismatched cardinalities for a link property and a link value property" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/class-with-mismatched-link-cardinalities-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load a project-specific ontology containing an invalid cardinality on a boolean property" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/invalid-card-on-boolean-prop.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load a project-specific ontology containing a class with a cardinality on a property from a non-shared ontology in another project" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/class-with-non-shared-cardinality.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load a project-specific ontology containing a class with a base class defined in a non-shared ontology in another project" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/class-with-non-shared-base-class.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load a project-specific ontology containing a property with a base property defined in a non-shared ontology in another project" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/prop-with-non-shared-base-prop.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load a project-specific ontology containing a property whose subject class constraint is defined in a non-shared ontology in another project" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/prop-with-non-shared-scc.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load a project-specific ontology containing a property whose object class constraint is defined in a non-shared ontology in another project" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/prop-with-non-shared-occ.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

    "not load a project-specific ontology containing a class with two cardinalities that override the same base class cardinality of 1 or 0-1" in {
      val invalidOnto = List(
        RdfDataObject(
          path = "test_data/responders.v2.OntologyResponderV2Spec/conflicting-cardinalities-onto.ttl",
          name = "http://www.knora.org/ontology/invalid"
        )
      )

      loadInvalidTestData(invalidOnto)
    }

  }
}
