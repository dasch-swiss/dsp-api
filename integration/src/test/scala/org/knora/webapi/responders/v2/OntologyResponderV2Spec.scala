/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import eu.timepit.refined.types.string.NonEmptyString
import org.apache.pekko.actor.Status.Failure
import org.apache.pekko.pattern.ask
import zio.ZIO

import java.time.Instant
import java.util.UUID

import dsp.constants.SalsahGui
import dsp.errors.*
import dsp.valueobjects.Iri
import dsp.valueobjects.Schema
import org.knora.webapi.*
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.v2.responder.CanDoResponseV2
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.messages.v2.responder.ontologymessages.LabelOrComment
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateValueInNewResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.ontology.api.AddCardinalitiesToClassRequestV2
import org.knora.webapi.slice.ontology.api.ChangePropertyLabelsOrCommentsRequestV2
import org.knora.webapi.slice.ontology.api.CreateClassRequestV2
import org.knora.webapi.slice.ontology.domain.model.Cardinality.*
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.util.MutableTestIri
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

/**
 * Tests [[OntologyResponderV2]].
 */
class OntologyResponderV2Spec extends E2ESpec {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val imagesUser           = SharedTestDataADM.imagesUser01
  private val imagesProjectIri     = SharedTestDataADM.imagesProjectIri
  private val anythingAdminUser    = SharedTestDataADM.anythingAdminUser
  private val anythingNonAdminUser = SharedTestDataADM.anythingUser1
  private val anythingProjectIri   = SharedTestDataADM.anythingProjectIri
  private val ontologyResponder    = ZIO.serviceWithZIO[OntologyResponderV2]
  private val triplestoreService   = ZIO.serviceWithZIO[TriplestoreService]

  override lazy val rdfDataObjects: List[RdfDataObject] =
    List(
      RdfDataObject(
        path = "test_data/project_ontologies/example-box.ttl",
        name = "http://www.knora.org/ontology/shared/example-box",
      ),
      RdfDataObject(
        path = "test_data/project_data/anything-data.ttl",
        name = "http://www.knora.org/data/0001/anything",
      ),
      RdfDataObject(
        path = "test_data/project_ontologies/anything-onto.ttl",
        name = "http://www.knora.org/ontology/0001/anything",
      ),
      RdfDataObject(
        path = "test_data/project_ontologies/freetest-onto.ttl",
        name = "http://www.knora.org/ontology/0001/freetest",
      ),
      RdfDataObject(
        path = "test_data/project_data/freetest-data.ttl",
        name = "http://www.knora.org/data/0001/freetest",
      ),
    )

  // The default timeout for receiving reply messages from actors.
  private val fooIri                       = new MutableTestIri
  private val barIri                       = new MutableTestIri
  private val chairIri                     = new MutableTestIri
  private val ExampleSharedOntologyIri     = "http://api.knora.org/ontology/shared/example-box/v2".toSmartIri
  private val IncunabulaOntologyIri        = "http://0.0.0.0:3333/ontology/0803/incunabula/v2".toSmartIri
  private val AnythingOntologyIri          = OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
  private val FreeTestOntologyIri          = OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/freetest/v2".toSmartIri)
  private var fooLastModDate: Instant      = Instant.now
  private var barLastModDate: Instant      = Instant.now
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

  private val validPropertyInfo: PropertyInfoContentV2 = PropertyInfoContentV2(
    propertyIri = AnythingOntologyIri.makeEntityIri("hasNothingness"),
    predicates = Map(
      OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
        predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
        objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
      ),
      OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
        predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
        objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Nothing"))),
      ),
      OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
        predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
        objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.BooleanValue.toSmartIri)),
      ),
      Rdfs.Label.toSmartIri -> PredicateInfoV2(
        predicateIri = Rdfs.Label.toSmartIri,
        objects = Seq(
          StringLiteralV2.from("has nothingness", Some("en")),
          StringLiteralV2.from("hat Nichtsein", Some("de")),
        ),
      ),
      Rdfs.Comment.toSmartIri -> PredicateInfoV2(
        predicateIri = Rdfs.Comment.toSmartIri,
        objects = Seq(
          StringLiteralV2.from("Indicates whether a Nothing has nothingness", Some("en")),
          StringLiteralV2.from("Anzeigt, ob ein Nichts Nichtsein hat", Some("de")),
        ),
      ),
      SalsahGui.External.GuiElementProp.toSmartIri -> PredicateInfoV2(
        predicateIri = SalsahGui.External.GuiElementProp.toSmartIri,
        objects = Seq(SmartIriLiteralV2("http://api.knora.org/ontology/salsah-gui/v2#Checkbox".toSmartIri)),
      ),
    ),
    subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
    ontologySchema = ApiV2Complex,
  )

  private val validClassInfoContentV2: ClassInfoContentV2 =
    ClassInfoContentV2(
      classIri = AnythingOntologyIri.makeEntityIri("Void"),
      predicates = Map(
        OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
          predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
          objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
        ),
        Rdfs.Label.toSmartIri -> PredicateInfoV2(
          predicateIri = Rdfs.Label.toSmartIri,
          objects = Seq(StringLiteralV2.from("void", Some("en"))),
        ),
        Rdfs.Comment.toSmartIri -> PredicateInfoV2(
          predicateIri = Rdfs.Comment.toSmartIri,
          objects = Seq(StringLiteralV2.from("Represents a void", Some("en"))),
        ),
      ),
      subClassOf = Set(AnythingOntologyIri.makeEntityIri("Nothing")),
      ontologySchema = ApiV2Complex,
    )

  "The ontology responder v2" should {
    "create an empty ontology called 'foo' with a project code" in {
      val createReq =
        CreateOntologyRequestV2(
          "foo",
          imagesProjectIri,
          false,
          "The foo ontology",
          None,
          UUID.randomUUID,
          imagesUser,
        )
      val response = UnsafeZioRun.runOrThrow(ontologyResponder(_.createOntology(createReq)))

      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri.toString == "http://www.knora.org/ontology/00FF/foo")
      fooIri.set(metadata.ontologyIri.toString)
      fooLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
    }

    "change the label in the metadata of 'foo'" in {
      val newLabel = "The modified foo ontology"

      val response = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.changeOntologyMetadata(
            fooIri.asOntologyIri,
            Some(newLabel),
            None,
            fooLastModDate,
            UUID.randomUUID,
            imagesUser,
          ),
        ),
      )

      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri == fooIri.get.toSmartIri)
      assert(metadata.label.contains(newLabel))
      val newFooLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newFooLastModDate.isAfter(fooLastModDate))
      fooLastModDate = newFooLastModDate
    }

    "add a comment to the metadata of 'foo' ontology" in {
      val aComment = NonEmptyString.unsafeFrom("a comment")

      val response = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.changeOntologyMetadata(
            fooIri.asOntologyIri,
            None,
            Some(aComment),
            fooLastModDate,
            UUID.randomUUID,
            imagesUser,
          ),
        ),
      )

      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri == fooIri.get.toSmartIri)
      assert(metadata.comment.contains(aComment))
      val newFooLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newFooLastModDate.isAfter(fooLastModDate))
      fooLastModDate = newFooLastModDate
    }

    "change both the label and the comment of the 'foo' ontology" in {
      val aLabel   = "a changed label"
      val aComment = NonEmptyString.unsafeFrom("a changed comment")

      val response = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.changeOntologyMetadata(
            fooIri.asOntologyIri,
            Some(aLabel),
            Some(aComment),
            fooLastModDate,
            UUID.randomUUID,
            imagesUser,
          ),
        ),
      )

      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri == fooIri.get.toSmartIri)
      assert(metadata.label.contains(aLabel))
      assert(metadata.comment.contains(aComment))
      val newFooLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newFooLastModDate.isAfter(fooLastModDate))
      fooLastModDate = newFooLastModDate
    }

    "change the label of 'foo' again" in {
      val newLabel = "a label changed again"

      val response = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.changeOntologyMetadata(
            fooIri.asOntologyIri,
            Some(newLabel),
            None,
            fooLastModDate,
            UUID.randomUUID,
            imagesUser,
          ),
        ),
      )

      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri == fooIri.get.toSmartIri)
      assert(metadata.label.contains(newLabel))
      assert(metadata.comment.contains("a changed comment"))
      val newFooLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newFooLastModDate.isAfter(fooLastModDate))
      fooLastModDate = newFooLastModDate
    }

    "delete the comment from 'foo'" in {
      val response = UnsafeZioRun.runOrThrow(
        ontologyResponder(_.deleteOntologyComment(fooIri.asOntologyIri, fooLastModDate, UUID.randomUUID, imagesUser)),
      )
      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri == fooIri.get.toSmartIri)
      assert(metadata.label.contains("a label changed again"))
      assert(metadata.comment.isEmpty)
      val newFooLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newFooLastModDate.isAfter(fooLastModDate))
      fooLastModDate = newFooLastModDate
    }

    "not create an ontology if the given name matches NCName pattern but is not URL safe" in {
      val createReq =
        CreateOntologyRequestV2(
          "bär",
          imagesProjectIri,
          false,
          "The bär ontology",
          Some(NonEmptyString.unsafeFrom("some comment")),
          UUID.randomUUID,
          imagesUser,
        )
      val exit = UnsafeZioRun.run(ontologyResponder(_.createOntology(createReq)))
      assertFailsWithA[BadRequestException](exit)
    }

    "create an empty ontology called 'bar' with a comment" in {
      val comment = NonEmptyString.unsafeFrom("some comment")
      val createReq = CreateOntologyRequestV2(
        "bar",
        imagesProjectIri,
        false,
        "The bar ontology",
        Some(comment),
        UUID.randomUUID,
        imagesUser,
      )
      val response = UnsafeZioRun.runOrThrow(ontologyResponder(_.createOntology(createReq)))
      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri.toString == "http://www.knora.org/ontology/00FF/bar")
      assert(metadata.comment.contains(comment))
      barIri.set(metadata.ontologyIri.toString)
      barLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
    }

    "change the existing comment in the metadata of 'bar' ontology" in {
      val newComment = NonEmptyString.unsafeFrom("a new comment")

      val response = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.changeOntologyMetadata(
            OntologyIri.unsafeFrom(barIri.get.toSmartIri.toComplexSchema),
            None,
            Some(newComment),
            barLastModDate,
            UUID.randomUUID,
            imagesUser,
          ),
        ),
      )

      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri == barIri.get.toSmartIri)
      assert(metadata.comment.contains(newComment))
      val newBarLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newBarLastModDate.isAfter(barLastModDate))
      barLastModDate = newBarLastModDate
    }

    "not create 'foo' again" in {
      val createReq =
        CreateOntologyRequestV2("foo", imagesProjectIri, false, "ignored", None, UUID.randomUUID, imagesUser)
      val exit = UnsafeZioRun.run(ontologyResponder(_.createOntology(createReq)))
      assertFailsWithA[BadRequestException](exit)
    }

    "not delete an ontology that doesn't exist" in {
      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.deleteOntology(
            ontologyIri = OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/1234/nonexistent/v2".toSmartIri),
            lastModificationDate = fooLastModDate,
            apiRequestID = UUID.randomUUID,
          ),
        ),
      )
      assertFailsWithA[NotFoundException](exit)
    }

    "delete the 'foo' ontology" in {
      val _ = UnsafeZioRun.runOrThrow(
        ontologyResponder(_.deleteOntology(fooIri.asOntologyIri, fooLastModDate, UUID.randomUUID)),
      )

      // Request the metadata of all ontologies to check that 'foo' isn't listed.
      val cachedMetadataResponse = UnsafeZioRun.runOrThrow(ontologyResponder(_.getOntologyMetadataForAllProjects))
      assert(!cachedMetadataResponse.ontologies.exists(_.ontologyIri == fooIri.get.toSmartIri))

      // Reload the ontologies from the triplestore and check again.
      UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[OntologyCache](_.refreshCache()))
      val loadedMetadataResponse = UnsafeZioRun.runOrThrow(ontologyResponder(_.getOntologyMetadataForAllProjects))
      assert(!loadedMetadataResponse.ontologies.exists(_.ontologyIri == fooIri.get.toSmartIri))
    }

    "not delete the 'anything' ontology, because it is used in data and in the 'something' ontology" in {
      val metadataResponse =
        UnsafeZioRun.runOrThrow(ontologyResponder(_.getOntologyMetadataForProject(anythingProjectIri)))
      assert(metadataResponse.ontologies.size == 3)
      anythingLastModDate = metadataResponse
        .toOntologySchema(ApiV2Complex)
        .ontologies
        .find(_.ontologyIri == AnythingOntologyIri.smartIri)
        .get
        .lastModificationDate
        .get

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.deleteOntology(
            ontologyIri = AnythingOntologyIri,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
          ),
        ),
      )
      val expectedSubjects = Set(
        "<http://rdfh.ch/0001/a-thing>",                                   // rdf:type anything:Thing
        "<http://rdfh.ch/0001/a-blue-thing>",                              // rdf:type anything:BlueThing, a subclass of anything:Thing
        "<http://www.knora.org/ontology/0001/something#Something>",        // a subclass of anything:Thing in another ontology
        "<http://www.knora.org/ontology/0001/something#hasOtherSomething>", // a subproperty of anything:hasOtherThing in another ontology
      )
      assertFailsWithA[BadRequestException](exit, e => expectedSubjects.forall(e.getMessage.contains))
    }

    "not create an ontology called 'rdfs'" in {
      val createReq =
        CreateOntologyRequestV2("rdfs", imagesProjectIri, false, "The rdfs ontology", None, UUID.randomUUID, imagesUser)
      val exit = UnsafeZioRun.run(ontologyResponder(_.createOntology(createReq)))
      assertFailsWithA[BadRequestException](exit)
    }

    "not create an ontology called '0000'" in {
      val createReq =
        CreateOntologyRequestV2("0000", imagesProjectIri, false, "The 0000 ontology", None, UUID.randomUUID, imagesUser)
      val exit = UnsafeZioRun.run(ontologyResponder(_.createOntology(createReq)))
      assertFailsWithA[BadRequestException](exit)
    }

    "not create an ontology called '-foo'" in {
      val createReq =
        CreateOntologyRequestV2("-foo", imagesProjectIri, false, "The -foo ontology", None, UUID.randomUUID, imagesUser)
      val exit = UnsafeZioRun.run(ontologyResponder(_.createOntology(createReq)))
      assertFailsWithA[BadRequestException](exit)
    }

    "not create an ontology called 'v3'" in {
      val createReq =
        CreateOntologyRequestV2("v3", imagesProjectIri, false, "The v3 ontology", None, UUID.randomUUID, imagesUser)
      val exit = UnsafeZioRun.run(ontologyResponder(_.createOntology(createReq)))
      assertFailsWithA[BadRequestException](exit)
    }

    "not create an ontology called 'ontology'" in {
      val createReq =
        CreateOntologyRequestV2("ontology", imagesProjectIri, false, "ignored", None, UUID.randomUUID, imagesUser)
      val exit = UnsafeZioRun.run(ontologyResponder(_.createOntology(createReq)))
      assertFailsWithA[BadRequestException](exit)
    }

    "not create an ontology called 'knora'" in {
      val createReq =
        CreateOntologyRequestV2("knora", imagesProjectIri, false, "ignored", None, UUID.randomUUID, imagesUser)
      val exit = UnsafeZioRun.run(ontologyResponder(_.createOntology(createReq)))
      assertFailsWithA[BadRequestException](exit)
    }

    "not create an ontology called 'simple'" in {
      val createReq =
        CreateOntologyRequestV2("simple", imagesProjectIri, false, "ignored", None, UUID.randomUUID, imagesUser)
      val exit = UnsafeZioRun.run(ontologyResponder(_.createOntology(createReq)))
      assertFailsWithA[BadRequestException](exit)
    }

    "not create an ontology called 'shared'" in {
      val createReq =
        CreateOntologyRequestV2("shared", imagesProjectIri, false, "ignored", None, UUID.randomUUID, imagesUser)
      val exit = UnsafeZioRun.run(ontologyResponder(_.createOntology(createReq)))
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a shared ontology in the wrong project" in {
      val createReq =
        CreateOntologyRequestV2("misplaced", imagesProjectIri, true, "ignored", None, UUID.randomUUID, imagesUser)
      val exit = UnsafeZioRun.run(ontologyResponder(_.createOntology(createReq)))
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a non-shared ontology in the shared ontologies project" in {
      val createReq =
        CreateOntologyRequestV2(
          "misplaced",
          OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject,
          false,
          "ignored",
          None,
          UUID.randomUUID,
          imagesUser,
        )
      val exit = UnsafeZioRun.run(ontologyResponder(_.createOntology(createReq)))
      assertFailsWithA[BadRequestException](exit)
    }

    "create a shared ontology" in {
      val createReq =
        CreateOntologyRequestV2(
          "chair",
          OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject,
          true,
          "a chaired ontology",
          None,
          UUID.randomUUID,
          SharedTestDataADM.superUser,
        )
      val response = UnsafeZioRun.runOrThrow(ontologyResponder(_.createOntology(createReq)))
      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri.toString == "http://www.knora.org/ontology/shared/chair")
      chairIri.set(metadata.ontologyIri.toOntologySchema(ApiV2Complex).toString)
      metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
    }

    "not allow a user to create a property if they are not a sysadmin or an admin in the ontology's project" in {
      val metadataResponse =
        UnsafeZioRun.runOrThrow(ontologyResponder(_.getOntologyMetadataForProject(anythingProjectIri)))
      assert(metadataResponse.ontologies.size == 3)
      anythingLastModDate = metadataResponse
        .toOntologySchema(ApiV2Complex)
        .ontologies
        .find(_.ontologyIri == AnythingOntologyIri.smartIri)
        .get
        .lastModificationDate
        .get

      val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("has name", Some("en")),
              StringLiteralV2.from("hat Namen", Some("de")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("The name of a Thing", Some("en")),
              StringLiteralV2.from("Der Name eines Dinges", Some("de")),
            ),
          ),
        ),
        subPropertyOf =
          Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri, OntologyConstants.SchemaOrg.Name.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val actual = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingNonAdminUser),
        ),
      )
      assertFailsWithA[ForbiddenException](actual)
    }

    "create a property anything:hasName as a subproperty of knora-api:hasValue and schema:name" in {
      val metadataResponse = UnsafeZioRun.runOrThrow(
        ontologyResponder(_.getOntologyMetadataForProject(anythingProjectIri)),
      )
      assert(metadataResponse.ontologies.size == 3)
      anythingLastModDate = metadataResponse
        .toOntologySchema(ApiV2Complex)
        .ontologies
        .find(_.ontologyIri == AnythingOntologyIri.smartIri)
        .get
        .lastModificationDate
        .get

      val propertyIri = AnythingOntologyIri.makeEntityIri("hasName")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("has name", Some("en")),
              StringLiteralV2.from("hat Namen", Some("de")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("The name of a Thing", Some("en")),
              StringLiteralV2.from("Der Name eines Dinges", Some("de")),
            ),
          ),
        ),
        subPropertyOf =
          Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri, OntologyConstants.SchemaOrg.Name.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )

      val externalOntology = msg.toOntologySchema(ApiV2Complex)
      val property         = externalOntology.properties(propertyIri)
      property.entityInfoContent should ===(propertyInfoContent)
      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate

      // Reload the ontology cache and see if we get the same result.
      UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyCache](_.refreshCache()),
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.getPropertiesFromOntologyV2(
              Set(PropertyIri.unsafeFrom(propertyIri)),
              allLanguages = true,
              requestingUser = anythingAdminUser,
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties.values.head
        readPropertyInfo.entityInfoContent should ===(propertyInfoContent)
      }
    }

    "create a link property in the 'anything' ontology, and automatically create the corresponding link value property" in {
      val metadataResponse = UnsafeZioRun.runOrThrow(
        ontologyResponder(_.getOntologyMetadataForProject(anythingProjectIri)),
      )
      assert(metadataResponse.ontologies.size == 3)
      anythingLastModDate = metadataResponse
        .toOntologySchema(ApiV2Complex)
        .ontologies
        .find(_.ontologyIri == AnythingOntologyIri.smartIri)
        .get
        .lastModificationDate
        .get

      val propertyIri = AnythingOntologyIri.makeEntityIri("hasInterestingThing")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("has interesting thing", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("an interesting Thing", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )

      val externalOntology = msg.toOntologySchema(ApiV2Complex)
      val property         = externalOntology.properties(propertyIri)
      assert(property.isLinkProp)
      assert(!property.isLinkValueProp)
      externalOntology.properties(propertyIri).entityInfoContent should ===(propertyInfoContent)
      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate

      // Check that the link value property was created.

      val linkValuePropIri = propertyIri.fromLinkPropToLinkValueProp

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.getPropertiesFromOntologyV2(
              propertyIris = Set(PropertyIri.unsafeFrom(linkValuePropIri)),
              allLanguages = true,
              requestingUser = anythingAdminUser,
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties.values.head
        assert(readPropertyInfo.entityInfoContent.propertyIri == linkValuePropIri)
        assert(!readPropertyInfo.isLinkProp)
        assert(readPropertyInfo.isLinkValueProp)
      }

      // Reload the ontology cache and see if we get the same result.
      UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyCache](_.refreshCache()),
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.getPropertiesFromOntologyV2(
              propertyIris = Set(PropertyIri.unsafeFrom(propertyIri)),
              allLanguages = true,
              requestingUser = anythingAdminUser,
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties.values.head
        assert(readPropertyInfo.isLinkProp)
        assert(!readPropertyInfo.isLinkValueProp)
        assert(readPropertyInfo.entityInfoContent == propertyInfoContent)
      }

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.getPropertiesFromOntologyV2(
              propertyIris = Set(PropertyIri.unsafeFrom(linkValuePropIri)),
              allLanguages = true,
              requestingUser = anythingAdminUser,
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties.values.head
        assert(readPropertyInfo.entityInfoContent.propertyIri == linkValuePropIri)
        assert(!readPropertyInfo.isLinkProp)
        assert(readPropertyInfo.isLinkValueProp)
      }
    }

    "create a subproperty of an existing custom link property and add it to a resource class, check if the correct link and link value properties were added to the class" in {
      val metadataResponse = UnsafeZioRun.runOrThrow(
        ontologyResponder(_.getOntologyMetadataForProject(anythingProjectIri)),
      )
      assert(metadataResponse.ontologies.size == 3)
      freetestLastModDate = metadataResponse
        .toOntologySchema(ApiV2Complex)
        .ontologies
        .find(_.ontologyIri == FreeTestOntologyIri.toComplexSchema)
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
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("Comic Book", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("A comic book", Some("en"))),
          ),
        ),
        directCardinalities = Map(),
        subClassOf = Set(FreeTestOntologyIri.makeEntityIri("Book")),
        ontologySchema = ApiV2Complex,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(comicBookClassInfoContent, freetestLastModDate, UUID.randomUUID, anythingAdminUser),
            ),
          ),
        )

        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val metadata         = externalOntology.ontologyMetadata
        val newFreetestLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate
      }

      // Create class freetest:ComicAuthor which is a subclass of freetest:Author

      val comicAuthorClassIri = FreeTestOntologyIri.makeEntityIri("ComicAuthor")

      val comicAuthorClassInfoContent = ClassInfoContentV2(
        classIri = comicAuthorClassIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("Comic Author", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("A comic author", Some("en"))),
          ),
        ),
        directCardinalities = Map(),
        subClassOf = Set(FreeTestOntologyIri.makeEntityIri("Author")),
        ontologySchema = ApiV2Complex,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(comicAuthorClassInfoContent, freetestLastModDate, UUID.randomUUID, anythingAdminUser),
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val metadata         = externalOntology.ontologyMetadata
        val newFreetestLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
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
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(FreeTestOntologyIri.makeEntityIri("ComicBook"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(FreeTestOntologyIri.makeEntityIri("ComicAuthor"))),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("Comic author", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("A comic author of a comic book", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(FreeTestOntologyIri.makeEntityIri("hasAuthor")),
        ontologySchema = ApiV2Complex,
      )

      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(comicAuthorPropertyInfoContent, freetestLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )

      val externalOntology = msg.toOntologySchema(ApiV2Complex)
      val property         = externalOntology.properties(comicAuthorPropertyIri)
      assert(property.isLinkProp)
      assert(!property.isLinkValueProp)
      externalOntology.properties(comicAuthorPropertyIri).entityInfoContent should ===(
        comicAuthorPropertyInfoContent,
      )
      val metadata = externalOntology.ontologyMetadata
      val newFreetestLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newFreetestLastModDate.isAfter(freetestLastModDate))
      freetestLastModDate = newFreetestLastModDate

      // Add new subproperty freetest:hasComicBookAuthor to class freetest:ComicBook
      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.addCardinalitiesToClass(
              AddCardinalitiesToClassRequestV2(
                classInfoContent = ClassInfoContentV2(
                  predicates = Map(
                    "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                      predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                      objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri)),
                    ),
                  ),
                  classIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#ComicBook".toSmartIri,
                  ontologySchema = ApiV2Complex,
                  directCardinalities = Map(
                    "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasComicAuthor".toSmartIri -> KnoraCardinalityInfo(
                      cardinality = ZeroOrOne,
                    ),
                  ),
                ),
                lastModificationDate = freetestLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser,
              ),
            ),
          ),
        )

        val comicBookClass =
          msg.classes("http://www.knora.org/ontology/0001/freetest#ComicBook".toSmartIri)
        val linkProperties      = comicBookClass.linkProperties
        val linkValueProperties = comicBookClass.linkValueProperties
        assert(
          linkProperties.contains(
            "http://www.knora.org/ontology/0001/freetest#hasComicAuthor".toSmartIri,
          ),
        )
        assert(
          linkValueProperties.contains(
            "http://www.knora.org/ontology/0001/freetest#hasComicAuthorValue".toSmartIri,
          ),
        )
        assert(
          !linkProperties.contains(
            "http://www.knora.org/ontology/0001/freetest#hasAuthor".toSmartIri,
          ),
        )
        assert(
          !linkValueProperties.contains(
            "http://www.knora.org/ontology/0001/freetest#hasAuthorValue".toSmartIri,
          ),
        )
        val newFreetestLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate

        // Verify the cardinality of the new property and its link value where created in the subclass
        val queryResult: SparqlSelectResult =
          UnsafeZioRun.runOrThrow(
            triplestoreService(_.query(Select("""
                                                |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                                |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                                |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                                |PREFIX freetest: <http://www.knora.org/ontology/0001/freetest#>
                                                |
                                                |SELECT ?property ?maxCardinality
                                                |WHERE {
                                                |  freetest:ComicBook rdfs:subClassOf ?restriction .
                                                |  ?restriction rdf:type owl:Restriction .
                                                |  ?restriction owl:onProperty ?property .
                                                |  ?restriction owl:maxCardinality ?maxCardinality .
                                                |}""".stripMargin))),
          )
        assert(
          queryResult.results.bindings.exists(row =>
            row.rowMap.get("property").contains("http://www.knora.org/ontology/0001/freetest#hasComicAuthor")
              && row.rowMap.get("maxCardinality").contains("1"),
          ),
        )
        assert(
          queryResult.results.bindings.exists(row =>
            row.rowMap.get("property").contains("http://www.knora.org/ontology/0001/freetest#hasComicAuthorValue")
              && row.rowMap.get("maxCardinality").contains("1"),
          ),
        )
      }
    }

    "not create a property without an rdf:type" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a property with the wrong rdf:type" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a property that already exists" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("hasInteger")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a property with a nonexistent Knora superproperty" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(AnythingOntologyIri.makeEntityIri("nonexistentProperty")),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a property that is not a subproperty of knora-api:hasValue or knora-api:hasLinkTo" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set("http://xmlns.com/foaf/0.1/name".toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a property that is a subproperty of both knora-api:hasValue and knora-api:hasLinkTo" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(
          AnythingOntologyIri.makeEntityIri("hasText"),
          AnythingOntologyIri.makeEntityIri("hasOtherThing"),
        ),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a property with a knora-base:subjectType that refers to a nonexistent class" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("NonexistentClass"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a property with a knora-base:objectType that refers to a nonexistent class" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(
              SmartIriLiteralV2(
                (OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion + "NonexistentClass").toSmartIri,
              ),
            ),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )
      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a subproperty of anything:hasInteger with a knora-base:subjectType of knora-api:Representation" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.Representation.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(AnythingOntologyIri.makeEntityIri("hasInteger")),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a file value property" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.FileValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasFileValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not directly create a link value property" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasLinkToValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not directly create a property with a knora-api:objectType of knora-api:LinkValue" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a property with a knora-api:objectType of xsd:string" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Xsd.String.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a property whose object type is knora-api:StillImageFileValue" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.StillImageFileValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a property whose object type is a Knora resource class if the property isn't a subproperty of knora-api:hasLinkValue" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a link property whose object type is knora-api:TextValue" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a subproperty of anything:hasText with a knora-api:objectType of knora-api:IntegerValue" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(AnythingOntologyIri.makeEntityIri("hasText")),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a subproperty of anything:hasBlueThing with a knora-api:objectType of anything:Thing" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("wrongProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Thing"))),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(AnythingOntologyIri.makeEntityIri("hasBlueThing")),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not allow a user to change the labels of a property if they are not a sysadmin or an admin in the ontology's project" in {

      val propertyIri = AnythingOntologyIri.makeProperty("hasName")

      val newObjects = Seq(
        StringLiteralV2.from("has name", Some("en")),
        StringLiteralV2.from("a nom", Some("fr")),
        StringLiteralV2.from("hat Namen", Some("de")),
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.changePropertyLabelsOrComments(
            ChangePropertyLabelsOrCommentsRequestV2(
              propertyIri,
              LabelOrComment.Label,
              newObjects,
              anythingLastModDate,
              UUID.randomUUID,
              anythingNonAdminUser,
            ),
          ),
        ),
      )
      assertFailsWithA[ForbiddenException](exit)
    }

    "change the labels of a property" in {
      val propertyIri = AnythingOntologyIri.makeProperty("hasName")

      val newObjects = Seq(
        StringLiteralV2.from("has name", Some("en")),
        StringLiteralV2.from("hat Namen", Some("de")),
        StringLiteralV2.from("a nom", Some("fr")),
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.changePropertyLabelsOrComments(
              ChangePropertyLabelsOrCommentsRequestV2(
                propertyIri,
                LabelOrComment.Label,
                newObjects,
                anythingLastModDate,
                UUID.randomUUID,
                anythingAdminUser,
              ),
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo = externalOntology.properties(propertyIri.smartIri)
        readPropertyInfo.entityInfoContent.predicates(Rdfs.Label.toSmartIri).objects should ===(
          newObjects,
        )

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the labels of a property, submitting the same labels again" in {
      val propertyIri = AnythingOntologyIri.makeProperty("hasName")

      val newObjects = Seq(
        StringLiteralV2.from("has name", Some("en")),
        StringLiteralV2.from("hat Namen", Some("de")),
        StringLiteralV2.from("a nom", Some("fr")),
      )

      val changeReq = ChangePropertyLabelsOrCommentsRequestV2(
        propertyIri,
        LabelOrComment.Label,
        newObjects,
        anythingLastModDate,
        UUID.randomUUID,
        anythingAdminUser,
      )
      val msg = UnsafeZioRun.runOrThrow(ontologyResponder(_.changePropertyLabelsOrComments(changeReq)))

      val externalOntology = msg.toOntologySchema(ApiV2Complex)
      assert(externalOntology.properties.size == 1)
      val readPropertyInfo = externalOntology.properties(propertyIri.smartIri)
      readPropertyInfo.entityInfoContent.predicates(Rdfs.Label.toSmartIri).objects should ===(newObjects)

      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "not allow a user to change the comments of a property if they are not a sysadmin or an admin in the ontology's project" in {

      val propertyIri = AnythingOntologyIri.makeProperty("hasName")

      val newObjects = Seq(
        StringLiteralV2.from("The name of a Thing", Some("en")),
        StringLiteralV2.from(
          "Le nom d\\'une chose",
          Some("fr"),
        ), // This is SPARQL-escaped as it would be if taken from a JSON-LD request.
        StringLiteralV2.from("Der Name eines Dinges", Some("de")),
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.changePropertyLabelsOrComments(
            ChangePropertyLabelsOrCommentsRequestV2(
              propertyIri,
              LabelOrComment.Comment,
              newObjects,
              anythingLastModDate,
              UUID.randomUUID,
              anythingNonAdminUser,
            ),
          ),
        ),
      )
      assertFailsWithA[ForbiddenException](exit)
    }

    "change the comments of a property" in {
      val propertyIri = AnythingOntologyIri.makeProperty("hasName")

      val newObjects = Seq(
        StringLiteralV2.from("The name of a Thing", Some("en")),
        StringLiteralV2.from("Der Name eines Dinges", Some("de")),
        StringLiteralV2.from(
          "Le nom d\\'une chose",
          Some("fr"),
        ), // This is SPARQL-escaped as it would be if taken from a JSON-LD request.
      )

      // Make an unescaped copy of the new comments, because this is how we will receive them in the API response.
      val newObjectsUnescaped = newObjects.map { case StringLiteralV2(text, lang) =>
        StringLiteralV2.from(Iri.fromSparqlEncodedString(text), lang)
      }

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.changePropertyLabelsOrComments(
              ChangePropertyLabelsOrCommentsRequestV2(
                propertyIri,
                LabelOrComment.Comment,
                newObjects,
                anythingLastModDate,
                UUID.randomUUID,
                anythingAdminUser,
              ),
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo = externalOntology.properties(propertyIri.smartIri)
        readPropertyInfo.entityInfoContent.predicates(Rdfs.Comment.toSmartIri).objects should ===(
          newObjectsUnescaped,
        )

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the comments of a property, submitting the same comments again" in {
      val propertyIri = AnythingOntologyIri.makeProperty("hasName")

      val newObjects = Seq(
        StringLiteralV2.from("The name of a Thing", Some("en")),
        StringLiteralV2.from("Der Name eines Dinges", Some("de")),
        StringLiteralV2.from(
          "Le nom d\\'une chose",
          Some("fr"),
        ), // This is SPARQL-escaped as it would be if taken from a JSON-LD request.
      )

      // Make an unescaped copy of the new comments, because this is how we will receive them in the API response.
      val newObjectsUnescaped = newObjects.map { case StringLiteralV2(text, lang) =>
        StringLiteralV2.from(Iri.fromSparqlEncodedString(text), lang)
      }

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.changePropertyLabelsOrComments(
              ChangePropertyLabelsOrCommentsRequestV2(
                propertyIri,
                LabelOrComment.Comment,
                newObjects,
                anythingLastModDate,
                UUID.randomUUID,
                anythingAdminUser,
              ),
            ),
          ),
        )

        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val readPropertyInfo = externalOntology.properties(propertyIri.smartIri)
        readPropertyInfo.entityInfoContent.predicates(Rdfs.Comment.toSmartIri).objects should ===(
          newObjectsUnescaped,
        )

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the comment of a property that has a comment" in {
      val propertyIri = FreeTestOntologyIri.makeProperty("hasPropertyWithComment")
      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(_.deletePropertyComment(propertyIri, freetestLastModDate, UUID.randomUUID, anythingAdminUser)),
      )

      val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
      assert(externalOntology.properties.size == 1)
      val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties(propertyIri.toComplexSchema)
      readPropertyInfo.entityInfoContent.predicates.contains(
        Rdfs.Comment.toSmartIri,
      ) should ===(false)
      val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
      val newFreeTestLastModDate: Instant = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newFreeTestLastModDate.isAfter(freetestLastModDate))
      freetestLastModDate = newFreeTestLastModDate
    }

    "not update the ontology when trying to delete a comment of a property that has no comment" in {
      val propertyIri = FreeTestOntologyIri.makeProperty("hasPropertyWithoutComment")
      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(_.deletePropertyComment(propertyIri, freetestLastModDate, UUID.randomUUID, anythingAdminUser)),
      )

      val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
      assert(externalOntology.properties.size == 1)
      val readPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties(propertyIri.toComplexSchema)
      readPropertyInfo.entityInfoContent.predicates.contains(
        Rdfs.Comment.toSmartIri,
      ) should ===(false)
      val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
      val newFreeTestLastModDate: Instant = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      // the ontology was not changed and thus should not have a new last modification date
      assert(newFreeTestLastModDate == freetestLastModDate)
      freetestLastModDate = newFreeTestLastModDate
    }

    "delete the comment of a class that has a comment" in {
      val classIri = FreeTestOntologyIri.makeClass("BookWithComment")
      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(_.deleteClassComment(classIri, freetestLastModDate, UUID.randomUUID, anythingAdminUser)),
      )
      val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
      assert(externalOntology.classes.size == 1)
      val readClassInfo: ReadClassInfoV2 = externalOntology.classes(classIri.toComplexSchema)
      readClassInfo.entityInfoContent.predicates.contains(
        Rdfs.Comment.toSmartIri,
      ) should ===(false)
      val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
      val newFreeTestLastModDate: Instant = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newFreeTestLastModDate.isAfter(freetestLastModDate))
      freetestLastModDate = newFreeTestLastModDate
    }

    "not update the ontology when trying to delete a comment of a class that has no comment" in {
      val classIri = FreeTestOntologyIri.makeClass("BookWithoutComment")
      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(_.deleteClassComment(classIri, freetestLastModDate, UUID.randomUUID, anythingAdminUser)),
      )
      val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
      assert(externalOntology.classes.size == 1)
      val readClassInfo: ReadClassInfoV2 = externalOntology.classes(classIri.toComplexSchema)
      readClassInfo.entityInfoContent.predicates.contains(
        Rdfs.Comment.toSmartIri,
      ) should ===(false)
      val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
      val newFreeTestLastModDate: Instant = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      // the ontology was not changed and thus should not have a new last modification date
      assert(newFreeTestLastModDate == freetestLastModDate)
      freetestLastModDate = newFreeTestLastModDate
    }

    "delete the comment of a link property and remove the comment of the link value property as well" in {
      val linkPropertyIri = FreeTestOntologyIri.makeProperty("hasLinkPropertyWithComment")
      val linkValueIri    = linkPropertyIri.fromLinkPropToLinkValueProp

      // delete the comment of the link property
      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.deletePropertyComment(linkPropertyIri, freetestLastModDate, UUID.randomUUID, anythingAdminUser),
          ),
        )
        val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)

        val propertyReadPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties(linkPropertyIri.toComplexSchema)
        propertyReadPropertyInfo.entityInfoContent.predicates.contains(
          Rdfs.Comment.toSmartIri,
        ) should ===(false)

        val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
        val newFreeTestLastModDate: Instant = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newFreeTestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreeTestLastModDate
      }

      // check that the comment of the link value property was deleted as well
      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.getPropertiesFromOntologyV2(
            propertyIris = Set(linkValueIri),
            allLanguages = true,
            requestingUser = anythingAdminUser,
          ),
        ),
      )
      val externalOntology: ReadOntologyV2              = msg.toOntologySchema(ApiV2Complex)
      val linkValueReadPropertyInfo: ReadPropertyInfoV2 = externalOntology.properties(linkValueIri.toComplexSchema)

      linkValueReadPropertyInfo.entityInfoContent.predicates.contains(
        Rdfs.Comment.toSmartIri,
      ) should ===(false)
    }

    "not allow a user to create a class if they are not a sysadmin or an admin in the ontology's project" in {

      val classIri = AnythingOntologyIri.makeEntityIri("WildThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("wild thing", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("A thing that is wild", Some("en"))),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(ZeroOrOne),
          AnythingOntologyIri.makeEntityIri("hasInteger") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(20),
          ),
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createClass(
            CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingNonAdminUser),
          ),
        ),
      )
      assertFailsWithA[ForbiddenException](exit)
    }

    "not allow a user to create a class with cardinalities both on property P and on a subproperty of P" in {

      val classIri = AnythingOntologyIri.makeEntityIri("InvalidThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("invalid thing", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("A thing that is invalid", Some("en"))),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasOtherThing") -> KnoraCardinalityInfo(ExactlyOne),
          AnythingOntologyIri.makeEntityIri("hasBlueThing") -> KnoraCardinalityInfo(
            cardinality = ExactlyOne,
          ),
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser)),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not allow the user to submit a direct cardinality on anything:hasInterestingThingValue" in {
      val classIri = AnythingOntologyIri.makeEntityIri("WildThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("wild thing", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("A thing that is wild", Some("en"))),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasInterestingThingValue") -> KnoraCardinalityInfo(ZeroOrOne),
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser)),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a class if rdfs:label is missing" in {
      val invalid =
        validClassInfoContentV2.copy(predicates = validClassInfoContentV2.predicates - Rdfs.Label.toSmartIri)
      val req  = CreateClassRequestV2(invalid, anythingLastModDate, UUID.randomUUID, anythingAdminUser)
      val exit = UnsafeZioRun.run(ontologyResponder(_.createClass(req)))
      assertFailsWithA[BadRequestException](exit, s"Missing ${Rdfs.Label}")
    }

    "not create a class if rdfs:label does not have a language code" in {
      val invalid: ClassInfoContentV2 =
        validClassInfoContentV2.copy(predicates =
          validClassInfoContentV2.predicates.updated(
            Rdfs.Label.toSmartIri,
            PredicateInfoV2(
              Rdfs.Label.toSmartIri,
              Seq(StringLiteralV2.unsafeFrom("without language code", None)),
            ),
          ),
        )
      val req  = CreateClassRequestV2(invalid, anythingLastModDate, UUID.randomUUID, anythingAdminUser)
      val exit = UnsafeZioRun.run(ontologyResponder(_.createClass(req)))
      assertFailsWithA[BadRequestException](
        exit,
        s"All values of ${Rdfs.Label} must be string literals with a language code",
      )
    }

    "not create a class if rdfs:comment does not have a language code" in {
      val invalid: ClassInfoContentV2 =
        validClassInfoContentV2.copy(predicates =
          validClassInfoContentV2.predicates.updated(
            Rdfs.Comment.toSmartIri,
            PredicateInfoV2(
              Rdfs.Comment.toSmartIri,
              Seq(StringLiteralV2.unsafeFrom("without language code", None)),
            ),
          ),
        )
      val req  = CreateClassRequestV2(invalid, anythingLastModDate, UUID.randomUUID, anythingAdminUser)
      val exit = UnsafeZioRun.run(ontologyResponder(_.createClass(req)))
      assertFailsWithA[BadRequestException](
        exit,
        s"All values of ${Rdfs.Comment} must be string literals with a language code",
      )
    }

    "create a class anything:CardinalityThing with cardinalities on anything:hasInterestingThing and anything:hasInterestingThingValue" in {
      val classIri = AnythingOntologyIri.makeEntityIri("CardinalityThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("thing with cardinalities", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("A thing that has cardinalities", Some("en"))),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasInterestingThing") -> KnoraCardinalityInfo(ZeroOrOne),
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        Set(
          AnythingOntologyIri.makeEntityIri("hasInterestingThing"),
          AnythingOntologyIri.makeEntityIri("hasInterestingThingValue"),
        )
          .subsetOf(readClassInfo.allResourcePropertyCardinalities.keySet) should ===(true)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
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
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("Thing as part", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("Thing that is part of something else", Some("en"))),
          ),
        ),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      {
        val msg: ReadOntologyV2 = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(partThingClassInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val metadata         = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
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
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("Thing as a whole", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("A thing that has multiple parts", Some("en"))),
          ),
        ),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(wholeThingClassInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val metadata         = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
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
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("partThing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("wholeThing"))),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("is part of", Some("en")),
              StringLiteralV2.from("ist Teil von", Some("de")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("Represents a part of a whole relation", Some("en")),
              StringLiteralV2.from("Repräsentiert eine Teil-Ganzes-Beziehung", Some("de")),
            ),
          ),
          SalsahGui.External.GuiElementProp.toSmartIri -> PredicateInfoV2(
            predicateIri = SalsahGui.External.GuiElementProp.toSmartIri,
            objects = Seq(SmartIriLiteralV2("http://api.knora.org/ontology/salsah-gui/v2#Searchbox".toSmartIri)),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraBase.IsPartOf.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(partOfPropertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )

      val externalOntology = msg.toOntologySchema(ApiV2Complex)
      assert(externalOntology.properties.size == 1)
      val property = externalOntology.properties(partOfPropertyIri)
      // check that partOf is a subproperty of knora-api:isPartOf
      property.entityInfoContent.subPropertyOf.contains(
        OntologyConstants.KnoraApiV2Complex.IsPartOf.toSmartIri,
      ) should ===(true)
      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate

      // Check that the corresponding partOfValue was created
      val partOfValuePropertyIri = AnythingOntologyIri.makeEntityIri("partOfValue")

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.getPropertiesFromOntologyV2(
              propertyIris = Set(PropertyIri.unsafeFrom(partOfValuePropertyIri)),
              allLanguages = true,
              requestingUser = anythingAdminUser,
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val property = externalOntology.properties(partOfValuePropertyIri)
        // check that partOfValue is a subproperty of knora-api:isPartOfValue
        property.entityInfoContent.subPropertyOf.contains(
          OntologyConstants.KnoraApiV2Complex.IsPartOfValue.toSmartIri,
        ) should ===(true)
        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the metadata of the 'anything' ontology" in {
      val newLabel = "The modified anything ontology"

      val response = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.changeOntologyMetadata(
            AnythingOntologyIri,
            Some(newLabel),
            None,
            anythingLastModDate,
            UUID.randomUUID,
            anythingAdminUser,
          ),
        ),
      )

      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      assert(metadata.ontologyIri.toOntologySchema(ApiV2Complex) == AnythingOntologyIri.smartIri)
      assert(metadata.label.contains(newLabel))
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "delete the class anything:CardinalityThing" in {
      val classIri = AnythingOntologyIri.makeClass("CardinalityThing")

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.deleteClass(
          classIri = classIri,
          lastModificationDate = anythingLastModDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = anythingAdminUser,
        ))
      )

      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "create a class anything:WildThing that is a subclass of anything:Thing, with a direct cardinality for anything:hasName, overriding the cardinality for anything:hasInteger" in {
      val classIri = AnythingOntologyIri.makeEntityIri("WildThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("wild thing", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("A thing that is wild", Some("en"))),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(ZeroOrOne),
          AnythingOntologyIri.makeEntityIri("hasInteger") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(20),
          ),
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
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
        anythingHasStandoffLinkToValue,
      ).map(_.toSmartIri)

      val expectedAllBaseClasses: Seq[SmartIri] = Seq(
        (anythingOntology + "WildThing").toSmartIri,
        anythingThing.toSmartIri,
        "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
            ),
          ),
        )
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
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
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
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("sub thing", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("A subclass thing of thing", Some("en"))),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(ZeroOrOne),
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
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
        anythingHasStandoffLinkToValue,
      ).map(_.toSmartIri)

      val expectedAllBaseClasses: Seq[SmartIri] = Seq(
        (anythingOntology + "SubThing").toSmartIri,
        anythingThing.toSmartIri,
        "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
            ),
          ),
        )

        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val readClassInfo    = externalOntology.classes(classIri)
        readClassInfo.allBaseClasses should ===(expectedAllBaseClasses)
        readClassInfo.entityInfoContent should ===(classInfoContent)
        readClassInfo.inheritedCardinalities.keySet
          .contains(anythingHasInteger.toSmartIri) should ===(true)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      val classInfoContentWithCardinalityToDeleteDontAllow = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("sub thing", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("A subclass thing of thing", Some("en"))),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasInteger") -> KnoraCardinalityInfo(ZeroOrOne),
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
      )

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.canDeleteCardinalitiesFromClass(
          CanDeleteCardinalitiesFromClassRequestV2(
            classInfoContent = classInfoContentWithCardinalityToDeleteDontAllow,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          )
        ))
      )

      assert(!response.canDo.value)
    }

    "allow direct property to be deleted on subclass" in {
      val classIri = AnythingOntologyIri.makeEntityIri("OtherSubThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("other sub thing", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("Another subclass thing of thing", Some("en"))),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(ZeroOrOne),
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
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
        anythingHasStandoffLinkToValue,
      ).map(_.toSmartIri)

      val expectedAllBaseClasses: Seq[SmartIri] = Seq(
        (anythingOntology + "OtherSubThing").toSmartIri,
        anythingThing.toSmartIri,
        "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        val readClassInfo    = externalOntology.classes(classIri)
        readClassInfo.allBaseClasses should ===(expectedAllBaseClasses)
        readClassInfo.entityInfoContent should ===(classInfoContent)
        readClassInfo.inheritedCardinalities.keySet
          .contains(anythingHasName.toSmartIri) should ===(false)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      val classInfoContentWithCardinalityToDeleteAllow = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("other sub thing", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("Another subclass thing of thing", Some("en"))),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(ZeroOrOne),
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
      )

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.canDeleteCardinalitiesFromClass(
          CanDeleteCardinalitiesFromClassRequestV2(
            classInfoContent = classInfoContentWithCardinalityToDeleteAllow,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          )
        ))
      )

      assert(response.canDo.value)
    }

    "create a class anything:Nothing with no properties" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("nothing", Some("en")),
              StringLiteralV2.from("Nichts", Some("de")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("Represents nothing", Some("en")),
              StringLiteralV2.from("Stellt nichts dar", Some("de")),
            ),
          ),
        ),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val expectedProperties = Set(
        anythingHasStandoffLinkTo,
        anythingHasStandoffLinkToValue,
      ).map(_.toSmartIri)

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent should ===(classInfoContent)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not allow a user to change the labels of a class if they are not a sysadmin or an admin in the ontology's project" in {

      val classIri = AnythingOntologyIri.makeClass("Nothing")

      val newObjects = Seq(
        StringLiteralV2.from("nothing", Some("en")),
        StringLiteralV2.from("rien", Some("fr")),
      )

      val exit = UnsafeZioRun.run(
        ZIO.serviceWithZIO[OntologyResponderV2](_.changeClassLabelsOrComments(
          ChangeClassLabelsOrCommentsRequestV2(
            classIri = classIri,
            predicateToUpdate = LabelOrComment.Label,
            newObjects = newObjects,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingNonAdminUser,
          )
        ))
      )
      assertFailsWithA[ForbiddenException](exit)

    }

    "change the labels of a class" in {
      val classIri = AnythingOntologyIri.makeClass("Nothing")

      val newObjects = Seq(
        StringLiteralV2.from("nothing", Some("en")),
        StringLiteralV2.from("rien", Some("fr")),
      )

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.changeClassLabelsOrComments(
          ChangeClassLabelsOrCommentsRequestV2(
            classIri = classIri,
            predicateToUpdate = LabelOrComment.Label,
            newObjects = newObjects,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          )
        ))
      )

      val externalOntology = response.toOntologySchema(ApiV2Complex)
      assert(externalOntology.classes.size == 1)
      val readClassInfo = externalOntology.classes(classIri.smartIri)
      readClassInfo.entityInfoContent.predicates(Rdfs.Label.toSmartIri).objects should ===(
        newObjects,
      )

      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "change the labels of a class, submitting the same labels again" in {
      val classIri = AnythingOntologyIri.makeClass("Nothing")

      val newObjects = Seq(
        StringLiteralV2.from("nothing", Some("en")),
        StringLiteralV2.from("rien", Some("fr")),
      )

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.changeClassLabelsOrComments(
          ChangeClassLabelsOrCommentsRequestV2(
            classIri = classIri,
            predicateToUpdate = LabelOrComment.Label,
            newObjects = newObjects,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          )
        ))
      )

      val externalOntology = response.toOntologySchema(ApiV2Complex)
      assert(externalOntology.classes.size == 1)
      val readClassInfo = externalOntology.classes(classIri.smartIri)
      readClassInfo.entityInfoContent.predicates(Rdfs.Label.toSmartIri).objects should ===(
        newObjects,
      )

      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "not allow a user to change the comments of a class if they are not a sysadmin or an admin in the ontology's project" in {

      val classIri = AnythingOntologyIri.makeClass("Nothing")

      val newObjects = Seq(
        StringLiteralV2.from("Represents nothing", Some("en")),
        StringLiteralV2.from("ne représente rien", Some("fr")),
      )

      val exit = UnsafeZioRun.run(
        ZIO.serviceWithZIO[OntologyResponderV2](_.changeClassLabelsOrComments(
          ChangeClassLabelsOrCommentsRequestV2(
            classIri = classIri,
            predicateToUpdate = LabelOrComment.Comment,
            newObjects = newObjects,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingNonAdminUser,
          )
        ))
      )
      assertFailsWithA[ForbiddenException](exit)

    }

    "change the comments of a class" in {
      val classIri = AnythingOntologyIri.makeClass("Nothing")

      val newObjects = Seq(
        StringLiteralV2.from("Represents nothing", Some("en")),
        StringLiteralV2.from("ne représente rien", Some("fr")),
      )

      // Make an unescaped copy of the new comments, because this is how we will receive them in the API response.
      val newObjectsUnescaped = newObjects.map { case StringLiteralV2(text, lang) =>
        StringLiteralV2.from(Iri.fromSparqlEncodedString(text), lang)
      }

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.changeClassLabelsOrComments(
          ChangeClassLabelsOrCommentsRequestV2(
            classIri = classIri,
            predicateToUpdate = LabelOrComment.Comment,
            newObjects = newObjects,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          )
        ))
      )

      val externalOntology = response.toOntologySchema(ApiV2Complex)
      assert(externalOntology.classes.size == 1)
      val readClassInfo = externalOntology.classes(classIri.smartIri)
      readClassInfo.entityInfoContent.predicates(Rdfs.Comment.toSmartIri).objects should ===(
        newObjectsUnescaped,
      )

      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "change the comments of a class, submitting the same comments again" in {
      val classIri = AnythingOntologyIri.makeClass("Nothing")

      val newObjects = Seq(
        StringLiteralV2.from("Represents nothing", Some("en")),
        StringLiteralV2.from("ne représente rien", Some("fr")),
      )

      // Make an unescaped copy of the new comments, because this is how we will receive them in the API response.
      val newObjectsUnescaped = newObjects.map { case StringLiteralV2(text, lang) =>
        StringLiteralV2.from(Iri.fromSparqlEncodedString(text), lang)
      }

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.changeClassLabelsOrComments(
          ChangeClassLabelsOrCommentsRequestV2(
            classIri = classIri,
            predicateToUpdate = LabelOrComment.Comment,
            newObjects = newObjects,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          )
        ))
      )

      val externalOntology = response.toOntologySchema(ApiV2Complex)
      assert(externalOntology.classes.size == 1)
      val readClassInfo = externalOntology.classes(classIri.smartIri)
      readClassInfo.entityInfoContent.predicates(Rdfs.Comment.toSmartIri).objects should ===(
        newObjectsUnescaped,
      )

      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "not create a class with the wrong rdf:type" in {
      val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong class", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid class definition", Some("en")),
            ),
          ),
        ),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser)),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a class that already exists" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Thing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong class", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid class definition", Some("en")),
            ),
          ),
        ),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser)),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a class with a nonexistent base class" in {
      val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong class", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid class definition", Some("en")),
            ),
          ),
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("NonexistentClass")),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser)),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a class that is not a subclass of knora-api:Resource" in {
      val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong class", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid class definition", Some("en")),
            ),
          ),
        ),
        subClassOf = Set("http://xmlns.com/foaf/0.1/Person".toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser)),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a class with a cardinality for a Knora property that doesn't exist" in {
      val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong class", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid class definition", Some("en")),
            ),
          ),
        ),
        directCardinalities =
          Map(AnythingOntologyIri.makeEntityIri("nonexistentProperty") -> KnoraCardinalityInfo(ZeroOrOne)),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser)),
        ),
      )
      assertFailsWithA[NotFoundException](exit)
    }

    "not create a class that has a cardinality for anything:hasInteger but is not a subclass of anything:Thing" in {
      val classIri = AnythingOntologyIri.makeEntityIri("WrongClass")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong class", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid class definition", Some("en")),
            ),
          ),
        ),
        directCardinalities = Map(AnythingOntologyIri.makeEntityIri("hasInteger") -> KnoraCardinalityInfo(ZeroOrOne)),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser)),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "create a subclass of anything:Thing that has cardinality 1 for anything:hasBoolean" in {
      val classIri = AnythingOntologyIri.makeEntityIri("RestrictiveThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("restrictive thing", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("A more restrictive Thing", Some("en")),
            ),
          ),
        ),
        directCardinalities = Map(AnythingOntologyIri.makeEntityIri("hasBoolean") -> KnoraCardinalityInfo(ExactlyOne)),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent should ===(classInfoContent)
        readClassInfo.allCardinalities(AnythingOntologyIri.makeEntityIri("hasBoolean")).cardinality should ===(
          ExactlyOne,
        )

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
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
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong class", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid class definition", Some("en")),
            ),
          ),
        ),
        directCardinalities = Map(AnythingOntologyIri.makeEntityIri("hasBoolean") -> KnoraCardinalityInfo(Unbounded)),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createClass(
            CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
          ),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "reject a request to delete a link value property directly" in {
      val hasInterestingThingValue = AnythingOntologyIri.makeProperty("hasInterestingThingValue")
      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.deleteProperty(
            propertyIri = hasInterestingThingValue,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          ),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "delete a link property and automatically delete the corresponding link value property" in {
      val linkPropIri = AnythingOntologyIri.makeProperty("hasInterestingThing")
      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.deleteProperty(
            propertyIri = linkPropIri,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          ),
        ),
      )
      assert(msg.ontologies.size == 1)
      val metadata = msg.ontologies.head
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate

      // Reload the ontology cache and see if we get the same result.
      UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyCache](_.refreshCache()),
      )
      // Check that both properties were deleted.
      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.getPropertiesFromOntologyV2(
            propertyIris = Set(linkPropIri),
            allLanguages = true,
            requestingUser = anythingAdminUser,
          ),
        ),
      )
      assertFailsWithA[NotFoundException](exit)

      val linkValuePropIri = linkPropIri.fromLinkPropToLinkValueProp
      val exit2 = UnsafeZioRun.run(
        ontologyResponder(_.getPropertiesFromOntologyV2(Set(linkValuePropIri), true, anythingAdminUser)),
      )
      assertFailsWithA[NotFoundException](exit2)
    }

    "not create a property if rdfs:label is missing" in {
      val invalid = validPropertyInfo.copy(predicates = validPropertyInfo.predicates - Rdfs.Label.toSmartIri)
      assertFailsWithA[BadRequestException](
        UnsafeZioRun.run(
          ontologyResponder(_.createProperty(invalid, anythingLastModDate, UUID.randomUUID, anythingAdminUser)),
        ),
        s"Missing ${Rdfs.Label}",
      )
    }

    "not create a property if rdfs:label is missing a language" in {
      val invalid =
        validPropertyInfo.copy(predicates =
          validPropertyInfo.predicates.updated(
            Rdfs.Label.toSmartIri,
            PredicateInfoV2(Rdfs.Label.toSmartIri, List(StringLiteralV2.unsafeFrom("foo", None))),
          ),
        )
      assertFailsWithA[BadRequestException](
        UnsafeZioRun.run(
          ontologyResponder(_.createProperty(invalid, anythingLastModDate, UUID.randomUUID, anythingAdminUser)),
        ),
        s"All values of ${Rdfs.Label} must be string literals with a language code",
      )
    }

    "not create a property if rdfs:comment is missing a language" in {
      val invalid =
        validPropertyInfo.copy(predicates =
          validPropertyInfo.predicates.updated(
            Rdfs.Comment.toSmartIri,
            PredicateInfoV2(Rdfs.Comment.toSmartIri, List(StringLiteralV2.unsafeFrom("foo", None))),
          ),
        )
      assertFailsWithA[BadRequestException](
        UnsafeZioRun.run(
          ontologyResponder(_.createProperty(invalid, anythingLastModDate, UUID.randomUUID, anythingAdminUser)),
        ),
        s"All values of ${Rdfs.Comment} must be string literals with a language code",
      )
    }

    "create a property anything:hasNothingness with knora-api:subjectType anything:Nothing" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasNothingness")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Nothing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.BooleanValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("has nothingness", Some("en")),
              StringLiteralV2.from("hat Nichtsein", Some("de")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("Indicates whether a Nothing has nothingness", Some("en")),
              StringLiteralV2.from("Anzeigt, ob ein Nichts Nichtsein hat", Some("de")),
            ),
          ),
          SalsahGui.External.GuiElementProp.toSmartIri -> PredicateInfoV2(
            predicateIri = SalsahGui.External.GuiElementProp.toSmartIri,
            objects = Seq(SmartIriLiteralV2("http://api.knora.org/ontology/salsah-gui/v2#Checkbox".toSmartIri)),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )

      val externalOntology = msg.toOntologySchema(ApiV2Complex)
      assert(externalOntology.properties.size == 1)
      val property = externalOntology.properties(propertyIri)
      property.entityInfoContent should ===(propertyInfoContent)

      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "change the salsah-gui:guiElement and salsah-gui:guiAttribute of anything:hasNothingness" in {
      val propertyIri = AnythingOntologyIri.makeProperty("hasNothingness")
      val guiElement =
        Schema.GuiElement
          .make("http://www.knora.org/ontology/salsah-gui#SimpleText")
          .fold(e => throw e.head, v => Some(v))
      val guiAttributes =
        Set("size=80")
          .map(attribute =>
            Schema.GuiAttribute
              .make(attribute)
              .fold(e => throw e.head, v => v),
          )
      val guiObject =
        Schema.GuiObject
          .make(guiAttributes, guiElement)
          .fold(e => throw e.head, v => v)

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.changePropertyGuiElement(
          ChangePropertyGuiElementRequest(
            propertyIri = propertyIri,
            newGuiObject = guiObject,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          )
        ))
      )

      val msg = response
      msg.properties.head._2.entityInfoContent.predicates
          .get(stringFormatter.toSmartIri(SalsahGui.GuiElementProp)) match {
          case Some(predicateInfo) =>
            val guiElementTypeFromMessage = predicateInfo.objects.head.asInstanceOf[SmartIriLiteralV2]
            val guiElementTypeInternal    = guiElementTypeFromMessage.toOntologySchema(InternalSchema)
            guiElementTypeFromMessage should equal(guiElementTypeInternal)
          case None => ()
        }

        // Check that the salsah-gui:guiElement from the message is as expected
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.properties.size == 1)
        val property = externalOntology.properties(propertyIri.smartIri)

        val guiElementPropComplex = property.entityInfoContent.predicates(
          SalsahGui.External.GuiElementProp.toSmartIri,
        )

        val guiElementPropComplexExpected = PredicateInfoV2(
          predicateIri = SalsahGui.External.GuiElementProp.toSmartIri,
          objects = Seq(SmartIriLiteralV2("http://api.knora.org/ontology/salsah-gui/v2#SimpleText".toSmartIri)),
        )

        guiElementPropComplex should equal(guiElementPropComplexExpected)

        val guiAttributeComplex = property.entityInfoContent.predicates(
          SalsahGui.External.GuiAttribute.toSmartIri,
        )

        val guiAttributeComplexExpected = PredicateInfoV2(
          predicateIri = SalsahGui.External.GuiAttribute.toSmartIri,
          objects = Seq(StringLiteralV2.from("size=80", None)),
        )

        guiAttributeComplex should equal(guiAttributeComplexExpected)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
    }

    "delete the salsah-gui:guiElement and salsah-gui:guiAttribute of anything:hasNothingness" in {
      val propertyIri                             = AnythingOntologyIri.makeProperty("hasNothingness")
      val guiElement                              = None
      val guiAttributes: Set[Schema.GuiAttribute] = Set.empty
      val guiObject =
        Schema.GuiObject
          .make(guiAttributes, guiElement)
          .fold(e => throw e.head, v => v)

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.changePropertyGuiElement(
          ChangePropertyGuiElementRequest(
            propertyIri = propertyIri,
            newGuiObject = guiObject,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          )
        ))
      )

      val externalOntology = response.toOntologySchema(ApiV2Complex)
      assert(externalOntology.properties.size == 1)
      val property = externalOntology.properties(propertyIri.smartIri)

      property.entityInfoContent.predicates
        .get(SalsahGui.External.GuiElementProp.toSmartIri) should ===(None)

      property.entityInfoContent.predicates
        .get(SalsahGui.External.GuiAttribute.toSmartIri) should ===(None)

      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "not create a property called anything:Thing, because that IRI is already used for a class" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("Thing")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Nothing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.BooleanValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a class called anything:hasNothingness, because that IRI is already used for a property" in {
      val classIri = AnythingOntologyIri.makeEntityIri("hasNothingness")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("wrong class", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid class definition", Some("en")),
            ),
          ),
        ),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createClass(
            CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
          ),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "create a class anything:Void as a subclass of anything:Nothing" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Void")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("void", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("Represents a void", Some("en"))),
          ),
        ),
        subClassOf = Set(AnythingOntologyIri.makeEntityIri("Nothing")),
        ontologySchema = ApiV2Complex,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent should ===(classInfoContent)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
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
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(ExactlyOne),
        ),
        ontologySchema = ApiV2Complex,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.addCardinalitiesToClass(
              AddCardinalitiesToClassRequestV2(
                classInfoContent,
                anythingLastModDate,
                UUID.randomUUID,
                anythingAdminUser,
              ),
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.deleteCardinalitiesFromClass(
          DeleteCardinalitiesFromClassRequestV2(
            classInfoContent = classInfoContent,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          )
        ))
      )

      val externalOntology = response.toOntologySchema(ApiV2Complex)
      val metadata         = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "not allow a user to delete a class if they are not a sysadmin or an admin in the ontology's project" in {
      val classIri = AnythingOntologyIri.makeClass("Void")

      val exit = UnsafeZioRun.run(
        ZIO.serviceWithZIO[OntologyResponderV2](_.deleteClass(
          classIri = classIri,
          lastModificationDate = anythingLastModDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = anythingNonAdminUser,
        ))
      )
      assertFailsWithA[ForbiddenException](exit)
    }

    "delete the class anything:Void" in {
      val classIri = AnythingOntologyIri.makeClass("Void")

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.deleteClass(
          classIri = classIri,
          lastModificationDate = anythingLastModDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = anythingAdminUser,
        ))
      )

      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "not allow a user to add a cardinality to a class if they are not a sysadmin or an admin in the user's project" in {

      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(0),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.addCardinalitiesToClass(
            AddCardinalitiesToClassRequestV2(
              classInfoContent,
              anythingLastModDate,
              UUID.randomUUID,
              anythingNonAdminUser,
            ),
          ),
        ),
      )
      assertFailsWithA[ForbiddenException](exit)
    }

    "create a link property, anything:hasOtherNothing, and add a cardinality for it to the class anything:Nothing" in {
      val classIri    = AnythingOntologyIri.makeEntityIri("Nothing")
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasOtherNothing")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(classIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(classIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("has other nothing", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("Indicates whether a Nothing has another Nothing", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      val externalOntology = msg.toOntologySchema(ApiV2Complex)
      val metadata         = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          propertyIri -> KnoraCardinalityInfo(cardinality = ZeroOrOne, guiOrder = Some(0)),
        ),
        ontologySchema = ApiV2Complex,
      )

      val expectedDirectCardinalities = Map(
        propertyIri -> KnoraCardinalityInfo(cardinality = ZeroOrOne, guiOrder = Some(0)),
        propertyIri.fromLinkPropToLinkValueProp -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(0),
        ),
      )

      val expectedProperties = Set(
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
        propertyIri,
        propertyIri.fromLinkPropToLinkValueProp,
      )

      val expectedAllBaseClasses: Seq[SmartIri] = Seq(
        (anythingOntology + "Nothing").toSmartIri,
        "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.addCardinalitiesToClass(
              AddCardinalitiesToClassRequestV2(
                classInfoContent,
                anythingLastModDate,
                UUID.randomUUID,
                anythingAdminUser,
              ),
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        assert(readClassInfo.allBaseClasses == expectedAllBaseClasses)
        readClassInfo.entityInfoContent.directCardinalities should ===(expectedDirectCardinalities)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
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
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
            cardinality = Unbounded,
            guiOrder = Some(0),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.addCardinalitiesToClass(
            AddCardinalitiesToClassRequestV2(
              classInfoContent,
              anythingLastModDate,
              UUID.randomUUID,
              anythingAdminUser,
            ),
          ),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "add a cardinality for the property anything:hasNothingness to the class anything:Nothing" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(0),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )

      val expectedDirectCardinalities = Map(
        AnythingOntologyIri.makeEntityIri("hasOtherNothing") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(0),
        ),
        AnythingOntologyIri.makeEntityIri("hasOtherNothingValue") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(0),
        ),
        AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(0),
        ),
      )

      val expectedProperties = Set(
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
        AnythingOntologyIri.makeEntityIri("hasOtherNothing"),
        AnythingOntologyIri.makeEntityIri("hasOtherNothingValue"),
        AnythingOntologyIri.makeEntityIri("hasNothingness"),
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.addCardinalitiesToClass(
              AddCardinalitiesToClassRequestV2(
                classInfoContent,
                anythingLastModDate,
                UUID.randomUUID,
                anythingAdminUser,
              ),
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent.directCardinalities should ===(expectedDirectCardinalities)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
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
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(AtLeastOne),
        ),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.addCardinalitiesToClass(
            AddCardinalitiesToClassRequestV2(
              classInfoContent,
              anythingLastModDate,
              UUID.randomUUID,
              anythingAdminUser,
            ),
          ),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "add a maxCardinality=1 for property anything:hasName to class anything:BlueThing even though the class is used in data" in {
      val classIri = AnythingOntologyIri.makeEntityIri("BlueThing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(ZeroOrOne),
        ),
        ontologySchema = ApiV2Complex,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.addCardinalitiesToClass(
              AddCardinalitiesToClassRequestV2(
                classInfoContent,
                anythingLastModDate,
                UUID.randomUUID,
                anythingAdminUser,
              ),
            ),
          ),
        )

        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
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
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(AnythingOntologyIri.makeEntityIri("Nothing"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.BooleanValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("has emptiness", Some("en")),
              StringLiteralV2.from("hat Leerheit", Some("de")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("Indicates whether a Nothing has emptiness", Some("en")),
              StringLiteralV2.from("Anzeigt, ob ein Nichts Leerheit hat", Some("de")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      val externalOntology = msg.toOntologySchema(ApiV2Complex)
      assert(externalOntology.properties.size == 1)
      val property = externalOntology.properties(propertyIri)

      property.entityInfoContent should ===(propertyInfoContent)
      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "add a cardinality for the property anything:hasEmptiness to the class anything:Nothing" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(1),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )

      val expectedDirectCardinalities = Map(
        AnythingOntologyIri.makeEntityIri("hasOtherNothing") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(0),
        ),
        AnythingOntologyIri.makeEntityIri("hasOtherNothingValue") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(0),
        ),
        AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(0),
        ),
        AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(1),
        ),
      )

      val expectedProperties = Set(
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
        AnythingOntologyIri.makeEntityIri("hasOtherNothing"),
        AnythingOntologyIri.makeEntityIri("hasOtherNothingValue"),
        AnythingOntologyIri.makeEntityIri("hasNothingness"),
        AnythingOntologyIri.makeEntityIri("hasEmptiness"),
      )

      val addReq = AddCardinalitiesToClassRequestV2(
        classInfoContent,
        anythingLastModDate,
        UUID.randomUUID,
        anythingAdminUser,
      )
      val msg = UnsafeZioRun.runOrThrow(ontologyResponder(_.addCardinalitiesToClass(addReq)))

      val externalOntology = msg.toOntologySchema(ApiV2Complex)
      assert(externalOntology.classes.size == 1)
      val readClassInfo = externalOntology.classes(classIri)
      readClassInfo.entityInfoContent.directCardinalities should ===(expectedDirectCardinalities)
      readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "not allow a user to change the cardinalities of a class if they are not a sysadmin or an admin in the user's project" in {

      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(0),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ZIO.serviceWithZIO[OntologyResponderV2](_.replaceClassCardinalities(
          ReplaceClassCardinalitiesRequestV2(
            classInfoContent = classInfoContent,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingNonAdminUser,
          )
        ))
      )
      assertFailsWithA[ForbiddenException](exit)

    }

    "change the GUI order of the cardinalities of the class anything:Nothing" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasOtherNothing") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(1),
          ),
          AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(2),
          ),
          AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(3),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.changeGuiOrder(
          ChangeGuiOrderRequestV2(
            classInfoContent = classInfoContent,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          )
        ))
      )

      val expectedCardinalities = Map(
        AnythingOntologyIri.makeEntityIri("hasOtherNothing") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(1),
        ),
        AnythingOntologyIri.makeEntityIri("hasOtherNothingValue") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(1),
        ),
        AnythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(2),
        ),
        AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(3),
        ),
      )

      val externalOntology = response.toOntologySchema(ApiV2Complex)
      assert(externalOntology.classes.size == 1)
      val readClassInfo = externalOntology.classes(classIri)
      readClassInfo.entityInfoContent.directCardinalities should ===(expectedCardinalities)

      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "change the cardinalities of the class anything:Nothing, removing anything:hasOtherNothing and anything:hasNothingness and leaving anything:hasEmptiness" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(0),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.replaceClassCardinalities(
          ReplaceClassCardinalitiesRequestV2(
            classInfoContent = classInfoContent,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          )
        ))
      )

      val expectedProperties = Set(
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
        AnythingOntologyIri.makeEntityIri("hasEmptiness"),
      )

      val expectedAllBaseClasses: Seq[SmartIri] = Seq(
        (anythingOntology + "Nothing").toSmartIri,
        "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri,
      )

      val externalOntology = response.toOntologySchema(ApiV2Complex)
      assert(externalOntology.classes.size == 1)
      val readClassInfo = externalOntology.classes(classIri)
      assert(readClassInfo.allBaseClasses == expectedAllBaseClasses)
      readClassInfo.entityInfoContent.directCardinalities should ===(classInfoContent.directCardinalities)
      readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "not delete the class anything:Nothing, because the property anything:hasEmptiness refers to it" in {
      val classIri = AnythingOntologyIri.makeClass("Nothing")

      val exit = UnsafeZioRun.run(
        ZIO.serviceWithZIO[OntologyResponderV2](_.deleteClass(
          classIri = classIri,
          lastModificationDate = anythingLastModDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = anythingAdminUser,
        ))
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "delete the property anything:hasNothingness" in {
      val hasNothingness = AnythingOntologyIri.makeProperty("hasNothingness")
      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.deleteProperty(
            propertyIri = hasNothingness,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          ),
        ),
      )
      assert(msg.ontologies.size == 1)
      val metadata = msg.ontologies.head
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "not delete the property anything:hasEmptiness, because the class anything:Nothing refers to it" in {
      val hasNothingness = AnythingOntologyIri.makeProperty("hasEmptiness")
      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.deleteProperty(
            propertyIri = hasNothingness,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          ),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not allow a user to remove all cardinalities from a class if they are not a sysadmin or an admin in the user's project" in {

      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ZIO.serviceWithZIO[OntologyResponderV2](_.replaceClassCardinalities(
          ReplaceClassCardinalitiesRequestV2(
            classInfoContent = classInfoContent,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingNonAdminUser,
          )
        ))
      )
      assertFailsWithA[ForbiddenException](exit)
    }

    "remove all cardinalities from the class anything:Nothing" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Nothing")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.replaceClassCardinalities(
          ReplaceClassCardinalitiesRequestV2(
            classInfoContent = classInfoContent,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          )
        ))
      )

      val expectedProperties = Set(
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
      )

      val externalOntology = response.toOntologySchema(ApiV2Complex)
      assert(externalOntology.classes.size == 1)
      val readClassInfo = externalOntology.classes(classIri)
      readClassInfo.entityInfoContent.directCardinalities should ===(classInfoContent.directCardinalities)
      readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "not delete the property anything:hasEmptiness with the wrong knora-api:lastModificationDate" in {
      val hasEmptiness = AnythingOntologyIri.makeProperty("hasEmptiness")
      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.deleteProperty(
            propertyIri = hasEmptiness,
            lastModificationDate = anythingLastModDate.minusSeconds(60),
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          ),
        ),
      )
      assertFailsWithA[EditConflictException](exit)
    }

    "not allow a user to delete a property if they are not a sysadmin or an admin in the ontology's project" in {
      val hasEmptiness = AnythingOntologyIri.makeProperty("hasEmptiness")
      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.deleteProperty(
            propertyIri = hasEmptiness,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingNonAdminUser,
          ),
        ),
      )
      assertFailsWithA[ForbiddenException](exit)
    }

    "delete the properties anything:hasOtherNothing and anything:hasEmptiness" in {
      val hasOtherNothing = AnythingOntologyIri.makeProperty("hasOtherNothing")
      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.deleteProperty(
            propertyIri = hasOtherNothing,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          ),
        ),
      )
      assert(msg.ontologies.size == 1)
      val metadata = msg.ontologies.head
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate

      { // prop2
        val hasEmptiness = AnythingOntologyIri.makeProperty("hasEmptiness")
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.deleteProperty(
              propertyIri = hasEmptiness,
              lastModificationDate = anythingLastModDate,
              apiRequestID = UUID.randomUUID,
              requestingUser = anythingAdminUser,
            ),
          ),
        )

        assert(msg.ontologies.size == 1)
        val metadata = msg.ontologies.head
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the class anything:Nothing" in {
      val classIri = AnythingOntologyIri.makeClass("Nothing")

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.deleteClass(
          classIri = classIri,
          lastModificationDate = anythingLastModDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = anythingAdminUser,
        ))
      )

      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "not create a class whose base class is in a non-shared ontology in another project" in {
      val classIri = AnythingOntologyIri.makeEntityIri("InvalidClass")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("invalid class", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("Represents an invalid class", Some("en"))),
          ),
        ),
        subClassOf = Set(IncunabulaOntologyIri.makeEntityIri("book")),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createClass(
            CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
          ),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a class with a cardinality on a property defined in a non-shared ontology in another project" in {
      val classIri = AnythingOntologyIri.makeEntityIri("InvalidClass")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("invalid class", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("Represents an invalid class", Some("en"))),
          ),
        ),
        subClassOf = Set(OntologyConstants.KnoraApiV2Complex.Resource.toSmartIri),
        directCardinalities =
          Map(IncunabulaOntologyIri.makeEntityIri("description") -> KnoraCardinalityInfo(ZeroOrOne)),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createClass(
            CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
          ),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create a subproperty of a property defined in a non-shared ontology in another project" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("invalidProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("invalid property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(IncunabulaOntologyIri.makeEntityIri("description")),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create property with a subject type defined in a non-shared ontology in another project" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("invalidProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(IncunabulaOntologyIri.makeEntityIri("book"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("invalid property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "not create property with an object type defined in a non-shared ontology in another project" in {

      val propertyIri = AnythingOntologyIri.makeEntityIri("invalidProperty")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(IncunabulaOntologyIri.makeEntityIri("book"))),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("invalid property", Some("en")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("An invalid property definition", Some("en")),
            ),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val exit = UnsafeZioRun.run(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      assertFailsWithA[BadRequestException](exit)
    }

    "create a class anything:AnyBox1 as a subclass of example-box:Box" in {
      val classIri = AnythingOntologyIri.makeEntityIri("AnyBox1")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("any box", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("Represents any box", Some("en"))),
          ),
        ),
        subClassOf = Set(ExampleSharedOntologyIri.makeEntityIri("Box")),
        ontologySchema = ApiV2Complex,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent should ===(classInfoContent)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the class anything:AnyBox1" in {
      val classIri = AnythingOntologyIri.makeClass("AnyBox1")

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.deleteClass(
          classIri = classIri,
          lastModificationDate = anythingLastModDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = anythingAdminUser,
        ))
      )

      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "create a class anything:AnyBox2 with a cardinality on example-box:hasName" in {
      val classIri = AnythingOntologyIri.makeEntityIri("AnyBox2")

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("any box", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("Represents any box", Some("en"))),
          ),
        ),
        subClassOf = Set(ExampleSharedOntologyIri.makeEntityIri("Box")),
        directCardinalities = Map(ExampleSharedOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(ZeroOrOne)),
        ontologySchema = ApiV2Complex,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
            ),
          ),
        )
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent should ===(classInfoContent)

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the class anything:AnyBox2" in {
      val classIri = AnythingOntologyIri.makeClass("AnyBox2")

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.deleteClass(
          classIri = classIri,
          lastModificationDate = anythingLastModDate,
          apiRequestID = UUID.randomUUID,
          requestingUser = anythingAdminUser,
        ))
      )

      assert(response.ontologies.size == 1)
      val metadata = response.ontologies.head
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "create a property anything:hasAnyName with base property example-box:hasName" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasAnyName")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("has any shared name", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("Represents a shared name", Some("en"))),
          ),
        ),
        subPropertyOf = Set(ExampleSharedOntologyIri.makeEntityIri("hasName")),
        ontologySchema = ApiV2Complex,
      )

      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      val externalOntology = msg.toOntologySchema(ApiV2Complex)
      assert(externalOntology.properties.size == 1)
      val property = externalOntology.properties(propertyIri)

      property.entityInfoContent should ===(propertyInfoContent)
      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "delete the property anything:hasAnyName" in {
      val propertyIri = AnythingOntologyIri.makeProperty("hasAnyName")
      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.deleteProperty(
            propertyIri = propertyIri,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          ),
        ),
      )
      assert(msg.ontologies.size == 1)
      val metadata = msg.ontologies.head
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "create a property anything:BoxHasBoolean with subject type example-box:Box" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("BoxHasBoolean")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(ExampleSharedOntologyIri.makeEntityIri("Box"))),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.BooleanValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("has boolean", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("Represents a boolean", Some("en"))),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )
      val externalOntology = msg.toOntologySchema(ApiV2Complex)
      assert(externalOntology.properties.size == 1)
      val property = externalOntology.properties(propertyIri)

      property.entityInfoContent should ===(propertyInfoContent)
      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "delete the property anything:BoxHasBoolean" in {
      val propertyIri = AnythingOntologyIri.makeProperty("BoxHasBoolean")
      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.deleteProperty(
            propertyIri = propertyIri,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          ),
        ),
      )
      assert(msg.ontologies.size == 1)
      val metadata = msg.ontologies.head
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "create a property anything:hasBox with object type example-box:Box" in {
      val propertyIri = AnythingOntologyIri.makeEntityIri("hasBox")

      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(ExampleSharedOntologyIri.makeEntityIri("Box"))),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("has box", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("Has a box", Some("en"))),
          ),
        ),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2Complex.HasLinkTo.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )

      val externalOntology = msg.toOntologySchema(ApiV2Complex)
      assert(externalOntology.properties.size == 1)
      val property = externalOntology.properties(propertyIri)

      property.entityInfoContent should ===(propertyInfoContent)
      val metadata = externalOntology.ontologyMetadata
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "delete the property anything:hasBox" in {
      val propertyIri = AnythingOntologyIri.makeProperty("hasBox")
      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.deleteProperty(
            propertyIri = propertyIri,
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          ),
        ),
      )
      assert(msg.ontologies.size == 1)
      val metadata = msg.ontologies.head
      val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
    }

    "create a class with several cardinalities, then remove one of the cardinalities" in {
      // Create a class with no cardinalities.

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(
                classInfoContent = ClassInfoContentV2(
                  predicates = Map(
                    "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
                      predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                      objects = Vector(
                        StringLiteralV2.from(
                          value = "test class",
                          language = Some("en"),
                        ),
                      ),
                    ),
                    "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
                      predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
                      objects = Vector(
                        StringLiteralV2.from(
                          value = "A test class",
                          language = Some("en"),
                        ),
                      ),
                    ),
                    "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                      predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                      objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri)),
                    ),
                  ),
                  classIri = (anythingOntology + "TestClass").toSmartIri,
                  ontologySchema = ApiV2Complex,
                  subClassOf = Set("http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri),
                ),
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser,
              ),
            ),
          ),
        )
        val newAnythingLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      // Create a text property.
      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(
            propertyInfoContent = PropertyInfoContentV2(
              propertyIri = (anythingOntology + "testTextProp").toSmartIri,
              predicates = Map(
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                  objects = Vector(
                    StringLiteralV2.from(
                      value = "test text property",
                      language = Some("en"),
                    ),
                  ),
                ),
                "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri,
                  objects = Vector(SmartIriLiteralV2(value = (anythingOntology + "TestClass").toSmartIri)),
                ),
                "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
                  objects = Vector(
                    StringLiteralV2.from(
                      value = "A test text property",
                      language = Some("en"),
                    ),
                  ),
                ),
                "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri,
                  objects =
                    Vector(SmartIriLiteralV2(value = "http://api.knora.org/ontology/knora-api/v2#TextValue".toSmartIri)),
                ),
                "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                  objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri)),
                ),
              ),
              subPropertyOf = Set("http://api.knora.org/ontology/knora-api/v2#hasValue".toSmartIri),
              ontologySchema = ApiV2Complex,
            ),
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          ),
        ),
      )

      val newAnythingLastModDate = msg.ontologyMetadata.lastModificationDate
        .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate

      // Create an integer property.

      val msg2 = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(
            propertyInfoContent = PropertyInfoContentV2(
              propertyIri = (anythingOntology + "testIntProp").toSmartIri,
              predicates = Map(
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                  objects = Vector(
                    StringLiteralV2.from(
                      value = "test int property",
                      language = Some("en"),
                    ),
                  ),
                ),
                "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri,
                  objects = Vector(SmartIriLiteralV2(value = (anythingOntology + "TestClass").toSmartIri)),
                ),
                "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
                  objects = Vector(
                    StringLiteralV2.from(
                      value = "A test int property",
                      language = Some("en"),
                    ),
                  ),
                ),
                "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri,
                  objects =
                    Vector(SmartIriLiteralV2(value = "http://api.knora.org/ontology/knora-api/v2#IntValue".toSmartIri)),
                ),
                "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                  objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri)),
                ),
              ),
              subPropertyOf = Set("http://api.knora.org/ontology/knora-api/v2#hasValue".toSmartIri),
              ontologySchema = ApiV2Complex,
            ),
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          ),
        ),
      )

      val newAnythingLastModDate2 = msg2.ontologyMetadata.lastModificationDate
        .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
      assert(newAnythingLastModDate2.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate2

      // Create a link property.
      val msg3 = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(
            propertyInfoContent = PropertyInfoContentV2(
              propertyIri = (anythingOntology + "testLinkProp").toSmartIri,
              predicates = Map(
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                  objects = Vector(
                    StringLiteralV2.from(
                      value = "test link property",
                      language = Some("en"),
                    ),
                  ),
                ),
                "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri,
                  objects = Vector(SmartIriLiteralV2(value = (anythingOntology + "TestClass").toSmartIri)),
                ),
                "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
                  objects = Vector(
                    StringLiteralV2.from(
                      value = "A test link property",
                      language = Some("en"),
                    ),
                  ),
                ),
                "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri,
                  objects = Vector(SmartIriLiteralV2(value = (anythingOntology + "TestClass").toSmartIri)),
                ),
                "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                  objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri)),
                ),
              ),
              subPropertyOf = Set("http://api.knora.org/ontology/knora-api/v2#hasLinkTo".toSmartIri),
              ontologySchema = ApiV2Complex,
            ),
            lastModificationDate = anythingLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          ),
        ),
      )

      val newAnythingLastModDate3 = msg3.ontologyMetadata.lastModificationDate
        .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
      assert(newAnythingLastModDate3.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate3

      // Add cardinalities to the class.
      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.addCardinalitiesToClass(
              AddCardinalitiesToClassRequestV2(
                classInfoContent = ClassInfoContentV2(
                  predicates = Map(
                    "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                      predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                      objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri)),
                    ),
                  ),
                  classIri = (anythingOntology + "TestClass").toSmartIri,
                  ontologySchema = ApiV2Complex,
                  directCardinalities = Map(
                    (anythingOntology + "testTextProp").toSmartIri -> KnoraCardinalityInfo(
                      cardinality = ZeroOrOne,
                    ),
                    (anythingOntology + "testIntProp").toSmartIri -> KnoraCardinalityInfo(
                      cardinality = ZeroOrOne,
                    ),
                    (anythingOntology + "testLinkProp").toSmartIri -> KnoraCardinalityInfo(
                      cardinality = ZeroOrOne,
                    ),
                  ),
                ),
                lastModificationDate = anythingLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser,
              ),
            ),
          ),
        )
        val newAnythingLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      // Remove the link value cardinality from the class.

      appActor ! ReplaceClassCardinalitiesRequestV2(
        classInfoContent = ClassInfoContentV2(
          predicates = Map(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri)),
            ),
          ),
          classIri = (anythingOntology + "TestClass").toSmartIri,
          ontologySchema = ApiV2Complex,
          directCardinalities = Map(
            (anythingOntology + "testTextProp").toSmartIri -> KnoraCardinalityInfo(
              cardinality = ZeroOrOne,
            ),
            (anythingOntology + "testIntProp").toSmartIri -> KnoraCardinalityInfo(
              cardinality = ZeroOrOne,
            ),
          ),
        ),
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser,
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val newAnythingLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      // Check that the correct blank nodes were stored for the cardinalities.
      val actual = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[TriplestoreService](
          _.query(
            Select(
              """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                |
                |SELECT ?cardinalityProp
                |WHERE {
                |  <http://www.knora.org/ontology/0001/anything#TestClass> rdfs:subClassOf ?restriction .
                |  FILTER isBlank(?restriction)
                |  ?restriction owl:onProperty ?cardinalityProp .
                |}""".stripMargin,
            ),
          ),
        ),
      )

      assert(
        actual.getColOrThrow("cardinalityProp").sorted == Seq(
          "http://www.knora.org/ontology/0001/anything#testIntProp",
          "http://www.knora.org/ontology/0001/anything#testTextProp",
        ),
      )
    }

    "create a class with two cardinalities, use one in data, and allow only removal of the cardinality for the property not used in data" in {

      // Create a class with no cardinalities.

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(
                classInfoContent = ClassInfoContentV2(
                  predicates = Map(
                    "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
                      predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                      objects = Vector(
                        StringLiteralV2.from(
                          value = "A Blue Free Test class",
                          language = Some("en"),
                        ),
                      ),
                    ),
                    "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
                      predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
                      objects = Vector(
                        StringLiteralV2.from(
                          value = "A Blue Free Test class used for testing cardinalities",
                          language = Some("en"),
                        ),
                      ),
                    ),
                    "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                      predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                      objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri)),
                    ),
                  ),
                  classIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#BlueFreeTestClass".toSmartIri,
                  ontologySchema = ApiV2Complex,
                  subClassOf = Set("http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri),
                ),
                lastModificationDate = freetestLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser,
              ),
            ),
          ),
        )

        val newFreetestLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate
      }

      // Create a text property.
      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(
            propertyInfoContent = PropertyInfoContentV2(
              propertyIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasBlueTestTextProp".toSmartIri,
              predicates = Map(
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                  objects = Vector(
                    StringLiteralV2.from(
                      value = "blue test text property",
                      language = Some("en"),
                    ),
                  ),
                ),
                "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri,
                  objects = Vector(
                    SmartIriLiteralV2(value =
                      "http://0.0.0.0:3333/ontology/0001/freetest/v2#BlueFreeTestClass".toSmartIri,
                    ),
                  ),
                ),
                "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
                  objects = Vector(
                    StringLiteralV2.from(
                      value = "A blue test text property",
                      language = Some("en"),
                    ),
                  ),
                ),
                "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri,
                  objects =
                    Vector(SmartIriLiteralV2(value = "http://api.knora.org/ontology/knora-api/v2#TextValue".toSmartIri)),
                ),
                "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                  objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri)),
                ),
              ),
              subPropertyOf = Set("http://api.knora.org/ontology/knora-api/v2#hasValue".toSmartIri),
              ontologySchema = ApiV2Complex,
            ),
            lastModificationDate = freetestLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          ),
        ),
      )

      val newFreetestLastModDate = msg.ontologyMetadata.lastModificationDate
        .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
      assert(newFreetestLastModDate.isAfter(freetestLastModDate))
      freetestLastModDate = newFreetestLastModDate

      // Create an integer property.
      val msg2 = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(
            propertyInfoContent = PropertyInfoContentV2(
              propertyIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasBlueTestIntProp".toSmartIri,
              predicates = Map(
                "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                  objects = Vector(
                    StringLiteralV2.from(
                      value = "blue test integer property",
                      language = Some("en"),
                    ),
                  ),
                ),
                "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://api.knora.org/ontology/knora-api/v2#subjectType".toSmartIri,
                  objects = Vector(
                    SmartIriLiteralV2(value =
                      "http://0.0.0.0:3333/ontology/0001/freetest/v2#BlueFreeTestClass".toSmartIri,
                    ),
                  ),
                ),
                "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
                  objects = Vector(
                    StringLiteralV2.from(
                      value = "A blue test integer property",
                      language = Some("en"),
                    ),
                  ),
                ),
                "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://api.knora.org/ontology/knora-api/v2#objectType".toSmartIri,
                  objects =
                    Vector(SmartIriLiteralV2(value = "http://api.knora.org/ontology/knora-api/v2#IntValue".toSmartIri)),
                ),
                "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                  predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                  objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri)),
                ),
              ),
              subPropertyOf = Set("http://api.knora.org/ontology/knora-api/v2#hasValue".toSmartIri),
              ontologySchema = ApiV2Complex,
            ),
            lastModificationDate = freetestLastModDate,
            apiRequestID = UUID.randomUUID,
            requestingUser = anythingAdminUser,
          ),
        ),
      )

      val newFreetestLastModDate2 = msg2.ontologyMetadata.lastModificationDate
        .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
      assert(newFreetestLastModDate2.isAfter(freetestLastModDate))
      freetestLastModDate = newFreetestLastModDate2

      // Add cardinalities to the class.
      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.addCardinalitiesToClass(
              AddCardinalitiesToClassRequestV2(
                classInfoContent = ClassInfoContentV2(
                  predicates = Map(
                    "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                      predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                      objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri)),
                    ),
                  ),
                  classIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#BlueFreeTestClass".toSmartIri,
                  ontologySchema = ApiV2Complex,
                  directCardinalities = Map(
                    "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasBlueTestTextProp".toSmartIri -> KnoraCardinalityInfo(
                      cardinality = ZeroOrOne,
                    ),
                    "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasBlueTestIntProp".toSmartIri -> KnoraCardinalityInfo(
                      cardinality = ZeroOrOne,
                    ),
                  ),
                ),
                lastModificationDate = freetestLastModDate,
                apiRequestID = UUID.randomUUID,
                requestingUser = anythingAdminUser,
              ),
            ),
          ),
        )

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
              comment = Some("this is the number five"),
            ),
            permissions = Some("CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher"),
          ),
        ),
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#BlueFreeTestClass".toSmartIri,
        label = "my blue test class thing instance",
        values = inputValues,
        projectADM = SharedTestDataADM.anythingProject,
      )

      appActor.ask(
        CreateResourceRequestV2(
          createResource = inputResource,
          requestingUser = anythingAdminUser,
          apiRequestID = UUID.randomUUID,
        ),
      )(timeout)

      // Successfully check if the cardinality can be deleted

      appActor ! CanDeleteCardinalitiesFromClassRequestV2(
        classInfoContent = ClassInfoContentV2(
          predicates = Map(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri)),
            ),
          ),
          classIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#BlueFreeTestClass".toSmartIri,
          ontologySchema = ApiV2Complex,
          directCardinalities = Map(
            "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasBlueTestTextProp".toSmartIri -> KnoraCardinalityInfo(
              cardinality = ZeroOrOne,
            ),
          ),
        ),
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser,
      )

      expectMsgPF(timeout) { case msg: CanDoResponseV2 =>
        assert(msg.canDo.value)
      }

      // Successfully remove the (unused) text value cardinality from the class.

      appActor ! DeleteCardinalitiesFromClassRequestV2(
        classInfoContent = ClassInfoContentV2(
          predicates = Map(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
              predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              objects = Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#Class".toSmartIri)),
            ),
          ),
          classIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#BlueFreeTestClass".toSmartIri,
          ontologySchema = ApiV2Complex,
          directCardinalities = Map(
            "http://0.0.0.0:3333/ontology/0001/freetest/v2#hasBlueTestTextProp".toSmartIri -> KnoraCardinalityInfo(
              cardinality = ZeroOrOne,
            ),
          ),
        ),
        lastModificationDate = freetestLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser,
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val newFreetestLastModDate = msg.ontologyMetadata.lastModificationDate
          .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate
      }

      // Check that the correct blank nodes were stored for the cardinalities.
      val actual = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[TriplestoreService](
          _.query(
            Select(
              """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                |
                |SELECT ?cardinalityProp
                |WHERE {
                |  <http://www.knora.org/ontology/0001/freetest#BlueFreeTestClass> rdfs:subClassOf ?restriction .
                |  FILTER isBlank(?restriction)
                |  ?restriction owl:onProperty ?cardinalityProp .
                |}""".stripMargin,
            ),
          ),
        ),
      )

      assert(
        actual.getColOrThrow("cardinalityProp").sorted == Seq(
          "http://www.knora.org/ontology/0001/freetest#hasBlueTestIntProp",
        ),
      )
    }

    "create a class anything:FoafPerson as a subclass of foaf:Person" in {
      // create the class anything:FoafPerson
      val classIri: SmartIri = AnythingOntologyIri.makeEntityIri("FoafPerson")

      val classInfoContent: ClassInfoContentV2 = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(StringLiteralV2.from("FOAF person", Some("en"))),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(StringLiteralV2.from("FOAF person with reference to foaf:Person", Some("en"))),
          ),
        ),
        subClassOf = Set(
          "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri,
          "http://xmlns.com/foaf/0.1/Person".toSmartIri,
        ),
        directCardinalities = Map(ExampleSharedOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(ZeroOrOne)),
        ontologySchema = ApiV2Complex,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(classInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
            ),
          ),
        )
        // check if class was created correctly
        val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo: ReadClassInfoV2 = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent should ===(classInfoContent)

        val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
        val newAnythingLastModDate: Instant = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

    }

    "create a property anything:hasFoafName as a subproperty of foaf:name" in {
      // get the class IRI for anything:FoafPerson
      val classIri: SmartIri = AnythingOntologyIri.makeEntityIri("FoafPerson")

      // create the property anything:hasFoafName
      val metadataResponse: ReadOntologyMetadataV2 =
        UnsafeZioRun.runOrThrow(ontologyResponder(_.getOntologyMetadataForProject(anythingProjectIri)))
      assert(metadataResponse.ontologies.size == 3)
      anythingLastModDate = metadataResponse
        .toOntologySchema(ApiV2Complex)
        .ontologies
        .find(_.ontologyIri == AnythingOntologyIri.smartIri)
        .get
        .lastModificationDate
        .get

      val propertyIri: SmartIri = AnythingOntologyIri.makeEntityIri("hasFoafName")

      val propertyInfoContent: PropertyInfoContentV2 = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.ObjectProperty.toSmartIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(classIri)),
          ),
          OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri)),
          ),
          Rdfs.Label.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Label.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("has foaf name", Some("en")),
              StringLiteralV2.from("hat foaf Namen", Some("de")),
            ),
          ),
          Rdfs.Comment.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdfs.Comment.toSmartIri,
            objects = Seq(
              StringLiteralV2.from("The foaf name of something", Some("en")),
              StringLiteralV2.from("Der foaf Name eines Dinges", Some("de")),
            ),
          ),
        ),
        subPropertyOf =
          Set(OntologyConstants.KnoraApiV2Complex.HasValue.toSmartIri, "http://xmlns.com/foaf/0.1/name".toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      val msg = UnsafeZioRun.runOrThrow(
        ontologyResponder(
          _.createProperty(propertyInfoContent, anythingLastModDate, UUID.randomUUID, anythingAdminUser),
        ),
      )

      val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
      val property: ReadPropertyInfoV2     = externalOntology.properties(propertyIri)
      property.entityInfoContent should ===(propertyInfoContent)
      val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
      val newAnythingLastModDate: Instant = metadata.lastModificationDate.getOrElse(
        throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
      )
      assert(newAnythingLastModDate.isAfter(anythingLastModDate))
      anythingLastModDate = newAnythingLastModDate
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
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          propertyIri -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(0),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )

      // check if cardinality was added correctly
      val expectedDirectCardinalities: Map[SmartIri, KnoraCardinalityInfo] = Map(
        propertyIri -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(0),
        ),
        ExampleSharedOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
        ),
      )

      val expectedProperties: Set[SmartIri] = Set(
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
        ExampleSharedOntologyIri.makeEntityIri("hasName"),
        propertyIri,
      )

      {
        val msg = UnsafeZioRun.runOrThrow(
          ontologyResponder(
            _.addCardinalitiesToClass(
              AddCardinalitiesToClassRequestV2(
                classWithNewCardinalityInfoContent,
                anythingLastModDate,
                UUID.randomUUID,
                anythingAdminUser,
              ),
            ),
          ),
        )
        val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo: ReadClassInfoV2 = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent.directCardinalities should ===(expectedDirectCardinalities)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedProperties)

        val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
        val newAnythingLastModDate: Instant = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
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
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          propertyIri -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(0),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )

      appActor ! CanDeleteCardinalitiesFromClassRequestV2(
        classInfoContent = classInfoContentWithCardinalityToDeleteAllow,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser,
      )

      expectMsgPF(timeout) { case msg: CanDoResponseV2 =>
        assert(msg.canDo.value)
      }

      // remove cardinalities on the class anything:FoafPerson
      val classChangeInfoContent: ClassInfoContentV2 = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )

      appActor ! ReplaceClassCardinalitiesRequestV2(
        classInfoContent = classChangeInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser,
      )

      // check if cardinalities were removed correctly
      val expectedPropertiesAfterDeletion: Set[SmartIri] = Set(
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkTo.toSmartIri,
        OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue.toSmartIri,
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology: ReadOntologyV2 = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo: ReadClassInfoV2 = externalOntology.classes(classIri)
        readClassInfo.entityInfoContent.directCardinalities should ===(classChangeInfoContent.directCardinalities)
        readClassInfo.allResourcePropertyCardinalities.keySet should ===(expectedPropertiesAfterDeletion)

        val metadata: OntologyMetadataV2 = externalOntology.ontologyMetadata
        val newAnythingLastModDate: Instant = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the GUI order of a cardinality in a base class, and update its subclass in the ontology cache" in {
      val classIri = AnythingOntologyIri.makeEntityIri("Thing")

      val newCardinality = KnoraCardinalityInfo(cardinality = Unbounded, guiOrder = Some(100))

      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          AnythingOntologyIri.makeEntityIri("hasText") -> newCardinality,
        ),
        ontologySchema = ApiV2Complex,
      )

      appActor ! ChangeGuiOrderRequestV2(
        classInfoContent = classInfoContent,
        lastModificationDate = anythingLastModDate,
        apiRequestID = UUID.randomUUID,
        requestingUser = anythingAdminUser,
      )

      expectMsgPF(timeout) { case msg: ReadOntologyV2 =>
        val externalOntology = msg.toOntologySchema(ApiV2Complex)
        assert(externalOntology.classes.size == 1)
        val readClassInfo = externalOntology.classes(classIri)
        assert(
          readClassInfo.entityInfoContent
            .directCardinalities(AnythingOntologyIri.makeEntityIri("hasText")) == newCardinality,
        )

        val metadata = externalOntology.ontologyMetadata
        val newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
          throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
        )
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }

      val response = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[OntologyResponderV2](_.handle(
          ClassesGetRequestV2(
            classIris = Set(AnythingOntologyIri.makeEntityIri("ThingWithSeqnum")),
            allLanguages = false,
            requestingUser = anythingAdminUser,
          )
        ).asInstanceOf[Task[ReadOntologyV2]])
      )

      val externalOntology = response.toOntologySchema(ApiV2Complex)
      assert(externalOntology.classes.size == 1)
      val readClassInfo = externalOntology.classes(AnythingOntologyIri.makeEntityIri("ThingWithSeqnum"))
      assert(readClassInfo.inheritedCardinalities(AnythingOntologyIri.makeEntityIri("hasText")) == newCardinality)
    }
  }

}
