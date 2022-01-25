/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
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
