/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.shacl

import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.nio.file.Files
import java.nio.file.Path

object ShaclValidatorSpec extends ZIOSpecDefault {

  private val validator = ShaclValidator

  /** Minimal knora-base schema — just enough for the SHACL shapes to work. */
  private val minimalSchema =
    """@prefix rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
      |@prefix rdfs:       <http://www.w3.org/2000/01/rdf-schema#> .
      |@prefix owl:        <http://www.w3.org/2002/07/owl#> .
      |@prefix xsd:        <http://www.w3.org/2001/XMLSchema#> .
      |@prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
      |
      |knora-base:Resource           a owl:Class .
      |knora-base:hasValue           a rdf:Property .
      |knora-base:hasLinkTo          a rdf:Property .
      |knora-base:TextValue          a owl:Class .
      |knora-base:objectClassConstraint    a rdf:Property .
      |knora-base:objectDatatypeConstraint a rdf:Property .
      |knora-base:attachedToProject        a rdf:Property .
      |knora-base:lastModificationDate     a rdf:Property .
      |""".stripMargin

  private val prefixes =
    """@prefix :           <http://www.knora.org/ontology/0001/test#> .
      |@prefix xsd:        <http://www.w3.org/2001/XMLSchema#> .
      |@prefix rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
      |@prefix rdfs:       <http://www.w3.org/2000/01/rdf-schema#> .
      |@prefix owl:        <http://www.w3.org/2002/07/owl#> .
      |@prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
      |@base <http://www.knora.org/ontology/0001/test> .
      |""".stripMargin

  private val validOntologyHeader =
    """<http://www.knora.org/ontology/0001/test>
      |    rdf:type                        owl:Ontology ;
      |    rdfs:label                      "Test ontology" ;
      |    knora-base:attachedToProject    <http://rdfh.ch/projects/0001> ;
      |    knora-base:lastModificationDate "2024-01-01T00:00:00Z"^^xsd:dateTime .
      |""".stripMargin

  private def writeTempTtl(content: String): ZIO[Scope, Throwable, Path] =
    ZIO.acquireRelease(
      ZIO.attemptBlocking {
        val tmp = Files.createTempFile("shacl-test-", ".ttl")
        Files.writeString(tmp, content)
        tmp
      },
    )(path => ZIO.attemptBlocking(Files.deleteIfExists(path)).ignoreLogged)

  private def shapesPath: Path =
    Path.of(getClass.getClassLoader.getResource("shacl/ontology-shapes.ttl").toURI)

  private def validate(ontologyTtl: String, schemaTtl: String = minimalSchema) =
    ZIO.scoped {
      for {
        schemaPath <- writeTempTtl(schemaTtl)
        dataPath   <- writeTempTtl(ontologyTtl)
        result     <- validator
                    .validate(
                      NonEmptyChunk(schemaPath),
                      NonEmptyChunk(dataPath),
                      NonEmptyChunk(shapesPath),
                    )
                    .either
      } yield result
    }

  def spec: Spec[Any, Any] = suite("ShaclValidatorSpec")(
    suite("ontology validation")(
      test("valid minimal ontology conforms") {
        val ttl = prefixes + validOntologyHeader
        validate(ttl).map(result => assert(result)(isRight))
      },
      test("ontology missing rdfs:label fails") {
        val ttl = prefixes +
          """<http://www.knora.org/ontology/0001/test>
            |    rdf:type                        owl:Ontology ;
            |    knora-base:attachedToProject    <http://rdfh.ch/projects/0001> ;
            |    knora-base:lastModificationDate "2024-01-01T00:00:00Z"^^xsd:dateTime .
            |""".stripMargin
        validate(ttl).map(result => assert(result)(isLeft))
      },
      test("ontology missing knora-base:attachedToProject fails") {
        val ttl = prefixes +
          """<http://www.knora.org/ontology/0001/test>
            |    rdf:type                        owl:Ontology ;
            |    rdfs:label                      "Test ontology" ;
            |    knora-base:lastModificationDate "2024-01-01T00:00:00Z"^^xsd:dateTime .
            |""".stripMargin
        validate(ttl).map(result => assert(result)(isLeft))
      },
      test("ontology missing knora-base:lastModificationDate fails") {
        val ttl = prefixes +
          """<http://www.knora.org/ontology/0001/test>
            |    rdf:type                        owl:Ontology ;
            |    rdfs:label                      "Test ontology" ;
            |    knora-base:attachedToProject    <http://rdfh.ch/projects/0001> .
            |""".stripMargin
        validate(ttl).map(result => assert(result)(isLeft))
      },
    ),
    suite("resource class validation")(
      test("resource class with rdfs:label conforms") {
        val ttl = prefixes + validOntologyHeader +
          """:TestThing
            |    rdf:type        owl:Class ;
            |    rdfs:subClassOf knora-base:Resource ;
            |    rdfs:label      "Test thing"@en .
            |""".stripMargin
        validate(ttl).map(result => assert(result)(isRight))
      },
      test("resource class missing rdfs:label fails") {
        val ttl = prefixes + validOntologyHeader +
          """:TestThing
            |    rdf:type        owl:Class ;
            |    rdfs:subClassOf knora-base:Resource .
            |""".stripMargin
        validate(ttl).map(result => assert(result)(isLeft))
      },
    ),
    suite("property validation")(
      test("property with objectClassConstraint conforms") {
        val ttl = prefixes + validOntologyHeader +
          """:hasTestValue
            |    rdf:type                         owl:ObjectProperty ;
            |    rdfs:label                       "has test value"@en ;
            |    rdfs:subPropertyOf               knora-base:hasValue ;
            |    knora-base:objectClassConstraint knora-base:TextValue .
            |""".stripMargin
        validate(ttl).map(result => assert(result)(isRight))
      },
      test("property missing objectClassConstraint and objectDatatypeConstraint fails") {
        val ttl = prefixes + validOntologyHeader +
          """:hasTestValue
            |    rdf:type           owl:ObjectProperty ;
            |    rdfs:label         "has test value"@en ;
            |    rdfs:subPropertyOf knora-base:hasValue .
            |""".stripMargin
        validate(ttl).map(result => assert(result)(isLeft))
      },
      test("property missing rdfs:label fails") {
        val ttl = prefixes + validOntologyHeader +
          """:hasTestValue
            |    rdf:type                         owl:ObjectProperty ;
            |    rdfs:subPropertyOf               knora-base:hasValue ;
            |    knora-base:objectClassConstraint knora-base:TextValue .
            |""".stripMargin
        validate(ttl).map(result => assert(result)(isLeft))
      },
    ),
    suite("RDFS inference")(
      test("indirect subclass of knora-base:Resource is validated") {
        val ttl = prefixes + validOntologyHeader +
          """:ParentThing
            |    rdf:type        owl:Class ;
            |    rdfs:subClassOf knora-base:Resource ;
            |    rdfs:label      "Parent thing"@en .
            |
            |:ChildThing
            |    rdf:type        owl:Class ;
            |    rdfs:subClassOf :ParentThing .
            |""".stripMargin
        // ChildThing is an indirect subclass of knora-base:Resource (via ParentThing)
        // and is missing rdfs:label — should fail
        validate(ttl).map(result => assert(result)(isLeft))
      },
      test("indirect subclass of knora-base:Resource with label conforms") {
        val ttl = prefixes + validOntologyHeader +
          """:ParentThing
            |    rdf:type        owl:Class ;
            |    rdfs:subClassOf knora-base:Resource ;
            |    rdfs:label      "Parent thing"@en .
            |
            |:ChildThing
            |    rdf:type        owl:Class ;
            |    rdfs:subClassOf :ParentThing ;
            |    rdfs:label      "Child thing"@en .
            |""".stripMargin
        validate(ttl).map(result => assert(result)(isRight))
      },
      test("indirect subproperty of knora-base:hasValue is validated") {
        val ttl = prefixes + validOntologyHeader +
          """:hasBaseValue
            |    rdf:type                         owl:ObjectProperty ;
            |    rdfs:label                       "has base value"@en ;
            |    rdfs:subPropertyOf               knora-base:hasValue ;
            |    knora-base:objectClassConstraint knora-base:TextValue .
            |
            |:hasSpecialValue
            |    rdf:type           owl:ObjectProperty ;
            |    rdfs:subPropertyOf :hasBaseValue .
            |""".stripMargin
        // hasSpecialValue is an indirect subproperty of knora-base:hasValue
        // and is missing rdfs:label + constraint — should fail
        validate(ttl).map(result => assert(result)(isLeft))
      },
      test("indirect subproperty of knora-base:hasValue with all required fields conforms") {
        val ttl = prefixes + validOntologyHeader +
          """:hasBaseValue
            |    rdf:type                         owl:ObjectProperty ;
            |    rdfs:label                       "has base value"@en ;
            |    rdfs:subPropertyOf               knora-base:hasValue ;
            |    knora-base:objectClassConstraint knora-base:TextValue .
            |
            |:hasSpecialValue
            |    rdf:type                         owl:ObjectProperty ;
            |    rdfs:label                       "has special value"@en ;
            |    rdfs:subPropertyOf               :hasBaseValue ;
            |    knora-base:objectClassConstraint knora-base:TextValue .
            |""".stripMargin
        validate(ttl).map(result => assert(result)(isRight))
      },
    ),
  )
}
