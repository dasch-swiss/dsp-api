package org.knora.webapi.util

import java.io._

import org.eclipse.rdf4j.rio.{RDFFormat, RDFParser, RDFWriter, Rio}

object RdfXmlToTurtle extends App {
    private val inputFile: File = new File(args(0))
    private val outputFile: File = new File(args(1))
    private val bufferedFileReader: BufferedReader = new BufferedReader(new FileReader(inputFile))
    private val bufferedFileWriter: BufferedWriter = new BufferedWriter(new FileWriter(outputFile))
    private val xmlFileParser: RDFParser = Rio.createParser(RDFFormat.RDFXML)
    private val turtleFileWriter: RDFWriter = Rio.createWriter(RDFFormat.TURTLE, bufferedFileWriter)
    xmlFileParser.setRDFHandler(turtleFileWriter)
    xmlFileParser.parse(bufferedFileReader, "")
    bufferedFileReader.close()
    bufferedFileWriter.close()
}
