/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.util

import java.io.File
import java.nio.file.{Files, Paths}

import akka.event.LoggingAdapter
import com.google.gwt.safehtml.shared.UriUtils._
import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.twirl.StandoffTagV1
import spray.json.JsonParser


/**
  * Do save String to expected value type conversions (to be inserted in the SPARQL template).
  * If the conversion fails, the callback function `errorFun` is called
  */
object InputValidation {

    val calendar_separator = ":"
    val precision_separator = "-"
    val dateTimeFormat = "yyyy-MM-dd'T'HH:mm:ss"

    private val schemes = Array("http", "https")
    private val urlValidator = new UrlValidator(schemes, UrlValidator.ALLOW_LOCAL_URLS) // local urls are url encoded Knora Iris as part of the whole URL

    def toInt(s: String, errorFun: () => Nothing): Int = {
        try {
            s.toInt
        } catch {
            case e: Exception => errorFun() // value could not be converted to an Integer
        }
    }

    def toBigDecimal(s: String, errorFun: () => Nothing): BigDecimal = {
        try {
            BigDecimal(s)
        } catch {
            case e: Exception => errorFun() // value could not be converted to an Float
        }
    }

    def toDateTime(s: String, errorFun: () => Nothing) = {
        // check if a string corresponds to the expected format `dateTimeFormat`

        try {
            val formatter = DateTimeFormat.forPattern(dateTimeFormat)
            DateTime.parse(s, formatter).toString(formatter)
        } catch {
            case e: Exception => errorFun() // value could not be converted to a valid DateTime using the specified format
        }
    }

    def toIri(s: String, errorFun: () => Nothing): IRI = {
        val urlEncodedStr = encodeAllowEscapes(s)

        if (urlValidator.isValid(urlEncodedStr)) {
            urlEncodedStr
        } else {
            errorFun()
        }
    }

    /**
      *
      * Makes a string safe to be entered in the triplestore by escaping special chars.
      *
      * If the param `revert` is set to `true`, this operation is reverted.
      *
      * @param s        the string to be entered in the triplestore.
      * @param errorFun the error
      * @param revert   if set to `true`, the escaping is reverted. This is useful when a string is read back from the triplestore.
      * @return a [[String]].
      */
    def toSparqlEncodedString(s: String, errorFun: () => Nothing, revert: Boolean = false): String = {
        if (s.isEmpty || s.contains("\r")) errorFun()

        // http://www.morelab.deusto.es/code_injection/

        val input = Array(
            "\\",
            "\"",
            "'",
            "\t",
            "\n"
        )

        val output = Array(
            "\\\\",
            "\\\"",
            "\\'",
            "\\t",
            "\\n"
        )

        if (!revert) {
            StringUtils.replaceEach(
                s,
                input,
                output
            )
        } else {
            StringUtils.replaceEach(
                s,
                output,
                input
            )
        }
    }

    def toGeometryString(s: String, errorFun: () => Nothing): String = {
        // TODO: For now, we just make sure that the string is valid JSON. We should stop JSON in the triplestore, and represent geometry in RDF instead (issue 169).

        try {
            JsonParser(s)
            s
        } catch {
            case e: Exception => errorFun()
        }
    }

    def toColor(s: String, errorFun: () => Nothing): String = {

        val pattern = "^#(?:[0-9a-fA-F]{3}){1,2}$".r // http://stackoverflow.com/questions/1636350/how-to-identify-a-given-string-is-hex-color-format

        pattern.findFirstIn(s) match {
            case Some(datestr) => datestr
            case None => errorFun() // not a valid color hex value string
        }
    }

    def toDate(s: String, errorFun: () => Nothing): String = {
        // TODO: how to deal with dates BC -> ERA

        // TODO: how to treat invalid dates (e.g. 29 February)

        // TODO: import calendars instead of hardcoding them

        // Calendar:YYYY[-MM[-DD]][:YYYY[-MM[-DD]]]
        val pattern = "^(GREGORIAN|JULIAN)" +
            calendar_separator + // calendar name
            "\\d{1,4}(" + // year
            precision_separator +
            "\\d{1,2}(" + // month
            precision_separator +
            "\\d{1,2})?)?(" + // day
            calendar_separator + // separator if a period is given
            "\\d{1,4}(" + // year 2
            precision_separator +
            "\\d{1,2}(" + // month 2
            precision_separator +
            "\\d{1,2})?)?)?$" // day 2

        // if the pattern doesn't match (=> None), the date string is invalid
        pattern.r.findFirstIn(s) match {
            case Some(value) => value
            case None => errorFun() // calling this function throws an error
        }
    }

    def toBoolean(s: String, errorFun: () => Nothing): Boolean = {
        try {
            s.toBoolean
        } catch {
            case e: Exception => errorFun() // value could not be converted to Boolean
        }
    }

    // TODO: Move to test case if needed
    /*
    def main(args: Array[String]): Unit = {
        val delimiter = StringUtils.repeat('=', 80)
        val goodIriStr = "http://foo.bar.org"
        val unicodeIriStr = "http://اختبار.org"
        val badIriStr = "http://foo\". DELETE ha ha ha"
        val junkStr = "Blah blah \"blah\" and 'blah'.\nAnd more \\\" blah."

        println("Good IRI string:")
        println(goodIriStr)
        println("Validator result: " + urlValidator.isValid(goodIriStr))
        println(delimiter)

        println("Unicode IRI string:")
        println(unicodeIriStr)
        println("Validator result: " + urlValidator.isValid(unicodeIriStr))
        println(delimiter)

        println("Bad IRI string:")
        println(badIriStr)
        println("Validator result: " + urlValidator.isValid(badIriStr))
        println(delimiter)

        println("Junk string:")
        println(junkStr)
        println(delimiter)
        println("Junk string, encoded:")
        println(toSparqlEncodedString(junkStr))
    }
    */


    /**
      * Map over all standoff tags to collect IRIs that are referred to by linking standoff tags.
      *
      * @param standoffTags The list of [[StandoffTagV1]].
      * @return a set of Iris referred to in the [[StandoffTagV1]].
      */
    def getResourceIrisFromStandoffTags(standoffTags: Seq[StandoffTagV1]): Set[IRI] = {
        standoffTags.foldLeft(Set.empty[IRI]) {
            case (acc: Set[IRI], standoffNode: StandoffTagV1) =>

                standoffNode match {

                    case node: StandoffTagV1 if node.dataType.isDefined && node.dataType.get == StandoffDataTypeClasses.StandoffLinkTag =>
                        acc + node.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.StandoffTagHasLink).getOrElse(throw NotFoundException(s"${OntologyConstants.KnoraBase.StandoffTagHasLink} was not found in $node")).stringValue

                    case _ => acc
                }
        }
    }

    /**
      * Turn a possibly empty value returned by the triplestore into a Boolean value.
      * Returns false if the value is empty or if the given String is cannot be converted to a Boolean `true`.
      *
      * @param maybe the value returned by the triplestore.
      * @return a Boolean.
      */
    def optionStringToBoolean(maybe: Option[String]): Boolean = maybe.exists(_.toBoolean)


    /**
      *
      * @param settings   Knora application settings.
      * @param binaryData the binary file data to be saved.
      * @return the location where the file has been written to.
      */
    def saveFileToTmpLocation(settings: SettingsImpl, binaryData: Array[Byte]): File = {

        val fileName = createTempFile(settings)
        // write given file to disk
        Files.write(fileName.toPath, binaryData)

        fileName
    }

    /**
      * Creates an empty file in the default temporary-file directory specified in the 'settings'.
      *
      * @param settings
      * @return the location where the file has been written to.
      */
    def createTempFile(settings: SettingsImpl): File = {

        // check if the location for writing temporary files exists
        if (!Files.exists(Paths.get(settings.tmpDataDir))) {
            throw FileWriteException(s"Data directory ${
                settings.tmpDataDir
            } does not exist on server")
        }

        val file: File = File.createTempFile("tmp_", ".bin", new File(settings.tmpDataDir))

        if (!file.canWrite)
            throw FileWriteException(s"File ${file} cannot be written.")
        file
    }

    def deleteFileFromTmpLocation(fileName: File, log: LoggingAdapter): Boolean = {

        val path = fileName.toPath

        if (!fileName.canWrite) {
            val ex = FileWriteException(s"File $path cannot be deleted.")
            log.error(ex, ex.getMessage)
        }

        Files.deleteIfExists(path)
    }
}