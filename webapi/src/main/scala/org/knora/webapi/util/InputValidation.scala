/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.valuemessages.{CreateRichtextV1, StandoffPositionV1}


/**
  * Do save String to expected value type conversions (to be inserted in the SPARQL template).
  * If the conversion fails, the callback function `errorFun` is called
  */
object InputValidation {

    val calendar_separator = ":"
    val precision_separator = "-"
    val dateTimeFormat = "yyyy-MM-dd'T'HH:mm:ss"

    private val schemes = Array("http", "https")
    private val urlValidator = new UrlValidator(schemes)

    def toInt(s: String, errorFun: () => Nothing): Int = {
        try {
            s.toInt
        } catch {
            case e: Exception => errorFun() // value could not be converted to an Integer
        }
    }

    def toDouble(s: String, errorFun: () => Nothing): Double = {
        try {
            s.toDouble
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
        if (urlValidator.isValid(s)) {
            s
        } else {
            errorFun()
        }
    }

    def toSparqlEncodedString(s: String): String = {
        // http://www.morelab.deusto.es/code_injection/
        // TODO: if the user submits backslashes, could the possibly neutralize the backslashes we insert?

        StringUtils.replaceEach(
            s,
            Array(
                "\\",
                "\"",
                "'",
                "\t",
                "\n",
                "\r"
            ),
            Array(
                "\\\\",
                "\\\"",
                "\\'",
                "\\t",
                "\\n",
                "\\r"
            )
        )
    }

    def toGeometryString(s: String, errorFun: () => Nothing) = {
        // TODO: here, we expect a serialized JSON object

        s

    }

    def toColor(s: String, errorFun: () => Nothing) = {

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
      * Validate textattr (member of [[CreateRichtextV1]]): convert every attribute name to a save String
      * and process each StandoffPositionV1's arguments.
      *
      * @param textattr text attributes sent by the client as part of a richtext object
      * @return validated text attributes
      */
    def validateTextattr(textattr: Map[String, Seq[StandoffPositionV1]]): Map[String, Seq[StandoffPositionV1]] = {
        textattr.map {
            case (attr: String, positions: Seq[StandoffPositionV1]) => (InputValidation.toSparqlEncodedString(attr), positions.map {
                case (position: StandoffPositionV1) => StandoffPositionV1(start = position.start, end = position.end, href = position.href match {
                    case Some(href) => Some(InputValidation.toIri(href, () => throw BadRequestException(s"Invalid Knora resource Iri $href")))
                    case _ => None
                }, resid = position.resid match {
                    case Some(resid) => Some(InputValidation.toIri(resid, () => throw BadRequestException(s"Invalid Knora resource Iri $resid")))
                    case _ => None
                })
            })
        }
    }

    /**
      * Validate resource_references (member of [[CreateRichtextV1]]): all references must be valid Knora Iris.
      *
      * @param resRefs resource references sent by the client as patr of a richtext object
      * @return validate resource references
      */
    def validateResourceReference(resRefs: Seq[IRI]): Seq[IRI] = {
        resRefs.map {
            case (ref: IRI) => InputValidation.toIri(ref, () => throw BadRequestException(s"Invalid Knora resource IRI $ref"))
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
      * @param settings Knora application settings.
      * @param binaryData the binary file data to be saved.
      * @return the location where the file has been written to.
      */
    def saveFileToTmpLocation(settings: SettingsImpl, binaryData: Array[Byte]): File = {

        // check if the location for writing temporary files exists
        if (!Files.exists(Paths.get(settings.tmpDataDir))) {
            throw FileWriteException(s"Data directory ${
                settings.tmpDataDir
            } does not exist on server")
        }

        val fileName: File = File.createTempFile("tmp_", ".bin", new File(settings.tmpDataDir))

        if (!fileName.canWrite) throw FileWriteException(s"File ${fileName} cannot be written.")

        // write given file to disk
        Files.write(fileName.toPath, binaryData)

        fileName
    }

    def deleteFileFromTmpLocation(fileName: File): Boolean = {

        val path = fileName.toPath

        if (!fileName.canWrite) throw FileWriteException(s"File ${path} cannot be deleted.")

        Files.deleteIfExists(path)

    }

}