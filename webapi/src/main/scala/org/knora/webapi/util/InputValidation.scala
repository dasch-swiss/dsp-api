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
import java.util.UUID

import akka.event.LoggingAdapter
import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.messages.v1.responder.valuemessages.{CreateRichtextV1, StandoffPositionV1, TextValueV1}
import org.knora.webapi.twirl.{StandoffTagIriAttributeV1, StandoffTagStringAttributeV1, StandoffTagV1}
import spray.json.JsonParser
import com.google.gwt.safehtml.shared.UriUtils._


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
      * @param s the string to be entered in the triplestore.
      * @param errorFun the error
      * @param revert if set to `true`, the escaping is reverted. This is useful when a string is read back from the triplestore.
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
      * An enumeration of the possible names of a standoff tag submitted as a JSON `textattr`. Note: do not use the `withName` method to get instances
      * of the values of this enumeration; use `lookup` instead, because it reports errors better.
      */
    object TextattrV1 extends Enumeration {

        // internal name / standoff tag name in JSON representation  /  corresponding HTML tag (generated by the GUI)
        val paragraph = Value("p")
        // <p>...</p>
        val italic = Value("italic")
        // <em>...</em>
        val bold = Value("bold")
        // <strong>...</strong>
        val underline = Value("underline")
        // <u>...</u>
        val strikethrough = Value("strikethrough")
        // <s>...</s>
        val link = Value("_link")
        // <a>...</a>
        val header1 = Value("h1")
        // <h1>...</h1>
        val header2 = Value("h2")
        // <h2>...</h2>
        val header3 = Value("h3")
        // <h3>...</h3>
        val header4 = Value("h4")
        // <h4>...</h4>
        val header5 = Value("h5")
        // <h5>...</h5>
        val header6 = Value("h6")
        // <h6>...</h6>
        val superscript = Value("sup")
        // <sup>...</sup>
        val subscript = Value("sub")
        // <sub>...</sub>
        val orderedList = Value("ol")
        // <ol>...</ol>
        val unorderedList = Value("ul")
        // <ul>...</ul>
        val listElement = Value("li")
        // <li>...</li>
        val styleElement = Value("style") // <span>...</span>

        val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

        /**
          * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
          * [[BadRequestException]].
          *
          * @param name     the name of the value.
          * @param errorFun the function to be called in case of an error.
          * @return the requested value.
          */
        def lookup(name: String, errorFun: () => Nothing): Value = {
            valueMap.get(name) match {
                case Some(value) => value
                case None => errorFun()
            }
        }

        /**
          * Maps standoff tag IRIs to this enumeration's values.
          */
        val IriToEnumValue: Map[IRI, TextattrV1.Value] = new ErrorHandlingMap(Map(
            OntologyConstants.Standoff.StandoffParagraphTag -> paragraph,
            OntologyConstants.Standoff.StandoffItalicTag -> italic,
            OntologyConstants.Standoff.StandoffBoldTag -> bold,
            OntologyConstants.Standoff.StandoffUnderlineTag -> underline,
            OntologyConstants.Standoff.StandoffStrikethroughTag -> strikethrough,
            OntologyConstants.KnoraBase.StandoffLinkTag -> link,
            OntologyConstants.KnoraBase.StandoffUriTag -> link,
            OntologyConstants.Standoff.StandoffHeader1Tag -> header1,
            OntologyConstants.Standoff.StandoffHeader2Tag -> header2,
            OntologyConstants.Standoff.StandoffHeader3Tag -> header3,
            OntologyConstants.Standoff.StandoffHeader4Tag -> header4,
            OntologyConstants.Standoff.StandoffHeader5Tag -> header5,
            OntologyConstants.Standoff.StandoffHeader6Tag -> header6,
            OntologyConstants.Standoff.StandoffSuperscriptTag -> superscript,
            OntologyConstants.Standoff.StandoffSubscriptTag -> subscript,
            OntologyConstants.Standoff.StandoffOrderedListTag -> orderedList,
            OntologyConstants.Standoff.StandoffUnorderedListTag -> unorderedList,
            OntologyConstants.Standoff.StandoffListElementTag -> listElement,
            OntologyConstants.Standoff.StandoffStyleElementTag -> styleElement
        ), { key => throw InconsistentTriplestoreDataException(s"Invalid standoff tag IRI: $key") })

        /**
          * Maps this enumeration's values to standoff tag IRIs.
          */
        val EnumValueToIri: Map[TextattrV1.Value, IRI] = new ErrorHandlingMap(IriToEnumValue.map(_.swap), { key => throw InconsistentTriplestoreDataException(s"Invalid standoff tag name: $key") })

        def textattrToStandoffTagV1(tagname: String, positions: Seq[StandoffPositionV1]): Seq[StandoffTagV1] = {

            val standoffTagName: TextattrV1.Value = TextattrV1.lookup(tagname, () => throw BadRequestException(s"Standoff tag not supported: $tagname"))

            // TODO: this code is going to be removed once the routes natively accept XML
            standoffTagName match {
                // depending on whether it is a linking tag or not, process the arguments
                case linkingTag: TextattrV1.Value if linkingTag == TextattrV1.link =>
                    // it is a linking tag:
                    // "href" is required for all positions belonging to this tag, "resid" may be given in case it is an internal link to a Knora resource
                    positions.map {
                        position =>
                            position match {
                                case internalLink if internalLink.resid.isDefined && internalLink.href.isDefined =>
                                    val internalLink = StandoffTagIriAttributeV1(
                                        standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink,
                                        value = InputValidation.toIri(position.resid.get, () => throw BadRequestException(s"Invalid Knora resource Iri in attribute resid: ${position.resid}"))
                                    )

                                    StandoffTagV1(dataType = Some(StandoffDataTypeClasses.StandoffLinkTag), standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag, uuid = UUID.randomUUID.toString, originalXMLID = None, startPosition = position.start, endPosition = position.end, attributes = List(internalLink))

                                case internalLink if internalLink.href.isDefined =>
                                    val reference = StandoffTagIriAttributeV1(
                                        standoffPropertyIri = OntologyConstants.KnoraBase.ValueHasUri,
                                        value = InputValidation.toIri(position.href.get, () => throw BadRequestException(s"Invalid URL in attribute href: ${position.href}"))
                                    )

                                    StandoffTagV1(dataType = Some(StandoffDataTypeClasses.StandoffUriTag), standoffTagClassIri = OntologyConstants.KnoraBase.StandoffUriTag, uuid = UUID.randomUUID.toString, originalXMLID = None, startPosition = position.start, endPosition = position.end, attributes = List(reference))

                                case _ => throw BadRequestException("no resid or href given for linking tag")
                            }
                    }
                case nonLinkingTag: TextattrV1.Value =>
                    // only "start" and "end" are required, no further members allowed
                    positions.map {

                        position: StandoffPositionV1 =>

                            if (position.resid.isDefined || position.href.isDefined) {
                                throw BadRequestException(s"members 'resid' or 'href' given for non linking standoff tag $nonLinkingTag")
                            }

                            StandoffTagV1(standoffTagClassIri = EnumValueToIri(nonLinkingTag), startPosition = position.start, endPosition = position.end, uuid = UUID.randomUUID.toString, originalXMLID = None)
                    }
            }

        }

    }

    /**
      * Represents the optional components of a [[TextValueV1]]: `textattr` and `resource_reference`.
      *
      * @param textattr           the standoff tags of a [[TextValueV1]].
      * @param resource_reference the resources referred to by a [[TextValueV1]]
      */
    case class RichtextComponents(textattr: Seq[org.knora.webapi.twirl.StandoffTagV1], resource_reference: Set[IRI])

    /**
      * Processes the optional components of a [[TextValueV1]]: `textattr` and `resource_reference`.
      * Returns a [[RichtextComponents]] that can be used to create a [[TextValueV1]].
      *
      * @param richtext the data submitted by the client.
      * @return a [[RichtextComponents]].
      */
    def handleRichtext(richtext: CreateRichtextV1): RichtextComponents = {
        import org.knora.webapi.messages.v1.responder.valuemessages.ApiValueV1JsonProtocol._

        // convert the given string into a Seq[StandoffTagV1]
        val textattr: Seq[StandoffTagV1] = richtext.textattr match {
            case Some(textattrString: String) =>

                // convert each `StandoffPositionV1` to a `StandoffTagV1` and append it to the Seq
                JsonParser(textattrString).convertTo[Map[String, Seq[StandoffPositionV1]]].foldLeft(Seq.empty[StandoffTagV1]) {
                    case (acc, (tagname: String, standoffPos: Seq[StandoffPositionV1])) =>

                        acc ++ TextattrV1.textattrToStandoffTagV1(tagname, standoffPos)
                }

            case None =>
                Seq.empty[StandoffTagV1]
        }

        // make sure that `resource_reference` contains valid IRIs
        val resourceReference: Set[IRI] = richtext.resource_reference match {
            case Some(resRefs: Seq[IRI]) =>
                InputValidation.validateResourceReference(resRefs)
            case None =>
                Set.empty[IRI]
        }

        // collect the links from the standoff linking tags
        val resIrisfromStandoffLinkTags: Set[IRI] = getResourceIrisFromStandoffTags(textattr)

        // check if resources references in standoff link tags exactly correspond to those submitted in richtext.resource_reference
        if (resourceReference != resIrisfromStandoffLinkTags) throw BadRequestException("Submitted resource references in standoff link tags and in member 'resource_reference' are inconsistent")

        RichtextComponents(textattr = textattr, resource_reference = resourceReference)

    }

    /**
      * Validate resource_reference (member of [[CreateRichtextV1]]): all references must be valid Knora Iris.
      *
      * @param resRefs resource references sent by the client as patr of a richtext object
      * @return validate resource references
      */
    private def validateResourceReference(resRefs: Seq[IRI]): Set[IRI] = {
        resRefs.map {
            case (ref: IRI) => InputValidation.toIri(ref, () => throw BadRequestException(s"Invalid Knora resource IRI $ref"))
        }.toSet
    }

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
                        acc + node.attributes.find(_.standoffPropertyIri == OntologyConstants.KnoraBase.StandoffTagHasLink).getOrElse(throw NotFoundException(s"${OntologyConstants.KnoraBase.StandoffTagHasLink} was not found in $node")).stringValue()

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