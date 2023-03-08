/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.upgrade.plugins

import com.typesafe.scalalogging.Logger
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import scala.util.Failure
import scala.util.Success
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import scala.util.Using

import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.util.rdf._

/**
 * Provides helper methods for specs that test upgrade plugins.
 */
abstract class UpgradePluginSpec extends AnyWordSpecLike with Matchers {

  protected val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()

  val log: Logger = Logger(this.getClass())

  /**
   * Parses a TriG file and returns it as an [[RdfModel]].
   *
   * @param path the file path of the TriG file.
   * @return an [[RdfModel]].
   */
  def trigFileToModel(path: String): RdfModel = {
    val fileInputStream    = new BufferedInputStream(new FileInputStream(path))
    val rdfModel: RdfModel = rdfFormatUtil.inputStreamToRdfModel(inputStream = fileInputStream, rdfFormat = TriG)
    fileInputStream.close()
    rdfModel
  }

  /**
   * Parses a TriG String and returns it as an [[RdfModel]].
   *
   * @param s the [[String]] content of a "TriG file".
   * @return an [[RdfModel]].
   */
  def stringToModel(s: String): RdfModel =
    Using(new ByteArrayInputStream(s.getBytes))(rdfFormatUtil.inputStreamToRdfModel(_, TriG)) match {
      case Success(value) => value
      case Failure(e)     => throw new IllegalArgumentException("Invalid model", e)
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
