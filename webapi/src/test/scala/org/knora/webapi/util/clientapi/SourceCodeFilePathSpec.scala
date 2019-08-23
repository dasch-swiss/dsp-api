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

import org.knora.webapi.CoreSpec

class SourceCodeFilePathSpec extends CoreSpec() {
    "The SourceCodeFilePath class" should {
        "walk one directory up, then one directory down" in {
            val sourcePath = SourceCodeFilePath(
                directoryPath = Seq("foo", "bar", "baz"),
                filename = "test1",
                fileExtension = "js"
            )

            val targetPath = SourceCodeFilePath(
                directoryPath = Seq("foo", "bar", "quux"),
                filename = "test2",
                fileExtension = "js"
            )

            assert(sourcePath.makeImportPath(targetPath) == "../quux/test2.js")
            assert(sourcePath.makeImportPath(targetPath, includeFileExtension = false) == "../quux/test2")
        }

        "walk two directories up, then three directories down" in {
            val sourcePath = SourceCodeFilePath(
                directoryPath = Seq("foo", "bar", "baz", "quux"),
                filename = "test1",
                fileExtension = "js"
            )

            val targetPath = SourceCodeFilePath(
                directoryPath = Seq("foo", "bar", "corge", "grault", "garply"),
                filename = "test2",
                fileExtension = "js"
            )

            assert(sourcePath.makeImportPath(targetPath) == "../../corge/grault/garply/test2.js")
        }

        "walk down only" in {
            val sourcePath = SourceCodeFilePath(
                directoryPath = Seq("foo", "bar"),
                filename = "test1",
                fileExtension = "js"
            )

            val targetPath = SourceCodeFilePath(
                directoryPath = Seq("foo", "bar", "baz"),
                filename = "test2",
                fileExtension = "js"
            )

            assert(sourcePath.makeImportPath(targetPath) == "./baz/test2.js")
        }

        "walk up only" in {
            val sourcePath = SourceCodeFilePath(
                directoryPath = Seq("foo", "bar", "baz"),
                filename = "test1",
                fileExtension = "js"
            )

            val targetPath = SourceCodeFilePath(
                directoryPath = Seq("foo", "bar"),
                filename = "test2",
                fileExtension = "js"
            )

            assert(sourcePath.makeImportPath(targetPath) == "../test2.js")
        }

        "use the same directory" in {
            val sourcePath = SourceCodeFilePath(
                directoryPath = Seq("foo", "bar", "baz"),
                filename = "test1",
                fileExtension = "js"
            )

            val targetPath = SourceCodeFilePath(
                directoryPath = Seq("foo", "bar", "baz"),
                filename = "test2",
                fileExtension = "js"
            )

            assert(sourcePath.makeImportPath(targetPath) == "./test2.js")
        }
    }
}
