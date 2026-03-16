/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.shacl

import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.nio.file.Path

object ShaclValidatorSpec extends ZIOSpecDefault {

  private val schemaGraphIri = "http://www.knora.org/ontology/knora-base"
  private val ontoGraphIri   = "http://www.knora.org/ontology/0001/test"
  private val dataGraphIri   = "http://example.org/data#"

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

  private def shapesPath: Path =
    Path.of(getClass.getClassLoader.getResource("shacl/ontology-shapes.ttl").toURI)

  /** No-op shapes — contains no SHACL shape definitions, so validation always conforms. */
  private val noOpShapes = RdfData.InMemoryTurtle("", "urn:no-op-shapes")

  private def ontologyShapes = ShaclShapes(
    ontologyShapes = NonEmptyChunk(RdfData.TurtleFile(shapesPath, "urn:shapes")),
    dataShapes = NonEmptyChunk(noOpShapes),
  )

  private def validate(ontologyTtl: String, schemaTtl: String = minimalSchema) =
    ShaclValidator
      .validate(
        graphs = RdfGraphs(
          ontologies = NonEmptyChunk(
            RdfData.InMemoryTurtle(schemaTtl, schemaGraphIri),
            RdfData.InMemoryTurtle(ontologyTtl, ontoGraphIri),
          ),
          data = NonEmptyChunk(RdfData.InMemoryTurtle("", "urn:dummy")),
        ),
        shapes = ontologyShapes,
      )
      .either

  /** Convert Turtle triples into NQuad lines within a named graph. */
  private def ttlToNQuads(ttl: String, graphIri: String): String = {
    val model = org.apache.jena.rdf.model.ModelFactory.createDefaultModel()
    org.apache.jena.riot.RDFDataMgr.read(model, new java.io.StringReader(ttl), null, org.apache.jena.riot.Lang.TURTLE)
    val sw      = new java.io.StringWriter()
    val dataset = org.apache.jena.query.DatasetFactory.create()
    dataset.addNamedModel(graphIri, model)
    org.apache.jena.riot.RDFDataMgr.write(sw, dataset, org.apache.jena.riot.Lang.NQUADS)
    sw.toString
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
    suite("NQuad input")(
      test("ontology provided as NQuads conforms") {
        val ttl = prefixes + validOntologyHeader
        val nq  = ttlToNQuads(ttl, ontoGraphIri)
        ShaclValidator
          .validate(
            graphs = RdfGraphs(
              ontologies = NonEmptyChunk(
                RdfData.InMemoryTurtle(minimalSchema, schemaGraphIri),
                RdfData.InMemoryNQuad(nq),
              ),
              data = NonEmptyChunk(RdfData.InMemoryTurtle("", "urn:dummy")),
            ),
            shapes = ontologyShapes,
          )
          .either
          .map(result => assert(result)(isRight))
      },
      test("ontology as NQuads missing rdfs:label fails") {
        val ttl = prefixes +
          """<http://www.knora.org/ontology/0001/test>
            |    rdf:type                        owl:Ontology ;
            |    knora-base:attachedToProject    <http://rdfh.ch/projects/0001> ;
            |    knora-base:lastModificationDate "2024-01-01T00:00:00Z"^^xsd:dateTime .
            |""".stripMargin
        val nq = ttlToNQuads(ttl, ontoGraphIri)
        ShaclValidator
          .validate(
            graphs = RdfGraphs(
              ontologies = NonEmptyChunk(
                RdfData.InMemoryTurtle(minimalSchema, schemaGraphIri),
                RdfData.InMemoryNQuad(nq),
              ),
              data = NonEmptyChunk(RdfData.InMemoryTurtle("", "urn:dummy")),
            ),
            shapes = ontologyShapes,
          )
          .either
          .map(result => assert(result)(isLeft))
      },
    ),
    suite("two-step validation")(
      test("ontology validation fails before data is loaded") {
        val invalidOntoTtl = prefixes +
          """<http://www.knora.org/ontology/0001/test>
            |    rdf:type                        owl:Ontology ;
            |    knora-base:attachedToProject    <http://rdfh.ch/projects/0001> ;
            |    knora-base:lastModificationDate "2024-01-01T00:00:00Z"^^xsd:dateTime .
            |""".stripMargin
        // Data contains an owl:Ontology too — but ontology validation should fail first, before data is added
        val dataNq =
          s"""<http://example.org/ind1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#NamedIndividual> <$dataGraphIri> .\n"""
        ShaclValidator
          .validate(
            graphs = RdfGraphs(
              ontologies = NonEmptyChunk(
                RdfData.InMemoryTurtle(minimalSchema, schemaGraphIri),
                RdfData.InMemoryTurtle(invalidOntoTtl, ontoGraphIri),
              ),
              data = NonEmptyChunk(RdfData.InMemoryNQuad(dataNq)),
            ),
            shapes = ShaclShapes(
              ontologyShapes = NonEmptyChunk(RdfData.TurtleFile(shapesPath, "urn:shapes")),
              dataShapes = NonEmptyChunk(noOpShapes),
            ),
          )
          .either
          .map { result =>
            assert(result)(isLeft) &&
            assert(result.left.toOption.get)(
              Assertion.isSubtype[ShaclValidationError.OntologyValidationError](anything),
            )
          }
      },
      test("data shapes can use RDFS inference from ontology triples") {
        val ontoTtl = prefixes + validOntologyHeader +
          """:TestThing
            |    rdf:type        owl:Class ;
            |    rdfs:subClassOf knora-base:Resource ;
            |    rdfs:label      "Test thing"@en .
            |""".stripMargin
        // Shape requires rdfs:label on all knora-base:Resource instances (via inference)
        val dataShapesTtl =
          """@prefix sh:         <http://www.w3.org/ns/shacl#> .
            |@prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
            |@prefix rdfs:       <http://www.w3.org/2000/01/rdf-schema#> .
            |
            |<urn:shapes/ResourceInstanceShape>
            |    a sh:NodeShape ;
            |    sh:targetClass knora-base:Resource ;
            |    sh:property [
            |        sh:path rdfs:label ;
            |        sh:minCount 1 ;
            |        sh:message "Resource instance must have rdfs:label" ;
            |    ] .
            |""".stripMargin
        // Data: instance of :TestThing (subclass of knora-base:Resource) with label
        val dataNq =
          s"""<http://example.org/thing1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/0001/test#TestThing> <$dataGraphIri> .
             |<http://example.org/thing1> <http://www.w3.org/2000/01/rdf-schema#label> "Thing 1" <$dataGraphIri> .
             |""".stripMargin
        ShaclValidator
          .validate(
            graphs = RdfGraphs(
              ontologies = NonEmptyChunk(
                RdfData.InMemoryTurtle(minimalSchema, schemaGraphIri),
                RdfData.InMemoryTurtle(ontoTtl, ontoGraphIri),
              ),
              data = NonEmptyChunk(RdfData.InMemoryNQuad(dataNq)),
            ),
            shapes = ShaclShapes(
              ontologyShapes = NonEmptyChunk(noOpShapes),
              dataShapes = NonEmptyChunk(RdfData.InMemoryTurtle(dataShapesTtl, "urn:data-shapes")),
            ),
          )
          .either
          .map(result => assert(result)(isRight))
      },
      test("data validation fails when RDFS-inferred instance violates shape") {
        val ontoTtl = prefixes + validOntologyHeader +
          """:TestThing
            |    rdf:type        owl:Class ;
            |    rdfs:subClassOf knora-base:Resource ;
            |    rdfs:label      "Test thing"@en .
            |""".stripMargin
        val dataShapesTtl =
          """@prefix sh:         <http://www.w3.org/ns/shacl#> .
            |@prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
            |@prefix rdfs:       <http://www.w3.org/2000/01/rdf-schema#> .
            |
            |<urn:shapes/ResourceInstanceShape>
            |    a sh:NodeShape ;
            |    sh:targetClass knora-base:Resource ;
            |    sh:property [
            |        sh:path rdfs:label ;
            |        sh:minCount 1 ;
            |        sh:message "Resource instance must have rdfs:label" ;
            |    ] .
            |""".stripMargin
        // Data: instance of :TestThing without rdfs:label — should fail via RDFS inference
        val dataNq =
          s"""<http://example.org/thing1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/0001/test#TestThing> <$dataGraphIri> .
             |""".stripMargin
        ShaclValidator
          .validate(
            graphs = RdfGraphs(
              ontologies = NonEmptyChunk(
                RdfData.InMemoryTurtle(minimalSchema, schemaGraphIri),
                RdfData.InMemoryTurtle(ontoTtl, ontoGraphIri),
              ),
              data = NonEmptyChunk(RdfData.InMemoryNQuad(dataNq)),
            ),
            shapes = ShaclShapes(
              ontologyShapes = NonEmptyChunk(noOpShapes),
              dataShapes = NonEmptyChunk(RdfData.InMemoryTurtle(dataShapesTtl, "urn:data-shapes")),
            ),
          )
          .either
          .map(result => assert(result)(isLeft))
      },
    ),
    suite("NQuad single-graph check")(
      test("NQuad data with two named graphs fails with LoadingError") {
        val graph1 =
          s"""<http://example.org/s1> <http://example.org/p1> <http://example.org/o1> <http://example.org/g1> .\n"""
        val graph2 =
          s"""<http://example.org/s2> <http://example.org/p2> <http://example.org/o2> <http://example.org/g2> .\n"""
        val nq = graph1 + graph2
        ShaclValidator
          .validate(
            graphs = RdfGraphs(
              ontologies = NonEmptyChunk(RdfData.InMemoryNQuad(nq)),
              data = NonEmptyChunk(RdfData.InMemoryTurtle("", "urn:dummy")),
            ),
            shapes = ShaclShapes(
              ontologyShapes = NonEmptyChunk(noOpShapes),
              dataShapes = NonEmptyChunk(noOpShapes),
            ),
          )
          .either
          .map { result =>
            assert(result)(isLeft) &&
            assert(result.left.toOption.get)(Assertion.isSubtype[ShaclValidationError.LoadingError](anything))
          }
      },
      test("NQuad data with triples only in default graph fails with LoadingError") {
        // NTriples-style triples (no graph component) parsed as NQuads go into the default graph and should fail
        val nq = s"""<http://example.org/s1> <http://example.org/p1> <http://example.org/o1> .\n"""
        ShaclValidator
          .validate(
            graphs = RdfGraphs(
              ontologies = NonEmptyChunk(RdfData.InMemoryNQuad(nq)),
              data = NonEmptyChunk(RdfData.InMemoryTurtle("", "urn:dummy")),
            ),
            shapes = ShaclShapes(
              ontologyShapes = NonEmptyChunk(noOpShapes),
              dataShapes = NonEmptyChunk(noOpShapes),
            ),
          )
          .either
          .map { result =>
            assert(result)(isLeft) &&
            assert(result.left.toOption.get)(Assertion.isSubtype[ShaclValidationError.LoadingError](anything))
          }
      },
    ),
  )
}
