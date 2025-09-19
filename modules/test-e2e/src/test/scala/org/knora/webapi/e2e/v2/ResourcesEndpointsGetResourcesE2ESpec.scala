/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import sttp.client4.UriContext
import zio.test.TestResult
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
import org.knora.webapi.testservices.RequestsUpdates.addAcceptHeaderRdfXml
import org.knora.webapi.testservices.RequestsUpdates.addAcceptHeaderTurtle
import org.knora.webapi.testservices.RequestsUpdates.addSimpleSchemaHeader
import org.knora.webapi.testservices.RequestsUpdates.addSimpleSchemaQueryParam
import org.knora.webapi.testservices.RequestsUpdates.addVersionQueryParam
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.TestDataFileUtil

object ResourcesEndpointsGetResourcesE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = List(
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

  private val getV2ResourcesSuite = suite("GET /v2/resources")(
    test("for the book 'Reise ins Heilige Land' using the complex schema in JSON-LD") {
      for {
        actual   <- TestApiClient.getJsonLd(uri"/v2/resources/$reiseInsHeiligeLandIri").flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "BookReiseInsHeiligeLand.jsonld")
        _        <- instanceChecker.check(actual, bookResourceClassIri.smartIri)
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test("for the book 'Reise ins Heilige Land' using the complex schema in Turtle") {
      for {
        actual <- TestApiClient
                    .getAsString(uri"/v2/resources/$reiseInsHeiligeLandIri", addAcceptHeaderTurtle)
                    .flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "BookReiseInsHeiligeLand.ttl")
      } yield assertTrue(RdfModel.fromTurtle(actual) == RdfModel.fromTurtle(expected))
    },
    test("for the book 'Reise ins Heilige Land' using the complex schema in RDF/XML") {
      for {
        actual <- TestApiClient
                    .getAsString(uri"/v2/resources/$reiseInsHeiligeLandIri", addAcceptHeaderRdfXml)
                    .flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "BookReiseInsHeiligeLand.rdf")
      } yield assertTrue(RdfModel.fromRdfXml(actual) == RdfModel.fromRdfXml(expected))
    },
    test("for the book 'Reise ins Heilige Land' using the simple schema (query) in JSON-LD") {
      for {
        actual <- TestApiClient
                    .getAsString(uri"/v2/resources/$reiseInsHeiligeLandIri", addSimpleSchemaQueryParam)
                    .flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "BookReiseInsHeiligeLandSimple.jsonld")
        _        <- instanceChecker.check(actual, bookSimpleResourceClassIri.smartIri)
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test(
      "for the book 'Reise ins Heilige Land' using the simple schema (header) in JSON-LD",
    ) {
      for {
        actual <-
          TestApiClient
            .getAsString(uri"/v2/resources/$reiseInsHeiligeLandIri", addSimpleSchemaHeader)
            .flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "BookReiseInsHeiligeLandSimple.jsonld")
        _        <- instanceChecker.check(actual, bookSimpleResourceClassIri.smartIri)
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test(
      s"for the book 'Reise ins Heilige Land' using the simple schema (header) in RDF/XML",
    ) {
      for {
        actual <-
          TestApiClient
            .getAsString(
              uri"/v2/resources/$reiseInsHeiligeLandIri",
              addSimpleSchemaHeader.andThen(addAcceptHeaderRdfXml),
            )
            .flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "BookReiseInsHeiligeLandSimple.rdf")
      } yield assertTrue(RdfModel.fromRdfXml(actual) == RdfModel.fromRdfXml(expected))
    },
    test(
      s"for the first page of the book '[Das] Narrenschiff (lat.) using the complex schema in JSON-LD",
    ) {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0803/7bbb8e59b703".toSmartIri)
      for {
        actual   <- TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri").flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "NarrenschiffFirstPage.jsonld")
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test("for a resource with a BCE date property using the complex schema in JSON-LD") {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing_with_BCE_date".toSmartIri)
      for {
        actual   <- TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri").flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "ThingWithBCEDate.jsonld")
        _        <- instanceChecker.check(actual, thingResourceClassIri.smartIri)
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test(
      "for a resource with a date property that represents a period going from BCE to CE" +
        s"using the complex schema " +
        s"in JSON-LD",
    ) {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing_with_BCE_date2".toSmartIri)
      for {
        actual   <- TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri").flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "ThingWithBCEDate2.jsonld")
        _        <- instanceChecker.check(actual, thingResourceClassIri.smartIri)
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test(s"for a resource with a list value using the complex schema in JSON-LD") {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing_with_list_value".toSmartIri)
      for {
        actual   <- TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri").flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "ThingWithListValue.jsonld")
        _        <- instanceChecker.check(actual, thingResourceClassIri.smartIri)
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test(
      s"for a resource with a list value " +
        s"using the simple schema (header) " +
        s"in JSON-LD",
    ) {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing_with_list_value".toSmartIri)
      for {
        actual <- TestApiClient
                    .getAsString(uri"/v2/resources/$resourceIri", addSimpleSchemaHeader)
                    .flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "ThingWithListValueSimple.jsonld")
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test("for a resource with a link using the complex schema in JSON-LD") {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ".toSmartIri)
      for {
        actual   <- TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri").flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "ThingWithLinkComplex.jsonld")
        _        <- instanceChecker.check(actual, thingResourceClassIri.smartIri)
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test("for a resource with a link using the simple schema (header) in JSON-LD") {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ".toSmartIri)
      for {
        actual <- TestApiClient
                    .getAsString(uri"/v2/resources/$resourceIri", addSimpleSchemaHeader)
                    .flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "ThingWithLinkSimple.jsonld")
        _        <- instanceChecker.check(actual, thingSimpleResourceClassIri.smartIri)
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test("for a resource with a Text language using the complex schema in JSON-LD") {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing-with-text-valuesLanguage".toSmartIri)
      for {
        actual   <- TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri").flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "ThingWithTextLangComplex.jsonld")
        _        <- instanceChecker.check(actual, thingResourceClassIri.smartIri)
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test("for a resource with a Text language using the simple schema (header) in JSON-LD") {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing-with-text-valuesLanguage".toSmartIri)
      for {
        actual <- TestApiClient
                    .getAsString(uri"/v2/resources/$resourceIri", addSimpleSchemaHeader)
                    .flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "ThingWithTextLangSimple.jsonld")
        _        <- instanceChecker.check(actual, thingSimpleResourceClassIri.smartIri)
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test(
      "for a resource with values of different types using the complex schema in JSON-LD",
    ) {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw".toSmartIri)
      for {
        actual   <- TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri").flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "Testding.jsonld")
        _        <- instanceChecker.check(actual, thingResourceClassIri.smartIri)
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test(
      "for a Thing resource with a link to a ThingPicture resource using the complex schema in JSON-LD",
    ) {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing-with-picture".toSmartIri)
      for {
        actual   <- TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri").flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "ThingWithPicture.jsonld")
        _        <- instanceChecker.check(actual, thingResourceClassIri.smartIri)
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test(
      "for a resource with a link to a resource that the user doesn't have permission to see using the complex schema in JSON-LD",
    ) {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/0JhgKcqoRIeRRG6ownArSw".toSmartIri)
      for {
        actual   <- TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri").flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "ThingWithOneHiddenResource.jsonld")
        _        <- instanceChecker.check(actual, thingResourceClassIri.smartIri)
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test(
      "for a resource with a link to a resource that is marked as deleted using the complex schema in JSON-LD",
    ) {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/l8f8FVEiSCeq9A1p8gBR-A".toSmartIri)
      for {
        actual   <- TestApiClient.getJsonLd(uri"/v2/resources/$resourceIri").flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "ThingWithOneDeletedResource.jsonld")
        _        <- instanceChecker.check(actual, thingResourceClassIri.smartIri)
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test(
      "for for a past version of a resource, using a URL-encoded xsd:dateTimeStampusing the complex schema in JSON-LD",
    ) {
      val version = "2019-02-12T08:05:10.351Z"
      for {
        actual <- TestApiClient
                    .getAsString(uri"/v2/resources/$aThingWithHistoryIri", addVersionQueryParam(version))
                    .flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "ThingWithVersionHistory.jsonld")
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
    test(
      s"for for a past version of a resource, using a Knora ARK timestamp using the complex schema in JSON-LD",
    ) {
      val version = "20190212T080510351Z"
      for {
        actual <- TestApiClient
                    .getAsString(uri"/v2/resources/$aThingWithHistoryIri", addVersionQueryParam(version))
                    .flatMap(_.assert200)
        expected <- TestDataFileUtil.readTestData("resourcesR2RV2", "ThingWithVersionHistory.jsonld")
      } yield assertTrue(RdfModel.fromJsonLD(actual) == RdfModel.fromJsonLD(expected))
    },
  )

  override val e2eSpec = suite("ResourcesEndpointsGetResourcesE2ESpec")(
    getV2ResourcesSuite,
  )
}
