/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.*
import zio.nio.file.Files
import zio.nio.file.Path
import zio.test.*

import java.nio.charset.StandardCharsets

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

object ProjectMigrationImportShaclValidatorSpec extends ZIOSpecDefault {

  private val testProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/9999")

  private val RdfType        = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
  private val OntologyGraph  = "http://www.knora.org/ontology/9999/test"
  private val DataGraph      = "http://www.knora.org/data/9999/test"
  private val OwlOntology    = "http://www.w3.org/2002/07/owl#Ontology"
  private val OwlClass       = "http://www.w3.org/2002/07/owl#Class"
  private val RdfsLabel      = "http://www.w3.org/2000/01/rdf-schema#label"
  private val RdfsSubClassOf = "http://www.w3.org/2000/01/rdf-schema#subClassOf"
  private val KnoraBase      = "http://www.knora.org/ontology/knora-base#"
  private val XsdDateTime    = "http://www.w3.org/2001/XMLSchema#dateTime"

  private val validOntologyNq =
    s"""<$OntologyGraph> <$RdfType> <$OwlOntology> <$OntologyGraph> .
       |<$OntologyGraph> <$RdfsLabel> "Test Ontology" <$OntologyGraph> .
       |<$OntologyGraph> <${KnoraBase}attachedToProject> <http://rdfh.ch/projects/9999> <$OntologyGraph> .
       |<$OntologyGraph> <${KnoraBase}lastModificationDate> "2024-01-01T00:00:00Z"^^<$XsdDateTime> <$OntologyGraph> .
       |""".stripMargin

  private val emptyDataNq =
    s"""<http://rdfh.ch/9999/resource001> <$RdfType> <${OntologyGraph}#TestResource> <$DataGraph> .
       |""".stripMargin

  private def writeNqFile(dir: Path, name: String, content: String): Task[Path] = {
    val file = dir / name
    Files.writeBytes(file, Chunk.fromArray(content.getBytes(StandardCharsets.UTF_8))).as(file)
  }

  private def validate(
    ontologyNq: String,
    dataNq: String = emptyDataNq,
  ): ZIO[Scope, Throwable, Either[Throwable, Unit]] =
    for {
      dir          <- Files.createTempDirectoryScoped(Some("shacl-test"), Seq.empty)
      ontologyFile <- writeNqFile(dir, "ontology-0.nq", ontologyNq)
      dataFile     <- writeNqFile(dir, "data.nq", dataNq)
      validator     = new ProjectMigrationImportShaclValidator()
      result       <- validator.validate(Chunk(ontologyFile), Chunk(dataFile), testProjectIri).either
    } yield result

  override def spec: Spec[Any, Any] = suite("ProjectMigrationImportShaclValidatorSpec")(
    suite("OntologyShape")(
      test("valid ontology conforms") {
        ZIO.scoped {
          validate(validOntologyNq).map(result => assertTrue(result.isRight))
        }
      },
      test("rejects ontology attached to wrong project") {
        val nq =
          s"""<$OntologyGraph> <$RdfType> <$OwlOntology> <$OntologyGraph> .
             |<$OntologyGraph> <$RdfsLabel> "Test Ontology" <$OntologyGraph> .
             |<$OntologyGraph> <${KnoraBase}attachedToProject> <http://rdfh.ch/projects/0001> <$OntologyGraph> .
             |<$OntologyGraph> <${KnoraBase}lastModificationDate> "2024-01-01T00:00:00Z"^^<$XsdDateTime> <$OntologyGraph> .
             |""".stripMargin
        ZIO.scoped {
          validate(nq).map(result => assertTrue(result.isLeft))
        }
      },
      test("rejects ontology missing lastModificationDate") {
        val nq =
          s"""<$OntologyGraph> <$RdfType> <$OwlOntology> <$OntologyGraph> .
             |<$OntologyGraph> <$RdfsLabel> "Test Ontology" <$OntologyGraph> .
             |<$OntologyGraph> <${KnoraBase}attachedToProject> <http://rdfh.ch/projects/9999> <$OntologyGraph> .
             |""".stripMargin
        ZIO.scoped {
          validate(nq).map(result => assertTrue(result.isLeft))
        }
      },
    ),
    suite("ResourceClassShape")(
      test("rejects resource class missing rdfs:label") {
        val nq = validOntologyNq +
          s"""<${OntologyGraph}#TestThing> <$RdfType> <$OwlClass> <$OntologyGraph> .
             |<${OntologyGraph}#TestThing> <$RdfsSubClassOf> <${KnoraBase}Resource> <$OntologyGraph> .
             |""".stripMargin
        ZIO.scoped {
          validate(nq).map(result => assertTrue(result.isLeft))
        }
      },
      test("accepts resource class with rdfs:label") {
        val nq = validOntologyNq +
          s"""<${OntologyGraph}#TestThing> <$RdfType> <$OwlClass> <$OntologyGraph> .
             |<${OntologyGraph}#TestThing> <$RdfsSubClassOf> <${KnoraBase}Resource> <$OntologyGraph> .
             |<${OntologyGraph}#TestThing> <$RdfsLabel> "Test Thing"@en <$OntologyGraph> .
             |""".stripMargin
        ZIO.scoped {
          validate(nq).map(result => assertTrue(result.isRight))
        }
      },
    ),
  ) @@ TestAspect.timeout(30.seconds)
}
