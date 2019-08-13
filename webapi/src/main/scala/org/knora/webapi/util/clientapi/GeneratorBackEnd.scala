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

package org.knora.webapi.util.clientapi

import scala.annotation.tailrec

/**
 * Represents a client API as input to the generator back end.
 *
 * @param apiDef          the API definition.
 * @param clientClassDefs the class definitions used in the API.
 */
case class ClientApiBackendInput(apiDef: ClientApi, clientClassDefs: Set[ClientClassDefinition])

case class SourceCodeFilePath(directoryPath: Seq[String], filename: String, fileExtension: String) {
    /**
     * Given two paths that are both relative to the root directory of the source tree, strips leading
     * directories until the paths diverge.
     *
     * @param thisPath the path of the importing file.
     * @param thatPath the path of the imported file.
     * @return the diverging parts of the two paths.
     */
    @tailrec
    private def stripDirsUntilDifferent(thisPath: Seq[String], thatPath: Seq[String]): (Seq[String], Seq[String]) = {
        if (thisPath.isEmpty || thatPath.isEmpty) {
            (thisPath, thatPath)
        } else if (thisPath.head == thatPath.head) {
            stripDirsUntilDifferent(thisPath.tail, thatPath.tail)
        } else {
            (thisPath, thatPath)
        }
    }

    /**
     * Given the [[SourceCodeFilePath]] of a file to be imported, returns that path relative to this
     * [[SourceCodeFilePath]].
     *
     * @param thatSourceCodeFilePath the path of the file to be imported.
     * @param includeFileExtension   if true, include the imported file's extension in the result.
     * @return a relative file path for importing `thatSourceCodeFilePath` in the file represented by this
     *         [[SourceCodeFilePath]].
     */
    def makeImportPath(thatSourceCodeFilePath: SourceCodeFilePath, includeFileExtension: Boolean = true): String = {
        // Find the first common parent directory.
        val (thisPathFromCommonParent, thatPathFromCommonParent) = stripDirsUntilDifferent(directoryPath, thatSourceCodeFilePath.directoryPath)

        // Make a relative path for walking up the directory tree to the first common parent directory,
        // then down to the target directory.
        val dirPath = (thisPathFromCommonParent.map(_ => "..") ++ thatPathFromCommonParent).mkString("/")

        // Add the filename.
        val importPathWithoutExtension = if (dirPath.isEmpty) {
            thatSourceCodeFilePath.filename
        } else {
            dirPath + "/" + thatSourceCodeFilePath.filename
        }

        // Add the file extension if requested.
        if (includeFileExtension) {
            importPathWithoutExtension + "." + thatSourceCodeFilePath.fileExtension
        } else {
            importPathWithoutExtension
        }
    }
}

/**
 * Represents a file containing generated client API source code.
 *
 * @param filePath the filename in which the source code should be saved.
 * @param text     the source code.
 */
case class ClientSourceCodeFileContent(filePath: String, text: String)

/**
 * A trait for client API code generator back ends. A back end is responsible for producing client API library
 * source code in a particular programming language.
 */
trait GeneratorBackEnd {
    /**
     * Generates client API source code.
     *
     * @param apis the APIs from which source code is to be generated.
     * @return the generated source code.
     */
    def generateClientSourceCode(apis: Set[ClientApiBackendInput]): Set[ClientSourceCodeFileContent]
}
