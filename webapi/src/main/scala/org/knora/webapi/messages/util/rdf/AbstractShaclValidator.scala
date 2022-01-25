/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf

import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import org.knora.webapi.exceptions.AssertionException

/**
 * An abstract base class for classes that validate RDF models using SHACL shapes.
 *
 * @param baseDir       the base directory that SHACL graphs are loaded from.
 * @param rdfFormatUtil an [[RdfFormatUtil]].
 * @tparam ShaclGraphT an implementation-specific representation of a graph of SHACL shapes.
 */
abstract class AbstractShaclValidator[ShaclGraphT](baseDir: Path, private val rdfFormatUtil: RdfFormatUtil)
    extends ShaclValidator {

  /**
   * A map of relative paths to objects representing graphs of SHACL shapes.
   */
  private val shaclGraphs: Map[Path, ShaclGraphT] = if (Files.exists(baseDir)) {
    val fileVisitor = new ShaclGraphCollectingFileVisitor
    Files.walkFileTree(baseDir, fileVisitor)
    fileVisitor.visitedShaclGraphs.toMap
  } else {
    Map.empty
  }

  def validate(rdfModel: RdfModel, shaclPath: Path): ShaclValidationResult =
    validateWithShaclGraph(
      rdfModel = rdfModel,
      shaclGraph = shaclGraphs.getOrElse(shaclPath, throw AssertionException(s"SHACL graph $shaclPath not found"))
    )

  /**
   * A [[FileVisitor]] that loads graphs of SHACL shapes while walking a file tree.
   */
  private class ShaclGraphCollectingFileVisitor extends SimpleFileVisitor[Path] {
    // A collection of the graphs that have been loaded so far.
    val visitedShaclGraphs: collection.mutable.Map[Path, ShaclGraphT] = collection.mutable.Map.empty

    override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
      // Is this a Turtle file?
      if (file.getFileName.toString.endsWith(".ttl")) {
        // Yes. Parse it.
        val shaclModel: RdfModel = rdfFormatUtil.fileToRdfModel(file = file, rdfFormat = Turtle)

        // Convert it to a ShaclGraphT.
        val shaclGraph: ShaclGraphT = rdfModelToShaclGraph(shaclModel)

        // Get its path relative to baseDir.
        val relativePath: Path = baseDir.relativize(file)

        // Add it to the collection.
        visitedShaclGraphs += relativePath -> shaclGraph
      }

      FileVisitResult.CONTINUE
    }
  }

  /**
   * Validates the default graph of an [[RdfModel]] using a graph of SHACL shapes.
   *
   * @param rdfModel   the [[RdfModel]] to be validated.
   * @param shaclGraph a graph of SHACL shapes.
   * @return the validation result.
   */
  protected def validateWithShaclGraph(rdfModel: RdfModel, shaclGraph: ShaclGraphT): ShaclValidationResult

  /**
   * Converts the default graph of an [[RdfModel]] to a [[ShaclGraphT]].
   *
   * @param rdfModel an [[RdfModel]] whose default graph contains SHACL shapes.
   * @return a [[ShaclGraphT]] representing the SHACL shapes.
   */
  protected def rdfModelToShaclGraph(rdfModel: RdfModel): ShaclGraphT
}
