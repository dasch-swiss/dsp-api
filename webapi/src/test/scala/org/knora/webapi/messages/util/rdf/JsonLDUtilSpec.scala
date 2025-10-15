/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf

import zio.*
import zio.json.*
import zio.json.ast.*
import zio.test.*

import java.nio.file.Paths

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.util.FileUtil

object JsonLDUtilSpec extends ZIOSpecDefault {
  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  def spec = suite("The JSON-LD tool")(
    test(
      "parse JSON-LD text, compact it with an empty context, convert the result to a JsonLDDocument, and convert that back to text",
    ) {
      val ontologyJsonLDInputStr =
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

      val ontologyCompactedJsonLDOutputStr =
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

      val actual                  = JsonLDUtil.parseJsonLD(ontologyJsonLDInputStr)
      val receivedOutputAsJsValue = actual.toPrettyString().fromJson[Json]
      val expectedOutputAsJsValue = ontologyCompactedJsonLDOutputStr.fromJson[Json]
      assertTrue(receivedOutputAsJsValue == expectedOutputAsJsValue)
    },
    test("convert JSON-LD representing an ontology to an RDF4J Model") {
      // Read a JSON-LD file.
      val inputJsonLD: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/ontologyR2RV2/anythingOntologyWithValueObjects.jsonld"),
        )

      // Parse it to a JsonLDDocument.
      val jsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(inputJsonLD)

      // Convert that document to an RDF4J Model.
      val outputModel: RdfModel = jsonLDDocument.toRdfModel

      // Read an isomorphic Turtle file.
      val expectedTurtle: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/ontologyR2RV2/anythingOntologyWithValueObjects.ttl"),
        )

      // Parse the Turtle to an RDF4J Model.
      val expectedModel: RdfModel = RdfModel.fromTurtle(expectedTurtle)

      // Compare the parsed Turtle with the model generated by the JsonLDDocument.
      assertTrue(outputModel == expectedModel)
    },
    test("convert an RDF4J Model representing an ontology to JSON-LD") {
      // Read a Turtle file.
      val turtle =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/ontologyR2RV2/anythingOntologyWithValueObjects.ttl"),
        )

      // Parse it to an RDF4J Model.
      val inputModel: RdfModel = RdfModel.fromTurtle(turtle)

      // Convert the model to a JsonLDDocument.
      val outputJsonLD: JsonLDDocument = JsonLDUtil.fromRdfModel(inputModel)

      // Convert the JsonLDDocument back to an RDF4J Model.
      val jsonLDOutputModel: RdfModel = outputJsonLD.toRdfModel

      // Compare the generated model with the original one.

      // Read an isomorphic JSON-LD file.
      val expectedJsonLD =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/ontologyR2RV2/anythingOntologyWithValueObjects.jsonld"),
        )

      // Parse it to an RDF4J Model.
      val jsonLDExpectedModel: RdfModel = RdfModel.fromJsonLD(expectedJsonLD)

      // Compare that with the model generated by the JsonLDDocument.
      assertTrue(jsonLDOutputModel == jsonLDExpectedModel, jsonLDOutputModel == inputModel)
    },
    test("convert JSON-LD representing a resource to an RDF4J Model") {
      // Read a JSON-LD file.
      val inputJsonLD: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"),
        )

      // Parse it to a JsonLDDocument.
      val jsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(inputJsonLD)

      // Convert the document to an RDF4J Model.
      val outputModel: RdfModel = jsonLDDocument.toRdfModel

      // Convert the model back to a JsonLDDocument.
      val outputModelAsJsonLDDocument: JsonLDDocument = JsonLDUtil.fromRdfModel(outputModel)

      // Read an isomorphic Turtle file.
      val expectedTurtle =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"),
        )

      // Parse it to an RDF4J Model.
      val expectedModel = RdfModel.fromTurtle(expectedTurtle)

      // Compare that with the model generated by the JsonLDDocument.
      assertTrue(outputModel == expectedModel, outputModelAsJsonLDDocument == jsonLDDocument)
    },
    test("convert an RDF4J Model representing a resource to JSON-LD") {
      // Read a Turtle file.
      val turtle = FileUtil.readTextFile(
        Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"),
      )

      // Parse it to an RDF4J Model.
      val inputModel = RdfModel.fromTurtle(turtle)

      // Convert the model to a JsonLDDocument.
      val outputJsonLD: JsonLDDocument = JsonLDUtil.fromRdfModel(inputModel)

      // Convert the JsonLDDocument back to an RDF4J Model.
      val jsonLDOutputModel: RdfModel = outputJsonLD.toRdfModel

      // Read an isomorphic JSON-LD file.
      val expectedJsonLD =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"),
        )

      // Parse it to a JsonLDDocument and compare it with the generated one.
      val expectedJsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(expectedJsonLD)

      // Parse the same file to an RDF4J Model.
      val jsonLDExpectedModel = RdfModel.fromJsonLD(expectedJsonLD)

      // Compare that with the model generated by the JsonLDDocument.
      assertTrue(
        jsonLDOutputModel == jsonLDExpectedModel,
        jsonLDOutputModel == inputModel,
        expectedJsonLDDocument.body == outputJsonLD.body,
      )
    },
    test("correctly convert an RDF model to JSON-LD if it contains a circular reference") {
      // A Turtle document with a circular reference.
      val turtle =
        """@prefix foo: <http://example.org/foo#> .
          |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
          |
          |<http://rdfh.ch/foo1> a foo:Foo;
          |  rdfs:label "foo 1";
          |  foo:hasOtherFoo <http://rdfh.ch/foo2>.
          |
          |<http://rdfh.ch/foo2> a foo:Foo;
          |  rdfs:label "foo 2";
          |  foo:hasOtherFoo <http://rdfh.ch/foo1>.
          |""".stripMargin

      // Parse it to an RDF4J Model.
      val inputModel = RdfModel.fromTurtle(turtle)

      // Convert the model to a JsonLDDocument.
      val outputJsonLD: JsonLDDocument = JsonLDUtil.fromRdfModel(inputModel)

      // The output could be nested in two different ways:

      val expectedWithFoo1AtTopLevel = JsonLDObject(
        value = Map(
          "@id"                                        -> JsonLDString(value = "http://rdfh.ch/foo1"),
          "@type"                                      -> JsonLDString(value = "http://example.org/foo#Foo"),
          "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 1"),
          "http://example.org/foo#hasOtherFoo" -> JsonLDObject(value =
            Map(
              "@id"                                        -> JsonLDString(value = "http://rdfh.ch/foo2"),
              "@type"                                      -> JsonLDString(value = "http://example.org/foo#Foo"),
              "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 2"),
              "http://example.org/foo#hasOtherFoo" -> JsonLDObject(
                value = Map("@id" -> JsonLDString(value = "http://rdfh.ch/foo1")),
              ),
            ),
          ),
        ),
      )

      val expectedWithFoo2AtTopLevel = JsonLDObject(
        value = Map(
          "@id"                                        -> JsonLDString(value = "http://rdfh.ch/foo2"),
          "@type"                                      -> JsonLDString(value = "http://example.org/foo#Foo"),
          "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 2"),
          "http://example.org/foo#hasOtherFoo" -> JsonLDObject(value =
            Map(
              "@id"                                        -> JsonLDString(value = "http://rdfh.ch/foo1"),
              "@type"                                      -> JsonLDString(value = "http://example.org/foo#Foo"),
              "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 1"),
              "http://example.org/foo#hasOtherFoo" -> JsonLDObject(
                value = Map("@id" -> JsonLDString(value = "http://rdfh.ch/foo2")),
              ),
            ),
          ),
        ),
      )

      assertTrue(
        outputJsonLD.body == expectedWithFoo1AtTopLevel ||
          outputJsonLD.body == expectedWithFoo2AtTopLevel,
      )
    },
    test("convert an RDF model to flat or hierarchical JSON-LD") {
      // A simple Turtle document.
      val turtle =
        """@prefix foo: <http://example.org/foo#> .
          |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
          |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
          |
          |<http://rdfh.ch/foo1> a foo:Foo;
          |  rdfs:label "foo 1";
          |  foo:hasBar [
          |    a foo:Bar;
          |    rdfs:label "bar 1"
          |  ];
          |  foo:hasOtherFoo <http://rdfh.ch/foo2>.
          |
          |<http://rdfh.ch/foo2> a foo:Foo;
          |  rdfs:label "foo 2";
          |  foo:hasIndex "3"^^xsd:integer;
          |  foo:hasBar [
          |    a foo:Bar;
          |    rdfs:label "bar 2"
          |  ].
          |""".stripMargin

      // Parse it to an RDF4J Model.
      val inputModel = RdfModel.fromTurtle(turtle)

      // Convert the model to a hierarchical JsonLDDocument.
      val hierarchicalJsonLD: JsonLDDocument = JsonLDUtil.fromRdfModel(model = inputModel, flatJsonLD = false)

      val expectedHierarchicalJsonLD = JsonLDObject(
        value = Map(
          "@id"   -> JsonLDString(value = "http://rdfh.ch/foo1"),
          "@type" -> JsonLDString(value = "http://example.org/foo#Foo"),
          "http://example.org/foo#hasBar" -> JsonLDObject(
            value = Map(
              "@type"                                      -> JsonLDString(value = "http://example.org/foo#Bar"),
              "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "bar 1"),
            ),
          ),
          "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 1"),
          "http://example.org/foo#hasOtherFoo" -> JsonLDObject(value =
            Map(
              "@id"                                        -> JsonLDString(value = "http://rdfh.ch/foo2"),
              "@type"                                      -> JsonLDString(value = "http://example.org/foo#Foo"),
              "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 2"),
              "http://example.org/foo#hasIndex"            -> JsonLDInt(value = 3),
              "http://example.org/foo#hasBar" -> JsonLDObject(value =
                Map(
                  "@type"                                      -> JsonLDString(value = "http://example.org/foo#Bar"),
                  "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "bar 2"),
                ),
              ),
            ),
          ),
        ),
      )

      // Convert the model to a flat JsonLDDocument.
      val flatJsonLD: JsonLDDocument = JsonLDUtil.fromRdfModel(model = inputModel, flatJsonLD = true)

      val expectedFlatJsonLD = JsonLDObject(
        value = Map(
          "@graph" -> JsonLDArray(value =
            Vector(
              JsonLDObject(value =
                Map(
                  "@id"                                        -> JsonLDString(value = "http://rdfh.ch/foo1"),
                  "@type"                                      -> JsonLDString(value = "http://example.org/foo#Foo"),
                  "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 1"),
                  "http://example.org/foo#hasBar" -> JsonLDObject(value =
                    Map(
                      "@type"                                      -> JsonLDString(value = "http://example.org/foo#Bar"),
                      "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "bar 1"),
                    ),
                  ),
                  "http://example.org/foo#hasOtherFoo" -> JsonLDObject(value =
                    Map("@id" -> JsonLDString(value = "http://rdfh.ch/foo2")),
                  ),
                ),
              ),
              JsonLDObject(value =
                Map(
                  "@id"                                        -> JsonLDString(value = "http://rdfh.ch/foo2"),
                  "@type"                                      -> JsonLDString(value = "http://example.org/foo#Foo"),
                  "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 2"),
                  "http://example.org/foo#hasIndex"            -> JsonLDInt(value = 3),
                  "http://example.org/foo#hasBar" -> JsonLDObject(value =
                    Map(
                      "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "bar 2"),
                      "@type"                                      -> JsonLDString(value = "http://example.org/foo#Bar"),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      )
      assertTrue(flatJsonLD.body == expectedFlatJsonLD, hierarchicalJsonLD.body == expectedHierarchicalJsonLD)
    },
    test("correctly process input that results in an empty blank node") {
      // The JSON-LD parser ignores statements with invalid IRIs, and this can produce an empty
      // blank node.

      val jsonLDWithInvalidProperties =
        """{
          |   "http://ns.dasch.swiss/repository#hasLicense":{
          |      "type": "https://schema.org/URL",
          |      "value": "https://creativecommons.org/licenses/by/3.0"
          |   }
          |}""".stripMargin

      // Parse the JSON-LD and check the parsed data structure.

      val jsonLDDocument = JsonLDUtil.parseJsonLD(jsonLDWithInvalidProperties)

      val expectedJsonLDDocument = JsonLDDocument(
        body = JsonLDObject(Map("http://ns.dasch.swiss/repository#hasLicense" -> JsonLDObject(Map.empty))),
        context = JsonLDObject(Map.empty),
      )

      // Convert it to an RdfModel and check the result.
      val rdfModel         = jsonLDDocument.toRdfModel
      val expectedRdfModel = RdfModel.fromTurtle("[] <http://ns.dasch.swiss/repository#hasLicense> [] .")

      // Convert back to JSON-LD and check that it's the same.
      val jsonLDDocumentFromRdfModel = JsonLDUtil.fromRdfModel(rdfModel)
      assertTrue(
        jsonLDDocumentFromRdfModel == expectedJsonLDDocument,
        jsonLDDocument == expectedJsonLDDocument,
        rdfModel == expectedRdfModel,
      )
    },
  )
}
