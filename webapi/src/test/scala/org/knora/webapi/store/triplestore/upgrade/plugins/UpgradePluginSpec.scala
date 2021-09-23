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

package org.knora.webapi.store.triplestore.upgrade.plugins

import java.io.{BufferedInputStream, FileInputStream}

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.util.rdf._

/**
 * Provides helper methods for specs that test upgrade plugins.
 */
abstract class UpgradePluginSpec extends CoreSpec() {
  protected val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(defaultFeatureFactoryConfig)

  /**
   * Parses a TriG file and returns it as an [[RdfModel]].
   *
   * @param path the file path of the TriG file.
   * @return an [[RdfModel]].
   */
  def trigFileToModel(path: String): RdfModel = {
    val fileInputStream = new BufferedInputStream(new FileInputStream(path))
    val rdfModel: RdfModel = rdfFormatUtil.inputStreamToRdfModel(inputStream = fileInputStream, rdfFormat = TriG)
    fileInputStream.close()
    rdfModel
  }

  /**
   * Wraps expected SPARQL SELECT results in a [[SparqlSelectResultBody]].
   *
   * @param rows the expected results.
   * @return a [[SparqlSelectResultBody]] containing the expected results.
   */
  def expectedResult(rows: Seq[Map[String, String]]): SparqlSelectResultBody = {
    val rowMaps = rows.map { mapToWrap =>
      VariableResultsRow(
        new ErrorHandlingMap[String, String](
          mapToWrap,
          { key: String =>
            s"No value found for SPARQL query variable '$key' in query result row"
          }
        )
      )
    }

    SparqlSelectResultBody(bindings = rowMaps)
  }
}
