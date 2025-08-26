/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.apache.pekko
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import org.xmlunit.diff.Diff
import spray.json.*

import java.nio.file.Paths
import scala.concurrent.ExecutionContextExecutor

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.FileUtil
import org.knora.webapi.util.MutableTestIri

import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.headers.BasicHttpCredentials

class SearchEndpointsE2ESpec extends E2ESpec {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  implicit val ec: ExecutionContextExecutor             = system.dispatcher

  private val anythingUser      = SharedTestDataADM.anythingUser1
  private val anythingUserEmail = anythingUser.email

  private val password = SharedTestDataADM.testPass

  private val hamletResourceIri = new MutableTestIri

  override lazy val rdfDataObjects: List[RdfDataObject] = SearchEndpointE2ESpecHelper.rdfDataObjects

  "The Search v2 Endpoint" should {
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Queries with type inference

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Queries that submit the complex schema

    "create a resource with a large text containing a lot of markup (32849 words, 6738 standoff tags)" ignore { // uses too much memory for GitHub CI
      // Create a resource containing the text of Hamlet.
      val hamletXml = FileUtil.readTextFile(Paths.get("test_data/generated_test_data/resourcesR2RV2/hamlet.xml"))
      val jsonLDEntity =
        s"""{
           |  "@type" : "anything:Thing",
           |  "anything:hasRichtext" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:textValueAsXml" : ${JsString(hamletXml).compactPrint},
           |    "knora-api:textValueHasMapping" : {
           |      "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping"
           |    }
           |  },
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "rdfs:label" : "test thing",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
      val resourceCreateResponseAsJsonLD = getResponseAsJsonLD(
        Post(
          s"$baseApiUrl/v2/resources",
          HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val validationFun: (String, => Nothing) => String =
        (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI =
        resourceCreateResponseAsJsonLD.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(resourceIri.toSmartIri.isKnoraDataIri)
      hamletResourceIri.set(resourceIri)
    }

    "search for the large text and its markup and receive it as XML, and check that it matches the original XML" ignore { // depends on previous test
      val hamletXml = FileUtil.readTextFile(Paths.get("test_data/generated_test_data/resourcesR2RV2/hamlet.xml"))
      val gravsearchQuery =
        s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
           |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
           |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
           |
           |CONSTRUCT {
           |    ?thing knora-api:isMainResource true .
           |    ?thing anything:hasRichtext ?text .
           |} WHERE {
           |    BIND(<${hamletResourceIri.get}> AS ?thing)
           |    ?thing a anything:Thing .
           |    ?thing anything:hasRichtext ?text .
           |}""".stripMargin
      val searchResponseAsJsonLD = getResponseAsJsonLD(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val xmlFromResponse: String = searchResponseAsJsonLD.body
        .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext")
        .flatMap(_.getRequiredString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml))
        .fold(e => throw BadRequestException(e), identity)

      // Compare it to the original XML.
      val xmlDiff: Diff =
        DiffBuilder.compare(Input.fromString(hamletXml)).withTest(Input.fromString(xmlFromResponse)).build()
      xmlDiff.hasDifferences should be(false)
    }

  }
}
