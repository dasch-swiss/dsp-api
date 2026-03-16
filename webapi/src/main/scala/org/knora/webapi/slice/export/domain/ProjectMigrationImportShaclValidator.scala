/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.*
import zio.nio.file.Path

import org.knora.shacl.RdfData
import org.knora.shacl.RdfGraphs
import org.knora.shacl.ShaclShapes
import org.knora.shacl.ShaclValidator
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

final class ProjectMigrationImportShaclValidator() {

  private val builtInOntologyResources = Chunk(
    ("knora-ontologies/knora-base.ttl", "http://www.knora.org/ontology/knora-base"),
    ("knora-ontologies/knora-admin.ttl", "http://www.knora.org/ontology/knora-admin"),
    ("knora-ontologies/salsah-gui.ttl", "http://www.knora.org/ontology/salsah-gui"),
    ("knora-ontologies/standoff-onto.ttl", "http://www.knora.org/ontology/standoff"),
  )

  private val ProjectIriPlaceholder = "urn:placeholder:projectIri"

  def validate(ontologyFiles: Chunk[Path], dataFiles: Chunk[Path], projectIri: ProjectIri): Task[Unit] =
    for {
      builtIn <- ZIO.foreach(builtInOntologyResources) { case (resource, graphIri) =>
                   readClasspathResource(resource).map(RdfData.InMemoryTurtle(_, graphIri))
                 }
      ontologyNq = ontologyFiles.map(p => RdfData.NQuadFile(p.toFile.toPath): RdfData)
      dataNq     = dataFiles.map(p => RdfData.NQuadFile(p.toFile.toPath): RdfData)
      graphs     = RdfGraphs(
                 ontologies = NonEmptyChunk.fromChunk(builtIn ++ ontologyNq).get,
                 data = NonEmptyChunk.fromChunk(dataNq).get,
               )
      ontologyShapesTtl <- readClasspathResource("shacl/ontology-shapes.ttl")
                             .map(_.replace(ProjectIriPlaceholder, projectIri.value))
      dataShapesTtl     <- readClasspathResource("shacl/data-shapes.ttl")
      shapes             = ShaclShapes(
                 ontologyShapes = NonEmptyChunk(RdfData.InMemoryTurtle(ontologyShapesTtl, "")),
                 dataShapes = NonEmptyChunk(RdfData.InMemoryTurtle(dataShapesTtl, "")),
               )
      _ <- ShaclValidator
             .validate(graphs, shapes)
             .mapError(err => new RuntimeException(err.message))
    } yield ()

  private def readClasspathResource(path: String): Task[String] =
    ZIO.attemptBlocking {
      val is = getClass.getClassLoader.getResourceAsStream(path)
      if (is == null) throw new RuntimeException(s"Classpath resource '$path' not found")
      try new String(is.readAllBytes(), "UTF-8")
      finally is.close()
    }
}

object ProjectMigrationImportShaclValidator {
  val layer: ULayer[ProjectMigrationImportShaclValidator] = ZLayer.derive[ProjectMigrationImportShaclValidator]
}
