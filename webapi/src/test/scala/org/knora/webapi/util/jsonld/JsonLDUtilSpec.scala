/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util.jsonld

import org.knora.webapi.OntologyConstants
import org.knora.webapi.util.StringFormatter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.{JsValue, JsonParser}

/**
 * Tests [[JsonLDUtil]].
 */
class JsonLDUtilSpec extends AnyWordSpecLike with Matchers {

    StringFormatter.initForTest()

    "The JSON-LD utility" should {
        "parse JSON-LD text, compact it with an empty context, convert the result to a JsonLDDocument, and convert that back to text" in {
            val inputStr =
                """
                  |{
                  |  "knora-api:hasOntologies" : {
                  |    "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                  |    "@type" : "owl:Ontology",
                  |    "knora-api:hasProperties" : {
                  |      "anything:hasName" : {
                  |        "@id" : "anything:hasName",
                  |        "@type" : "owl:ObjectProperty",
                  |        "knora-api:objectType" : "http://api.knora.org/ontology/knora-api/v2#TextValue",
                  |        "knora-api:subjectType" : "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
                  |        "rdfs:comment" : [ {
                  |          "@language" : "en",
                  |          "@value" : "The name of a Thing"
                  |        }, {
                  |          "@language" : "de",
                  |          "@value" : "Der Name eines Dinges"
                  |        } ],
                  |        "rdfs:label" : [ {
                  |          "@language" : "en",
                  |          "@value" : "has name"
                  |        }, {
                  |          "@language" : "de",
                  |          "@value" : "hat Namen"
                  |        } ],
                  |        "rdfs:subPropertyOf" : [ "http://api.knora.org/ontology/knora-api/v2#hasValue", "http://schema.org/name" ]
                  |      }
                  |    },
                  |    "knora-api:lastModificationDate" : "2017-12-19T15:23:42.166Z"
                  |  },
                  |  "@context" : {
                  |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                  |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                  |    "owl" : "http://www.w3.org/2002/07/owl#",
                  |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                  |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                  |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                  |  }
                  |}
                """.stripMargin

            val expectedOutputStr =
                """
                  |{
                  |  "http://api.knora.org/ontology/knora-api/v2#hasOntologies" : {
                  |    "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
                  |    "@type" : "http://www.w3.org/2002/07/owl#Ontology",
                  |    "http://api.knora.org/ontology/knora-api/v2#hasProperties" : {
                  |      "http://0.0.0.0:3333/ontology/0001/anything/v2#hasName" : {
                  |        "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2#hasName",
                  |        "@type" : "http://www.w3.org/2002/07/owl#ObjectProperty",
                  |        "http://api.knora.org/ontology/knora-api/v2#objectType" : "http://api.knora.org/ontology/knora-api/v2#TextValue",
                  |        "http://api.knora.org/ontology/knora-api/v2#subjectType" : "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
                  |        "http://www.w3.org/2000/01/rdf-schema#comment" : [ {
                  |          "@language" : "en",
                  |          "@value" : "The name of a Thing"
                  |        }, {
                  |          "@language" : "de",
                  |          "@value" : "Der Name eines Dinges"
                  |        } ],
                  |        "http://www.w3.org/2000/01/rdf-schema#label" : [ {
                  |          "@language" : "en",
                  |          "@value" : "has name"
                  |        }, {
                  |          "@language" : "de",
                  |          "@value" : "hat Namen"
                  |        } ],
                  |        "http://www.w3.org/2000/01/rdf-schema#subPropertyOf" : [ "http://api.knora.org/ontology/knora-api/v2#hasValue", "http://schema.org/name" ]
                  |      }
                  |    },
                  |    "http://api.knora.org/ontology/knora-api/v2#lastModificationDate" : "2017-12-19T15:23:42.166Z"
                  |  }
                  |}
                """.stripMargin

            val compactedJsonLDDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(inputStr)
            val formattedCompactedDoc = compactedJsonLDDoc.toPrettyString
            val receivedOutputAsJsValue: JsValue = JsonParser(formattedCompactedDoc)
            val expectedOutputAsJsValue: JsValue = JsonParser(expectedOutputStr)
            receivedOutputAsJsValue should ===(expectedOutputAsJsValue)
        }

        "make a JSON-LD document with a URL as a context" in {
            val iiifUrls = Seq(
                "http://example.org/1",
                "http://example.org/2"
            )

            val body: JsonLDObject = JsonLDObject(
                Map(
                    JsonLDConstants.ID -> JsonLDString("https://localhost:1025/server/manifest.json"),
                    JsonLDConstants.TYPE -> JsonLDString(OntologyConstants.IIIF.PresentationV2.Manifest),
                    OntologyConstants.Rdfs.Label -> JsonLDString("TEST MANIFEST"),
                    OntologyConstants.IIIF.PresentationV2.HasSequences -> JsonLDArray(
                        iiifUrls.map {
                            iiifUrl =>
                                JsonLDObject(
                                    Map(
                                        JsonLDConstants.CONTEXT -> JsonLDString(OntologyConstants.IIIF.PresentationV2.ContextUrl),
                                        JsonLDConstants.ID -> JsonLDString("https://localhost:1025/server/sequence/normal"),
                                        JsonLDConstants.TYPE -> JsonLDString(OntologyConstants.IIIF.PresentationV2.Sequence),
                                        OntologyConstants.IIIF.PresentationV2.HasCanvases -> JsonLDArray(
                                            Seq(
                                                JsonLDObject(
                                                    Map(
                                                        JsonLDConstants.CONTEXT -> JsonLDString(OntologyConstants.IIIF.PresentationV2.ContextUrl),
                                                        JsonLDConstants.ID -> JsonLDString("https://localhost:1025/server/canvas/bigcanvas1"),
                                                        JsonLDConstants.TYPE -> JsonLDString(OntologyConstants.IIIF.PresentationV2.Canvas),
                                                        OntologyConstants.IIIF.PresentationV2.HasImageAnnotations -> JsonLDArray(
                                                            Seq(
                                                                JsonLDObject(
                                                                    Map(
                                                                        JsonLDConstants.CONTEXT -> JsonLDString(OntologyConstants.IIIF.PresentationV2.ContextUrl),
                                                                        JsonLDConstants.TYPE -> JsonLDString(OntologyConstants.WebAnnotation.Annotation),
                                                                        OntologyConstants.WebAnnotation.HasBody -> JsonLDObject(
                                                                            Map(
                                                                                JsonLDConstants.ID -> JsonLDString(iiifUrl)
                                                                            )
                                                                        )
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                        }
                    )
                )
            )

            val jsonLDDoc = JsonLDDocument(
                body = body,
                context = JsonLDArray(Seq(JsonLDString(OntologyConstants.IIIF.PresentationV2.ContextUrl)))
            )

            val formattedDoc = jsonLDDoc.toPrettyString

            val expectedOutputDoc =
                """{
                  |  "@id" : "https://localhost:1025/server/manifest.json",
                  |  "@type" : "sc:Manifest",
                  |  "sc:hasSequences" : [ {
                  |    "@id" : "https://localhost:1025/server/sequence/normal",
                  |    "@type" : "sc:Sequence",
                  |    "sc:hasCanvases" : {
                  |      "@id" : "https://localhost:1025/server/canvas/bigcanvas1",
                  |      "@type" : "sc:Canvas",
                  |      "sc:hasImageAnnotations" : {
                  |        "@type" : "oa:Annotation",
                  |        "resource" : "http://example.org/1"
                  |      }
                  |    }
                  |  }, {
                  |    "@id" : "https://localhost:1025/server/sequence/normal",
                  |    "@type" : "sc:Sequence",
                  |    "sc:hasCanvases" : {
                  |      "@id" : "https://localhost:1025/server/canvas/bigcanvas1",
                  |      "@type" : "sc:Canvas",
                  |      "sc:hasImageAnnotations" : {
                  |        "@type" : "oa:Annotation",
                  |        "resource" : "http://example.org/2"
                  |      }
                  |    }
                  |  } ],
                  |  "label" : "TEST MANIFEST",
                  |  "@context" : "http://iiif.io/api/presentation/2/context.json"
                  |}""".stripMargin

            val receivedOutputAsJsValue: JsValue = JsonParser(formattedDoc)
            val expectedOutputAsJsValue: JsValue = JsonParser(expectedOutputDoc)
            receivedOutputAsJsValue should ===(expectedOutputAsJsValue)
        }
    }
}
