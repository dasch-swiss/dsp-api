/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import sttp.client4.Request
import sttp.client4.UriContext
import sttp.model.MediaType
import zio.ZIO
import zio.test.assertTrue
import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2e.InstanceChecker
import org.knora.webapi.e2e.v2.ResourcesRouteV2E2ESpec.aThingWithHistoryIri
import org.knora.webapi.e2e.v2.ResourcesRouteV2E2ESpec.reiseInsHeiligeLandIri
import org.knora.webapi.e2e.v2.ResourcesRouteV2E2ESpec.suite
import org.knora.webapi.e2e.v2.ResourcesRouteV2E2ESpec.test
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.TestDataFileUtil
import zio.test.TestResult

object ResourcesEndpointsGetResourcesE2ESpec extends E2EZSpec {

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(
      path = "test_data/project_ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything",
    ),
  )

  private val instanceChecker: InstanceChecker = InstanceChecker.make

  private val thingResourceClassIri =
    ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri)
  private val thingSimpleResourceClassIri =
    ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri)

  private val bookResourceClassIri =
    ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri)
  private val bookSimpleResourceClassIri =
    ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri)

  private enum TestMediaType(val mediaType: MediaType, val readRdf: String => RdfModel) { self =>
    override def toString: String = self.mediaType.toString()

    case JsonLd extends TestMediaType(MediaType.unsafeApply("application", "ld+json"), RdfModel.fromJsonLD)
    case RdfXml extends TestMediaType(MediaType.unsafeApply("application", "rdf+xml"), RdfModel.fromRdfXml)
    case Turtle extends TestMediaType(MediaType.unsafeApply("text", "turtle"), RdfModel.fromTurtle)
  }

  private enum TestSchema(val f: Request[Either[String, String]] => Request[Either[String, String]]) {
    case Simple      extends TestSchema(r => r.header("X-Knora-Accept-Schema", "simple"))
    case SimpleQuery extends TestSchema(r => r.copy(uri = r.uri.addParam("schema", "simple")))
    case Complex     extends TestSchema(r => r)
  }

  private final case class TestCase(
    description: String,
    resourceIri: ResourceIri,
    filename: String,
    mediaType: TestMediaType = TestMediaType.JsonLd,
    resourceClassIri: Option[ResourceClassIri] = None,
    schema: TestSchema = TestSchema.Complex,
    version: Option[String] = None,
  ) { self =>

    private def readRdf(str: String): RdfModel = mediaType.readRdf(str)

    private def updateRequest(r: Request[Either[String, String]]): Request[Either[String, String]] = {
      def versionUpdate = (r: Request[Either[String, String]]) =>
        version match {
          case None    => r
          case Some(v) => r.copy(uri = r.uri.addParam("version", v))
        }
      versionUpdate.andThen(schema.f)(r).header("Accept", mediaType.toString())
    }

    def check: ZIO[TestApiClient & TestDataFileUtil, Throwable, TestResult] = for {
      actual <-
        TestApiClient.getAsString(uri"/v2/resources/${self.resourceIri}", self.updateRequest).flatMap(_.assert200)
      expected <- TestDataFileUtil.readTestData("resourcesR2RV2", self.filename)
      _ <- (self.resourceClassIri, self.mediaType) match {
             case (Some(iri), TestMediaType.JsonLd) => instanceChecker.check(actual, iri.smartIri)
             case _                                 => ZIO.unit
           }
    } yield assertTrue(self.readRdf(actual) == self.readRdf(expected))
  }

  private val readResourcesSuite = suite("GET /v2/resources") {
    Seq(
      TestCase(
        "the book 'Reise ins Heilige Land'",
        reiseInsHeiligeLandIri,
        "BookReiseInsHeiligeLand.jsonld",
        resourceClassIri = Some(bookResourceClassIri),
      ),
      TestCase(
        "the book 'Reise ins Heilige Land'",
        reiseInsHeiligeLandIri,
        "BookReiseInsHeiligeLand.ttl",
        mediaType = TestMediaType.Turtle,
      ),
      TestCase(
        "the book 'Reise ins Heilige Land'",
        reiseInsHeiligeLandIri,
        "BookReiseInsHeiligeLand.rdf",
        mediaType = TestMediaType.RdfXml,
      ),
      TestCase(
        "the book 'Reise ins Heilige Land'",
        reiseInsHeiligeLandIri,
        "BookReiseInsHeiligeLandSimple.jsonld",
        schema = TestSchema.SimpleQuery,
        resourceClassIri = Some(bookSimpleResourceClassIri),
      ),
      TestCase(
        "the book 'Reise ins Heilige Land'",
        reiseInsHeiligeLandIri,
        "BookReiseInsHeiligeLandSimple.jsonld",
        schema = TestSchema.Simple,
        resourceClassIri = Some(bookSimpleResourceClassIri),
      ),
      TestCase(
        "the book 'Reise ins Heilige Land'",
        reiseInsHeiligeLandIri,
        "BookReiseInsHeiligeLandSimple.rdf",
        schema = TestSchema.Simple,
        mediaType = TestMediaType.RdfXml,
      ),
      TestCase(
        "the first page of the book '[Das] Narrenschiff (lat.)'",
        ResourceIri.unsafeFrom("http://rdfh.ch/0803/7bbb8e59b703".toSmartIri),
        "NarrenschiffFirstPage.jsonld",
      ),
      TestCase(
        "a resource with a BCE date property",
        ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing_with_BCE_date".toSmartIri),
        "ThingWithBCEDate.jsonld",
        resourceClassIri = Some(thingResourceClassIri),
      ),
      TestCase(
        "a resource with a date property that represents a period going from BCE to CE",
        ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing_with_BCE_date2".toSmartIri),
        "ThingWithBCEDate2.jsonld",
        resourceClassIri = Some(thingResourceClassIri),
      ),
      TestCase(
        "a resource with a list value",
        ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing_with_list_value".toSmartIri),
        "ThingWithListValue.jsonld",
        resourceClassIri = Some(thingResourceClassIri),
      ),
      TestCase(
        "a resource with a list value",
        ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing_with_list_value".toSmartIri),
        "ThingWithListValueSimple.jsonld",
        schema = TestSchema.Simple,
      ),
      TestCase(
        "a resource with a link ",
        ResourceIri.unsafeFrom("http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ".toSmartIri),
        "ThingWithLinkComplex.jsonld",
        resourceClassIri = Some(thingResourceClassIri),
      ),
      TestCase(
        "a resource with a link",
        ResourceIri.unsafeFrom("http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ".toSmartIri),
        "ThingWithLinkSimple.jsonld",
        schema = TestSchema.Simple,
        resourceClassIri = Some(thingSimpleResourceClassIri),
      ),
      TestCase(
        "a resource with a Text language",
        ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing-with-text-valuesLanguage".toSmartIri),
        "ThingWithTextLangComplex.jsonld",
        resourceClassIri = Some(thingResourceClassIri),
      ),
      TestCase(
        "a resource with a Text language",
        ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing-with-text-valuesLanguage".toSmartIri),
        "ThingWithTextLangSimple.jsonld",
        schema = TestSchema.Simple,
        resourceClassIri = Some(thingSimpleResourceClassIri),
      ),
      TestCase(
        "a resource with values of different types",
        ResourceIri.unsafeFrom("http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw".toSmartIri),
        "Testding.jsonld",
        resourceClassIri = Some(thingResourceClassIri),
      ),
      TestCase(
        "a Thing resource with a link to a ThingPicture resource",
        ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing-with-picture".toSmartIri),
        "ThingWithPicture.jsonld",
        resourceClassIri = Some(thingResourceClassIri),
      ),
      TestCase(
        "a resource with a link to a resource that the user doesn't have permission to see",
        ResourceIri.unsafeFrom("http://rdfh.ch/0001/0JhgKcqoRIeRRG6ownArSw".toSmartIri),
        "ThingWithOneHiddenResource.jsonld",
        resourceClassIri = Some(thingResourceClassIri),
      ),
      TestCase(
        "a resource with a link to a resource that is marked as deleted",
        ResourceIri.unsafeFrom("http://rdfh.ch/0001/l8f8FVEiSCeq9A1p8gBR-A".toSmartIri),
        "ThingWithOneDeletedResource.jsonld",
        resourceClassIri = Some(thingResourceClassIri),
      ),
      TestCase(
        "for a past version of a resource, using a URL-encoded xsd:dateTimeStamp",
        aThingWithHistoryIri,
        "ThingWithVersionHistory.jsonld",
        version = Some("2019-02-12T08:05:10.351Z"),
      ),
      TestCase(
        "for a past version of a resource, using a Knora ARK timestamp",
        aThingWithHistoryIri,
        "ThingWithVersionHistory.jsonld",
        version = Some("20190212T080510351Z"),
      ),
    ).map { t =>
      test(
        s"perform a resource request for ${t.description} ${t.resourceIri} " +
          s"using the ${t.schema} schema " +
          s"in ${t.mediaType}",
      )(t.check)
    }.toList
  }

  override val e2eSpec = suite("ResourcesEndpointsGetResourcesE2ESpec")(
    readResourcesSuite,
  )
}
