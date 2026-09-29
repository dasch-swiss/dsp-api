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
import org.knora.webapi.slice.`export`.domain.ProjectMigrationImportValidator.ImportMode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.UserIri
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

  // Replaced at runtime with the on-behalf-of user IRI, only when the bulk import shapes are loaded.
  private val UserIriPlaceholder = "urn:placeholder:userIri"

  /**
   * Validates the import graph data against the SHACL shapes.
   *
   * @param mode selects the extra data shapes layered on top of `shacl/data-shapes.ttl`.
   *             `ImportMode.Migration` loads `shacl/migration-shapes.ttl`, which allows the modification and
   *             deletion predicates that already-modified graphs carry. `ImportMode.BulkData` loads
   *             `shacl/bulk-import-shapes.ttl`, which forbids those predicates and pins
   *             `knora-base:attachedToUser` to the on-behalf-of user. The two shape files never load together.
   */
  def validate(
    ontologyFiles: NonEmptyChunk[Path],
    dataFiles: NonEmptyChunk[Path],
    projectIri: ProjectIri,
    mode: ImportMode,
  ): Task[Unit] =
    for {
      _              <- assertNoPlaceholderUnlessAllowed(dataFiles)
      ontologies     <- loadOntologies(ontologyFiles)
      ontologyShapes <- loadOntologyShapes(projectIri)
      graphs          = RdfGraphs(ontologies, dataFiles.map(toNQuadFile))
      dataShapesTtl  <- readClasspathResource("shacl/data-shapes.ttl")
                         .map(_.replace(ProjectIriPlaceholder, projectIri.value))
      extraDataShapes <- mode match {
                           case ImportMode.Migration =>
                             readClasspathResource("shacl/migration-shapes.ttl")
                               .map(ttl => Chunk(RdfData.InMemoryTurtle(ttl, "")))
                           case ImportMode.BulkData(userIri) =>
                             readClasspathResource("shacl/bulk-import-shapes.ttl")
                               .map(_.replace(UserIriPlaceholder, userIri.value))
                               .map(ttl => Chunk(RdfData.InMemoryTurtle(ttl, "")))
                         }
      dataShapes = NonEmptyChunk(RdfData.InMemoryTurtle(dataShapesTtl, "")) ++ extraDataShapes
      _         <- ShaclValidator
             .validate(graphs, ShaclShapes(ontologyShapes, dataShapes))
             .mapError(err => new RuntimeException(err.message))
    } yield ()

  /**
   * Validates the ontologies against `shacl/ontology-shapes.ttl`. The data files are only parsed (syntax and one
   * named graph per file) and scanned for the placeholder sentinel. They are not loaded into the SHACL model and
   * no data shapes run: over a large project the RDFS-inference expansion of the data shape targets exceeds the
   * heap (DEV-7440).
   */
  def validateWithoutDataShapes(
    ontologyFiles: NonEmptyChunk[Path],
    dataFiles: NonEmptyChunk[Path],
    projectIri: ProjectIri,
  ): Task[Unit] =
    for {
      _              <- assertNoPlaceholderUnlessAllowed(dataFiles)
      ontologies     <- loadOntologies(ontologyFiles)
      ontologyShapes <- loadOntologyShapes(projectIri)
      _              <- ShaclValidator
             .validateOntologies(ontologies, ontologyShapes)
             .mapError(err => new RuntimeException(err.message))
      _ <- ShaclValidator
             .checkParsable(dataFiles.map(toNQuadFile))
             .mapError(err => new RuntimeException(err.message))
    } yield ()

  private def assertNoPlaceholderUnlessAllowed(dataFiles: NonEmptyChunk[Path]): Task[Unit] =
    ZIO.unlessZIO(AppConfig.features(_.allowPlaceholder))(assertNoPlaceholderInObjectPosition(dataFiles)).unit

  /** The built-in ontologies followed by the project ontologies. */
  private def loadOntologies(ontologyFiles: NonEmptyChunk[Path]): Task[NonEmptyChunk[RdfData]] =
    ZIO
      .foreach(builtInOntologyResources) { case (resource, graphIri) =>
        readClasspathResource(resource).map(RdfData.InMemoryTurtle(_, graphIri): RdfData)
      }
      .map(builtIn => NonEmptyChunk.fromIterable(builtIn.head, builtIn.tail) ++ ontologyFiles.map(toNQuadFile))

  private def toNQuadFile(path: Path): RdfData = RdfData.NQuadFile(path.toFile.toPath)

  private def loadOntologyShapes(projectIri: ProjectIri): Task[NonEmptyChunk[RdfData]] =
    readClasspathResource("shacl/ontology-shapes.ttl")
      .map(_.replace(ProjectIriPlaceholder, projectIri.value))
      .map(ttl => NonEmptyChunk(RdfData.InMemoryTurtle(ttl, "")))

  /**
   * Streams each N-Quad file and fails on the first quad whose object is the
   * placeholder sentinel (`urn:dasch:placeholder`), either as an IRI or as a string
   * literal. Called only when the `allow-placeholder` feature switch is off, so
   * a rejected import fails before any model/SHACL work is done.
   *
   * Aborts parsing on the first hit by throwing a private sentinel exception
   * from the StreamRDF sink -- Jena's RDFParser propagates the throw and stops
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
        // Jena may wrap our exception inside a RiotException; unwrap it.
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

  /**
   * Selects the extra data shapes layered on top of `shacl/data-shapes.ttl`.
   */
  enum ImportMode {
    case Migration
    case BulkData(onBehalfOfUser: UserIri)
  }

  val layer: ULayer[ProjectMigrationImportValidator] = ZLayer.derive[ProjectMigrationImportValidator]
}
