/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.shacl

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import org.knora.testrunner.DspZTestJUnitRunner

/**
 * Guards that validation heap stays bounded for data typed only with leaf classes below a class and property
 * hierarchy: validation must use a plain model, not an RDFS inference model. Runs under a small heap
 * (see `memory_test` in BUILD.bazel). A regression ends as a JVM exit with OutOfMemoryError, because
 * `validateModel` runs in `ZIO.attemptBlocking` and the timeout cannot interrupt it.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ShaclValidatorMemorySpec extends ZIOSpecDefault {

  private val ns         = "http://example.org/mem#"
  private val dataGraph  = "http://example.org/mem/data"
  private val leafCount  = 10
  private val instances  = 25000
  private val rdfType    = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>"
  private val rdfsLabel  = "<http://www.w3.org/2000/01/rdf-schema#label>"
  private val linkFamily = Seq("link0", "link1", "link2")

  private val prefixes =
    s"""@prefix :     <$ns> .
       |@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix owl:  <http://www.w3.org/2002/07/owl#> .
       |@prefix sh:   <http://www.w3.org/ns/shacl#> .
       |""".stripMargin

  // Class chain Root <- Mid1 <- Mid2 <- Leaf0..N, property chain link0 <- link1 <- link2.
  private val ontologyTtl: String = {
    val leaves = (0 until leafCount).map(i => s":Leaf$i a owl:Class ; rdfs:subClassOf :Mid2 .").mkString("\n")
    s"""$prefixes
       |:Root a owl:Class .
       |:Mid1 a owl:Class ; rdfs:subClassOf :Root .
       |:Mid2 a owl:Class ; rdfs:subClassOf :Mid1 .
       |$leaves
       |:link0 a owl:ObjectProperty .
       |:link1 a owl:ObjectProperty ; rdfs:subPropertyOf :link0 .
       |:link2 a owl:ObjectProperty ; rdfs:subPropertyOf :link1 .
       |""".stripMargin
  }

  // Shapes on the root and intermediate classes, each requiring link targets to be Root instances.
  private val dataShapesTtl: String = {
    val shapes = for {
      target <- Seq("Root", "Mid1", "Mid2")
      link   <- linkFamily
      n      <- 0 until 2
    } yield s"""
                |:Shape${target}_${link}_$n a sh:NodeShape ;
                |  sh:targetClass :$target ;
                |  sh:property [ sh:path :$link ; sh:class :Root ] .""".stripMargin
    s"$prefixes\n${shapes.mkString("\n")}\n"
  }

  // Ontology shapes need a non-empty graph; this shape targets no instances.
  private val ontologyShapesTtl = s"$prefixes\n:NoTargets a sh:NodeShape ;\n  sh:targetClass :DoesNotExist .\n"

  private def iri(local: String): String = s"<$ns$local>"

  private def writeQuads(path: Path): Unit = {
    val writer: BufferedWriter = Files.newBufferedWriter(path, StandardCharsets.UTF_8)
    try {
      def quad(s: String, p: String, o: String): Unit = writer.write(s"$s $p $o <$dataGraph> .\n")
      (0 until instances).foreach { i =>
        val subject = iri(s"i$i")
        quad(subject, rdfType, iri(s"Leaf${i % leafCount}"))
        quad(subject, rdfsLabel, s"\"instance $i\"")
        quad(subject, iri("link2"), iri(s"i${(i + 1) % instances}"))
        quad(subject, iri("link2"), iri(s"i${(i + 7) % instances}"))
      }
    } finally writer.close()
  }

  private val quadFile: ZIO[Scope, Throwable, Path] =
    ZIO.acquireRelease(
      ZIO.attemptBlocking {
        val path = Files.createTempFile("shacl-memory-", ".nq")
        writeQuads(path)
        path
      },
    )(path => ZIO.attemptBlocking(Files.deleteIfExists(path)).ignore)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ShaclValidatorMemorySpec")(
      test("validates leaf-typed data below class and property hierarchies within a bounded heap") {
        ZIO.scoped {
          for {
            path <- quadFile
            graphs = RdfGraphs(
                       ontologies = NonEmptyChunk(RdfData.InMemoryTurtle(ontologyTtl, "http://example.org/mem/onto")),
                       data = NonEmptyChunk(RdfData.NQuadFile(path)),
                     )
            shapes = ShaclShapes(
                       ontologyShapes = NonEmptyChunk(RdfData.InMemoryTurtle(ontologyShapesTtl, "http://example.org/mem/os")),
                       dataShapes = NonEmptyChunk(RdfData.InMemoryTurtle(dataShapesTtl, "http://example.org/mem/ds")),
                     )
            result <- ShaclValidator.validate(graphs, shapes).exit
          } yield assertTrue(result.isSuccess)
        }
      } @@ TestAspect.timeout(60.seconds),
    )
}
