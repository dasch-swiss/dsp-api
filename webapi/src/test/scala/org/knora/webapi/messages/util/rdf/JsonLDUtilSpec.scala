/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

package org.knora.webapi.util.rdf

import java.nio.file.Paths

import org.knora.webapi.CoreSpec
import org.knora.webapi.feature._
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.util.FileUtil
import spray.json.{JsValue, JsonParser}

/**
  * Tests [[JsonLDUtil]].
  */
abstract class JsonLDUtilSpec(featureToggle: FeatureToggle) extends CoreSpec {
  private val featureFactoryConfig: FeatureFactoryConfig = new TestFeatureFactoryConfig(
    testToggles = Set(featureToggle),
    parent = new KnoraSettingsFeatureFactoryConfig(settings)
  )

  private val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(featureFactoryConfig)
  private val rdfModelFactory: RdfModelFactory = RdfFeatureFactory.getRdfModelFactory(featureFactoryConfig)

  "The JSON-LD tool" should {
    "parse JSON-LD text, compact it with an empty context, convert the result to a JsonLDDocument, and convert that back to text" in {
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

      val compactedJsonLDDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(ontologyJsonLDInputStr)
      val formattedCompactedDoc = compactedJsonLDDoc.toPrettyString()
      val receivedOutputAsJsValue: JsValue = JsonParser(formattedCompactedDoc)
      val expectedOutputAsJsValue: JsValue = JsonParser(ontologyCompactedJsonLDOutputStr)
      receivedOutputAsJsValue should ===(expectedOutputAsJsValue)
    }

    "convert JSON-LD representing an ontology to an RDF4J Model" in {
      // Read a JSON-LD file.
      val inputJsonLD: String =
        FileUtil.readTextFile(Paths.get("test_data/ontologyR2RV2/anythingOntologyWithValueObjects.jsonld"))

      // Parse it to a JsonLDDocument.
      val jsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(inputJsonLD)

      // Convert that document to an RDF4J Model.
      val outputModel: RdfModel = jsonLDDocument.toRdfModel(rdfModelFactory)

      // Read an isomorphic Turtle file.
      val expectedTurtle: String =
        FileUtil.readTextFile(Paths.get("test_data/ontologyR2RV2/anythingOntologyWithValueObjects.ttl"))

      // Parse the Turtle to an RDF4J Model.
      val expectedModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = expectedTurtle, rdfFormat = Turtle)

      // Compare the parsed Turtle with the model generated by the JsonLDDocument.
      outputModel should ===(expectedModel)
    }

    "convert an RDF4J Model representing an ontology to JSON-LD" in {
      // Read a Turtle file.
      val turtle = FileUtil.readTextFile(Paths.get("test_data/ontologyR2RV2/anythingOntologyWithValueObjects.ttl"))

      // Parse it to an RDF4J Model.
      val inputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = turtle, rdfFormat = Turtle)

      // Convert the model to a JsonLDDocument.
      val outputJsonLD: JsonLDDocument = JsonLDUtil.fromRdfModel(inputModel)

      // Convert the JsonLDDocument back to an RDF4J Model.
      val jsonLDOutputModel: RdfModel = outputJsonLD.toRdfModel(rdfModelFactory)

      // Compare the generated model with the original one.
      jsonLDOutputModel should ===(inputModel)

      // Read an isomorphic JSON-LD file.
      val expectedJsonLD =
        FileUtil.readTextFile(Paths.get("test_data/ontologyR2RV2/anythingOntologyWithValueObjects.jsonld"))

      // Parse it to an RDF4J Model.
      val jsonLDExpectedModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = expectedJsonLD, rdfFormat = JsonLD)

      // Compare that with the model generated by the JsonLDDocument.
      jsonLDOutputModel should ===(jsonLDExpectedModel)
    }

    "convert JSON-LD representing a resource to an RDF4J Model" in {
      // Read a JSON-LD file.
      val inputJsonLD: String =
        FileUtil.readTextFile(Paths.get("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"))

      // Parse it to a JsonLDDocument.
      val jsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(inputJsonLD)

      // Convert the document to an RDF4J Model.
      val outputModel: RdfModel = jsonLDDocument.toRdfModel(rdfModelFactory)

      // Convert the model back to a JsonLDDocument.
      val outputModelAsJsonLDDocument: JsonLDDocument = JsonLDUtil.fromRdfModel(outputModel)

      // Compare that with the original JsonLDDocument.
      outputModelAsJsonLDDocument should ===(jsonLDDocument)

      // Read an isomorphic Turtle file.
      val expectedTurtle = FileUtil.readTextFile(Paths.get("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"))

      // Parse it to an RDF4J Model.
      val expectedModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = expectedTurtle, rdfFormat = Turtle)

      // Compare that with the model generated by the JsonLDDocument.
      outputModel should ===(expectedModel)
    }

    "convert an RDF4J Model representing a resource to JSON-LD" in {
      // Read a Turtle file.
      val turtle = FileUtil.readTextFile(Paths.get("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"))

      // Parse it to an RDF4J Model.
      val inputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = turtle, rdfFormat = Turtle)

      // Convert the model to a JsonLDDocument.
      val outputJsonLD: JsonLDDocument = JsonLDUtil.fromRdfModel(inputModel)

      // Convert the JsonLDDocument back to an RDF4J Model.
      val jsonLDOutputModel: RdfModel = outputJsonLD.toRdfModel(rdfModelFactory)

      // Compare the generated model with the original one.
      jsonLDOutputModel should ===(inputModel)

      // Read an isomorphic JSON-LD file.
      val expectedJsonLD = FileUtil.readTextFile(Paths.get("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"))

      // Parse it to a JsonLDDocument and compare it with the generated one.
      val expectedJsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(expectedJsonLD)
      expectedJsonLDDocument.body should ===(outputJsonLD.body)

      // Parse the same file to an RDF4J Model.
      val jsonLDExpectedModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = expectedJsonLD, rdfFormat = JsonLD)

      // Compare that with the model generated by the JsonLDDocument.
      jsonLDOutputModel should ===(jsonLDExpectedModel)
    }

    "correctly convert an RDF model to JSON-LD if it contains a circular reference" in {
      // A Turtle document with a circular reference.
      val turtle =
        """@prefix foo: <http://example.org/foo#> .
                  |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                  |
                  |<http://rdfh.ch/resources/foo1> a foo:Foo;
                  |  rdfs:label "foo 1";
                  |  foo:hasOtherFoo <http://rdfh.ch/resources/foo2>.
                  |
                  |<http://rdfh.ch/resources/foo2> a foo:Foo;
                  |  rdfs:label "foo 2";
                  |  foo:hasOtherFoo <http://rdfh.ch/resources/foo1>.
                  |""".stripMargin

      // Parse it to an RDF4J Model.
      val inputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = turtle, rdfFormat = Turtle)

      // Convert the model to a JsonLDDocument.
      val outputJsonLD: JsonLDDocument = JsonLDUtil.fromRdfModel(inputModel)

      // The output could be nested in two different ways:

      val expectedWithFoo1AtTopLevel = JsonLDObject(
        value = Map(
          "@id" -> JsonLDString(value = "http://rdfh.ch/resources/foo1"),
          "@type" -> JsonLDString(value = "http://example.org/foo#Foo"),
          "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 1"),
          "http://example.org/foo#hasOtherFoo" -> JsonLDObject(value = Map(
            "@id" -> JsonLDString(value = "http://rdfh.ch/resources/foo2"),
            "@type" -> JsonLDString(value = "http://example.org/foo#Foo"),
            "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 2"),
            "http://example.org/foo#hasOtherFoo" -> JsonLDObject(
              value = Map("@id" -> JsonLDString(value = "http://rdfh.ch/resources/foo1"))),
          )),
        ))

      val expectedWithFoo2AtTopLevel = JsonLDObject(
        value = Map(
          "@id" -> JsonLDString(value = "http://rdfh.ch/resources/foo2"),
          "@type" -> JsonLDString(value = "http://example.org/foo#Foo"),
          "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 2"),
          "http://example.org/foo#hasOtherFoo" -> JsonLDObject(value = Map(
            "@id" -> JsonLDString(value = "http://rdfh.ch/resources/foo1"),
            "@type" -> JsonLDString(value = "http://example.org/foo#Foo"),
            "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 1"),
            "http://example.org/foo#hasOtherFoo" -> JsonLDObject(
              value = Map("@id" -> JsonLDString(value = "http://rdfh.ch/resources/foo2")))
          ))
        ))

      assert(
        outputJsonLD.body == expectedWithFoo1AtTopLevel ||
          outputJsonLD.body == expectedWithFoo2AtTopLevel
      )
    }

    "convert an RDF model to flat or hierarchical JSON-LD" in {
      // A simple Turtle document.
      val turtle =
        """@prefix foo: <http://example.org/foo#> .
                  |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                  |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                  |
                  |<http://rdfh.ch/resources/foo1> a foo:Foo;
                  |  rdfs:label "foo 1";
                  |  foo:hasBar [
                  |    a foo:Bar;
                  |    rdfs:label "bar 1"
                  |  ];
                  |  foo:hasOtherFoo <http://rdfh.ch/resources/foo2>.
                  |
                  |<http://rdfh.ch/resources/foo2> a foo:Foo;
                  |  rdfs:label "foo 2";
                  |  foo:hasIndex "3"^^xsd:integer;
                  |  foo:hasBar [
                  |    a foo:Bar;
                  |    rdfs:label "bar 2"
                  |  ].
                  |""".stripMargin

      // Parse it to an RDF4J Model.
      val inputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = turtle, rdfFormat = Turtle)

      // Convert the model to a hierarchical JsonLDDocument.
      val hierarchicalJsonLD: JsonLDDocument = JsonLDUtil.fromRdfModel(model = inputModel, flatJsonLD = false)

      val expectedHierarchicalJsonLD = JsonLDObject(
        value = Map(
          "@id" -> JsonLDString(value = "http://rdfh.ch/resources/foo1"),
          "@type" -> JsonLDString(value = "http://example.org/foo#Foo"),
          "http://example.org/foo#hasBar" -> JsonLDObject(
            value = Map(
              "@type" -> JsonLDString(value = "http://example.org/foo#Bar"),
              "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "bar 1")
            )),
          "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 1"),
          "http://example.org/foo#hasOtherFoo" -> JsonLDObject(value = Map(
            "@id" -> JsonLDString(value = "http://rdfh.ch/resources/foo2"),
            "@type" -> JsonLDString(value = "http://example.org/foo#Foo"),
            "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 2"),
            "http://example.org/foo#hasIndex" -> JsonLDInt(value = 3),
            "http://example.org/foo#hasBar" -> JsonLDObject(value = Map(
              "@type" -> JsonLDString(value = "http://example.org/foo#Bar"),
              "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "bar 2")
            ))
          ))
        ))

      assert(hierarchicalJsonLD.body == expectedHierarchicalJsonLD)

      // Convert the model to a flat JsonLDDocument.
      val flatJsonLD: JsonLDDocument = JsonLDUtil.fromRdfModel(model = inputModel, flatJsonLD = true)

      val expectedFlatJsonLD = JsonLDObject(
        value = Map("@graph" -> JsonLDArray(value = Vector(
          JsonLDObject(value = Map(
            "@id" -> JsonLDString(value = "http://rdfh.ch/resources/foo1"),
            "@type" -> JsonLDString(value = "http://example.org/foo#Foo"),
            "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 1"),
            "http://example.org/foo#hasBar" -> JsonLDObject(value = Map(
              "@type" -> JsonLDString(value = "http://example.org/foo#Bar"),
              "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "bar 1")
            )),
            "http://example.org/foo#hasOtherFoo" -> JsonLDObject(value =
              Map("@id" -> JsonLDString(value = "http://rdfh.ch/resources/foo2")))
          )),
          JsonLDObject(value = Map(
            "@id" -> JsonLDString(value = "http://rdfh.ch/resources/foo2"),
            "@type" -> JsonLDString(value = "http://example.org/foo#Foo"),
            "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "foo 2"),
            "http://example.org/foo#hasIndex" -> JsonLDInt(value = 3),
            "http://example.org/foo#hasBar" -> JsonLDObject(value = Map(
              "http://www.w3.org/2000/01/rdf-schema#label" -> JsonLDString(value = "bar 2"),
              "@type" -> JsonLDString(value = "http://example.org/foo#Bar")
            ))
          ))
        ))))

      assert(flatJsonLD.body == expectedFlatJsonLD)
    }

    "correctly process input that results in an empty blank node" in {
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
        context = JsonLDObject(Map.empty)
      )

      assert(jsonLDDocument == expectedJsonLDDocument)

      // Convert it to an RdfModel and check the result.
      val rdfModel = jsonLDDocument.toRdfModel(rdfModelFactory)
      val expectedRdfModel =
        rdfFormatUtil.parseToRdfModel("[] <http://ns.dasch.swiss/repository#hasLicense> [] .", Turtle)
      assert(rdfModel == expectedRdfModel)

      // Convert back to JSON-LD and check that it's the same.
      val jsonLDDocumentFromRdfModel = JsonLDUtil.fromRdfModel(rdfModel)
      assert(jsonLDDocumentFromRdfModel == expectedJsonLDDocument)
    }
  }
}
