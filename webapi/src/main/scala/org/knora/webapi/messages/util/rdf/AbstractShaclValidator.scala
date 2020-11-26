/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import java.io.File
import java.nio.file.Path

import org.knora.webapi.exceptions.AssertionException

/**
 * An abstract base class for classes that validate RDF models using SHACL shapes.
 *
 * @param baseDir       the base directory that SHACL graphs are loaded from.
 * @param rdfFormatUtil an [[RdfFormatUtil]].
 * @tparam ShaclGraphT an implementation-specific representation of a graph of SHACL shapes.
 */
abstract class AbstractShaclValidator[ShaclGraphT](baseDir: File, private val rdfFormatUtil: RdfFormatUtil) extends ShaclValidator {

    /**
     * A map of relative paths to objects representing graphs of SHACL shapes.
     */
    private val shaclGraphs: Map[Path, ShaclGraphT] = if (baseDir.exists) {
        loadShaclGraphs(baseDir, baseDir)
    } else {
        Map.empty
    }

    def validate(rdfModel: RdfModel, shaclPath: Path): ShaclValidationResult = {
        validateWithShaclGraph(
            rdfModel = rdfModel,
            shaclGraph = shaclGraphs.getOrElse(shaclPath, throw AssertionException(s"SHACL graph $shaclPath not found"))
        )
    }

    /**
     * Recursively loads graphs of SHACL shapes from a directory and its subdirectories.
     *
     * @param baseDir the base directory that SHACL graphs are loaded from.
     * @param dir     the base directory or a subdirectory.
     * @return a map of file paths (relative to the base directory) to graphs of SHACL shapes.
     */
    private def loadShaclGraphs(baseDir: File, dir: File): Map[Path, ShaclGraphT] = {
        // Parse the files representing SHACL graphs in this directory.
        val modelsInDir: Map[Path, ShaclGraphT] = dir.listFiles.collect {
            case file: File if file.isFile && file.getName.endsWith(".ttl") =>
                // Map each SHACL file's relative path to a ShapesT containing the SHACL graph.
                val relativePath: Path = baseDir.toPath.relativize(file.toPath)
                val shaclModel: RdfModel = rdfFormatUtil.fileToRdfModel(file = file, rdfFormat = Turtle)
                relativePath -> rdfModelToShaclGraph(shaclModel)
        }.toMap

        // Recurse in subdirectories.
        modelsInDir ++ dir.listFiles.filter(_.isDirectory).flatMap {
            subDir => loadShaclGraphs(baseDir = baseDir, dir = subDir)
        }
    }

    /**
     * Validates the default graph of an [[RdfModel]] using a graph of SHACL shapes.
     *
     * @param rdfModel the [[RdfModel]] to be validated.
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
