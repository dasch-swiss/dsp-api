/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.rdf

import java.nio.file.Paths

import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.messages.v2.responder.KnoraJsonLDResponseV2
import org.knora.webapi.messages.v2.responder.KnoraTurtleResponseV2
import org.knora.webapi.util.FileUtil

/**
 * Tests the formatting of Knora API v2 responses.
 */
class KnoraResponseV2Spec() extends E2ESpec {

  private val turtle =
    """<http://rdfh.ch/foo1> a <http://example.org/foo#Foo>;
      |  <http://example.org/foo#hasBar> [ a <http://example.org/foo#Bar>;
      |      <http://www.w3.org/2000/01/rdf-schema#label> "bar 1"
      |    ];
      |  <http://example.org/foo#hasOtherFoo> <http://rdfh.ch/foo2>;
      |  <http://www.w3.org/2000/01/rdf-schema#label> "foo 1" .
      |
      |<http://rdfh.ch/foo2> a <http://example.org/foo#Foo>;
      |  <http://example.org/foo#hasBar> [ a <http://example.org/foo#Bar>;
      |      <http://www.w3.org/2000/01/rdf-schema#label> "bar 2"
      |    ];
      |  <http://example.org/foo#hasIndex> 3;
      |  <http://www.w3.org/2000/01/rdf-schema#label> "foo 2" .""".stripMargin

  private val hierarchicalJsonLD = JsonLDDocument(
    JsonLDObject(
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
    ),
  )

  private val flatJsonLD = JsonLDDocument(
    JsonLDObject(
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
                "http://example.org/foo#hasOtherFoo" -> JsonLDObject(
                  value = Map("@id" -> JsonLDString(value = "http://rdfh.ch/foo2")),
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
    ),
    isFlat = true,
  )

  /**
   * A test implementation of [[KnoraTurtleResponseV2]].
   */
  case class TurtleTestResponse(turtle: String) extends KnoraTurtleResponseV2

  /**
   * A test implementation of [[KnoraJsonLDResponseV2]].
   */
  case class JsonLDTestResponse(jsonLDDocument: JsonLDDocument) extends KnoraJsonLDResponseV2 {

    override protected def toJsonLDDocument(
      targetSchema: ApiV2Schema,
      appConfig: AppConfig,
      schemaOptions: Set[Rendering],
    ): JsonLDDocument = jsonLDDocument
  }

  "KnoraResponseV2" should {
    "convert Turtle to JSON-LD" in {
      // Read a Turtle file representing a resource. TODO: Use sample project metadata for this test.
      val turtle: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"),
        )

      // Wrap it in a KnoraTurtleResponseV2.
      val turtleTestResponse = TurtleTestResponse(turtle)

      // Ask the KnoraTurtleResponseV2 to convert the content to JSON-LD.
      val jsonLD: String = turtleTestResponse.format(
        rdfFormat = JsonLD,
        targetSchema = InternalSchema,
        schemaOptions = Set.empty,
        appConfig = appConfig,
      )

      // Parse the JSON-LD to a JsonLDDocument.
      val parsedJsonLD: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonLD)

      // Read an isomorphic JSON-LD file and parse it to a JsonLDDocument.
      val expectedJsonLD: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"),
        )
      val parsedExpectedJsonLD: JsonLDDocument = JsonLDUtil.parseJsonLD(expectedJsonLD)

      // Compare the two documents.
      parsedJsonLD.body should ===(parsedExpectedJsonLD.body)
    }

    "convert JSON-LD to Turtle" in {
      // Read a JSON-LD file representing a resource.
      val jsonLD: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"),
        )

      // Wrap it in a KnoraJsonLDResponseV2.
      val jsonLDTestResponse = JsonLDTestResponse(JsonLDUtil.parseJsonLD(jsonLD))

      // Ask the KnoraJsonLDResponseV2 to convert the content to Turtle.
      val turtle: String = jsonLDTestResponse.format(
        rdfFormat = Turtle,
        targetSchema = ApiV2Complex,
        schemaOptions = Set.empty,
        appConfig = appConfig,
      )

      // Parse the Turtle to an RDF4J Model.
      val parsedTurtle: RdfModel = RdfModel.fromTurtle(turtle)

      // Read an isomorphic Turtle file and parse it to an RDF4J Model.
      val expectedTurtle: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"),
        )
      val parsedExpectedTurtle: RdfModel = RdfModel.fromTurtle(expectedTurtle)

      // Compare the two models.
      parsedTurtle should ===(parsedExpectedTurtle)
    }

    "convert a hierarchical JsonLDDocument to a flat one" in {
      val actual = JsonLDTestResponse(hierarchicalJsonLD)
        .format(JsonLD, ApiV2Complex, Set(JsonLdRendering.Flat), appConfig)

      assert(JsonLDUtil.parseJsonLD(actual).body == flatJsonLD.body)
    }

    "convert Turtle to a hierarchical JSON-LD document" in {
      val actual = TurtleTestResponse(turtle)
        .format(JsonLD, InternalSchema, Set(JsonLdRendering.Hierarchical), appConfig)

      assert(JsonLDUtil.parseJsonLD(actual).body == hierarchicalJsonLD.body)
    }

    "convert Turtle to a flat JSON-LD document" in {
      val actual = TurtleTestResponse(turtle)
        .format(JsonLD, InternalSchema, Set(JsonLdRendering.Flat), appConfig)

      assert(JsonLDUtil.parseJsonLD(actual).body == flatJsonLD.body)
    }
  }
}
