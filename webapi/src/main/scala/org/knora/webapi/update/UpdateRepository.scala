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

package org.knora.webapi.update

import java.io._

import org.eclipse.rdf4j.model.impl.LinkedHashModel
import org.eclipse.rdf4j.rio.helpers.StatementCollector
import org.eclipse.rdf4j.rio.{RDFFormat, RDFParser, Rio}
import org.rogach.scallop._


/**
  * Updates a Knora repository to accommodate changes in Knora.
  */
object UpdateRepository extends App {

    private val conf = new TransformDataConf(args)
    private val inputFile = new File(conf.input())
    private val outputFile = new File(conf.output())

    private val trigParser: RDFParser = Rio.createParser(RDFFormat.TRIG)
    private val fileReader = new BufferedReader(new FileReader(inputFile))
    private val model = new LinkedHashModel()
    trigParser.setRDFHandler(new StatementCollector(model))

    val parseStartTime = System.currentTimeMillis()
    trigParser.parse(fileReader, "")
    val parseEndTime = System.currentTimeMillis()

    println(s"Parsed ${model.size} statements in ${parseEndTime - parseStartTime} ms")

    private val fileWriter = new BufferedWriter(new FileWriter(outputFile))
    Rio.write(model, fileWriter, RDFFormat.TRIG)

    println("Wrote output file")

    /**
      * Parses command-line arguments.
      */
    private class TransformDataConf(arguments: Seq[String]) extends ScallopConf(arguments) {
        banner(
            s"""
               |Updates the structure of Knora repository data to accommodate changes in Knora.
               |
               |Usage: org.knora.webapi.util.UpdateRepository input output
            """.stripMargin)

        val input: ScallopOption[String] = trailArg[String](required = true, descr = "Input TriG file")
        val output: ScallopOption[String] = trailArg[String](required = true, descr = "Output TriG file")
        verify()
    }
}
