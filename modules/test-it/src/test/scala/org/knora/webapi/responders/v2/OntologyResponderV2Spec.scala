/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import scala.language.implicitConversions
import eu.timepit.refined.types.string.NonEmptyString
import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.time.Instant
import java.util.UUID.randomUUID

import dsp.constants.SalsahGui
import dsp.errors.*
import dsp.valueobjects.Iri
import dsp.valueobjects.Schema
import org.knora.webapi.*
import org.knora.webapi.E2EZSpec.failsWithMessageEqualTo
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.OntologyConstants.Owl
import org.knora.webapi.messages.OntologyConstants.Rdf
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.OntologyConstants.SchemaOrg
import org.knora.webapi.messages.OntologyConstants.Xsd
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateValueInNewResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.IntegerValueContentV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.ontology.api.AddCardinalitiesToClassRequestV2
import org.knora.webapi.slice.ontology.api.ChangeGuiOrderRequestV2
import org.knora.webapi.slice.ontology.api.ChangePropertyLabelsOrCommentsRequestV2
import org.knora.webapi.slice.ontology.api.CreateClassRequestV2
import org.knora.webapi.slice.ontology.api.ReplaceClassCardinalitiesRequestV2
import org.knora.webapi.slice.ontology.domain.model.Cardinality.*
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.util.MutableTestIri

object OntologyResponderV2Spec extends E2EZSpec { self =>

  private val ontologyResponder  = ZIO.serviceWithZIO[OntologyResponderV2]
  private val ontologyCache      = ZIO.serviceWithZIO[OntologyCache]
  private val triplestoreService = ZIO.serviceWithZIO[TriplestoreService]

  override val rdfDataObjects: List[RdfDataObject] =
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

  private val fooIri                   = new MutableTestIri
  private val barIri                   = new MutableTestIri
  private val chairIri                 = new MutableTestIri
  private val ExampleSharedOntologyIri = "http://api.knora.org/ontology/shared/example-box/v2".toSmartIri
  private val IncunabulaOntologyIri    = "http://0.0.0.0:3333/ontology/0803/incunabula/v2".toSmartIri
  private val anythingOntologyIri      = OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
  private val freeTestOntologyIri      = OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/freetest/v2".toSmartIri)

  private val fooLastModDate               = LastModRef.make
  private val barLastModDate               = LastModRef.make
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
    propertyIri = anythingOntologyIri.makeEntityIri("hasNothingness"),
    predicates = Map(
      Rdf.Type.toSmartIri -> PredicateInfoV2(
        predicateIri = Rdf.Type.toSmartIri,
        objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
      ),
      KA.SubjectType.toSmartIri -> PredicateInfoV2(
        predicateIri = KA.SubjectType.toSmartIri,
        objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Nothing"))),
      ),
      KA.ObjectType.toSmartIri -> PredicateInfoV2(
        predicateIri = KA.ObjectType.toSmartIri,
        objects = Seq(SmartIriLiteralV2(KA.BooleanValue.toSmartIri)),
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
    subPropertyOf = Set(KA.HasValue.toSmartIri),
    ontologySchema = ApiV2Complex,
  )

  private val validClassInfoContentV2: ClassInfoContentV2 =
    ClassInfoContentV2(
      classIri = anythingOntologyIri.makeEntityIri("Void"),
      predicates = Map(
        Rdf.Type.toSmartIri -> PredicateInfoV2(
          predicateIri = Rdf.Type.toSmartIri,
          objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
      subClassOf = Set(anythingOntologyIri.makeEntityIri("Nothing")),
      ontologySchema = ApiV2Complex,
    )

  private def getLastModificationDate(r: ReadOntologyMetadataV2, ontologyIri: OntologyIri): Instant =
    r.toOntologySchema(ApiV2Complex)
      .ontologies
      .find(_.ontologyIri == ontologyIri.toComplexSchema)
      .flatMap(_.lastModificationDate)
      .getOrElse(throw AssertionException(s"$ontologyIri has no last modification date"))

  private def getLastModificationDate(r: ReadOntologyV2): Instant =
    r.toOntologySchema(ApiV2Complex)
      .ontologyMetadata
      .lastModificationDate
      .getOrElse(throw AssertionException(s"${r.ontologyIri} has no last modification date"))

  override val e2eSpec = suite("The ontology responder v2")(
    test("create an empty ontology called 'foo' with a project code") {
      val createReq =
        CreateOntologyRequestV2("foo", imagesProjectIri, false, "The foo ontology", None, randomUUID, imagesUser01)

      for {
        response <- ontologyResponder(_.createOntology(createReq))
        metadata  = response.ontologies.head
        _         = self.fooIri.set(metadata.ontologyIri.toString)
        (_, _)    = self.fooLastModDate.updateFrom(response)
      } yield assertTrue(
        response.ontologies.size == 1,
        metadata.ontologyIri.toString == "http://www.knora.org/ontology/00FF/foo",
      )
    },
    test("change the label in the metadata of 'foo'") {
      val newLabel = "The modified foo ontology"

      for {
        response <- ontologyResponder(
                      _.changeOntologyMetadata(
                        fooIri.asOntologyIri,
                        Some(newLabel),
                        None,
                        self.fooLastModDate,
                        randomUUID,
                        imagesUser01,
                      ),
                    )
        metadata                            = response.ontologies.head
        (oldLastModDate, newFooLastModDate) = self.fooLastModDate.updateFrom(response)
      } yield assertTrue(
        response.ontologies.size == 1,
        metadata.ontologyIri == fooIri.get.toSmartIri,
        metadata.label.contains(newLabel),
        newFooLastModDate.isAfter(oldLastModDate),
      )
    },
    test("add a comment to the metadata of 'foo' ontology") {
      val aComment = NonEmptyString.unsafeFrom("a comment")

      for {
        response <- ontologyResponder(
                      _.changeOntologyMetadata(
                        fooIri.asOntologyIri,
                        None,
                        Some(aComment),
                        self.fooLastModDate,
                        randomUUID,
                        imagesUser01,
                      ),
                    )
        metadata                            = response.ontologies.head
        (oldLastModDate, newFooLastModDate) = self.fooLastModDate.updateFrom(response)
      } yield assertTrue(
        response.ontologies.size == 1,
        metadata.ontologyIri == fooIri.get.toSmartIri,
        metadata.comment.contains(aComment),
        newFooLastModDate.isAfter(oldLastModDate),
      )
    },
    test("change both the label and the comment of the 'foo' ontology") {
      val aLabel   = "a changed label"
      val aComment = NonEmptyString.unsafeFrom("a changed comment")

      for {
        response <- ontologyResponder(
                      _.changeOntologyMetadata(
                        fooIri.asOntologyIri,
                        Some(aLabel),
                        Some(aComment),
                        self.fooLastModDate,
                        randomUUID,
                        imagesUser01,
                      ),
                    )
        metadata                            = response.ontologies.head
        (oldLastModDate, newFooLastModDate) = self.fooLastModDate.updateFrom(response)
      } yield assertTrue(
        response.ontologies.size == 1,
        metadata.ontologyIri == fooIri.get.toSmartIri,
        metadata.label.contains(aLabel),
        metadata.comment.contains(aComment),
        newFooLastModDate.isAfter(oldLastModDate),
      )
    },
    test("change the label of 'foo' again") {
      val newLabel = "a label changed again"
      for {
        response <- ontologyResponder(
                      _.changeOntologyMetadata(
                        fooIri.asOntologyIri,
                        Some(newLabel),
                        None,
                        self.fooLastModDate,
                        randomUUID,
                        imagesUser01,
                      ),
                    )
        metadata                            = response.ontologies.head
        (oldLastModDate, newFooLastModDate) = self.fooLastModDate.updateFrom(response)
      } yield assertTrue(
        response.ontologies.size == 1,
        metadata.ontologyIri == fooIri.get.toSmartIri,
        metadata.label.contains(newLabel),
        metadata.comment.contains("a changed comment"),
        newFooLastModDate.isAfter(oldLastModDate),
      )
    },
    test("delete the comment from 'foo'") {
      for {
        response <-
          ontologyResponder(
            _.deleteOntologyComment(fooIri.asOntologyIri, self.fooLastModDate, randomUUID, imagesUser01),
          )
        metadata                            = response.ontologies.head
        (oldLastModDate, newFooLastModDate) = self.fooLastModDate.updateFrom(response)
      } yield assertTrue(
        response.ontologies.size == 1,
        metadata.ontologyIri == fooIri.get.toSmartIri,
        metadata.label.contains("a label changed again"),
        metadata.comment.isEmpty,
        newFooLastModDate.isAfter(oldLastModDate),
      )
    },
    test("not create an ontology if the given name matches NCName pattern but is not URL safe") {
      val createReq = CreateOntologyRequestV2(
        "bär",
        imagesProjectIri,
        false,
        "The bär ontology",
        Some(NonEmptyString.unsafeFrom("some comment")),
        randomUUID,
        imagesUser01,
      )
      ontologyResponder(_.createOntology(createReq)).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("create an empty ontology called 'bar' with a comment") {
      val comment = NonEmptyString.unsafeFrom("some comment")
      val createReq = CreateOntologyRequestV2(
        "bar",
        imagesProjectIri,
        false,
        "The bar ontology",
        Some(comment),
        randomUUID,
        imagesUser01,
      )

      for {
        response <- ontologyResponder(_.createOntology(createReq))
        metadata  = response.ontologies.head
        _         = self.barIri.set(metadata.ontologyIri.toString)
        (_, _)    = self.barLastModDate.updateFrom(response)
      } yield assertTrue(
        response.ontologies.size == 1,
        metadata.ontologyIri.toString == "http://www.knora.org/ontology/00FF/bar",
        metadata.comment.contains(comment),
        metadata.lastModificationDate.isDefined,
      )
    },
    test("change the existing comment in the metadata of 'bar' ontology") {
      val newComment = NonEmptyString.unsafeFrom("a new comment")

      for {
        response <- ontologyResponder(
                      _.changeOntologyMetadata(
                        OntologyIri.unsafeFrom(barIri.get.toSmartIri.toComplexSchema),
                        None,
                        Some(newComment),
                        self.barLastModDate,
                        randomUUID,
                        imagesUser01,
                      ),
                    )
        metadata                               = response.ontologies.head
        (oldBarLastModDate, newBarLastModDate) = self.barLastModDate.updateFrom(response)
      } yield assertTrue(
        response.ontologies.size == 1,
        metadata.ontologyIri == barIri.get.toSmartIri,
        metadata.comment.contains(newComment),
        newBarLastModDate.isAfter(oldBarLastModDate),
      )
    },
    test("not create 'foo' again") {
      val createReq =
        CreateOntologyRequestV2("foo", imagesProjectIri, false, "ignored", None, randomUUID, imagesUser01)
      ontologyResponder(_.createOntology(createReq)).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not delete an ontology that doesn't exist") {
      ontologyResponder(
        _.deleteOntology(
          OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/1234/nonexistent/v2".toSmartIri),
          self.fooLastModDate,
          randomUUID,
        ),
      ).exit.map(actual => assert(actual)(failsWithA[NotFoundException]))
    },
    test("delete the 'foo' ontology") {
      for {
        _ <- ontologyResponder(_.deleteOntology(fooIri.asOntologyIri, self.fooLastModDate, randomUUID))
        // Request the metadata of all ontologies to check that 'foo' isn't listed.
        cachedMetadataResponse <- ontologyResponder(_.getOntologyMetadataForAllProjects)
        // Reload the ontologies from the triplestore and check again.
        _                      <- ontologyCache(_.refreshCache())
        loadedMetadataResponse <- ontologyResponder(_.getOntologyMetadataForAllProjects)
      } yield assertTrue(
        !cachedMetadataResponse.ontologies.exists(_.ontologyIri == fooIri.get.toSmartIri),
        !loadedMetadataResponse.ontologies.exists(_.ontologyIri == fooIri.get.toSmartIri),
      )
    },
    test("not delete the 'anything' ontology, because it is used in data and in the 'something' ontology") {
      for {
        metadataResponse   <- ontologyResponder(_.getOntologyMetadataForProject(anythingProjectIri))
        anythingLastModDate = getLastModificationDate(metadataResponse, anythingOntologyIri)
        exit               <- ontologyResponder(_.deleteOntology(anythingOntologyIri, anythingLastModDate, randomUUID)).exit
      } yield assertTrue(metadataResponse.ontologies.size == 3) &&
        assert(exit)(
          fails(
            isSubtype[BadRequestException](
              hasMessage(containsString("<http://rdfh.ch/0001/a-thing>")) && // rdf:type anything:Thing
                hasMessage(
                  containsString("<http://rdfh.ch/0001/a-blue-thing>"),
                ) && // rdf:type anything:BlueThing, a subclass of anything:Thing
                hasMessage(
                  containsString("<http://www.knora.org/ontology/0001/something#Something>"),
                ) && // a subclass of anything:Thing in another ontology
                hasMessage(
                  containsString("<http://www.knora.org/ontology/0001/something#hasOtherSomething>"),
                ), // a subproperty of anything:hasOtherThing in another ontology
            ),
          ),
        )
    },
    test("not create an ontology called 'rdfs'") {
      val createReq =
        CreateOntologyRequestV2("rdfs", imagesProjectIri, false, "The rdfs ontology", None, randomUUID, imagesUser01)
      ontologyResponder(_.createOntology(createReq)).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create an ontology called '0000'") {
      val createReq =
        CreateOntologyRequestV2("0000", imagesProjectIri, false, "The 0000 ontology", None, randomUUID, imagesUser01)
      ontologyResponder(_.createOntology(createReq)).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create an ontology called '-foo'") {
      val createReq =
        CreateOntologyRequestV2("-foo", imagesProjectIri, false, "The -foo ontology", None, randomUUID, imagesUser01)
      ontologyResponder(_.createOntology(createReq)).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create an ontology called 'v3'") {
      val createReq =
        CreateOntologyRequestV2("v3", imagesProjectIri, false, "The v3 ontology", None, randomUUID, imagesUser01)
      ontologyResponder(_.createOntology(createReq)).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create an ontology called 'ontology'") {
      val createReq =
        CreateOntologyRequestV2("ontology", imagesProjectIri, false, "ignored", None, randomUUID, imagesUser01)
      ontologyResponder(_.createOntology(createReq)).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create an ontology called 'knora'") {
      val createReq =
        CreateOntologyRequestV2("knora", imagesProjectIri, false, "ignored", None, randomUUID, imagesUser01)
      ontologyResponder(_.createOntology(createReq)).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create an ontology called 'simple'") {
      val createReq =
        CreateOntologyRequestV2("simple", imagesProjectIri, false, "ignored", None, randomUUID, imagesUser01)
      ontologyResponder(_.createOntology(createReq)).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create an ontology called 'shared'") {
      val createReq =
        CreateOntologyRequestV2("shared", imagesProjectIri, false, "ignored", None, randomUUID, imagesUser01)
      ontologyResponder(_.createOntology(createReq)).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a shared ontology in the wrong project") {
      val createReq =
        CreateOntologyRequestV2("misplaced", imagesProjectIri, true, "ignored", None, randomUUID, imagesUser01)
      ontologyResponder(_.createOntology(createReq)).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a non-shared ontology in the shared ontologies project") {
      val createReq = CreateOntologyRequestV2(
        "misplaced",
        KnoraAdmin.DefaultSharedOntologiesProject,
        false,
        "ignored",
        None,
        randomUUID,
        imagesUser01,
      )
      ontologyResponder(_.createOntology(createReq)).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("create a shared ontology") {
      val createReq = CreateOntologyRequestV2(
        "chair",
        KnoraAdmin.DefaultSharedOntologiesProject,
        true,
        "a chaired ontology",
        None,
        randomUUID,
        superUser,
      )
      for {
        response <- ontologyResponder(_.createOntology(createReq))
        metadata  = response.ontologies.head
        _         = self.chairIri.set(metadata.ontologyIri.toOntologySchema(ApiV2Complex).toString)
      } yield assertTrue(
        response.ontologies.size == 1,
        metadata.ontologyIri.toString == "http://www.knora.org/ontology/shared/chair",
        metadata.lastModificationDate.isDefined,
      )
    },
    test("not allow a user to create a property if they are not a sysadmin or an admin in the ontology's project") {
      val propertyIri = anythingOntologyIri.makeEntityIri("hasName")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.TextValue.toSmartIri)),
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
        subPropertyOf = Set(KA.HasValue.toSmartIri, SchemaOrg.Name.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      for {
        metadataResponse <- ontologyResponder(_.getOntologyMetadataForProject(anythingProjectIri))
        _                 = self.anythingLastModDate = getLastModificationDate(metadataResponse, anythingOntologyIri)
        exit <-
          ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingUser1)).exit
      } yield assertTrue(metadataResponse.ontologies.size == 3) && assert(exit)(failsWithA[ForbiddenException])
    },
    test("create a property anything:hasName as a subproperty of knora-api:hasValue and schema:name") {
      for {
        metadataResponse <- ontologyResponder(_.getOntologyMetadataForProject(anythingProjectIri))
        _                 = self.anythingLastModDate = getLastModificationDate(metadataResponse, anythingOntologyIri)
        propertyIri       = anythingOntologyIri.makeEntityIri("hasName")
        propertyInfoContent = PropertyInfoContentV2(
                                propertyIri = propertyIri,
                                predicates = Map(
                                  Rdf.Type.toSmartIri -> PredicateInfoV2(
                                    predicateIri = Rdf.Type.toSmartIri,
                                    objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
                                  ),
                                  KA.SubjectType.toSmartIri -> PredicateInfoV2(
                                    predicateIri = KA.SubjectType.toSmartIri,
                                    objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
                                  ),
                                  KA.ObjectType.toSmartIri -> PredicateInfoV2(
                                    predicateIri = KA.ObjectType.toSmartIri,
                                    objects = Seq(SmartIriLiteralV2(KA.TextValue.toSmartIri)),
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
                                subPropertyOf = Set(KA.HasValue.toSmartIri, SchemaOrg.Name.toSmartIri),
                                ontologySchema = ApiV2Complex,
                              )
        propertyCreatedResponse <-
          ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser))
        propertyCreatedOntology = propertyCreatedResponse.toOntologySchema(ApiV2Complex)
        actualProperty          = propertyCreatedOntology.properties(propertyIri)
        metadata                = propertyCreatedOntology.ontologyMetadata
        oldAnythingLastModDate  = self.anythingLastModDate
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        _  = self.anythingLastModDate = newAnythingLastModDate
        _ <- ontologyCache(_.refreshCache())
        ontologyResponseAfterRefresh <- ontologyResponder(
                                          _.getPropertiesFromOntologyV2(
                                            Set(PropertyIri.unsafeFrom(propertyIri)),
                                            allLanguages = true,
                                            requestingUser = anythingAdminUser,
                                          ),
                                        )
        ontologyAfterRefresh = ontologyResponseAfterRefresh.toOntologySchema(ApiV2Complex)
        readPropertyInfo     = ontologyAfterRefresh.properties.values.head
      } yield assertTrue(
        actualProperty.entityInfoContent == propertyInfoContent,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
        ontologyResponseAfterRefresh.properties.size == 1,
        readPropertyInfo.entityInfoContent == propertyInfoContent,
      )
    },
    test(
      "create a link property in the 'anything' ontology, and automatically create the corresponding link value property",
    ) {
      for {
        metadataResponse   <- ontologyResponder(_.getOntologyMetadataForProject(anythingProjectIri))
        anythingLastModDate = getLastModificationDate(metadataResponse, anythingOntologyIri)
        propertyIri         = anythingOntologyIri.makeEntityIri("hasInterestingThing")
        propertyInfoContent = PropertyInfoContentV2(
                                propertyIri = propertyIri,
                                predicates = Map(
                                  Rdf.Type.toSmartIri -> PredicateInfoV2(
                                    predicateIri = Rdf.Type.toSmartIri,
                                    objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
                                  ),
                                  KA.SubjectType.toSmartIri -> PredicateInfoV2(
                                    predicateIri = KA.SubjectType.toSmartIri,
                                    objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
                                  ),
                                  KA.ObjectType.toSmartIri -> PredicateInfoV2(
                                    predicateIri = KA.ObjectType.toSmartIri,
                                    objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
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
                                subPropertyOf = Set(KA.HasLinkTo.toSmartIri),
                                ontologySchema = ApiV2Complex,
                              )
        createPropertyResponse <-
          ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser))
        ontologyFromCreate     = createPropertyResponse.toOntologySchema(ApiV2Complex)
        propertyFromCreate     = ontologyFromCreate.properties(propertyIri)
        metadataFromCreate     = ontologyFromCreate.ontologyMetadata
        oldAnythingLastModDate = self.anythingLastModDate
        newAnythingLastModDate =
          metadataFromCreate.lastModificationDate.getOrElse(
            throw AssertionException(s"${metadataFromCreate.ontologyIri} has no last modification date"),
          )
        _ = self.anythingLastModDate = newAnythingLastModDate

        // Check that the link value property was created.
        linkValuePropIri = propertyIri.fromLinkPropToLinkValueProp
        getPropertiesResponse <- ontologyResponder(
                                   _.getPropertiesFromOntologyV2(
                                     propertyIris = Set(PropertyIri.unsafeFrom(linkValuePropIri)),
                                     allLanguages = true,
                                     requestingUser = anythingAdminUser,
                                   ),
                                 )
        ontologyFromGetProperties         = getPropertiesResponse.toOntologySchema(ApiV2Complex)
        readPropertyInfoFromGetProperties = ontologyFromGetProperties.properties.values.head

        // Reload the ontology cache and see if we get the same result.
        _ <- ontologyCache(_.refreshCache())
        getPropertiesResponseAfterCacheRefresh <- ontologyResponder(
                                                    _.getPropertiesFromOntologyV2(
                                                      propertyIris = Set(PropertyIri.unsafeFrom(propertyIri)),
                                                      allLanguages = true,
                                                      requestingUser = anythingAdminUser,
                                                    ),
                                                  )
        ontologyAfterCacheRefresh = getPropertiesResponseAfterCacheRefresh.toOntologySchema(ApiV2Complex)
        propertyAfterCacheRefresh = ontologyAfterCacheRefresh.properties.values.head

        getLinkValuePropertiesResponse <-
          ontologyResponder(
            _.getPropertiesFromOntologyV2(
              propertyIris = Set(PropertyIri.unsafeFrom(linkValuePropIri)),
              allLanguages = true,
              requestingUser = anythingAdminUser,
            ),
          )
        ontologyFromLinkValue                 = getLinkValuePropertiesResponse.toOntologySchema(ApiV2Complex)
        linkValueProperty: ReadPropertyInfoV2 = ontologyFromLinkValue.properties.values.head
      } yield assertTrue(
        metadataResponse.ontologies.size == 3,
        propertyFromCreate.isLinkProp,
        !propertyFromCreate.isLinkValueProp,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
        ontologyFromCreate.properties(propertyIri).entityInfoContent == propertyInfoContent,
        ontologyFromGetProperties.properties.size == 1,
        readPropertyInfoFromGetProperties.entityInfoContent.propertyIri == linkValuePropIri,
        !readPropertyInfoFromGetProperties.isLinkProp,
        readPropertyInfoFromGetProperties.isLinkValueProp,
        ontologyAfterCacheRefresh.properties.size == 1,
        propertyAfterCacheRefresh.isLinkProp,
        !propertyAfterCacheRefresh.isLinkValueProp,
        propertyAfterCacheRefresh.entityInfoContent == propertyInfoContent,
        ontologyFromLinkValue.properties.size == 1,
        linkValueProperty.entityInfoContent.propertyIri == linkValuePropIri,
        !linkValueProperty.isLinkProp,
        linkValueProperty.isLinkValueProp,
      )
    },
    test(
      "create a subproperty of an existing custom link property and add it to a resource class, check if the correct link and link value properties were added to the class",
    ) {
      for {
        metadataResponse      <- ontologyResponder(_.getOntologyMetadataForProject(anythingProjectIri))
        newFreetestLastModDate = getLastModificationDate(metadataResponse, freeTestOntologyIri)
        _                      = self.freetestLastModDate = newFreetestLastModDate
        // Create class freetest:ComicBook which is a subclass of freetest:Book
        comicBookClassIri = freeTestOntologyIri.makeEntityIri("ComicBook")
        comicBookClassInfoContent = ClassInfoContentV2(
                                      classIri = comicBookClassIri,
                                      predicates = Map(
                                        Rdf.Type.toSmartIri -> PredicateInfoV2(
                                          predicateIri = Rdf.Type.toSmartIri,
                                          objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
                                      subClassOf = Set(freeTestOntologyIri.makeEntityIri("Book")),
                                      ontologySchema = ApiV2Complex,
                                    )
        createReq =
          CreateClassRequestV2(comicBookClassInfoContent, self.freetestLastModDate, randomUUID, anythingAdminUser)
        createClassResponse   <- ontologyResponder(_.createClass(createReq))
        newFreetestLastModDate = getLastModificationDate(createClassResponse)
        oldFreetestLastModDate = self.freetestLastModDate
        _                      = self.freetestLastModDate = newFreetestLastModDate
        // Create class freetest:ComicAuthor which is a subclass of freetest:Author
        comicAuthorClassIri = freeTestOntologyIri.makeEntityIri("ComicAuthor")
        comicAuthorClassInfoContent = ClassInfoContentV2(
                                        classIri = comicAuthorClassIri,
                                        predicates = Map(
                                          Rdf.Type.toSmartIri -> PredicateInfoV2(
                                            predicateIri = Rdf.Type.toSmartIri,
                                            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
                                        subClassOf = Set(freeTestOntologyIri.makeEntityIri("Author")),
                                        ontologySchema = ApiV2Complex,
                                      )
        createSubClassReq =
          CreateClassRequestV2(comicAuthorClassInfoContent, self.freetestLastModDate, randomUUID, anythingAdminUser)
        createSubclassResponse                    <- ontologyResponder(_.createClass(createSubClassReq))
        newFreetestLastModDateFromSubclassCreation = getLastModificationDate(createSubclassResponse)
        oldLastModDateFromSubclassCreation         = self.freetestLastModDate
        _                                          = self.freetestLastModDate = newFreetestLastModDateFromSubclassCreation
        // Create property freetest:hasComicBookAuthor which is a subproperty of freetest:hasAuthor and links freetest:ComicBook and freetest:ComicAuthor
        comicAuthorPropertyIri = freeTestOntologyIri.makeEntityIri("hasComicAuthor")
        comicAuthorPropertyInfoContent = PropertyInfoContentV2(
                                           propertyIri = comicAuthorPropertyIri,
                                           predicates = Map(
                                             Rdf.Type.toSmartIri -> PredicateInfoV2(
                                               predicateIri = Rdf.Type.toSmartIri,
                                               objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
                                             ),
                                             KA.SubjectType.toSmartIri -> PredicateInfoV2(
                                               predicateIri = KA.SubjectType.toSmartIri,
                                               objects =
                                                 Seq(SmartIriLiteralV2(freeTestOntologyIri.makeEntityIri("ComicBook"))),
                                             ),
                                             KA.ObjectType.toSmartIri -> PredicateInfoV2(
                                               predicateIri = KA.ObjectType.toSmartIri,
                                               objects = Seq(
                                                 SmartIriLiteralV2(freeTestOntologyIri.makeEntityIri("ComicAuthor")),
                                               ),
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
                                           subPropertyOf = Set(freeTestOntologyIri.makeEntityIri("hasAuthor")),
                                           ontologySchema = ApiV2Complex,
                                         )
        createPropertyResponse <-
          ontologyResponder(
            _.createProperty(comicAuthorPropertyInfoContent, freetestLastModDate, randomUUID, anythingAdminUser),
          )
        createPropertyOntology          = createPropertyResponse.toOntologySchema(ApiV2Complex)
        comicAuthorProperty             = createPropertyOntology.properties(comicAuthorPropertyIri)
        createPropertyMetadata          = createPropertyOntology.ontologyMetadata
        lastModDateBeforePropertyCreate = freetestLastModDate
        lastModDateAfterPropertyCreate =
          createPropertyMetadata.lastModificationDate.getOrElse(
            throw AssertionException(s"${createPropertyMetadata.ontologyIri} has no last modification date"),
          )
        _ = self.freetestLastModDate = lastModDateAfterPropertyCreate

        // Add new subproperty freetest:hasComicBookAuthor to class freetest:ComicBook
        msg <-
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
                apiRequestID = randomUUID,
                requestingUser = anythingAdminUser,
              ),
            ),
          )
        comicBookClass      = msg.classes("http://www.knora.org/ontology/0001/freetest#ComicBook".toSmartIri)
        linkProperties      = comicBookClass.linkProperties
        linkValueProperties = comicBookClass.linkValueProperties

        lastModDateBeforeSubProperty = self.freetestLastModDate
        lastModDateAfterSubProperty =
          msg.ontologyMetadata.lastModificationDate
            .getOrElse(throw AssertionException(s"${msg.ontologyMetadata.ontologyIri} has no last modification date"))
        _ = self.freetestLastModDate = newFreetestLastModDate

        // Verify the cardinality of the new property and its link value where created in the subclass
        queryResult <-
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
                                              |}""".stripMargin)))

      } yield assertTrue(
        metadataResponse.ontologies.size == 3,
        newFreetestLastModDate.isAfter(oldFreetestLastModDate),
        newFreetestLastModDateFromSubclassCreation.isAfter(oldLastModDateFromSubclassCreation),
        comicAuthorProperty.isLinkProp,
        !comicAuthorProperty.isLinkValueProp,
        createPropertyOntology.properties(comicAuthorPropertyIri).entityInfoContent == comicAuthorPropertyInfoContent,
        lastModDateAfterPropertyCreate.isAfter(freetestLastModDate),
        linkProperties.contains(
          "http://www.knora.org/ontology/0001/freetest#hasComicAuthor".toSmartIri,
        ),
        linkValueProperties.contains(
          "http://www.knora.org/ontology/0001/freetest#hasComicAuthorValue".toSmartIri,
        ),
        !linkProperties.contains(
          "http://www.knora.org/ontology/0001/freetest#hasAuthor".toSmartIri,
        ),
        !linkValueProperties.contains(
          "http://www.knora.org/ontology/0001/freetest#hasAuthorValue".toSmartIri,
        ),
        lastModDateAfterSubProperty.isAfter(lastModDateBeforeSubProperty),
        queryResult.results.bindings.exists(row =>
          row.rowMap.get("property").contains("http://www.knora.org/ontology/0001/freetest#hasComicAuthor")
            && row.rowMap.get("maxCardinality").contains("1"),
        ),
        queryResult.results.bindings.exists(row =>
          row.rowMap.get("property").contains("http://www.knora.org/ontology/0001/freetest#hasComicAuthorValue")
            && row.rowMap.get("maxCardinality").contains("1"),
        ),
      )
    },
    test("not create a property without an rdf:type") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.TextValue.toSmartIri)),
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
        subPropertyOf = Set(KA.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a property with the wrong rdf:type") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.TextValue.toSmartIri)),
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
        subPropertyOf = Set(KA.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a property that already exists") {
      val propertyIri = anythingOntologyIri.makeEntityIri("hasInteger")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.IntValue.toSmartIri)),
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
        subPropertyOf = Set(KA.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a property with a nonexistent Knora superproperty") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.IntValue.toSmartIri)),
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
        subPropertyOf = Set(anythingOntologyIri.makeEntityIri("nonexistentProperty")),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a property that is not a subproperty of knora-api:hasValue or knora-api:hasLinkTo") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.TextValue.toSmartIri)),
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
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a property that is a subproperty of both knora-api:hasValue and knora-api:hasLinkTo") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.TextValue.toSmartIri)),
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
          anythingOntologyIri.makeEntityIri("hasText"),
          anythingOntologyIri.makeEntityIri("hasOtherThing"),
        ),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a property with a knora-base:subjectType that refers to a nonexistent class") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("NonexistentClass"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.TextValue.toSmartIri)),
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
        subPropertyOf = Set(KA.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a property with a knora-base:objectType that refers to a nonexistent class") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(
              SmartIriLiteralV2(
                (KA.KnoraApiV2PrefixExpansion + "NonexistentClass").toSmartIri,
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
        subPropertyOf = Set(KA.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a subproperty of anything:hasInteger with a knora-base:subjectType of knora-api:Representation") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.Representation.toSmartIri)),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.TextValue.toSmartIri)),
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
        subPropertyOf = Set(anythingOntologyIri.makeEntityIri("hasInteger")),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a file value property") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.FileValue.toSmartIri)),
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
        subPropertyOf = Set(KA.HasFileValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not directly create a link value property") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.LinkValue.toSmartIri)),
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
        subPropertyOf = Set(KA.HasLinkToValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not directly create a property with a knora-api:objectType of knora-api:LinkValue") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.LinkValue.toSmartIri)),
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
        subPropertyOf = Set(KA.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a property with a knora-api:objectType of xsd:string") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Xsd.String.toSmartIri)),
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
        subPropertyOf = Set(KA.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a property whose object type is knora-api:StillImageFileValue") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.StillImageFileValue.toSmartIri)),
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
        subPropertyOf = Set(KA.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test(
      "not create a property whose object type is a Knora resource class if the property isn't a subproperty of knora-api:hasLinkValue",
    ) {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
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
        subPropertyOf = Set(KA.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a link property whose object type is knora-api:TextValue") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.TextValue.toSmartIri)),
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
        subPropertyOf = Set(KA.HasLinkTo.toSmartIri),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a subproperty of anything:hasText with a knora-api:objectType of knora-api:IntegerValue") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.IntValue.toSmartIri)),
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
        subPropertyOf = Set(anythingOntologyIri.makeEntityIri("hasText")),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a subproperty of anything:hasBlueThing with a knora-api:objectType of anything:Thing") {
      val propertyIri = anythingOntologyIri.makeEntityIri("wrongProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Thing"))),
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
        subPropertyOf = Set(anythingOntologyIri.makeEntityIri("hasBlueThing")),
        ontologySchema = ApiV2Complex,
      )
      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test(
      "not allow a user to change the labels of a property if they are not a sysadmin or an admin in the ontology's project",
    ) {
      val propertyIri = anythingOntologyIri.makeProperty("hasName")
      val newObjects = Seq(
        StringLiteralV2.from("has name", Some("en")),
        StringLiteralV2.from("a nom", Some("fr")),
        StringLiteralV2.from("hat Namen", Some("de")),
      )
      val changeReq = ChangePropertyLabelsOrCommentsRequestV2(
        propertyIri,
        LabelOrComment.Label,
        newObjects,
        anythingLastModDate,
        randomUUID,
        anythingUser1,
      )
      ontologyResponder(
        _.changePropertyLabelsOrComments(
          changeReq,
        ),
      ).exit.map(actual => assert(actual)(failsWithA[ForbiddenException]))
    },
    test("change the labels of a property") {
      val propertyIri = anythingOntologyIri.makeProperty("hasName")
      val newObjects = Seq(
        StringLiteralV2.from("has name", Some("en")),
        StringLiteralV2.from("hat Namen", Some("de")),
        StringLiteralV2.from("a nom", Some("fr")),
      )

      val changeReq =
        ChangePropertyLabelsOrCommentsRequestV2(
          propertyIri,
          LabelOrComment.Label,
          newObjects,
          anythingLastModDate,
          randomUUID,
          anythingAdminUser,
        )
      for {
        msg                   <- ontologyResponder(_.changePropertyLabelsOrComments(changeReq))
        externalOntology       = msg.toOntologySchema(ApiV2Complex)
        metadata               = externalOntology.ontologyMetadata
        readPropertyInfo       = externalOntology.properties(propertyIri.smartIri)
        oldAnythingLastModDate = self.anythingLastModDate
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        _ = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        readPropertyInfo.entityInfoContent.predicates(Rdfs.Label.toSmartIri).objects == newObjects,
        externalOntology.properties.size == 1,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("change the labels of a property, submitting the same labels again") {
      val propertyIri = anythingOntologyIri.makeProperty("hasName")
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
        randomUUID,
        anythingAdminUser,
      )

      for {
        msg             <- ontologyResponder(_.changePropertyLabelsOrComments(changeReq))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        readPropertyInfo = externalOntology.properties(propertyIri.smartIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        readPropertyInfo.entityInfoContent.predicates(Rdfs.Label.toSmartIri).objects == newObjects,
        externalOntology.properties.size == 1,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test(
      "not allow a user to change the comments of a property if they are not a sysadmin or an admin in the ontology's project",
    ) {
      val propertyIri = anythingOntologyIri.makeProperty("hasName")
      val newObjects = Seq(
        StringLiteralV2.from("The name of a Thing", Some("en")),
        StringLiteralV2.from(
          "Le nom d\\'une chose",
          Some("fr"),
        ), // This is SPARQL-escaped as it would be if taken from a JSON-LD request.
        StringLiteralV2.from("Der Name eines Dinges", Some("de")),
      )

      val changeReq = ChangePropertyLabelsOrCommentsRequestV2(
        propertyIri,
        LabelOrComment.Comment,
        newObjects,
        anythingLastModDate,
        randomUUID,
        anythingUser1,
      )
      ontologyResponder(_.changePropertyLabelsOrComments(changeReq)).exit.map(actual =>
        assert(actual)(failsWithA[ForbiddenException]),
      )
    },
    test("change the comments of a property") {
      val propertyIri = anythingOntologyIri.makeProperty("hasName")
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

      for {
        msg <- ontologyResponder(
                 _.changePropertyLabelsOrComments(
                   ChangePropertyLabelsOrCommentsRequestV2(
                     propertyIri,
                     LabelOrComment.Comment,
                     newObjects,
                     anythingLastModDate,
                     randomUUID,
                     anythingAdminUser,
                   ),
                 ),
               )
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        readPropertyInfo = externalOntology.properties(propertyIri.smartIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.properties.size == 1,
        externalOntology.properties.size == 1,
        readPropertyInfo.entityInfoContent.predicates(Rdfs.Comment.toSmartIri).objects == newObjectsUnescaped,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("change the comments of a property, submitting the same comments again") {
      val propertyIri = anythingOntologyIri.makeProperty("hasName")
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

      val changeReq = ChangePropertyLabelsOrCommentsRequestV2(
        propertyIri,
        LabelOrComment.Comment,
        newObjects,
        anythingLastModDate,
        randomUUID,
        anythingAdminUser,
      )
      for {
        msg             <- ontologyResponder(_.changePropertyLabelsOrComments(changeReq))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        readPropertyInfo = externalOntology.properties(propertyIri.smartIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.properties.size == 1,
        readPropertyInfo.entityInfoContent.predicates(Rdfs.Comment.toSmartIri).objects == newObjectsUnescaped,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("delete the comment of a property that has a comment") {
      val propertyIri = freeTestOntologyIri.makeProperty("hasPropertyWithComment")
      for {
        msg <-
          ontologyResponder(_.deletePropertyComment(propertyIri, freetestLastModDate, randomUUID, anythingAdminUser))
        externalOntology       = msg.toOntologySchema(ApiV2Complex)
        readPropertyInfo       = externalOntology.properties(propertyIri.toComplexSchema)
        metadata               = externalOntology.ontologyMetadata
        oldFreeTestLastModDate = self.freetestLastModDate
        newFreeTestLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        _ = self.freetestLastModDate = newFreeTestLastModDate
      } yield assertTrue(
        externalOntology.properties.size == 1,
        !readPropertyInfo.entityInfoContent.predicates.contains(Rdfs.Comment.toSmartIri),
        newFreeTestLastModDate.isAfter(freetestLastModDate),
      )
    },
    test("not update the ontology when trying to delete a comment of a property that has no comment") {
      val propertyIri = freeTestOntologyIri.makeProperty("hasPropertyWithoutComment")
      for {
        msg <-
          ontologyResponder(_.deletePropertyComment(propertyIri, freetestLastModDate, randomUUID, anythingAdminUser))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        readPropertyInfo = externalOntology.properties(propertyIri.toComplexSchema)
        metadata         = externalOntology.ontologyMetadata
        newFreeTestLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        _ = self.freetestLastModDate = newFreeTestLastModDate
      } yield assertTrue(
        externalOntology.properties.size == 1,
        !readPropertyInfo.entityInfoContent.predicates.contains(Rdfs.Comment.toSmartIri),
        // the ontology was not changed and thus should not have a new last modification date
        newFreeTestLastModDate == freetestLastModDate,
      )
    },
    test("delete the comment of a class that has a comment") {
      val classIri = freeTestOntologyIri.makeClass("BookWithComment")
      for {
        msg             <- ontologyResponder(_.deleteClassComment(classIri, freetestLastModDate, randomUUID, anythingAdminUser))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri.toComplexSchema)
        metadata         = externalOntology.ontologyMetadata
        newFreeTestLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldFreeTestLastModDate = self.freetestLastModDate
        _                      = freetestLastModDate = newFreeTestLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        !readClassInfo.entityInfoContent.predicates.contains(Rdfs.Comment.toSmartIri),
        newFreeTestLastModDate.isAfter(oldFreeTestLastModDate),
      )
    },
    test("not update the ontology when trying to delete a comment of a class that has no comment") {
      val classIri = freeTestOntologyIri.makeClass("BookWithoutComment")
      for {
        msg                   <- ontologyResponder(_.deleteClassComment(classIri, freetestLastModDate, randomUUID, anythingAdminUser))
        externalOntology       = msg.toOntologySchema(ApiV2Complex)
        readClassInfo          = externalOntology.classes(classIri.toComplexSchema)
        metadata               = externalOntology.ontologyMetadata
        oldFreeTestLastModDate = self.freetestLastModDate
        newFreeTestLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        _ = self.freetestLastModDate = newFreeTestLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        !readClassInfo.entityInfoContent.predicates.contains(Rdfs.Comment.toSmartIri),
        // the ontology was not changed and thus should not have a new last modification date
        newFreeTestLastModDate == oldFreeTestLastModDate,
      )
    },
    test("delete the comment of a link property and remove the comment of the link value property as well") {
      val linkPropertyIri = freeTestOntologyIri.makeProperty("hasLinkPropertyWithComment")
      val linkValueIri    = linkPropertyIri.fromLinkPropToLinkValueProp

      for {
        // delete the comment of the link property
        msg <- ontologyResponder(
                 _.deletePropertyComment(linkPropertyIri, freetestLastModDate, randomUUID, anythingAdminUser),
               )
        externalOntology         = msg.toOntologySchema(ApiV2Complex)
        metadata                 = externalOntology.ontologyMetadata
        propertyReadPropertyInfo = externalOntology.properties(linkPropertyIri.toComplexSchema)
        newFreeTestLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldFreeTestLastModDate = self.freetestLastModDate
        _                      = self.freetestLastModDate = newFreeTestLastModDate
        // check that the comment of the link value property was deleted as well
        getPropertiesResponse <- ontologyResponder(
                                   _.getPropertiesFromOntologyV2(
                                     propertyIris = Set(linkValueIri),
                                     allLanguages = true,
                                     requestingUser = anythingAdminUser,
                                   ),
                                 )
        ontologyFromGetProperties = getPropertiesResponse.toOntologySchema(ApiV2Complex)
        linkValueReadPropertyInfo = ontologyFromGetProperties.properties(linkValueIri.toComplexSchema)
      } yield assertTrue(
        externalOntology.properties.size == 1,
        newFreeTestLastModDate.isAfter(oldFreeTestLastModDate),
        !propertyReadPropertyInfo.entityInfoContent.predicates.contains(Rdfs.Comment.toSmartIri),
        !linkValueReadPropertyInfo.entityInfoContent.predicates.contains(Rdfs.Comment.toSmartIri),
      )
    },
    test("not allow a user to create a class if they are not a sysadmin or an admin in the ontology's project") {
      val classIri = anythingOntologyIri.makeEntityIri("WildThing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
          anythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(ZeroOrOne),
          anythingOntologyIri.makeEntityIri("hasInteger") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(20),
          ),
        ),
        subClassOf = Set(anythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
      )

      ontologyResponder(
        _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingUser1)),
      ).exit.map(actual => assert(actual)(failsWithA[ForbiddenException]))
    },
    test("not allow a user to create a class with cardinalities both on property P and on a subproperty of P") {
      val classIri = anythingOntologyIri.makeEntityIri("InvalidThing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
          anythingOntologyIri.makeEntityIri("hasOtherThing") -> KnoraCardinalityInfo(ExactlyOne),
          anythingOntologyIri.makeEntityIri("hasBlueThing") -> KnoraCardinalityInfo(
            cardinality = ExactlyOne,
          ),
        ),
        subClassOf = Set(anythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
      )

      ontologyResponder(
        _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
      ).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not allow the user to submit a direct cardinality on anything:hasInterestingThingValue") {
      val classIri = anythingOntologyIri.makeEntityIri("WildThing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
          anythingOntologyIri.makeEntityIri("hasInterestingThingValue") -> KnoraCardinalityInfo(ZeroOrOne),
        ),
        subClassOf = Set(anythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
      )

      ontologyResponder(
        _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
      ).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a class if rdfs:label is missing") {
      val invalid =
        validClassInfoContentV2.copy(predicates = validClassInfoContentV2.predicates - Rdfs.Label.toSmartIri)
      val req = CreateClassRequestV2(invalid, anythingLastModDate, randomUUID, anythingAdminUser)
      ontologyResponder(_.createClass(req)).exit.map(actual =>
        assert(actual)(failsWithMessageEqualTo[BadRequestException](s"Missing ${Rdfs.Label}")),
      )
    },
    test("not create a class if rdfs:label does not have a language code") {
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
      val req = CreateClassRequestV2(invalid, anythingLastModDate, randomUUID, anythingAdminUser)
      ontologyResponder(_.createClass(req)).exit.map(actual =>
        assert(actual)(
          failsWithMessageEqualTo[BadRequestException](
            s"All values of ${Rdfs.Label} must be string literals with a language code",
          ),
        ),
      )
    },
    test("not create a class if rdfs:comment does not have a language code") {
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
      val req = CreateClassRequestV2(invalid, anythingLastModDate, randomUUID, anythingAdminUser)
      ontologyResponder(_.createClass(req)).exit.map(actual =>
        assert(actual)(
          failsWithMessageEqualTo[BadRequestException](
            s"All values of ${Rdfs.Comment} must be string literals with a language code",
          ),
        ),
      )
    },
    test(
      "create a class anything:CardinalityThing with cardinalities on anything:hasInterestingThing and anything:hasInterestingThingValue",
    ) {
      val classIri = anythingOntologyIri.makeEntityIri("CardinalityThing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
          anythingOntologyIri.makeEntityIri("hasInterestingThing") -> KnoraCardinalityInfo(ZeroOrOne),
        ),
        subClassOf = Set(anythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
      )
      for {
        msg <-
          ontologyResponder(
            _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
          )
        externalOntology       = msg.toOntologySchema(ApiV2Complex)
        readClassInfo          = externalOntology.classes(classIri)
        metadata               = externalOntology.ontologyMetadata
        oldAnythingLastModDate = self.anythingLastModDate
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        _ = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        Set(
          anythingOntologyIri.makeEntityIri("hasInterestingThing"),
          anythingOntologyIri.makeEntityIri("hasInterestingThingValue"),
        ).subsetOf(readClassInfo.allResourcePropertyCardinalities.keySet),
        newAnythingLastModDate.isAfter(anythingLastModDate),
      )
    },
    test(
      "create classes anything:wholeThing and anything:partThing with a isPartOf relation and its corresponding value property",
    ) {
      // Create class partThing
      val partThingClassIri = anythingOntologyIri.makeEntityIri("partThing")
      val partThingClassInfoContent = ClassInfoContentV2(
        classIri = partThingClassIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
        subClassOf = Set(KA.Resource.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      for {
        msg <- ontologyResponder(
                 _.createClass(
                   CreateClassRequestV2(partThingClassInfoContent, anythingLastModDate, randomUUID, anythingAdminUser),
                 ),
               )
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        _ = self.anythingLastModDate = newAnythingLastModDate
        // Create class wholeThing
        wholeThingClassIri = anythingOntologyIri.makeEntityIri("wholeThing")
        wholeThingClassInfoContent = ClassInfoContentV2(
                                       classIri = wholeThingClassIri,
                                       predicates = Map(
                                         Rdf.Type.toSmartIri -> PredicateInfoV2(
                                           predicateIri = Rdf.Type.toSmartIri,
                                           objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
                                         ),
                                         Rdfs.Label.toSmartIri -> PredicateInfoV2(
                                           predicateIri = Rdfs.Label.toSmartIri,
                                           objects = Seq(StringLiteralV2.from("Thing as a whole", Some("en"))),
                                         ),
                                         Rdfs.Comment.toSmartIri -> PredicateInfoV2(
                                           predicateIri = Rdfs.Comment.toSmartIri,
                                           objects =
                                             Seq(StringLiteralV2.from("A thing that has multiple parts", Some("en"))),
                                         ),
                                       ),
                                       subClassOf = Set(KA.Resource.toSmartIri),
                                       ontologySchema = ApiV2Complex,
                                     )

        createClassWholeThingResponse <-
          ontologyResponder(
            _.createClass(
              CreateClassRequestV2(wholeThingClassInfoContent, anythingLastModDate, randomUUID, anythingAdminUser),
            ),
          )
        ontologyFromCreateClassWholeThing         = createClassWholeThingResponse.toOntologySchema(ApiV2Complex)
        metadataOntologyFromCreateClassWholeThing = ontologyFromCreateClassWholeThing.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        _ = self.anythingLastModDate = newAnythingLastModDate

        // Create property partOf with subject partThing and object wholeThing
        partOfPropertyIri = anythingOntologyIri.makeEntityIri("partOf")
        partOfPropertyInfoContent = PropertyInfoContentV2(
                                      propertyIri = partOfPropertyIri,
                                      predicates = Map(
                                        Rdf.Type.toSmartIri -> PredicateInfoV2(
                                          predicateIri = Rdf.Type.toSmartIri,
                                          objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
                                        ),
                                        KA.SubjectType.toSmartIri -> PredicateInfoV2(
                                          predicateIri = KA.SubjectType.toSmartIri,
                                          objects =
                                            Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("partThing"))),
                                        ),
                                        KA.ObjectType.toSmartIri -> PredicateInfoV2(
                                          predicateIri = KA.ObjectType.toSmartIri,
                                          objects =
                                            Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("wholeThing"))),
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
                                          objects = Seq(
                                            SmartIriLiteralV2(
                                              "http://api.knora.org/ontology/salsah-gui/v2#Searchbox".toSmartIri,
                                            ),
                                          ),
                                        ),
                                      ),
                                      subPropertyOf = Set(KnoraBase.IsPartOf.toSmartIri),
                                      ontologySchema = ApiV2Complex,
                                    )

        createPropertyPartOfResponse <-
          ontologyResponder(
            _.createProperty(partOfPropertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser),
          )
        ontologyFromCreatePropertyPartOf = createPropertyPartOfResponse.toOntologySchema(ApiV2Complex)
        propertyPartOf                   = ontologyFromCreatePropertyPartOf.properties(partOfPropertyIri)
        metadataFromPartOf               = ontologyFromCreatePropertyPartOf.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        createPartOfLastModDate = self.anythingLastModDate
        _                       = self.anythingLastModDate = newAnythingLastModDate

        // Check that the corresponding partOfValue was created
        partOfValuePropertyIri = anythingOntologyIri.makeEntityIri("partOfValue")
        propertyReadResponse <- ontologyResponder(
                                  _.getPropertiesFromOntologyV2(
                                    propertyIris = Set(PropertyIri.unsafeFrom(partOfValuePropertyIri)),
                                    allLanguages = true,
                                    requestingUser = anythingAdminUser,
                                  ),
                                )
        ontologyFromPropertyRead = msg.toOntologySchema(ApiV2Complex)
        propertyFromPropertyRead = ontologyFromPropertyRead.properties(partOfValuePropertyIri)
        metadataFromPropertyRead = ontologyFromPropertyRead.ontologyMetadata
        newAnythingLastModDate = metadataFromPropertyRead.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        _ = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        ontologyFromCreateClassWholeThing.properties.size == 1,
        // check that partOf is a subproperty of knora-api:isPartOf
        propertyPartOf.entityInfoContent.subPropertyOf.contains(KA.IsPartOf.toSmartIri),
        newAnythingLastModDate.isAfter(createPartOfLastModDate),
        ontologyFromPropertyRead.properties.size == 1,
        // check that partOfValue is a subproperty of knora-api:isPartOfValue
        propertyFromPropertyRead.entityInfoContent.subPropertyOf.contains(KA.IsPartOfValue.toSmartIri),
      )
    },
    test("change the metadata of the 'anything' ontology") {
      val newLabel = "The modified anything ontology"

      for {
        response <- ontologyResponder(
                      _.changeOntologyMetadata(
                        anythingOntologyIri,
                        Some(newLabel),
                        None,
                        anythingLastModDate,
                        randomUUID,
                        anythingAdminUser,
                      ),
                    )
        metadata = response.ontologies.head
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        response.ontologies.size == 1,
        metadata.ontologyIri.toOntologySchema(ApiV2Complex) == anythingOntologyIri.smartIri,
        metadata.label.contains(newLabel),
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("delete the class anything:CardinalityThing") {
      val classIri = anythingOntologyIri.makeClass("CardinalityThing")

      for {
        response <- ontologyResponder(
                      _.deleteClass(
                        classIri = classIri,
                        lastModificationDate = anythingLastModDate,
                        apiRequestID = randomUUID,
                        requestingUser = anythingAdminUser,
                      ),
                    )
        metadata = response.ontologies.head
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(response.ontologies.size == 1, newAnythingLastModDate.isAfter(oldAnythingLastModDate))
    },
    test(
      "create a class anything:WildThing that is a subclass of anything:Thing, with a direct cardinality for anything:hasName, overriding the cardinality for anything:hasInteger",
    ) {
      val classIri = anythingOntologyIri.makeEntityIri("WildThing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
          anythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(ZeroOrOne),
          anythingOntologyIri.makeEntityIri("hasInteger") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(20),
          ),
        ),
        subClassOf = Set(anythingOntologyIri.makeEntityIri("Thing")),
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

      for {
        msg <-
          ontologyResponder(
            _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
          )
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.allBaseClasses == expectedAllBaseClasses,
        readClassInfo.entityInfoContent == classInfoContent,
        !readClassInfo.inheritedCardinalities.keySet.contains(anythingHasInteger.toSmartIri),
        readClassInfo.allResourcePropertyCardinalities.keySet == expectedProperties,
        newAnythingLastModDate.isAfter(anythingLastModDate),
      )
    },
    test("not allow inherited property to be deleted on subclass") {
      val classIri = anythingOntologyIri.makeEntityIri("SubThing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
          anythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(ZeroOrOne),
        ),
        subClassOf = Set(anythingOntologyIri.makeEntityIri("Thing")),
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

      for {
        msg <-
          ontologyResponder(
            _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
          )
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
        classInfoContentWithCardinalityToDeleteDontAllow =
          ClassInfoContentV2(
            classIri = classIri,
            predicates = Map(
              Rdf.Type.toSmartIri -> PredicateInfoV2(
                predicateIri = Rdf.Type.toSmartIri,
                objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
              anythingOntologyIri.makeEntityIri("hasInteger") -> KnoraCardinalityInfo(ZeroOrOne),
            ),
            subClassOf = Set(anythingOntologyIri.makeEntityIri("Thing")),
            ontologySchema = ApiV2Complex,
          )
        canDeleteResponse <- ontologyResponder(
                               _.canDeleteCardinalitiesFromClass(
                                 CanDeleteCardinalitiesFromClassRequestV2(
                                   classInfoContent = classInfoContentWithCardinalityToDeleteDontAllow,
                                   lastModificationDate = anythingLastModDate,
                                   apiRequestID = randomUUID,
                                   requestingUser = anythingAdminUser,
                                 ),
                               ),
                             )
      } yield assertTrue(
        readClassInfo.allBaseClasses == expectedAllBaseClasses,
        readClassInfo.entityInfoContent == classInfoContent,
        readClassInfo.inheritedCardinalities.keySet.contains(anythingHasInteger.toSmartIri),
        readClassInfo.allResourcePropertyCardinalities.keySet == expectedProperties,
        newAnythingLastModDate.isAfter(anythingLastModDate),
        !canDeleteResponse.canDo.value,
      )
    },
    test("allow direct property to be deleted on subclass") {
      val classIri = anythingOntologyIri.makeEntityIri("OtherSubThing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
          anythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(ZeroOrOne),
        ),
        subClassOf = Set(anythingOntologyIri.makeEntityIri("Thing")),
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

      for {
        msg <-
          ontologyResponder(
            _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
          )
        externalOntology       = msg.toOntologySchema(ApiV2Complex)
        readClassInfo          = externalOntology.classes(classIri)
        metadata               = externalOntology.ontologyMetadata
        oldAnythingLastModDate = self.anythingLastModDate
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        classInfoContentWithCardinalityToDeleteAllow =
          ClassInfoContentV2(
            classIri = classIri,
            predicates = Map(
              Rdf.Type.toSmartIri -> PredicateInfoV2(
                predicateIri = Rdf.Type.toSmartIri,
                objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
              anythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(ZeroOrOne),
            ),
            subClassOf = Set(anythingOntologyIri.makeEntityIri("Thing")),
            ontologySchema = ApiV2Complex,
          )
        canDeleteResponse <-
          ontologyResponder(
            _.canDeleteCardinalitiesFromClass(
              CanDeleteCardinalitiesFromClassRequestV2(
                classInfoContent = classInfoContentWithCardinalityToDeleteAllow,
                lastModificationDate = anythingLastModDate,
                apiRequestID = randomUUID,
                requestingUser = anythingAdminUser,
              ),
            ),
          )
      } yield assertTrue(
        readClassInfo.allBaseClasses == expectedAllBaseClasses,
        readClassInfo.entityInfoContent == classInfoContent,
        !readClassInfo.inheritedCardinalities.keySet.contains(anythingHasName.toSmartIri),
        readClassInfo.allResourcePropertyCardinalities.keySet == expectedProperties,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
        canDeleteResponse.canDo.value,
      )
    },
    test("create a class anything:Nothing with no properties") {
      val classIri = anythingOntologyIri.makeEntityIri("Nothing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
        subClassOf = Set(KA.Resource.toSmartIri),
        ontologySchema = ApiV2Complex,
      )
      val expectedProperties = Set(
        anythingHasStandoffLinkTo,
        anythingHasStandoffLinkToValue,
      ).map(_.toSmartIri)

      for {
        msg <-
          ontologyResponder(
            _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
          )
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent == classInfoContent,
        readClassInfo.allResourcePropertyCardinalities.keySet == expectedProperties,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test(
      "not allow a user to change the labels of a class if they are not a sysadmin or an admin in the ontology's project",
    ) {
      val classIri = anythingOntologyIri.makeClass("Nothing")
      val newObjects = Seq(
        StringLiteralV2.from("nothing", Some("en")),
        StringLiteralV2.from("rien", Some("fr")),
      )
      val changeReq = ChangeClassLabelsOrCommentsRequestV2(
        classIri = classIri,
        predicateToUpdate = LabelOrComment.Label,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = randomUUID,
        requestingUser = anythingUser1,
      )
      ontologyResponder(_.changeClassLabelsOrComments(changeReq)).exit.map(actual =>
        assert(actual)(failsWithA[ForbiddenException]),
      )
    },
    test("change the labels of a class") {
      val classIri = anythingOntologyIri.makeClass("Nothing")
      val newObjects = Seq(
        StringLiteralV2.from("nothing", Some("en")),
        StringLiteralV2.from("rien", Some("fr")),
      )

      val changeReq = ChangeClassLabelsOrCommentsRequestV2(
        classIri = classIri,
        predicateToUpdate = LabelOrComment.Label,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = randomUUID,
        requestingUser = anythingAdminUser,
      )
      for {
        response        <- ontologyResponder(_.changeClassLabelsOrComments(changeReq))
        externalOntology = response.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri.smartIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent.predicates(Rdfs.Label.toSmartIri).objects == newObjects,
        newAnythingLastModDate.isAfter(anythingLastModDate),
      )
    },
    test("change the labels of a class, submitting the same labels again") {
      val classIri = anythingOntologyIri.makeClass("Nothing")
      val newObjects = Seq(
        StringLiteralV2.from("nothing", Some("en")),
        StringLiteralV2.from("rien", Some("fr")),
      )
      val changeReq = ChangeClassLabelsOrCommentsRequestV2(
        classIri = classIri,
        predicateToUpdate = LabelOrComment.Label,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = randomUUID,
        requestingUser = anythingAdminUser,
      )

      for {
        response        <- ontologyResponder(_.changeClassLabelsOrComments(changeReq))
        externalOntology = response.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri.smartIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent.predicates(Rdfs.Label.toSmartIri).objects == newObjects,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test(
      "not allow a user to change the comments of a class if they are not a sysadmin or an admin in the ontology's project",
    ) {
      val classIri = anythingOntologyIri.makeClass("Nothing")
      val newObjects = Seq(
        StringLiteralV2.from("Represents nothing", Some("en")),
        StringLiteralV2.from("ne représente rien", Some("fr")),
      )
      val changeReq = ChangeClassLabelsOrCommentsRequestV2(
        classIri = classIri,
        predicateToUpdate = LabelOrComment.Comment,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = randomUUID,
        requestingUser = anythingUser1,
      )
      ontologyResponder(_.changeClassLabelsOrComments(changeReq)).exit.map(actual =>
        assert(actual)(failsWithA[ForbiddenException]),
      )
    },
    test("change the comments of a class") {
      val classIri = anythingOntologyIri.makeClass("Nothing")
      val newObjects = Seq(
        StringLiteralV2.from("Represents nothing", Some("en")),
        StringLiteralV2.from("ne représente rien", Some("fr")),
      )
      // Make an unescaped copy of the new comments, because this is how we will receive them in the API response.
      val newObjectsUnescaped = newObjects.map { case StringLiteralV2(text, lang) =>
        StringLiteralV2.from(Iri.fromSparqlEncodedString(text), lang)
      }
      val changeReq = ChangeClassLabelsOrCommentsRequestV2(
        classIri = classIri,
        predicateToUpdate = LabelOrComment.Comment,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = randomUUID,
        requestingUser = anythingAdminUser,
      )

      for {
        response        <- ontologyResponder(_.changeClassLabelsOrComments(changeReq))
        externalOntology = response.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri.smartIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldLastModDate = self.anythingLastModDate
        _              = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent.predicates(Rdfs.Comment.toSmartIri).objects == newObjectsUnescaped,
        newAnythingLastModDate.isAfter(anythingLastModDate),
      )
    },
    test("change the comments of a class, submitting the same comments again") {
      val classIri = anythingOntologyIri.makeClass("Nothing")
      val newObjects = Seq(
        StringLiteralV2.from("Represents nothing", Some("en")),
        StringLiteralV2.from("ne représente rien", Some("fr")),
      )
      // Make an unescaped copy of the new comments, because this is how we will receive them in the API response.
      val newObjectsUnescaped = newObjects.map { case StringLiteralV2(text, lang) =>
        StringLiteralV2.from(Iri.fromSparqlEncodedString(text), lang)
      }
      val changeReq = ChangeClassLabelsOrCommentsRequestV2(
        classIri = classIri,
        predicateToUpdate = LabelOrComment.Comment,
        newObjects = newObjects,
        lastModificationDate = anythingLastModDate,
        apiRequestID = randomUUID,
        requestingUser = anythingAdminUser,
      )

      for {
        response        <- ontologyResponder(_.changeClassLabelsOrComments(changeReq))
        externalOntology = response.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri.smartIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldLastModDate = self.anythingLastModDate
        _              = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent.predicates(Rdfs.Comment.toSmartIri).objects == newObjectsUnescaped,
        newAnythingLastModDate.isAfter(oldLastModDate),
      )
    },
    test("not create a class with the wrong rdf:type") {
      val classIri = anythingOntologyIri.makeEntityIri("WrongClass")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
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
        subClassOf = Set(KA.Resource.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      ontologyResponder(
        _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
      ).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a class that already exists") {
      val classIri = anythingOntologyIri.makeEntityIri("Thing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
        subClassOf = Set(KA.Resource.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      ontologyResponder(
        _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
      ).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a class with a nonexistent base class") {
      val classIri = anythingOntologyIri.makeEntityIri("WrongClass")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
        subClassOf = Set(anythingOntologyIri.makeEntityIri("NonexistentClass")),
        ontologySchema = ApiV2Complex,
      )

      ontologyResponder(
        _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
      ).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a class that is not a subclass of knora-api:Resource") {
      val classIri = anythingOntologyIri.makeEntityIri("WrongClass")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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

      ontologyResponder(
        _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
      ).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a class with a cardinality for a Knora property that doesn't exist") {
      val classIri = anythingOntologyIri.makeEntityIri("WrongClass")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
          Map(anythingOntologyIri.makeEntityIri("nonexistentProperty") -> KnoraCardinalityInfo(ZeroOrOne)),
        subClassOf = Set(KA.Resource.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      ontologyResponder(
        _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
      ).exit.map(actual => assert(actual)(failsWithA[NotFoundException]))
    },
    test("not create a class that has a cardinality for anything:hasInteger but is not a subclass of anything:Thing") {
      val classIri = anythingOntologyIri.makeEntityIri("WrongClass")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
        directCardinalities = Map(anythingOntologyIri.makeEntityIri("hasInteger") -> KnoraCardinalityInfo(ZeroOrOne)),
        subClassOf = Set(KA.Resource.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      ontologyResponder(
        _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
      ).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("create a subclass of anything:Thing that has cardinality 1 for anything:hasBoolean") {
      val classIri = anythingOntologyIri.makeEntityIri("RestrictiveThing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
        directCardinalities = Map(anythingOntologyIri.makeEntityIri("hasBoolean") -> KnoraCardinalityInfo(ExactlyOne)),
        subClassOf = Set(anythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
      )

      val createReq = CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)
      for {
        msg             <- ontologyResponder(_.createClass(createReq))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent == classInfoContent,
        readClassInfo.allCardinalities(anythingOntologyIri.makeEntityIri("hasBoolean")).cardinality == ExactlyOne,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("not create a subclass of anything:Thing that has cardinality 0-n for anything:hasBoolean") {
      val classIri = anythingOntologyIri.makeEntityIri("WrongClass")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
        directCardinalities = Map(anythingOntologyIri.makeEntityIri("hasBoolean") -> KnoraCardinalityInfo(Unbounded)),
        subClassOf = Set(anythingOntologyIri.makeEntityIri("Thing")),
        ontologySchema = ApiV2Complex,
      )

      ontologyResponder(
        _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
      ).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("reject a request to delete a link value property directly") {
      val hasInterestingThingValue = anythingOntologyIri.makeProperty("hasInterestingThingValue")
      ontologyResponder(
        _.deleteProperty(
          propertyIri = hasInterestingThingValue,
          lastModificationDate = anythingLastModDate,
          apiRequestID = randomUUID,
          requestingUser = anythingAdminUser,
        ),
      ).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("delete a link property and automatically delete the corresponding link value property") {
      val linkPropIri = anythingOntologyIri.makeProperty("hasInterestingThing")

      for {
        msg <-
          ontologyResponder(
            _.deleteProperty(
              propertyIri = linkPropIri,
              lastModificationDate = anythingLastModDate,
              apiRequestID = randomUUID,
              requestingUser = anythingAdminUser,
            ),
          )
        metadata = msg.ontologies.head
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
        // Reload the ontology cache and see if we get the same result.
        _ <- ontologyCache(_.refreshCache())

        // Check that both properties were deleted.
        exit            <- ontologyResponder(_.getPropertiesFromOntologyV2(Set(linkPropIri), true, anythingAdminUser)).exit
        linkValuePropIri = linkPropIri.fromLinkPropToLinkValueProp
        exit2           <- ontologyResponder(_.getPropertiesFromOntologyV2(Set(linkValuePropIri), true, anythingAdminUser)).exit
      } yield assertTrue(msg.ontologies.size == 1, newAnythingLastModDate.isAfter(oldAnythingLastModDate)) &&
        assert(exit)(failsWithA[NotFoundException]) &&
        assert(exit2)(failsWithA[NotFoundException])
    },
    test("not create a property if rdfs:label is missing") {
      val invalid = validPropertyInfo.copy(predicates = validPropertyInfo.predicates - Rdfs.Label.toSmartIri)
      ontologyResponder(_.createProperty(invalid, anythingLastModDate, randomUUID, anythingAdminUser)).exit.map(
        actual => assert(actual)(failsWithMessageEqualTo[BadRequestException](s"Missing ${Rdfs.Label}")),
      )
    },
    test("not create a property if rdfs:label is missing a language") {
      val invalid =
        validPropertyInfo.copy(predicates =
          validPropertyInfo.predicates.updated(
            Rdfs.Label.toSmartIri,
            PredicateInfoV2(Rdfs.Label.toSmartIri, List(StringLiteralV2.unsafeFrom("foo", None))),
          ),
        )
      ontologyResponder(_.createProperty(invalid, anythingLastModDate, randomUUID, anythingAdminUser)).exit.map(
        actual =>
          assert(actual)(
            failsWithMessageEqualTo[BadRequestException](
              s"All values of ${Rdfs.Label} must be string literals with a language code",
            ),
          ),
      )
    },
    test("not create a property if rdfs:comment is missing a language") {
      val invalid =
        validPropertyInfo.copy(predicates =
          validPropertyInfo.predicates.updated(
            Rdfs.Comment.toSmartIri,
            PredicateInfoV2(Rdfs.Comment.toSmartIri, List(StringLiteralV2.unsafeFrom("foo", None))),
          ),
        )
      ontologyResponder(_.createProperty(invalid, anythingLastModDate, randomUUID, anythingAdminUser)).exit.map(
        actual =>
          assert(actual)(
            failsWithMessageEqualTo[BadRequestException](
              s"All values of ${Rdfs.Label} must be string literals with a language code",
            ),
          ),
      )
    },
    test("create a property anything:hasNothingness with knora-api:subjectType anything:Nothing") {
      val propertyIri = anythingOntologyIri.makeEntityIri("hasNothingness")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Nothing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.BooleanValue.toSmartIri)),
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
        subPropertyOf = Set(KA.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      for {
        msg <-
          ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        property         = externalOntology.properties(propertyIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.properties.size == 1,
        property.entityInfoContent == propertyInfoContent,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("change the salsah-gui:guiElement and salsah-gui:guiAttribute of anything:hasNothingness") {
      val propertyIri = anythingOntologyIri.makeProperty("hasNothingness")
      val guiElement =
        Schema.GuiElement
          .make("http://www.knora.org/ontology/salsah-gui#SimpleText")
          .fold(e => throw e.head, v => Some(v))
      val guiAttributes = Set("size=80").map(Schema.GuiAttribute.unsafeFrom)
      val guiObject     = Schema.GuiObject.unsafeFrom(guiAttributes, guiElement)

      val changeReq = ChangePropertyGuiElementRequest(
        propertyIri = propertyIri,
        newGuiObject = guiObject,
        lastModificationDate = anythingLastModDate,
        apiRequestID = randomUUID,
        requestingUser = anythingAdminUser,
      )
      for {
        response        <- ontologyResponder(_.changePropertyGuiElement(changeReq))
        externalOntology = response.toOntologySchema(ApiV2Complex)
        property         = externalOntology.properties(propertyIri.smartIri)
        // Check that the salsah-gui:guiElement from the message is as expected
        guiElementPropComplex = property.entityInfoContent.predicates(
                                  SalsahGui.External.GuiElementProp.toSmartIri,
                                )
        guiElementPropComplexExpected = PredicateInfoV2(
                                          predicateIri = SalsahGui.External.GuiElementProp.toSmartIri,
                                          objects = Seq(
                                            SmartIriLiteralV2(
                                              "http://api.knora.org/ontology/salsah-gui/v2#SimpleText".toSmartIri,
                                            ),
                                          ),
                                        )
        guiAttributeComplex = property.entityInfoContent.predicates(SalsahGui.External.GuiAttribute.toSmartIri)
        guiAttributeComplexExpected = PredicateInfoV2(
                                        predicateIri = SalsahGui.External.GuiAttribute.toSmartIri,
                                        objects = Seq(StringLiteralV2.from("size=80", None)),
                                      )
        metadata = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        response.properties.head._2.entityInfoContent.predicates.get(SalsahGui.GuiElementProp.toSmartIri) match {
          case Some(predicateInfo) =>
            val guiElementTypeFromMessage = predicateInfo.objects.head.asInstanceOf[SmartIriLiteralV2]
            val guiElementTypeInternal    = guiElementTypeFromMessage.toOntologySchema(InternalSchema)
            guiElementTypeFromMessage == guiElementTypeInternal
          case None => true
        },
        externalOntology.properties.size == 1,
        guiElementPropComplex == guiElementPropComplexExpected,
        guiAttributeComplex == guiAttributeComplexExpected,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("delete the salsah-gui:guiElement and salsah-gui:guiAttribute of anything:hasNothingness") {
      val propertyIri                             = anythingOntologyIri.makeProperty("hasNothingness")
      val guiElement                              = None
      val guiAttributes: Set[Schema.GuiAttribute] = Set.empty
      val guiObject                               = Schema.GuiObject.unsafeFrom(guiAttributes, guiElement)
      val changeReq = ChangePropertyGuiElementRequest(
        propertyIri = propertyIri,
        newGuiObject = guiObject,
        lastModificationDate = anythingLastModDate,
        apiRequestID = randomUUID,
        requestingUser = anythingAdminUser,
      )

      for {
        response        <- ontologyResponder(_.changePropertyGuiElement(changeReq))
        externalOntology = response.toOntologySchema(ApiV2Complex)
        property         = externalOntology.properties(propertyIri.smartIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.properties.size == 1,
        !property.entityInfoContent.predicates.contains(SalsahGui.External.GuiElementProp.toSmartIri),
        !property.entityInfoContent.predicates.contains(SalsahGui.External.GuiAttribute.toSmartIri),
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("not create a property called anything:Thing, because that IRI is already used for a class") {
      val propertyIri = anythingOntologyIri.makeEntityIri("Thing")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Nothing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.BooleanValue.toSmartIri)),
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
        subPropertyOf = Set(KA.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a class called anything:hasNothingness, because that IRI is already used for a property") {
      val classIri = anythingOntologyIri.makeEntityIri("hasNothingness")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
        subClassOf = Set(KA.Resource.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      ontologyResponder(
        _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
      ).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("create a class anything:Void as a subclass of anything:Nothing") {
      val classIri = anythingOntologyIri.makeEntityIri("Void")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
        subClassOf = Set(anythingOntologyIri.makeEntityIri("Nothing")),
        ontologySchema = ApiV2Complex,
      )

      for {
        msg <-
          ontologyResponder(
            _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
          )
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent == classInfoContent,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("add a cardinality=1 to the class anything:Nothing which has a subclass") {
      val classIri = anythingOntologyIri.makeEntityIri("Nothing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          anythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(ExactlyOne),
        ),
        ontologySchema = ApiV2Complex,
      )

      for {
        msg <-
          ontologyResponder(
            _.addCardinalitiesToClass(
              AddCardinalitiesToClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser),
            ),
          )
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate

        deleteCardinalitiesResponse <- ontologyResponder(
                                         _.deleteCardinalitiesFromClass(
                                           DeleteCardinalitiesFromClassRequestV2(
                                             classInfoContent = classInfoContent,
                                             lastModificationDate = anythingLastModDate,
                                             apiRequestID = randomUUID,
                                             requestingUser = anythingAdminUser,
                                           ),
                                         ),
                                       )
        ontologyFromDeleteCardinalities = deleteCardinalitiesResponse.toOntologySchema(ApiV2Complex)
        metadataFromDeleteCardinalities = ontologyFromDeleteCardinalities.ontologyMetadata
        anythingLastModDateAfterDelete =
          metadata.lastModificationDate.getOrElse(
            throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
          )
        anythingLastModDateBeforeDelete = self.anythingLastModDate
        _                               = self.anythingLastModDate = anythingLastModDateAfterDelete
      } yield assertTrue(
        externalOntology.classes.size == 1,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
        anythingLastModDateAfterDelete.isAfter(anythingLastModDateBeforeDelete),
      )
    },
    test("not allow a user to delete a class if they are not a sysadmin or an admin in the ontology's project") {
      val classIri = anythingOntologyIri.makeClass("Void")
      ontologyResponder(
        _.deleteClass(
          classIri = classIri,
          lastModificationDate = anythingLastModDate,
          apiRequestID = randomUUID,
          requestingUser = anythingUser1,
        ),
      ).exit.map(actual => assert(actual)(failsWithA[ForbiddenException]))
    },
    test("delete the class anything:Void") {
      val classIri = anythingOntologyIri.makeClass("Void")

      for {
        response <- ontologyResponder(_.deleteClass(classIri, anythingLastModDate, randomUUID, anythingAdminUser))
        metadata  = response.ontologies.head
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(response.ontologies.size == 1, newAnythingLastModDate.isAfter(oldAnythingLastModDate))
    },
    test(
      "not allow a user to add a cardinality to a class if they are not a sysadmin or an admin in the user's project",
    ) {
      val classIri = anythingOntologyIri.makeEntityIri("Nothing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          anythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(0),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )

      ontologyResponder(
        _.addCardinalitiesToClass(
          AddCardinalitiesToClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingUser1),
        ),
      ).exit.map(actual => assert(actual)(failsWithA[ForbiddenException]))
    },
    test(
      "create a link property, anything:hasOtherNothing, and add a cardinality for it to the class anything:Nothing",
    ) {
      val classIri    = anythingOntologyIri.makeEntityIri("Nothing")
      val propertyIri = anythingOntologyIri.makeEntityIri("hasOtherNothing")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(classIri)),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
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
        subPropertyOf = Set(KA.HasLinkTo.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      for {
        msg <-
          ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
        classInfoContent = ClassInfoContentV2(
                             classIri = classIri,
                             predicates = Map(
                               Rdf.Type.toSmartIri -> PredicateInfoV2(
                                 predicateIri = Rdf.Type.toSmartIri,
                                 objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
                               ),
                             ),
                             directCardinalities = Map(
                               propertyIri -> KnoraCardinalityInfo(cardinality = ZeroOrOne, guiOrder = Some(0)),
                             ),
                             ontologySchema = ApiV2Complex,
                           )

        expectedDirectCardinalities = Map(
                                        propertyIri -> KnoraCardinalityInfo(
                                          cardinality = ZeroOrOne,
                                          guiOrder = Some(0),
                                        ),
                                        propertyIri.fromLinkPropToLinkValueProp -> KnoraCardinalityInfo(
                                          cardinality = ZeroOrOne,
                                          guiOrder = Some(0),
                                        ),
                                      )
        expectedProperties = Set(
                               KA.HasStandoffLinkTo.toSmartIri,
                               KA.HasStandoffLinkToValue.toSmartIri,
                               propertyIri,
                               propertyIri.fromLinkPropToLinkValueProp,
                             )
        expectedAllBaseClasses: Seq[SmartIri] = Seq(
                                                  (anythingOntology + "Nothing").toSmartIri,
                                                  "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri,
                                                )
        addCardinalitiesResponse <- ontologyResponder(
                                      _.addCardinalitiesToClass(
                                        AddCardinalitiesToClassRequestV2(
                                          classInfoContent,
                                          anythingLastModDate,
                                          randomUUID,
                                          anythingAdminUser,
                                        ),
                                      ),
                                    )
        ontologyFromAddCardinalities = addCardinalitiesResponse.toOntologySchema(ApiV2Complex)
        classInfoFromAddCardinalties = ontologyFromAddCardinalities.classes(classIri)
        metadataFromAddCardinalties  = externalOntology.ontologyMetadata
        newAnythingLastModDateAfterAddCardinalities =
          metadata.lastModificationDate.getOrElse(
            throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
          )
        lastModDateBeforeAddCardinalities = self.anythingLastModDate
        _                                 = self.anythingLastModDate = newAnythingLastModDateAfterAddCardinalities
      } yield assertTrue(
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
        ontologyFromAddCardinalities.classes.size == 1,
        classInfoFromAddCardinalties.allBaseClasses == expectedAllBaseClasses,
        classInfoFromAddCardinalties.entityInfoContent.directCardinalities == expectedDirectCardinalities,
        classInfoFromAddCardinalties.allResourcePropertyCardinalities.keySet == expectedProperties,
        newAnythingLastModDateAfterAddCardinalities.isAfter(lastModDateBeforeAddCardinalities),
      )
    },
    test("not add an 0-n cardinality for a boolean property") {
      val classIri = anythingOntologyIri.makeEntityIri("Nothing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          anythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
            cardinality = Unbounded,
            guiOrder = Some(0),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )

      ontologyResponder(
        _.addCardinalitiesToClass(
          AddCardinalitiesToClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser),
        ),
      ).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("add a cardinality for the property anything:hasNothingness to the class anything:Nothing") {
      val classIri = anythingOntologyIri.makeEntityIri("Nothing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          anythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(0),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )
      val expectedDirectCardinalities = Map(
        anythingOntologyIri.makeEntityIri("hasOtherNothing") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(0),
        ),
        anythingOntologyIri.makeEntityIri("hasOtherNothingValue") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(0),
        ),
        anythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(0),
        ),
      )
      val expectedProperties = Set(
        KA.HasStandoffLinkTo.toSmartIri,
        KA.HasStandoffLinkToValue.toSmartIri,
        anythingOntologyIri.makeEntityIri("hasOtherNothing"),
        anythingOntologyIri.makeEntityIri("hasOtherNothingValue"),
        anythingOntologyIri.makeEntityIri("hasNothingness"),
      )

      for {
        msg <- ontologyResponder(
                 _.addCardinalitiesToClass(
                   AddCardinalitiesToClassRequestV2(
                     classInfoContent,
                     anythingLastModDate,
                     randomUUID,
                     anythingAdminUser,
                   ),
                 ),
               )
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent.directCardinalities == expectedDirectCardinalities,
        readClassInfo.allResourcePropertyCardinalities.keySet == expectedProperties,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test(
      "not add a minCardinality>0 for property anything:hasName to class anything:BlueThing, because the class is used in data",
    ) {
      val classIri = anythingOntologyIri.makeEntityIri("BlueThing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          anythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(AtLeastOne),
        ),
        ontologySchema = ApiV2Complex,
      )

      val addRequest =
        AddCardinalitiesToClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)
      ontologyResponder(_.addCardinalitiesToClass(addRequest)).exit.map(actual =>
        assert(actual)(failsWithA[BadRequestException]),
      )
    },
    test(
      "add a maxCardinality=1 for property anything:hasName to class anything:BlueThing even though the class is used in data",
    ) {
      val classIri = anythingOntologyIri.makeEntityIri("BlueThing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          anythingOntologyIri.makeEntityIri("hasName") -> KnoraCardinalityInfo(ZeroOrOne),
        ),
        ontologySchema = ApiV2Complex,
      )

      val addRequest =
        AddCardinalitiesToClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)
      for {
        msg             <- ontologyResponder(_.addCardinalitiesToClass(addRequest))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(externalOntology.classes.size == 1, newAnythingLastModDate.isAfter(oldAnythingLastModDate))
    },
    test("create a property anything:hasEmptiness with knora-api:subjectType anything:Nothing") {
      val propertyIri = anythingOntologyIri.makeEntityIri("hasEmptiness")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(anythingOntologyIri.makeEntityIri("Nothing"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.BooleanValue.toSmartIri)),
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
        subPropertyOf = Set(KA.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      for {
        msg <-
          ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        property         = externalOntology.properties(propertyIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.properties.size == 1,
        property.entityInfoContent == propertyInfoContent,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("add a cardinality for the property anything:hasEmptiness to the class anything:Nothing") {
      val classIri = anythingOntologyIri.makeEntityIri("Nothing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          anythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(1),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )
      val expectedDirectCardinalities = Map(
        anythingOntologyIri.makeEntityIri("hasOtherNothing") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(0),
        ),
        anythingOntologyIri.makeEntityIri("hasOtherNothingValue") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(0),
        ),
        anythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(0),
        ),
        anythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(1),
        ),
      )
      val expectedProperties = Set(
        KA.HasStandoffLinkTo.toSmartIri,
        KA.HasStandoffLinkToValue.toSmartIri,
        anythingOntologyIri.makeEntityIri("hasOtherNothing"),
        anythingOntologyIri.makeEntityIri("hasOtherNothingValue"),
        anythingOntologyIri.makeEntityIri("hasNothingness"),
        anythingOntologyIri.makeEntityIri("hasEmptiness"),
      )
      val addReq = AddCardinalitiesToClassRequestV2(
        classInfoContent,
        anythingLastModDate,
        randomUUID,
        anythingAdminUser,
      )

      for {
        msg             <- ontologyResponder(_.addCardinalitiesToClass(addReq))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent.directCardinalities == expectedDirectCardinalities,
        readClassInfo.allResourcePropertyCardinalities.keySet == expectedProperties,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test(
      "not allow a user to change the cardinalities of a class if they are not a sysadmin or an admin in the user's project",
    ) {
      val classIri = anythingOntologyIri.makeEntityIri("Nothing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          anythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(0),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )

      val replaceReq =
        ReplaceClassCardinalitiesRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingUser1)
      ontologyResponder(_.replaceClassCardinalities(replaceReq)).exit.map(actual =>
        assert(actual)(failsWithA[ForbiddenException]),
      )
    },
    test("change the GUI order of the cardinalities of the class anything:Nothing") {
      val classIri = anythingOntologyIri.makeEntityIri("Nothing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          anythingOntologyIri.makeEntityIri("hasOtherNothing") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(1),
          ),
          anythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(2),
          ),
          anythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(3),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )
      val expectedCardinalities = Map(
        anythingOntologyIri.makeEntityIri("hasOtherNothing") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(1),
        ),
        anythingOntologyIri.makeEntityIri("hasOtherNothingValue") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(1),
        ),
        anythingOntologyIri.makeEntityIri("hasNothingness") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(2),
        ),
        anythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
          cardinality = ZeroOrOne,
          guiOrder = Some(3),
        ),
      )

      val changeReq = ChangeGuiOrderRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)
      for {
        response        <- ontologyResponder(_.changeGuiOrder(changeReq))
        externalOntology = response.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent.directCardinalities == expectedCardinalities,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test(
      "change the cardinalities of the class anything:Nothing, removing anything:hasOtherNothing and anything:hasNothingness and leaving anything:hasEmptiness",
    ) {
      val classIri = anythingOntologyIri.makeEntityIri("Nothing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          anythingOntologyIri.makeEntityIri("hasEmptiness") -> KnoraCardinalityInfo(
            cardinality = ZeroOrOne,
            guiOrder = Some(0),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )
      val expectedProperties = Set(
        KA.HasStandoffLinkTo.toSmartIri,
        KA.HasStandoffLinkToValue.toSmartIri,
        anythingOntologyIri.makeEntityIri("hasEmptiness"),
      )
      val expectedAllBaseClasses: Seq[SmartIri] = Seq(
        (anythingOntology + "Nothing").toSmartIri,
        "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri,
      )

      val replaceReq =
        ReplaceClassCardinalitiesRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)

      for {
        response        <- ontologyResponder(_.replaceClassCardinalities(replaceReq))
        externalOntology = response.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.allBaseClasses == expectedAllBaseClasses,
        readClassInfo.entityInfoContent.directCardinalities == classInfoContent.directCardinalities,
        readClassInfo.allResourcePropertyCardinalities.keySet == expectedProperties,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("not delete the class anything:Nothing, because the property anything:hasEmptiness refers to it") {
      val classIri = anythingOntologyIri.makeClass("Nothing")
      ontologyResponder(_.deleteClass(classIri, anythingLastModDate, randomUUID, anythingAdminUser)).exit.map(actual =>
        assert(actual)(failsWithA[BadRequestException]),
      )
    },
    test("delete the property anything:hasNothingness") {
      val hasNothingness = anythingOntologyIri.makeProperty("hasNothingness")
      for {
        msg     <- ontologyResponder(_.deleteProperty(hasNothingness, anythingLastModDate, randomUUID, anythingAdminUser))
        metadata = msg.ontologies.head
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(msg.ontologies.size == 1, newAnythingLastModDate.isAfter(oldAnythingLastModDate))
    },
    test("not delete the property anything:hasEmptiness, because the class anything:Nothing refers to it") {
      val hasNothingness = anythingOntologyIri.makeProperty("hasEmptiness")
      ontologyResponder(_.deleteProperty(hasNothingness, anythingLastModDate, randomUUID, anythingAdminUser)).exit.map(
        actual => assert(actual)(failsWithA[BadRequestException]),
      )
    },
    test(
      "not allow a user to remove all cardinalities from a class if they are not a sysadmin or an admin in the user's project",
    ) {
      val classIri = anythingOntologyIri.makeEntityIri("Nothing")
      val classInfoContent = ClassInfoContentV2(
        classIri,
        Map(Rdf.Type.toSmartIri -> PredicateInfoV2(Rdf.Type.toSmartIri, Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)))),
        ontologySchema = ApiV2Complex,
      )
      val replaceReq =
        ReplaceClassCardinalitiesRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingUser1)
      ontologyResponder(_.replaceClassCardinalities(replaceReq)).exit.map(actual =>
        assert(actual)(failsWithA[ForbiddenException]),
      )
    },
    test("remove all cardinalities from the class anything:Nothing") {
      val classIri = anythingOntologyIri.makeEntityIri("Nothing")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
          ),
        ),
        ontologySchema = ApiV2Complex,
      )
      val expectedProperties = Set(
        KA.HasStandoffLinkTo.toSmartIri,
        KA.HasStandoffLinkToValue.toSmartIri,
      )

      for {
        response <- ontologyResponder(
                      _.replaceClassCardinalities(
                        ReplaceClassCardinalitiesRequestV2(
                          classInfoContent = classInfoContent,
                          lastModificationDate = anythingLastModDate,
                          apiRequestID = randomUUID,
                          requestingUser = anythingAdminUser,
                        ),
                      ),
                    )
        externalOntology = response.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent.directCardinalities == classInfoContent.directCardinalities,
        readClassInfo.allResourcePropertyCardinalities.keySet == expectedProperties,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("not delete the property anything:hasEmptiness with the wrong knora-api:lastModificationDate") {
      val hasEmptiness = anythingOntologyIri.makeProperty("hasEmptiness")
      ontologyResponder(
        _.deleteProperty(hasEmptiness, anythingLastModDate.minusSeconds(60), randomUUID, anythingAdminUser),
      ).exit.map(actual => assert(actual)(failsWithA[EditConflictException]))
    },
    test("not allow a user to delete a property if they are not a sysadmin or an admin in the ontology's project") {
      val hasEmptiness = anythingOntologyIri.makeProperty("hasEmptiness")
      ontologyResponder(_.deleteProperty(hasEmptiness, anythingLastModDate, randomUUID, anythingUser1)).exit.map(
        actual => assert(actual)(failsWithA[ForbiddenException]),
      )
    },
    test("delete the properties anything:hasOtherNothing and anything:hasEmptiness") {
      val hasOtherNothing = anythingOntologyIri.makeProperty("hasOtherNothing")
      val hasEmptiness    = anythingOntologyIri.makeProperty("hasEmptiness")

      for {
        msg     <- ontologyResponder(_.deleteProperty(hasOtherNothing, anythingLastModDate, randomUUID, anythingAdminUser))
        metadata = msg.ontologies.head
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
        // prop2
        deleteHasEmptinessResponse <-
          ontologyResponder(_.deleteProperty(hasEmptiness, anythingLastModDate, randomUUID, anythingAdminUser))
        metadataAfterDeleteHasEmptiness = deleteHasEmptinessResponse.ontologies.head
        lastModDateAfterDeleteHasEmptiness =
          metadataAfterDeleteHasEmptiness.lastModificationDate.getOrElse(
            throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
          )
        lastModDateBeforeDeleteHasEmptiness = self.anythingLastModDate
        _                                   = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        msg.ontologies.size == 1,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
        deleteHasEmptinessResponse.ontologies.size == 1,
        lastModDateAfterDeleteHasEmptiness.isAfter(lastModDateBeforeDeleteHasEmptiness),
      )
    },
    test("delete the class anything:Nothing") {
      val classIri = anythingOntologyIri.makeClass("Nothing")

      for {
        response <- ontologyResponder(_.deleteClass(classIri, anythingLastModDate, randomUUID, anythingAdminUser))
        metadata  = response.ontologies.head
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(response.ontologies.size == 1, newAnythingLastModDate.isAfter(oldAnythingLastModDate))
    },
    test("not create a class whose base class is in a non-shared ontology in another project") {
      val classIri = anythingOntologyIri.makeEntityIri("InvalidClass")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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

      val createReq = CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)
      ontologyResponder(_.createClass(createReq)).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a class with a cardinality on a property defined in a non-shared ontology in another project") {
      val classIri = anythingOntologyIri.makeEntityIri("InvalidClass")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
        subClassOf = Set(KA.Resource.toSmartIri),
        directCardinalities =
          Map(IncunabulaOntologyIri.makeEntityIri("description") -> KnoraCardinalityInfo(ZeroOrOne)),
        ontologySchema = ApiV2Complex,
      )

      val createReq = CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)
      ontologyResponder(_.createClass(createReq)).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create a subproperty of a property defined in a non-shared ontology in another project") {
      val propertyIri = anythingOntologyIri.makeEntityIri("invalidProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.TextValue.toSmartIri)),
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

      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create property with a subject type defined in a non-shared ontology in another project") {
      val propertyIri = anythingOntologyIri.makeEntityIri("invalidProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(IncunabulaOntologyIri.makeEntityIri("book"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.TextValue.toSmartIri)),
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
        subPropertyOf = Set(KA.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("not create property with an object type defined in a non-shared ontology in another project") {
      val propertyIri = anythingOntologyIri.makeEntityIri("invalidProperty")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
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
        subPropertyOf = Set(KA.HasLinkTo.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)).exit
        .map(actual => assert(actual)(failsWithA[BadRequestException]))
    },
    test("create a class anything:AnyBox1 as a subclass of example-box:Box") {
      val classIri = anythingOntologyIri.makeEntityIri("AnyBox1")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
      val createReq = CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)

      for {
        msg             <- ontologyResponder(_.createClass(createReq))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent == classInfoContent,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("delete the class anything:AnyBox1") {
      val classIri = anythingOntologyIri.makeClass("AnyBox1")
      for {
        response <- ontologyResponder(_.deleteClass(classIri, anythingLastModDate, randomUUID, anythingAdminUser))
        metadata  = response.ontologies.head
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(response.ontologies.size == 1, newAnythingLastModDate.isAfter(oldAnythingLastModDate))
    },
    test("create a class anything:AnyBox2 with a cardinality on example-box:hasName") {
      val classIri = anythingOntologyIri.makeEntityIri("AnyBox2")
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
      val createReq = CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)

      for {
        msg             <- ontologyResponder(_.createClass(createReq))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent == classInfoContent,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("delete the class anything:AnyBox2") {
      val classIri = anythingOntologyIri.makeClass("AnyBox2")

      for {
        response <- ontologyResponder(_.deleteClass(classIri, anythingLastModDate, randomUUID, anythingAdminUser))
        metadata  = response.ontologies.head
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(response.ontologies.size == 1, newAnythingLastModDate.isAfter(oldAnythingLastModDate))
    },
    test("create a property anything:hasAnyName with base property example-box:hasName") {
      val propertyIri = anythingOntologyIri.makeEntityIri("hasAnyName")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.TextValue.toSmartIri)),
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

      for {
        msg <-
          ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        property         = externalOntology.properties(propertyIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.properties.size == 1,
        property.entityInfoContent == propertyInfoContent,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("delete the property anything:hasAnyName") {
      val propertyIri = anythingOntologyIri.makeProperty("hasAnyName")

      for {
        msg     <- ontologyResponder(_.deleteProperty(propertyIri, anythingLastModDate, randomUUID, anythingAdminUser))
        metadata = msg.ontologies.head
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(msg.ontologies.size == 1, newAnythingLastModDate.isAfter(oldAnythingLastModDate))
    },
    test("create a property anything:BoxHasBoolean with subject type example-box:Box") {
      val propertyIri = anythingOntologyIri.makeEntityIri("BoxHasBoolean")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.SubjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.SubjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(ExampleSharedOntologyIri.makeEntityIri("Box"))),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
            objects = Seq(SmartIriLiteralV2(KA.BooleanValue.toSmartIri)),
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
        subPropertyOf = Set(KA.HasValue.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      for {
        msg <-
          ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        property         = externalOntology.properties(propertyIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.properties.size == 1,
        property.entityInfoContent == propertyInfoContent,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("delete the property anything:BoxHasBoolean") {
      val propertyIri = anythingOntologyIri.makeProperty("BoxHasBoolean")
      for {
        msg     <- ontologyResponder(_.deleteProperty(propertyIri, anythingLastModDate, randomUUID, anythingAdminUser))
        metadata = msg.ontologies.head
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(msg.ontologies.size == 1, newAnythingLastModDate.isAfter(oldAnythingLastModDate))
    },
    test("create a property anything:hasBox with object type example-box:Box") {
      val propertyIri = anythingOntologyIri.makeEntityIri("hasBox")
      val propertyInfoContent = PropertyInfoContentV2(
        propertyIri = propertyIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
          ),
          KA.ObjectType.toSmartIri -> PredicateInfoV2(
            predicateIri = KA.ObjectType.toSmartIri,
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
        subPropertyOf = Set(KA.HasLinkTo.toSmartIri),
        ontologySchema = ApiV2Complex,
      )

      for {
        msg <-
          ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        property         = externalOntology.properties(propertyIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.properties.size == 1,
        property.entityInfoContent == propertyInfoContent,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("delete the property anything:hasBox") {
      val propertyIri = anythingOntologyIri.makeProperty("hasBox")
      for {
        msg     <- ontologyResponder(_.deleteProperty(propertyIri, anythingLastModDate, randomUUID, anythingAdminUser))
        metadata = msg.ontologies.head
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(msg.ontologies.size == 1, newAnythingLastModDate.isAfter(oldAnythingLastModDate))
    },
    test("create a class with several cardinalities, then remove one of the cardinalities") {
      // Create a class with no cardinalities.
      val createReq = CreateClassRequestV2(
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
        apiRequestID = randomUUID,
        requestingUser = anythingAdminUser,
      )
      for {
        createClassResponse <- ontologyResponder(_.createClass(createReq))
        lastModDateAterCreateClass =
          createClassResponse.ontologyMetadata.lastModificationDate
            .getOrElse(
              throw AssertionException(
                s"${createClassResponse.ontologyMetadata.ontologyIri} has no last modification date",
              ),
            )
        lastModDateBeforeCreateClass = self.anythingLastModDate
        _                            = self.anythingLastModDate = lastModDateAterCreateClass

        // Create a text property.
        createPropertyResponse <-
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
                    objects = Vector(
                      SmartIriLiteralV2(value = "http://api.knora.org/ontology/knora-api/v2#TextValue".toSmartIri),
                    ),
                  ),
                  "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    objects =
                      Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri)),
                  ),
                ),
                subPropertyOf = Set("http://api.knora.org/ontology/knora-api/v2#hasValue".toSmartIri),
                ontologySchema = ApiV2Complex,
              ),
              lastModificationDate = anythingLastModDate,
              apiRequestID = randomUUID,
              requestingUser = anythingAdminUser,
            ),
          )
        lastModDateAfterCreateProperty =
          createPropertyResponse.ontologyMetadata.lastModificationDate
            .getOrElse(
              throw AssertionException(
                s"${createPropertyResponse.ontologyMetadata.ontologyIri} has no last modification date",
              ),
            )
        lastModDateBeforeCreateProperty = self.anythingLastModDate
        _                               = self.anythingLastModDate = lastModDateAfterCreateProperty

        // Create an integer property.
        createIntegerPropertyResponse <-
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
                    objects = Vector(
                      SmartIriLiteralV2(value = "http://api.knora.org/ontology/knora-api/v2#IntValue".toSmartIri),
                    ),
                  ),
                  "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    objects =
                      Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri)),
                  ),
                ),
                subPropertyOf = Set("http://api.knora.org/ontology/knora-api/v2#hasValue".toSmartIri),
                ontologySchema = ApiV2Complex,
              ),
              lastModificationDate = anythingLastModDate,
              apiRequestID = randomUUID,
              requestingUser = anythingAdminUser,
            ),
          )
        lastModDateAfterCreateIntegerProp =
          createIntegerPropertyResponse.ontologyMetadata.lastModificationDate
            .getOrElse(
              throw AssertionException(
                s"${createIntegerPropertyResponse.ontologyMetadata.ontologyIri} has no last modification date",
              ),
            )
        lastModDateBeforeCreateIntegerProp = self.anythingLastModDate
        _                                  = self.anythingLastModDate = lastModDateAfterCreateIntegerProp

        // Create a link property.
        createLinkPropertyResponse <-
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
                    objects =
                      Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri)),
                  ),
                ),
                subPropertyOf = Set("http://api.knora.org/ontology/knora-api/v2#hasLinkTo".toSmartIri),
                ontologySchema = ApiV2Complex,
              ),
              lastModificationDate = anythingLastModDate,
              apiRequestID = randomUUID,
              requestingUser = anythingAdminUser,
            ),
          )
        lastModDateAfterCreateLinkProperty =
          createLinkPropertyResponse.ontologyMetadata.lastModificationDate
            .getOrElse(
              throw AssertionException(
                s"${createLinkPropertyResponse.ontologyMetadata.ontologyIri} has no last modification date",
              ),
            )
        lastModDateBeforeCreateLinkProperty = self.anythingLastModDate
        _                                   = self.anythingLastModDate = lastModDateAfterCreateLinkProperty

        // Add cardinalities to the class.
        addCardinalitiesResponse <-
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
                apiRequestID = randomUUID,
                requestingUser = anythingAdminUser,
              ),
            ),
          )
        lastModDateAfterAddCardinalities =
          addCardinalitiesResponse.ontologyMetadata.lastModificationDate
            .getOrElse(
              throw AssertionException(
                s"${addCardinalitiesResponse.ontologyMetadata.ontologyIri} has no last modification date",
              ),
            )
        lastModDateBeforeAddCardinalities = self.anythingLastModDate
        _                                 = self.anythingLastModDate = lastModDateAfterAddCardinalities

        // Remove the link value cardinality from the class.
        replaceCardinalitiesResponse <-
          ontologyResponder(
            _.replaceClassCardinalities(
              ReplaceClassCardinalitiesRequestV2(
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
                apiRequestID = randomUUID,
                requestingUser = anythingAdminUser,
              ),
            ),
          )
        lastModDateAfterReplaceCardinalities =
          replaceCardinalitiesResponse.ontologyMetadata.lastModificationDate.getOrElse(
            throw AssertionException(
              s"${replaceCardinalitiesResponse.ontologyMetadata.ontologyIri} has no last modification date",
            ),
          )
        lastModDateBeforeReplaceCardinalities = self.anythingLastModDate
        _                                     = self.anythingLastModDate = lastModDateAfterReplaceCardinalities

        // Check that the correct blank nodes were stored for the cardinalities.
        actual <- triplestoreService(
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
                  )
      } yield assertTrue(
        lastModDateAterCreateClass.isAfter(lastModDateBeforeCreateClass),
        lastModDateBeforeCreateProperty.isAfter(lastModDateBeforeCreateProperty),
        lastModDateAfterCreateIntegerProp.isAfter(lastModDateBeforeCreateIntegerProp),
        lastModDateAfterCreateLinkProperty.isAfter(lastModDateBeforeCreateLinkProperty),
        lastModDateAfterAddCardinalities.isAfter(lastModDateBeforeAddCardinalities),
        lastModDateAfterReplaceCardinalities.isAfter(lastModDateBeforeReplaceCardinalities),
        actual.getColOrThrow("cardinalityProp").sorted == Seq(
          "http://www.knora.org/ontology/0001/anything#testIntProp",
          "http://www.knora.org/ontology/0001/anything#testTextProp",
        ),
      )
    },
    test(
      "create a class with two cardinalities, use one in data, and allow only removal of the cardinality for the property not used in data",
    ) {
      for {
        // Create a class with no cardinalities.
        createClassResponse <-
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
                apiRequestID = randomUUID,
                requestingUser = anythingAdminUser,
              ),
            ),
          )
        lastModDateAfterCreateClass =
          createClassResponse.ontologyMetadata.lastModificationDate
            .getOrElse(throw AssertionException(s"${createClassResponse.ontologyIri} has no last modification date"))
        lastModDateBeforeCreateClass = self.freetestLastModDate
        _                            = self.freetestLastModDate = lastModDateAfterCreateClass

        // Create a text property.
        createTextPropertyResponse <-
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
                    objects = Vector(
                      SmartIriLiteralV2(value = "http://api.knora.org/ontology/knora-api/v2#TextValue".toSmartIri),
                    ),
                  ),
                  "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    objects =
                      Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri)),
                  ),
                ),
                subPropertyOf = Set("http://api.knora.org/ontology/knora-api/v2#hasValue".toSmartIri),
                ontologySchema = ApiV2Complex,
              ),
              lastModificationDate = freetestLastModDate,
              apiRequestID = randomUUID,
              requestingUser = anythingAdminUser,
            ),
          )
        lastModDateAfterCreateTextProperty =
          createTextPropertyResponse.ontologyMetadata.lastModificationDate
            .getOrElse(
              throw AssertionException(
                s"${createTextPropertyResponse.ontologyMetadata.ontologyIri} has no last modification date",
              ),
            )
        lastModDateAfterBeforeTextProperty = self.freetestLastModDate
        _                                  = self.freetestLastModDate = lastModDateAfterCreateTextProperty

        // Create an integer property.
        createIntegerPropertyResponse <-
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
                    objects = Vector(
                      SmartIriLiteralV2(value = "http://api.knora.org/ontology/knora-api/v2#IntValue".toSmartIri),
                    ),
                  ),
                  "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri -> PredicateInfoV2(
                    predicateIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    objects =
                      Vector(SmartIriLiteralV2(value = "http://www.w3.org/2002/07/owl#ObjectProperty".toSmartIri)),
                  ),
                ),
                subPropertyOf = Set("http://api.knora.org/ontology/knora-api/v2#hasValue".toSmartIri),
                ontologySchema = ApiV2Complex,
              ),
              lastModificationDate = freetestLastModDate,
              apiRequestID = randomUUID,
              requestingUser = anythingAdminUser,
            ),
          )
        lastModDateAfterCreateIntegerProperty =
          createIntegerPropertyResponse.ontologyMetadata.lastModificationDate
            .getOrElse(
              throw AssertionException(
                s"${createIntegerPropertyResponse.ontologyMetadata.ontologyIri} has no last modification date",
              ),
            )
        lastModDateBeforeCreateIntegerProperty = self.freetestLastModDate
        _                                      = self.freetestLastModDate = lastModDateAfterCreateIntegerProperty

        // Add cardinalities to the class.
        addCardinalitiesResponse <-
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
                apiRequestID = randomUUID,
                requestingUser = anythingAdminUser,
              ),
            ),
          )
        lastModeDateAfterAddCardinalities =
          addCardinalitiesResponse.ontologyMetadata.lastModificationDate
            .getOrElse(
              throw AssertionException(
                s"${addCardinalitiesResponse.ontologyMetadata.ontologyIri} has no last modification date",
              ),
            )
        lastModeDateBeforeAddCardinalities = self.freetestLastModDate
        _                                  = self.freetestLastModDate = lastModeDateAfterAddCardinalities

        // Create a resource of #BlueTestClass using only #hasBlueTestIntProp.
        resourceIri = sf.makeRandomResourceIri(anythingProject.shortcode)
        inputValues = Map(
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
        inputResource = CreateResourceV2(
                          resourceIri = Some(resourceIri.toSmartIri),
                          resourceClassIri =
                            "http://0.0.0.0:3333/ontology/0001/freetest/v2#BlueFreeTestClass".toSmartIri,
                          label = "my blue test class thing instance",
                          values = inputValues,
                          projectADM = anythingProject,
                        )
        _ <- ZIO.serviceWithZIO[ResourcesResponderV2](
               _.createResource(
                 CreateResourceRequestV2(
                   createResource = inputResource,
                   requestingUser = anythingAdminUser,
                   apiRequestID = randomUUID,
                 ),
               ),
             )

        // Successfully check if the cardinality can be deleted
        canDeleteResponse <-
          ontologyResponder(
            _.canDeleteCardinalitiesFromClass(
              CanDeleteCardinalitiesFromClassRequestV2(
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
                apiRequestID = randomUUID,
                requestingUser = anythingAdminUser,
              ),
            ),
          )

        // Successfully remove the (unused) text value cardinality from the class.
        deleteCardinalitiesResponse <-
          ontologyResponder(
            _.deleteCardinalitiesFromClass(
              DeleteCardinalitiesFromClassRequestV2(
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
                apiRequestID = randomUUID,
                requestingUser = anythingAdminUser,
              ),
            ),
          )
        lastModAfterDeleteCardinalities =
          deleteCardinalitiesResponse.ontologyMetadata.lastModificationDate
            .getOrElse(
              throw AssertionException(
                s"${deleteCardinalitiesResponse.ontologyMetadata.ontologyIri} has no last modification date",
              ),
            )
        lastModBeforeDeleteCardinalities = self.freetestLastModDate
        _                                = self.freetestLastModDate = lastModAfterDeleteCardinalities

        // Check that the correct blank nodes were stored for the cardinalities.
        actual <-
          triplestoreService(
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
          )
      } yield assertTrue(
        lastModDateAfterCreateClass.isAfter(lastModDateBeforeCreateClass),
        lastModDateAfterCreateTextProperty.isAfter(lastModDateAfterBeforeTextProperty),
        lastModDateAfterCreateIntegerProperty.isAfter(lastModDateBeforeCreateIntegerProperty),
        lastModeDateAfterAddCardinalities.isAfter(lastModeDateBeforeAddCardinalities),
        canDeleteResponse.canDo.value,
        lastModAfterDeleteCardinalities.isAfter(lastModBeforeDeleteCardinalities),
        actual.getColOrThrow("cardinalityProp").sorted == Seq(
          "http://www.knora.org/ontology/0001/freetest#hasBlueTestIntProp",
        ),
      )
    },
    test("create a class anything:FoafPerson as a subclass of foaf:Person") {
      // create the class anything:FoafPerson
      val classIri: SmartIri = anythingOntologyIri.makeEntityIri("FoafPerson")
      val classInfoContent: ClassInfoContentV2 = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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

      for {
        createClassResponse <-
          ontologyResponder(
            _.createClass(CreateClassRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)),
          )
        // check if class was created correctly
        ontologyFromCreateClass  = createClassResponse.toOntologySchema(ApiV2Complex)
        classInfoFromCreateClass = ontologyFromCreateClass.classes(classIri)
        metadata                 = ontologyFromCreateClass.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        lastModDateBeforeCreateClass = self.anythingLastModDate
        _                            = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        ontologyFromCreateClass.classes.size == 1,
        classInfoFromCreateClass.entityInfoContent == classInfoContent,
        newAnythingLastModDate.isAfter(lastModDateBeforeCreateClass),
      )
    },
    test("create a property anything:hasFoafName as a subproperty of foaf:name") {
      // get the class IRI for anything:FoafPerson
      val classIri: SmartIri = anythingOntologyIri.makeEntityIri("FoafPerson")

      for {
        metadataResponse   <- ontologyResponder(_.getOntologyMetadataForProject(anythingProjectIri))
        anythingLastModDate = getLastModificationDate(metadataResponse, anythingOntologyIri)

        // create the property anything:hasFoafName
        propertyIri: SmartIri = anythingOntologyIri.makeEntityIri("hasFoafName")
        propertyInfoContent: PropertyInfoContentV2 = PropertyInfoContentV2(
                                                       propertyIri = propertyIri,
                                                       predicates = Map(
                                                         Rdf.Type.toSmartIri -> PredicateInfoV2(
                                                           predicateIri = Rdf.Type.toSmartIri,
                                                           objects =
                                                             Seq(SmartIriLiteralV2(Owl.ObjectProperty.toSmartIri)),
                                                         ),
                                                         KA.SubjectType.toSmartIri -> PredicateInfoV2(
                                                           predicateIri = KA.SubjectType.toSmartIri,
                                                           objects = Seq(SmartIriLiteralV2(classIri)),
                                                         ),
                                                         KA.ObjectType.toSmartIri -> PredicateInfoV2(
                                                           predicateIri = KA.ObjectType.toSmartIri,
                                                           objects = Seq(SmartIriLiteralV2(KA.TextValue.toSmartIri)),
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
                                                             StringLiteralV2
                                                               .from("The foaf name of something", Some("en")),
                                                             StringLiteralV2.from(
                                                               "Der foaf Name eines Dinges",
                                                               Some("de"),
                                                             ),
                                                           ),
                                                         ),
                                                       ),
                                                       subPropertyOf = Set(
                                                         KA.HasValue.toSmartIri,
                                                         "http://xmlns.com/foaf/0.1/name".toSmartIri,
                                                       ),
                                                       ontologySchema = ApiV2Complex,
                                                     )

        createPropertyResponse <-
          ontologyResponder(_.createProperty(propertyInfoContent, anythingLastModDate, randomUUID, anythingAdminUser))
        ontologyFromCreateProperty = createPropertyResponse.toOntologySchema(ApiV2Complex)
        property                   = ontologyFromCreateProperty.properties(propertyIri)
        metadataFromCreateProperty = ontologyFromCreateProperty.ontologyMetadata
        lastModDateAfterCreateProperty =
          metadataFromCreateProperty.lastModificationDate.getOrElse(
            throw AssertionException(s"${metadataFromCreateProperty.ontologyIri} has no last modification date"),
          )
        lastModDateBeforeCreateProperty = self.anythingLastModDate
        _                               = self.anythingLastModDate = lastModDateAfterCreateProperty
      } yield assertTrue(
        metadataResponse.ontologies.size == 3,
        property.entityInfoContent == propertyInfoContent,
        lastModDateAfterCreateProperty.isAfter(lastModDateBeforeCreateProperty),
      )
    },
    test("add property anything:hasFoafName to the class anything:FoafPerson") {
      // get the class IRI for anything:FoafPerson
      val classIri    = anythingOntologyIri.makeEntityIri("FoafPerson")
      val propertyIri = anythingOntologyIri.makeEntityIri("hasFoafName")
      // add a cardinality for the property anything:hasFoafName to the class anything:FoafPerson
      val classWithNewCardinalityInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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
        KA.HasStandoffLinkTo.toSmartIri,
        KA.HasStandoffLinkToValue.toSmartIri,
        ExampleSharedOntologyIri.makeEntityIri("hasName"),
        propertyIri,
      )

      val addReq = AddCardinalitiesToClassRequestV2(
        classWithNewCardinalityInfoContent,
        anythingLastModDate,
        randomUUID,
        anythingAdminUser,
      )
      for {
        msg             <- ontologyResponder(_.addCardinalitiesToClass(addReq))
        externalOntology = msg.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
        oldAnythingLastModDate = self.anythingLastModDate
        _                      = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent.directCardinalities == expectedDirectCardinalities,
        readClassInfo.allResourcePropertyCardinalities.keySet == expectedProperties,
        newAnythingLastModDate.isAfter(oldAnythingLastModDate),
      )
    },
    test("remove all properties from class anything:FoafPerson") {
      // get the class IRI for anything:FoafPerson
      val classIri: SmartIri    = anythingOntologyIri.makeEntityIri("FoafPerson")
      val propertyIri: SmartIri = anythingOntologyIri.makeEntityIri("hasFoafName")
      // check if cardinalities on class anything:FoafPerson can be removed
      val classInfoContentWithCardinalityToDeleteAllow: ClassInfoContentV2 = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
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

      for {
        response <- ontologyResponder(
                      _.canDeleteCardinalitiesFromClass(
                        CanDeleteCardinalitiesFromClassRequestV2(
                          classInfoContent = classInfoContentWithCardinalityToDeleteAllow,
                          lastModificationDate = anythingLastModDate,
                          apiRequestID = randomUUID,
                          requestingUser = anythingAdminUser,
                        ),
                      ),
                    )
        // remove cardinalities on the class anything:FoafPerson
        classChangeInfoContent = ClassInfoContentV2(
                                   classIri = classIri,
                                   predicates = Map(
                                     Rdf.Type.toSmartIri -> PredicateInfoV2(
                                       predicateIri = Rdf.Type.toSmartIri,
                                       objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
                                     ),
                                   ),
                                   ontologySchema = ApiV2Complex,
                                 )
        replaceResponse <- ontologyResponder(
                             _.replaceClassCardinalities(
                               ReplaceClassCardinalitiesRequestV2(
                                 classInfoContent = classChangeInfoContent,
                                 lastModificationDate = anythingLastModDate,
                                 apiRequestID = randomUUID,
                                 requestingUser = anythingAdminUser,
                               ),
                             ),
                           )

        // check if cardinalities were removed correctly
        expectedPropertiesAfterDeletion = Set(KA.HasStandoffLinkTo.toSmartIri, KA.HasStandoffLinkToValue.toSmartIri)
        externalOntology                = replaceResponse.toOntologySchema(ApiV2Complex)
        readClassInfo                   = externalOntology.classes(classIri)
        metadata: OntologyMetadataV2    = externalOntology.ontologyMetadata
        newAnythingLastModDate: Instant =
          metadata.lastModificationDate.getOrElse(
            throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
          )
        oldLastModDate = self.anythingLastModDate
        _              = self.anythingLastModDate = newAnythingLastModDate
      } yield assertTrue(
        response.canDo.value,
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent.directCardinalities == classChangeInfoContent.directCardinalities,
        readClassInfo.allResourcePropertyCardinalities.keySet == expectedPropertiesAfterDeletion,
        newAnythingLastModDate.isAfter(oldLastModDate),
      )
    },
    test("change the GUI order of a cardinality in a base class, and update its subclass in the ontology cache") {
      val classIri       = anythingOntologyIri.makeEntityIri("Thing")
      val newCardinality = KnoraCardinalityInfo(cardinality = Unbounded, guiOrder = Some(100))
      val classInfoContent = ClassInfoContentV2(
        classIri = classIri,
        predicates = Map(
          Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = Rdf.Type.toSmartIri,
            objects = Seq(SmartIriLiteralV2(Owl.Class.toSmartIri)),
          ),
        ),
        directCardinalities = Map(
          anythingOntologyIri.makeEntityIri("hasText") -> newCardinality,
        ),
        ontologySchema = ApiV2Complex,
      )

      val changeReq = ChangeGuiOrderRequestV2(classInfoContent, anythingLastModDate, randomUUID, anythingAdminUser)
      for {
        response        <- ontologyResponder(_.changeGuiOrder(changeReq))
        externalOntology = response.toOntologySchema(ApiV2Complex)
        readClassInfo    = externalOntology.classes(classIri)
        metadata         = externalOntology.ontologyMetadata
        newAnythingLastModDate = metadata.lastModificationDate.getOrElse(
                                   throw AssertionException(s"${metadata.ontologyIri} has no last modification date"),
                                 )
      } yield assertTrue(
        externalOntology.classes.size == 1,
        readClassInfo.entityInfoContent.directCardinalities(
          anythingOntologyIri.makeEntityIri("hasText"),
        ) == newCardinality,
        newAnythingLastModDate.isAfter(anythingLastModDate),
      )
    },
  )
}
case class LastModRef(private var value: Instant) {
  private def set(newValue: Instant): Instant =
    this.value = newValue
    this.value

  def updateFrom(r: ReadOntologyV2): (Instant, Instant) = {
    val oldValue = value
    val newValue = r
      .toOntologySchema(ApiV2Complex)
      .ontologyMetadata
      .lastModificationDate
      .getOrElse(throw AssertionException(s"${r.ontologyIri} has no last modification date"))
    (oldValue, set(newValue))
  }

  def updateFrom(r: ReadOntologyMetadataV2): (Instant, Instant) = {
    val oldValue = value
    val newValue = r
      .toOntologySchema(ApiV2Complex)
      .ontologies
      .headOption
      .flatMap(_.lastModificationDate)
      .getOrElse(throw AssertionException(s"$r has no last modification date"))
    (oldValue, set(newValue))
  }

  def isAfter(other: Instant): Boolean = value.isAfter(other)
}
object LastModRef {
  given Conversion[LastModRef, Instant] = _.value
  def make: LastModRef                  = LastModRef(Instant.now)
}
