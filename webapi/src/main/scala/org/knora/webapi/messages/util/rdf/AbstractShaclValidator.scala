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

package org.knora.webapi.messages.util.rdf

import org.knora.webapi.exceptions.AssertionException

import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

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
}
