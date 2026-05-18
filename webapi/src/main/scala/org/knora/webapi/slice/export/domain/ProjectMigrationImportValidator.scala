/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import org.apache.jena.riot.system.StreamRDF
import org.apache.jena.riot.system.StreamRDFBase
import org.apache.jena.sparql.core.Quad
import zio.*
import zio.nio.file.Path

import org.knora.shacl.RdfData
import org.knora.shacl.RdfGraphs
import org.knora.shacl.ShaclShapes
import org.knora.shacl.ShaclValidator
import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.PlaceholderIri

final class ProjectMigrationImportValidator() {

  private val builtInOntologyResources = Chunk(
    ("knora-ontologies/knora-base.ttl", "http://www.knora.org/ontology/knora-base"),
    ("knora-ontologies/knora-admin.ttl", "http://www.knora.org/ontology/knora-admin"),
    ("knora-ontologies/salsah-gui.ttl", "http://www.knora.org/ontology/salsah-gui"),
    ("knora-ontologies/standoff-onto.ttl", "http://www.knora.org/ontology/standoff"),
  )

  // Placeholder replaced at runtime with the actual project IRI.
  // Safe: the TTL templates are controlled by us and ProjectIri is validated by a strict regex.
  // Tests catch any mismatch since sh:hasValue would match the literal placeholder, failing validation.
  private val ProjectIriPlaceholder = "urn:placeholder:projectIri"

  def validate(ontologyFiles: NonEmptyChunk[Path], dataFiles: NonEmptyChunk[Path], projectIri: ProjectIri): Task[Unit] =
    for {
      _       <- ZIO.unlessZIO(AppConfig.features(_.allowPlaceholder))(assertNoPlaceholderInObjectPosition(dataFiles))
      builtIn <- ZIO.foreach(builtInOntologyResources) { case (resource, graphIri) =>
                   readClasspathResource(resource).map(RdfData.InMemoryTurtle(_, graphIri))
                 }
      ontologyNq = ontologyFiles.map(p => RdfData.NQuadFile(p.toFile.toPath): RdfData)
      dataNq     = dataFiles.map(p => RdfData.NQuadFile(p.toFile.toPath): RdfData)
      graphs     = RdfGraphs(
                 ontologies = NonEmptyChunk.fromChunk(builtIn ++ ontologyNq).get, // safe: builtIn is always 4 elements
                 data = dataNq,
               )
      ontologyShapesTtl <- readClasspathResource("shacl/ontology-shapes.ttl")
                             .map(_.replace(ProjectIriPlaceholder, projectIri.value))
      dataShapesTtl <- readClasspathResource("shacl/data-shapes.ttl")
                         .map(_.replace(ProjectIriPlaceholder, projectIri.value))
      shapes = ShaclShapes(
                 ontologyShapes = NonEmptyChunk(RdfData.InMemoryTurtle(ontologyShapesTtl, "")),
                 dataShapes = NonEmptyChunk(RdfData.InMemoryTurtle(dataShapesTtl, "")),
               )
      _ <- ShaclValidator
             .validate(graphs, shapes)
             .mapError(err => new RuntimeException(err.message))
    } yield ()

  /**
   * Streams each N-Quad file and fails on the first quad whose object is the
   * placeholder sentinel (`urn:placeholder`), either as an IRI or as a string
   * literal. Called only when the `allow-placeholder` feature switch is off, so
   * a rejected import fails before any model/SHACL work is done.
   *
   * Aborts parsing on the first hit by throwing a private sentinel exception
   * from the StreamRDF sink — Jena's RDFParser propagates the throw and stops
   * reading the file, so large bags with an early sentinel do not pay for a
   * full parse.
   */
  private def assertNoPlaceholderInObjectPosition(dataFiles: NonEmptyChunk[Path]): Task[Unit] =
    ZIO.foreachDiscard(dataFiles)(scanForPlaceholderObject)

  private final class PlaceholderHit(val quad: Quad) extends RuntimeException("placeholder sentinel hit")

  private def scanForPlaceholderObject(path: Path): Task[Unit] = {
    val sentinel = PlaceholderIri.instance.value
    ZIO.attemptBlocking {
      val sink: StreamRDF = new StreamRDFBase {
        override def quad(quad: Quad): Unit = {
          val o              = quad.getObject
          val uriMatches     = o.isURI && o.getURI == sentinel
          val literalMatches = o.isLiteral && o.getLiteralLexicalForm == sentinel
          if (uriMatches || literalMatches) throw new PlaceholderHit(quad)
        }
        override def triple(triple: org.apache.jena.graph.Triple): Unit = ()
      }
      try {
        RDFParser.source(path.toFile.toPath.toUri.toString).lang(Lang.NQUADS).parse(sink)
        None: Option[Quad]
      } catch {
        case hit: PlaceholderHit => Some(hit.quad)
        // Jena may wrap our exception inside a RiotException — unwrap it.
        case e: RuntimeException if e.getCause.isInstanceOf[PlaceholderHit] =>
          Some(e.getCause.asInstanceOf[PlaceholderHit].quad)
      }
    }.flatMap {
      case Some(quad) =>
        ZIO.fail(
          new RuntimeException(
            s"File '$path' contains the placeholder sentinel '$sentinel' in object position " +
              s"(subject=${quad.getSubject}, predicate=${quad.getPredicate}, object=${quad.getObject}), " +
              s"which is not allowed on this server.",
          ),
        )
      case None => ZIO.unit
    }
  }

  private def readClasspathResource(path: String): Task[String] =
    ZIO.attemptBlocking {
      val is = getClass.getClassLoader.getResourceAsStream(path)
      if (is == null) throw new RuntimeException(s"Classpath resource '$path' not found")
      try new String(is.readAllBytes(), "UTF-8")
      finally is.close()
    }
}

object ProjectMigrationImportValidator {
  val layer: ULayer[ProjectMigrationImportValidator] = ZLayer.derive[ProjectMigrationImportValidator]
}
