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

import scala.util.matching.Regex

/**
  * Provides the singleton instance of [[StringFormatter]], as well as string formatting constants.
  */
object StringFormatter {
    /**
      * A container for an XML import namespace and its prefix label.
      *
      * @param namespace   the namespace.
      * @param prefixLabel the prefix label.
      */
    case class XmlImportNamespaceInfoV1(namespace: IRI, prefixLabel: String)

    // A non-printing delimiter character, Unicode INFORMATION SEPARATOR ONE, that should never occur in data.
    val INFORMATION_SEPARATOR_ONE = '\u001F'

    // A non-printing delimiter character, Unicode INFORMATION SEPARATOR TWO, that should never occur in data.
    val INFORMATION_SEPARATOR_TWO = '\u001E'

    // A non-printing delimiter character, Unicode INFORMATION SEPARATOR TWO, that should never occur in data.
    val INFORMATION_SEPARATOR_THREE = '\u001D'

    // A non-printing delimiter character, Unicode INFORMATION SEPARATOR TWO, that should never occur in data.
    val INFORMATION_SEPARATOR_FOUR = '\u001C'

    // a separator to be inserted in the XML to separate nodes from one another
    // this separator is only used temporarily while XML is being processed
    val PARAGRAPH_SEPARATOR = '\u2029'

    // Control sequences for changing text colour in terminals.
    val ANSI_RED = "\u001B[31m"
    val ANSI_GREEN = "\u001B[32m"
    val ANSI_YELLOW = "\u001B[33m"
    val ANSI_RESET = "\u001B[0m"

    /**
      * Separates the calendar name from the rest of a Knora date.
      */
    val CalendarSeparator: String = ":"

    /**
      * Separates year, month, and day in a Knora date.
      */
    val PrecisionSeparator: String = "-"

    /**
      * Separates a date (year, month, day) from the era in a Knora date.
      */
    val EraSeparator: String = " "

    /**
      * Before Christ (equivalent to BCE)
      */
    val Era_BC: String = "BC"

    /**
      * Before Common Era (equivalent to BC)
      */
    val Era_BCE: String = "BCE"

    /**
      * Anno Domini (equivalent to CE)
      */
    val Era_AD: String = "AD"

    /**
      * Common Era (equivalent to AD)
      */
    val Era_CE: String = "CE"


    var maybeInstance: Option[StringFormatter] = None

    /**
      * Gets the singleton instance of [[StringFormatter]].
      */
    def getInstance: StringFormatter = {
        maybeInstance match {
            case Some(instance) => instance
            case None => throw AssertionException("StringFormatter not yet initialised")
        }
    }

    /**
      * Initialises the singleton instance of [[StringFormatter]].
      *
      * @param settings the application settings.
      */
    def init(settings: SettingsImpl): Unit = {
        this.synchronized {
            maybeInstance match {
                case Some(_) => ()
                case None => maybeInstance = Some(new StringFormatter(settings))
            }
        }
    }
}


/**
  * Handles string formatting and validation.
  */
class StringFormatter private(settings: SettingsImpl) {
    import StringFormatter._

    // The expected format of a Knora date.
    // Calendar:YYYY[-MM[-DD]][ EE][:YYYY[-MM[-DD]][ EE]]
    // EE being the era: one of BC or AD
    private val KnoraDateRegex: Regex = ("""^(GREGORIAN|JULIAN)""" +
        CalendarSeparator + // calendar name
        """(?:[1-9][0-9]{0,3})(""" + // year
        PrecisionSeparator +
        """(?!00)[0-9]{1,2}(""" + // month
        PrecisionSeparator +
        """(?!00)[0-9]{1,2})?)?( BC| AD| BCE| CE)?(""" + // day
        CalendarSeparator + // separator if a period is given
        """(?:[1-9][0-9]{0,3})(""" + // year 2
        PrecisionSeparator +
        """(?!00)[0-9]{1,2}(""" + // month 2
        PrecisionSeparator +
        """(?!00)[0-9]{1,2})?)?( BC| AD| BCE| CE)?)?$""").r // day 2

    // The expected format of a datetime.
    private val dateTimeFormat = "yyyy-MM-dd'T'HH:mm:ss"

    // Characters that are escaped in strings that will be used in SPARQL.
    private val SparqlEscapeInput = Array(
        "\\",
        "\"",
        "'",
        "\t",
        "\n"
    )

    // Escaped characters as they are used in SPARQL.
    private val SparqlEscapeOutput = Array(
        "\\\\",
        "\\\"",
        "\\'",
        "\\t",
        "\\n"
    )

    // A regex for matching hexadecimal color codes.
    // http://stackoverflow.com/questions/1636350/how-to-identify-a-given-string-is-hex-color-format
    private val ColorRegex: Regex = "^#(?:[0-9a-fA-F]{3}){1,2}$".r

    // A regex sub-pattern for ontology prefix labels and local entity names. According to
    // <https://www.w3.org/TR/turtle/#prefixed-name>, a prefix label in Turtle must be a valid XML NCName
    // <https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName>. Knora also requires a local entity name to
    // be an XML NCName.
    private val NCNamePattern: String =
    """[\p{L}_][\p{L}0-9_.-]*"""

    // A regex for matching a string containing only an ontology prefix label or a local entity name.
    private val NCNameRegex: Regex = ("^" + NCNamePattern + "$").r

    // A regex for entity IRIs in knora-base.
    private val KnoraBaseOntologyEntityRegex: Regex = (
        "^" + OntologyConstants.KnoraBase.KnoraBasePrefixExpansion +
            "(" + NCNamePattern + ")$"
        ).r

    // A regex for the URL path of a built-in ontology.
    private val BuiltInApiV2OntologyUrlPathRegex: Regex = (
        "^" + "/ontology/knora-api(" + OntologyConstants.KnoraApiV2WithValueObjects.VersionSegment + "|" + OntologyConstants.KnoraApiV2Simple.VersionSegment + ")$"
        ).r

    // A regex for the URL path of a project-specific ontology.
    private val ProjectSpecificApiV2OntologyUrlPathRegex: Regex = (
        "^" + "/ontology/(" + NCNamePattern + ")(" + OntologyConstants.KnoraApiV2WithValueObjects.VersionSegment + "|" + OntologyConstants.KnoraApiV2Simple.VersionSegment + ")$"
        ).r

    // A regex for entity IRIs in built-in external ontologies (knora-api).
    // This works for both cases: with value object and simple.
    private val BuiltInApiV2OntologyEntityRegex: Regex = (
        "^" + OntologyConstants.KnoraApi.ApiOntologyStart +
            OntologyConstants.KnoraApi.KnoraApiOntologyLabel + "(" + OntologyConstants.KnoraApiV2WithValueObjects.VersionSegment + "|" + OntologyConstants.KnoraApiV2Simple.VersionSegment + ")" +
            "#(" + NCNamePattern + ")$"
        ).r

    private val BuiltInApiV2SimpleOntologyEntityRegex: Regex = (
        "^" + OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion +
            "(" + NCNamePattern + ")$"
        ).r

    private val BuiltInApiV2WithValueObjectsOntologyEntityRegex: Regex = (
        "^" + OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion +
            "(" + NCNamePattern + ")$"
        ).r

    // A regex for project-specific internal ontologies.
    private val ProjectSpecificInternalOntologyRegex: Regex = (
        "^" + OntologyConstants.KnoraInternal.InternalOntologyStart +
            "(" + NCNamePattern + ")$"
        ).r

    // A regex for entity IRIs in project-specific internal ontologies.
    private val ProjectSpecificInternalOntologyEntityRegex: Regex = (
        "^" + OntologyConstants.KnoraInternal.InternalOntologyStart +
            "(" + NCNamePattern + ")#(" + NCNamePattern + ")$"
        ).r

    // The start of a project-specific external ontology IRI that is served by this API server.
    private val ProjectSpecificApiV2OntologyStart: String = settings.knoraApiHttpBaseUrl + "/ontology/"


    // A regex for project-specific external ontology IRIs with the simple schema.
    private val ProjectSpecificApiV2SimpleOntologyRegex: Regex = (
        "^" + ProjectSpecificApiV2OntologyStart +
            "(" + NCNamePattern + ")" + OntologyConstants.KnoraApiV2Simple.VersionSegment + "$"
        ).r

    // A regex for project-specific external ontology IRIs with the value object schema.
    private val ProjectSpecificApiV2WithValueObjectsOntologyRegex: Regex = (
        "^" + ProjectSpecificApiV2OntologyStart +
            "(" + NCNamePattern + ")" + OntologyConstants.KnoraApiV2WithValueObjects.VersionSegment + "$"
        ).r

    // A regex for entity IRIs in project-specific external ontologies.
    // This works for both cases: with value object and simple.
    private val ProjectSpecificApiV2OntologyEntityRegex: Regex = (
        "^" + ProjectSpecificApiV2OntologyStart +
            "(" + NCNamePattern + ")" + "(" + OntologyConstants.KnoraApiV2WithValueObjects.VersionSegment + "|" + OntologyConstants.KnoraApiV2Simple.VersionSegment + ")" +
            "#(" + NCNamePattern + ")$"
        ).r

    // A regex for external project-specific knora-api v2 simple entity IRIs.
    private val ProjectSpecificApiV2SimpleOntologyEntityRegex: Regex = (
        "^" + ProjectSpecificApiV2OntologyStart +
            "(" + NCNamePattern + ")" + OntologyConstants.KnoraApiV2Simple.VersionSegment +
            "#(" + NCNamePattern + ")$"
        ).r

    // A regex for external project-specific knora-api v2 with value object entity IRIs.
    private val ProjectSpecificApiV2WithValueObjectsOntologyEntityRegex: Regex = (
        "^" + ProjectSpecificApiV2OntologyStart +
            "(" + NCNamePattern + ")" + OntologyConstants.KnoraApiV2WithValueObjects.VersionSegment +
            "#(" + NCNamePattern + ")$"
        ).r

    // A regex for project-specific XML import namespaces.
    private val ProjectSpecificXmlImportNamespaceRegex: Regex = (
        "^" + OntologyConstants.KnoraXmlImportV1.ProjectSpecificXmlImportNamespace.XmlImportNamespaceStart +
            "(" + NCNamePattern + ")" +
            OntologyConstants.KnoraXmlImportV1.ProjectSpecificXmlImportNamespace.XmlImportNamespaceEnd + "$"
        ).r

    // In XML import data, a property from another ontology is referred to as prefixLabel__localName. This regex parses
    // that pattern.
    private val PropertyFromOtherOntologyInXmlImportRegex: Regex = (
        "^(" + NCNamePattern + ")__(" + NCNamePattern + ")$"
        ).r

    // In XML import data, a standoff link tag that refers to a resource described in the import must have the
    // form defined by this regex.
    private val StandoffLinkReferenceToClientIDForResourceRegex: Regex = (
        "^ref:(" + NCNamePattern + ")$"
        ).r

    // Valid URL schemes.
    private val schemes = Array("http", "https")

    // A validator for URLs.
    private val urlValidator = new UrlValidator(schemes, UrlValidator.ALLOW_LOCAL_URLS) // local urls are url encoded Knora Iris as part of the whole URL

    /**
      * Checks that a string represents a valid integer.
      *
      * @param s        the string to be checked.
      * @param errorFun a function that throws an exception. It will be called if the string does not represent a
      *                 valid integer.
      * @return the integer value of the string.
      */
    def toInt(s: String, errorFun: () => Nothing): Int = {
        try {
            s.toInt
        } catch {
            case e: Exception => errorFun() // value could not be converted to an Integer
        }
    }

    /**
      * Checks that a string represents a valid decimal number.
      *
      * @param s        the string to be checked.
      * @param errorFun a function that throws an exception. It will be called if the string does not represent a
      *                 valid decimal number.
      * @return the decimal value of the string.
      */
    def toBigDecimal(s: String, errorFun: () => Nothing): BigDecimal = {
        try {
            BigDecimal(s)
        } catch {
            case e: Exception => errorFun() // value could not be converted to a decimal
        }
    }

    /**
      * Checks that a string represents a valid datetime.
      *
      * @param s        the string to be checked.
      * @param errorFun a function that throws an exception. It will be called if the string does not represent
      *                 a valid datetime.
      * @return the same string.
      */
    def toDateTime(s: String, errorFun: () => Nothing): String = {
        // check if a string corresponds to the expected format `dateTimeFormat`

        try {
            val formatter = DateTimeFormat.forPattern(dateTimeFormat)
            DateTime.parse(s, formatter).toString(formatter)
        } catch {
            case e: Exception => errorFun() // value could not be converted to a valid DateTime using the specified format
        }
    }

    /**
      * Checks that a string represents a valid IRI.
      *
      * @param s        the string to be checked.
      * @param errorFun a function that throws an exception. It will be called if the string does not represent a valid
      *                 IRI.
      * @return the same string.
      */
    def toIri(s: String, errorFun: () => Nothing): IRI = {
        val urlEncodedStr = encodeAllowEscapes(s)

        if (urlValidator.isValid(urlEncodedStr)) {
            urlEncodedStr
        } else {
            errorFun()
        }
    }

    /**
      * Checks that a string represents a valid resource identifier in a standoff link.
      *
      * @param s               the string to be checked.
      * @param acceptClientIDs if `true`, the function accepts either an IRI or an XML NCName prefixed by `ref:`.
      *                        The latter is used to refer to a client's ID for a resource that is described in an XML bulk import.
      *                        If `false`, only an IRI is accepted.
      * @param errorFun        a function that throws an exception. It will be called if the form of the string is invalid.
      * @return the same string.
      */
    def toStandoffLinkResourceReference(s: String, acceptClientIDs: Boolean, errorFun: () => Nothing): IRI = {
        if (acceptClientIDs) {
            s match {
                case StandoffLinkReferenceToClientIDForResourceRegex(_) => s
                case _ => toIri(s, () => errorFun())
            }
        } else {
            toIri(s, () => errorFun())
        }
    }

    /**
      * Checks whether a string is a reference to a client's ID for a resource described in an XML bulk import.
      *
      * @param s the string to be checked.
      * @return `true` if the string is an XML NCName prefixed by `ref:`.
      */
    def isStandoffLinkReferenceToClientIDForResource(s: String): Boolean = {
        s match {
            case StandoffLinkReferenceToClientIDForResourceRegex(_) => true
            case _ => false
        }
    }

    /**
      * Accepts a reference from a standoff link to a resource. The reference may be either a real resource IRI
      * (referring to a resource that already exists) or a client's ID for a resource that doesn't yet exist and is
      * described in an XML bulk import. Returns the real IRI of the target resource.
      *
      * @param iri                             an IRI from a standoff link, either in the form of a real resource IRI or in the form of
      *                                        a reference to a client's ID for a resource.
      * @param clientResourceIDsToResourceIris a map of client resource IDs to real resource IRIs.
      */
    def toRealStandoffLinkTargetResourceIri(iri: IRI, clientResourceIDsToResourceIris: Map[String, IRI]): String = {
        iri match {
            case StandoffLinkReferenceToClientIDForResourceRegex(clientResourceID) => clientResourceIDsToResourceIris(clientResourceID)
            case _ => iri
        }
    }

    /**
      * Makes a string safe to be entered in the triplestore by escaping special chars.
      *
      * If the param `revert` is set to `true`, the string is unescaped.
      *
      * @param s        a string.
      * @param errorFun a function that throws an exception. It will be called if the string is empty or contains
      *                 a carriage return (`\r`).
      * @param revert   if set to `true`, the escaping is reverted. This is useful when a string is read back from the triplestore.
      * @return the same string, escaped or unescaped as requested.
      */
    def toSparqlEncodedString(s: String, errorFun: () => Nothing, revert: Boolean = false): String = {
        if (s.isEmpty || s.contains("\r")) errorFun()

        // http://www.morelab.deusto.es/code_injection/

        if (!revert) {
            StringUtils.replaceEach(
                s,
                SparqlEscapeInput,
                SparqlEscapeOutput
            )
        } else {
            StringUtils.replaceEach(
                s,
                SparqlEscapeOutput,
                SparqlEscapeInput
            )
        }
    }

    /**
      * Checks that a geometry string contains valid JSON.
      *
      * @param s        a geometry string.
      * @param errorFun a function that throws an exception. It will be called if the string does not contain valid
      *                 JSON.
      * @return the same string.
      */
    def toGeometryString(s: String, errorFun: () => Nothing): String = {
        // TODO: For now, we just make sure that the string is valid JSON. We should stop JSON in the triplestore, and represent geometry in RDF instead (issue 169).

        try {
            JsonParser(s)
            s
        } catch {
            case e: Exception => errorFun()
        }
    }

    /**
      * Checks that a hexadecimal color code string is valid.
      *
      * @param s        a string containing a hexadecimal color code.
      * @param errorFun a function that throws an exception. It will be called if the string does not contain a valid
      *                 hexadecimal color code.
      * @return the same string.
      */
    def toColor(s: String, errorFun: () => Nothing): String = {
        ColorRegex.findFirstIn(s) match {
            case Some(datestr) => datestr
            case None => errorFun() // not a valid color hex value string
        }
    }

    /**
      * Checks that the format of a Knora date string is valid.
      *
      * @param s        a Knora date string.
      * @param errorFun a function that throws an exception. It will be called if the date's format is invalid.
      * @return the same string.
      */
    def toDate(s: String, errorFun: () => Nothing): String = {
        // if the pattern doesn't match (=> None), the date string is formally invalid
        // Please note that this is a mere formal validation,
        // the actual validity check is done in `DateUtilV1.dateString2DateRange`
        KnoraDateRegex.findFirstIn(s) match {
            case Some(value) => value
            case None => errorFun() // calling this function throws an error
        }
    }

    /**
      * Checks that a string contains a valid boolean value.
      *
      * @param s        a string containing a boolean value.
      * @param errorFun a function that throws an exception. It will be called if the string does not contain
      *                 a boolean value.
      * @return the boolean value of the string.
      */
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
      * Turn a possibly empty string value into a boolean value.
      * Returns false if the value is empty or if the given string is cannot be converted to a Boolean `true`.
      *
      * @param maybe    an optional string representation of a boolean value.
      * @param errorFun a function that throws an exception. It will be called if the string cannot be parsed
      *                 as a boolean value.
      * @return a Boolean.
      */
    def optionStringToBoolean(maybe: Option[String], errorFun: () => Nothing): Boolean = {
        try {
            maybe.exists(_.toBoolean)
        } catch {
            case _: IllegalArgumentException => errorFun()
        }
    }

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
      * Creates an empty file in the default temporary-file directory specified in Knora's application settings.
      *
      * @param settings Knora's application settings.
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
            throw FileWriteException(s"File $file cannot be written.")
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

    /**
      * Checks that a string is a valid XML [[https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName NCName]].
      *
      * @param ncName   the string to be checked.
      * @param errorFun a function that throws an exception. It will be called if the string is invalid.
      * @return the same string.
      */
    def toNCName(ncName: String, errorFun: () => Nothing): String = {
        NCNameRegex.findFirstIn(ncName) match {
            case Some(value) => value
            case None => errorFun()
        }
    }

    /**
      * Checks that a string is valid as a project-specific ontology prefix label or entity local name, i.e. that it is
      * a valid XML NCName and does not start with `knora`.
      *
      * @param ncName   the string to be checked.
      * @param errorFun a function that throws an exception. It will be called if the string is invalid.
      * @return the same string.
      */
    def toProjectSpecificNCName(ncName: String, errorFun: () => Nothing): String = {
        if (ncName.startsWith("knora")) {
            errorFun()
        } else {
            toNCName(ncName, () => errorFun())
        }
    }

    /**
      * Converts the IRI of a project-specific internal ontology (used in the triplestore) to an XML prefix label and
      * namespace for use in data import.
      *
      * @param internalOntologyIri the IRI of the project-specific internal ontology. Any trailing # character will be
      *                            stripped before the conversion.
      * @param errorFun            a function that throws an exception. It will be called if the form of the IRI is not
      *                            valid for an internal ontology IRI.
      * @return the corresponding XML prefix label and import namespace.
      */
    def internalOntologyIriToXmlNamespaceInfoV1(internalOntologyIri: IRI, errorFun: () => Nothing): XmlImportNamespaceInfoV1 = {
        val prefixLabel = getOntologyPrefixLabelFromInternalOntologyIri(internalOntologyIri, () => errorFun())
        val namespace = OntologyConstants.KnoraXmlImportV1.ProjectSpecificXmlImportNamespace.XmlImportNamespaceStart +
            prefixLabel +
            OntologyConstants.KnoraXmlImportV1.ProjectSpecificXmlImportNamespace.XmlImportNamespaceEnd
        XmlImportNamespaceInfoV1(namespace = namespace, prefixLabel = prefixLabel)
    }

    /**
      * Converts an XML namespace (used in XML data import) to the IRI of a project-specific internal ontology (used
      * in the triplestore). The resulting IRI will not end in a # character.
      *
      * @param namespace the XML namespace.
      * @param errorFun  a function that throws an exception. It will be called if the form of the string is not
      *                  valid for a Knora XML import namespace.
      * @return the corresponding project-specific internal ontology IRI.
      */
    def xmlImportNamespaceToInternalOntologyIriV1(namespace: String, errorFun: () => Nothing): IRI = {
        namespace match {
            case ProjectSpecificXmlImportNamespaceRegex(prefixLabel) =>
                OntologyConstants.KnoraInternal.InternalOntologyStart + prefixLabel

            case _ => errorFun()
        }
    }

    /**
      * Converts a XML element name in a particular namespace (used in XML data import) to the IRI of a
      * project-specific internal ontology entity (used in the triplestore).
      *
      * @param namespace    the XML namespace.
      * @param elementLabel the XML element label.
      * @param errorFun     a function that throws an exception. It will be called if the form of the namespace is not
      *                     valid for a Knora XML import namespace.
      * @return the corresponding project-specific internal ontology entity IRI.
      */
    def xmlImportElementNameToInternalOntologyIriV1(namespace: String, elementLabel: String, errorFun: () => Nothing): IRI = {
        val ontologyIri = xmlImportNamespaceToInternalOntologyIriV1(namespace, errorFun)
        ontologyIri + "#" + elementLabel
    }

    /**
      * Given the IRI of an internal ontology entity (either in knora-base or in a project-specific ontology),
      * returns the ontology prefix label.
      *
      * @param internalEntityIri the ontology entity IRI.
      * @param errorFun          a function that throws an exception. It will be called if the form of the string is not
      *                          valid for an internal ontology entity IRI.
      * @return the ontology prefix label specified in the entity IRI.
      */
    def getOntologyPrefixLabelFromInternalEntityIri(internalEntityIri: IRI, errorFun: () => Nothing): String = {
        internalEntityIri match {
            case KnoraBaseOntologyEntityRegex(_) => OntologyConstants.KnoraBase.KnoraBaseOntologyLabel
            case ProjectSpecificInternalOntologyEntityRegex(prefixLabel, _) => prefixLabel
            case _ => errorFun()
        }
    }

    /**
      * Extracts the prefix label from the IRI of a project-specific internal ontology.
      *
      * @param internalOntologyIri the IRI of the project-specific internal ontology. Any trailing # character will be
      *                            stripped before the conversion.
      * @param errorFun            a function that throws an exception. It will be called if the form of the IRI is not
      *                            valid for an internal ontology IRI.
      * @return the corresponding prefix label.
      */
    def getOntologyPrefixLabelFromInternalOntologyIri(internalOntologyIri: IRI, errorFun: () => Nothing): String = {
        internalOntologyIri.stripSuffix("#") match {
            case ProjectSpecificInternalOntologyRegex(prefixLabel) => prefixLabel
            case _ => errorFun()
        }
    }

    /**
      * Given the IRI of an internal ontology entity (either knora-base or a project-specific ontology), returns the internal ontology IRI.
      *
      * @param internalEntityIri the ontology entity IRI.
      * @param errorFun          a function that throws an exception. It will be called if the form of the string is not
      *                          valid for an internal ontology entity IRI.
      * @return the ontology IRI portion of the entity IRI.
      */
    def getInternalOntologyIriFromInternalEntityIri(internalEntityIri: IRI, errorFun: () => Nothing): IRI = {
        internalEntityIri match {
            case KnoraBaseOntologyEntityRegex(_) => OntologyConstants.KnoraBase.KnoraBaseOntologyIri
            case ProjectSpecificInternalOntologyEntityRegex(prefixLabel, _) => OntologyConstants.KnoraInternal.InternalOntologyStart + prefixLabel
            case _ => errorFun()
        }
    }

    /**
      * Converts an external ontology name to an internal ontology IRI.
      *
      * @param ontologyName the external ontology name to be converted.
      * @return the internal ontology IRI.
      */
    private def projectSpecificOntologyNameToInternalOntologyIri(ontologyName: String): IRI = {
        OntologyConstants.KnoraInternal.InternalOntologyStart + ontologyName
    }

    /**
      * Given the IRI of an internal ontology, returns the knora-api with value object ontology IRI.
      *
      * @param internalOntologyIri the IRI of the internal ontology.
      * @param errorFun            a function that throws an exception. It will be called if the form of the string is not
      *                            valid for an internal ontology IRI.
      * @return the external ontology IRI.
      */
    def internalOntologyIriToApiV2SimpleOntologyIri(internalOntologyIri: IRI, errorFun: () => Nothing): IRI = {
        internalOntologyIri match {
            case OntologyConstants.KnoraBase.KnoraBaseOntologyIri => OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri

            case ProjectSpecificInternalOntologyRegex(ontologyName) =>
                ProjectSpecificApiV2OntologyStart + ontologyName + OntologyConstants.KnoraApiV2Simple.VersionSegment

            case _ => errorFun()
        }
    }

    /**
      * Given the IRI of an internal ontology, returns the knora-api with value object ontology IRI.
      *
      * @param internalOntologyIri the IRI of the internal ontology.
      * @param errorFun            a function that throws an exception. It will be called if the form of the string is not
      *                            valid for an internal ontology IRI.
      * @return the external ontology IRI.
      */
    def internalOntologyIriToApiV2WithValueObjectsOntologyIri(internalOntologyIri: IRI, errorFun: () => Nothing): IRI = {
        internalOntologyIri match {
            case OntologyConstants.KnoraBase.KnoraBaseOntologyIri => OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri

            case ProjectSpecificInternalOntologyRegex(ontologyName) =>
                ProjectSpecificApiV2OntologyStart + ontologyName + OntologyConstants.KnoraApiV2WithValueObjects.VersionSegment

            case _ => errorFun()
        }
    }

    /**
      * Converts an external entity name to an internal entity IRI.
      *
      * @param ontologyName   the name of the ontology the entity belongs.
      * @param entityName the name of the entity.
      * @return the internal entity IRI.
      */
    private def externalEntityNameToInternalEntityIri(ontologyName: String, entityName: String) = {
        val internalOntologyName = if (ontologyName == OntologyConstants.KnoraApi.KnoraApiOntologyLabel) OntologyConstants.KnoraBase.KnoraBaseOntologyLabel else ontologyName
        OntologyConstants.KnoraInternal.InternalOntologyStart + internalOntologyName + "#" + entityName
    }

    /**
      * Given the IRI of an external knora-api v2 with value object entity (in a built-in or project-specific ontology),
      * returns the internal entity IRI.
      *
      * @param externalEntityIri an external entity IRI.
      * @param errorFun          a function that throws an exception. It will be called if the form of the string is not
      *                          valid for an internal ontology IRI.
      * @return the internal entity IRI.
      */
    def externalApiV2WithValueObjectEntityIriToInternalEntityIri(externalEntityIri: IRI, errorFun: () => Nothing): IRI = {
        externalEntityIri match {
            case BuiltInApiV2WithValueObjectsOntologyEntityRegex(entityName) =>
                externalEntityNameToInternalEntityIri(OntologyConstants.KnoraApi.KnoraApiOntologyLabel, entityName)

            case ProjectSpecificApiV2WithValueObjectsOntologyEntityRegex(ontology, entityName) =>
                externalEntityNameToInternalEntityIri(ontology, entityName)

            case _ => errorFun()
        }
    }

    /**
      * Given the IRI of an external knora-api v2 simple entity (in a built-in or project-specific ontology), returns the internal entity IRI.
      *
      * @param externalEntityIri an external entity IRI.
      * @param errorFun          a function that throws an exception. It will be called if the form of the string is not
      *                          valid for an internal ontology IRI.
      * @return the internal entity IRI.
      */
    def externalApiV2SimpleEntityIriToInternalEntityIri(externalEntityIri: IRI, errorFun: () => Nothing): IRI = {
        externalEntityIri match {
            case BuiltInApiV2SimpleOntologyEntityRegex(entityName) =>
                externalEntityNameToInternalEntityIri(OntologyConstants.KnoraApi.KnoraApiOntologyLabel, entityName)

            case ProjectSpecificApiV2WithValueObjectsOntologyEntityRegex(ontology, entityName) =>
                externalEntityNameToInternalEntityIri(ontology, entityName)

            case _ => errorFun()
        }
    }

    /**
      * Given the IRI of an internal ontology entity (in a built-in or project-specific ontology), returns the knora-api v2 with value object entity IRI.
      *
      * @param internalEntityIri the IRI of the internal ontology entity.
      * @param errorFun          a function that throws an exception. It will be called if the internal entity IRI
      *                          is invalid.
      * @return the corresponding knora-api v2 with value object entity IRI.
      */
    def internalEntityIriToApiV2WithValueObjectEntityIri(internalEntityIri: IRI, errorFun: () => Nothing): IRI = {
        internalEntityIri match {
            case KnoraBaseOntologyEntityRegex(entityName) =>
                OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion + entityName

            case ProjectSpecificInternalOntologyEntityRegex(prefixLabel, entityName) =>
                ProjectSpecificApiV2OntologyStart + prefixLabel + OntologyConstants.KnoraApiV2WithValueObjects.VersionSegment + "#" + entityName

            case _ => errorFun()
        }
    }

    /**
      * Given the IRI of an internal ontology entity (in any ontology, even a non-Knora ontology), returns the
      * simplified knora-api v2 entity IRI.
      *
      * @param internalEntityIri the IRI of the internal ontology entity.
      * @param errorFun          a function that throws an exception. It will be called if the internal entity IRI
      *                          is invalid.
      * @return the corresponding simplified knora-api v2.
      */
    def internalEntityIriToApiV2SimpleEntityIri(internalEntityIri: IRI, errorFun: () => Nothing): IRI = {
        OntologyConstants.KnoraApiV2Simple.LiteralValueTypes.get(internalEntityIri) match {
            case Some(xsdType) => xsdType
            case None =>
                internalEntityIri match {
                    case KnoraBaseOntologyEntityRegex(entityName) =>
                        OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion + entityName

                    case ProjectSpecificInternalOntologyEntityRegex(prefixLabel, entityName) =>
                        ProjectSpecificApiV2OntologyStart + prefixLabel + OntologyConstants.KnoraApiV2Simple.VersionSegment + "#" + entityName

                    case _ => errorFun()
                }
        }
    }

    /**
      * Given the IRI of an internal ontology entity (in knora-base or a project-specific ontology), returns the local name of the entity.
      *
      * @param internalEntityIri the ontology entity IRI.
      * @param errorFun          a function that throws an exception. It will be called if the form of the string is not
      *                          valid for an internal ontology entity IRI.
      * @return the local name specified in the entity IRI.
      */
    def getEntityNameFromInternalEntityIri(internalEntityIri: IRI, errorFun: () => Nothing): String = {
        internalEntityIri match {
            case KnoraBaseOntologyEntityRegex(entityName) => entityName
            case ProjectSpecificInternalOntologyEntityRegex(_, entityName) => entityName
            case _ => errorFun()
        }
    }

    /**
      * Checks whether an IRI is the IRI of an internal ontology entity (in knora-base or a project-specific ontology).
      *
      * @param iri the IRI to be checked.
      * @return `true` if the IRI is the IRI of an internal ontology entity.
      */
    def isInternalEntityIri(iri: IRI): Boolean = {
        iri match {
            case KnoraBaseOntologyEntityRegex(_) | ProjectSpecificInternalOntologyEntityRegex(_*) => true
            case _ => false
        }
    }

    /**
      * Checks whether an IRI is the IRI of a project-specific internal ontology.
      *
      * @param iri the IRI to be checked.
      * @return `true` if the IRI is the IRI of a project-specific internal ontology.
      */
    def isProjectSpecificInternalOntologyIri(iri: IRI): Boolean = {
        iri match {
            case ProjectSpecificInternalOntologyRegex(ontologyName) if ontologyName != OntologyConstants.KnoraBase.KnoraBaseOntologyLabel => true
            case _ => false
        }
    }

    /**
      * Checks whether an IRI is the IRI of a project-specific ontology entity (in an internal or external ontology).
      *
      * @param iri the IRI to be checked.
      * @return `true` if the IRI is the IRI of a project-specific ontology entity.
      */
    def isProjectSpecificEntityIri(iri: IRI): Boolean = {
        iri match {
            case ProjectSpecificInternalOntologyEntityRegex(_*) | ProjectSpecificApiV2OntologyEntityRegex(_*) => true
            case _ => false
        }
    }

    /**
      * Checks whether an IRI is a Knora entity IRI (project-specific or built-in, internal or external).
      *
      * @param iri the IRI to be checked.
      * @return `true` if the IRI is a Knora entity IRI.
      */
    def isKnoraEntityIri(iri: IRI): Boolean = {
        isInternalEntityIri(iri) || isKnoraApiEntityIri(iri)
    }

    /**
      * Checks whether an IRI is the IRI of an external ontology entity (in a built-in or project-specific ontology).
      *
      * @param iri the IRI to be checked.
      * @return `true` if the IRI is the IRI of an external ontology entity.
      */
    def isKnoraApiEntityIri(iri: IRI): Boolean = {
        iri match {
            case BuiltInApiV2OntologyEntityRegex(_*) | ProjectSpecificApiV2OntologyEntityRegex(_*) => true
            case _ => false
        }
    }

    /**
      * Checks whether an IRI is the IRI of a project-specific API v2 with value objects ontology.
      *
      * @param iri the IRI to be checked.
      * @param errorFun a function that throws an exception. It will be called if the check fails.
      * @return the same IRI.
      */
    def toProjectSpecificApiV2WithValueObjectsOntologyIri(iri: IRI, errorFun: () => Nothing): IRI = {
        iri match {
            case ProjectSpecificApiV2OntologyEntityRegex(_*) => iri
            case _ => errorFun()
        }
    }

    /**
      * Checks whether an IRI is the IRI of a built-in knora-api ontology entity.
      *
      * @param iri the IRI to be checked.
      * @return `true` if the IRI is the IRI of a knora-api ontology entity.
      */
    def isBuiltInEntityIri(iri: IRI): Boolean = {
        iri match {
            case BuiltInApiV2OntologyEntityRegex(_*) => true
            case _ => false
        }
    }

    /**
      * Returns the API v2 schema used in an ontology entity IRI (from a built-in or project-specific ontology).
      *
      * @param entityIri the entity IRI.
      * @param errorFun a function that throws an exception. It will be called if the form of the IRI is not valid
      *                 for an external entity IRI.
      * @return an [[ApiV2Schema]].
      */
    def getEntityApiSchema(entityIri: IRI, errorFun: () => Nothing): ApiV2Schema = {
        entityIri match {
            case BuiltInApiV2SimpleOntologyEntityRegex(_) => ApiV2Simple
            case BuiltInApiV2WithValueObjectsOntologyEntityRegex(_) => ApiV2WithValueObjects
            case ProjectSpecificApiV2SimpleOntologyEntityRegex(ontology, _) if ontology != OntologyConstants.KnoraBase.KnoraBaseOntologyLabel => ApiV2Simple
            case ProjectSpecificApiV2WithValueObjectsOntologyEntityRegex(ontology, _) if ontology != OntologyConstants.KnoraBase.KnoraBaseOntologyLabel => ApiV2WithValueObjects
            case _ => errorFun()
        }
    }

    /**
      * Returns the API v2 schema used in an ontology IRI (from a built-in or project-specific ontology).
      *
      * @param ontologyIri the ontology IRI.
      * @param errorFun a function that throws an exception. It will be called if the form of the IRI is not valid
      *                 for an external ontology IRI.
      * @return an [[ApiV2Schema]].
      */
    def getOntologyApiSchema(ontologyIri: IRI, errorFun: () => Nothing): ApiV2Schema = {
        ontologyIri match {
            case OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri => ApiV2Simple
            case OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri => ApiV2WithValueObjects
            case ProjectSpecificApiV2SimpleOntologyRegex(_*) => ApiV2Simple
            case ProjectSpecificApiV2WithValueObjectsOntologyRegex(_*) => ApiV2WithValueObjects
            case _ => errorFun()
        }
    }

    /**
      * Converts an external entity IRI (either built-in or project specific, and either simple or with value objects)
      * to an internal IRI.
      *
      * @param iri      the external IRI to be converted.
      * @param errorFun a function that throws an exception. It will be called if the form of the string is not
      *                 valid for an external entity IRI.
      * @return an IRI which is not an external knora-api IRI.
      */
    def externalToInternalEntityIri(iri: IRI, errorFun: () => Nothing): IRI = {

        iri match {

            case BuiltInApiV2SimpleOntologyEntityRegex(entity) =>
                externalEntityNameToInternalEntityIri(OntologyConstants.KnoraApi.KnoraApiOntologyLabel, entity)

            case BuiltInApiV2WithValueObjectsOntologyEntityRegex(entity) =>
                externalEntityNameToInternalEntityIri(OntologyConstants.KnoraApi.KnoraApiOntologyLabel, entity)

            case ProjectSpecificApiV2SimpleOntologyEntityRegex(ontology, entity) =>
                externalEntityNameToInternalEntityIri(ontology, entity)

            case ProjectSpecificApiV2WithValueObjectsOntologyEntityRegex(ontology, entity) =>
                externalEntityNameToInternalEntityIri(ontology, entity)

            case _ => errorFun()
        }
    }


    /**
      * Converts an external ontology IRI (both with value object and simple) to an internal IRI.
      *
      * @param iri      the external IRI to be converted.
      * @param errorFun a function that throws an exception. It will be called if the form of the string is not
      *                 valid for an external ontology IRI.
      * @return an internal ontology IRI.
      */
    def externalToInternalOntologyIri(iri: IRI, errorFun: () => Nothing): IRI = {

        iri match {

            case OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri | OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri =>
                OntologyConstants.KnoraBase.KnoraBaseOntologyIri

            case ProjectSpecificApiV2SimpleOntologyRegex(ontologyName) =>
                projectSpecificOntologyNameToInternalOntologyIri(ontologyName)

            case ProjectSpecificApiV2WithValueObjectsOntologyRegex(ontologyName) =>
                projectSpecificOntologyNameToInternalOntologyIri(ontologyName)

            case _ => errorFun()
        }
    }

    /**
      * In XML import data, a property from another ontology is referred to as `prefixLabel__localName`. This function
      * attempts to parse a property name in that format.
      *
      * @param prefixLabelAndLocalName a string that may refer to a property in the format `prefixLabel__localName`.
      * @return if successful, a `Some` containing the entity's internal IRI, otherwise `None`.
      */
    def toPropertyIriFromOtherOntologyInXmlImport(prefixLabelAndLocalName: String): Option[IRI] = {
        prefixLabelAndLocalName match {
            case PropertyFromOtherOntologyInXmlImportRegex(prefixLabel, localName) =>
                Some(s"${OntologyConstants.KnoraInternal.InternalOntologyStart}$prefixLabel#$localName")
            case _ => None
        }
    }

    /**
      * Checks that a string represents a valid path for a `knora-base:Map`. A valid path must be a sequence of names
      * separated by slashes (`/`). Each name must be a valid XML
      * [[https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName NCName]].
      *
      * @param mapPath  the path to be checked.
      * @param errorFun a function that throws an exception. It will be called if the path is invalid.
      * @return the same path.
      */
    def toMapPath(mapPath: String, errorFun: () => Nothing): String = {
        val splitPath: Array[String] = mapPath.split('/')

        for (name <- splitPath) {
            toNCName(name, () => errorFun())
        }

        mapPath
    }

    /**
      * Converts an ontology entity IRI from one ontology schema to another. If the source schema is [[InternalSchema]]
      * and the target schema extends [[ApiV2Schema]], the IRI is converted. If the source and target schemas
      * are identical external and extend [[ApiV2Schema]], or if the source schema cannot be identified, the IRI is returned
      * without conversion.
      *
      * @param entityIri    the entity IRI to be converted.
      * @param targetSchema the target schema.
      * @return the converted IRI.
      */
    def toExternalEntityIri(entityIri: IRI, targetSchema: ApiV2Schema): IRI = {
        entityIri match {
            case KnoraBaseOntologyEntityRegex(_) | ProjectSpecificInternalOntologyEntityRegex(_*) =>
                targetSchema match {
                    case ApiV2Simple => internalEntityIriToApiV2SimpleEntityIri(entityIri, () => throw InconsistentTriplestoreDataException(s"Invalid internal ontology entity IRI: $entityIri"))
                    case ApiV2WithValueObjects => internalEntityIriToApiV2WithValueObjectEntityIri(entityIri, () => throw InconsistentTriplestoreDataException(s"Invalid internal ontology entity IRI: $entityIri"))
                }

            case BuiltInApiV2SimpleOntologyEntityRegex(_) | ProjectSpecificApiV2SimpleOntologyEntityRegex(_*) =>
                targetSchema match {
                    case ApiV2Simple => entityIri
                    case other => throw BadRequestException(s"Can't convert entity IRI to ontology schema $other: $entityIri")
                }

            case BuiltInApiV2WithValueObjectsOntologyEntityRegex(_) | ProjectSpecificApiV2SimpleOntologyEntityRegex(_*) =>
                targetSchema match {
                    case ApiV2WithValueObjects => entityIri
                    case other => throw BadRequestException(s"Can't convert entity IRI to ontology schema $other: $entityIri")
                }

            case _ => entityIri
        }
    }

    /**
      * Converts an ontology IRI from one ontology schema to another. If the source schema is [[InternalSchema]]
      * and the target schema extends [[ApiV2Schema]], the IRI is converted. If the ontology is a built-in API ontology
      * matching the target schema, it is returned unconverted. Otherwise, an exception is thrown.
      *
      * @param ontologyIri  the ontology IRI to be converted.
      * @param targetSchema the target schema.
      * @return the converted IRI.
      */
    def toExternalOntologyIri(ontologyIri: IRI, targetSchema: ApiV2Schema): IRI = {
        ontologyIri match {
            case OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri if targetSchema == ApiV2Simple => ontologyIri
            case OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri if targetSchema == ApiV2WithValueObjects => ontologyIri

            case ProjectSpecificInternalOntologyRegex(_*) =>
                targetSchema match {
                    case ApiV2Simple => internalOntologyIriToApiV2SimpleOntologyIri(ontologyIri, () => throw InconsistentTriplestoreDataException(s"Invalid internal ontology IRI: $ontologyIri"))
                    case ApiV2WithValueObjects => internalOntologyIriToApiV2WithValueObjectsOntologyIri(ontologyIri, () => throw InconsistentTriplestoreDataException(s"Invalid internal ontology IRI: $ontologyIri"))
                }

            case _ => throw BadRequestException(s"Can't convert from $ontologyIri to $targetSchema")
        }
    }

    /**
      * Given an ontology IRI requested by the user, converts it to the IRI of an ontology that the ontology responder knows about.
      *
      * @param requestedOntology the IRI of the ontology that the user requested.
      * @return the IRI of an ontology that the ontology responder can provide.
      */
    def requestedOntologyToOntologyForResponder(requestedOntology: IRI): IRI = {
        if (requestedOntology == OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri || requestedOntology == OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri) {
            // The client is asking about a built-in ontology, so don't translate its IRI.
            requestedOntology
        } else {
            // The client is asking about a project-specific ontology. Translate its IRI to an internal ontology IRI.
            val internalOntologyIri = externalToInternalOntologyIri(requestedOntology, () => throw BadRequestException(s"Invalid external ontology IRI: $requestedOntology"))
            toIri(internalOntologyIri, () => throw BadRequestException(s"Invalid named graph IRI: $internalOntologyIri"))
        }
    }

    /**
      * Given an ontology entity IRI requested by the user, converts it to the IRI of an entity that the ontology responder knows about.
      *
      * @param requestedEntity the IRI of the entity that the user requested.
      * @return the IRI of an entity that the ontology responder can provide.
      */
    def requestedEntityToEntityForResponder(requestedEntity: IRI): IRI = {
        if (isBuiltInEntityIri(requestedEntity)) {
            // The client is asking about a built-in class, so don't translate its IRI.
            requestedEntity
        } else {
            // The client is asking about a project-specific class. Translate its IRI to an internal class IRI.
            val internalEntityIri = externalToInternalEntityIri(requestedEntity, () => throw BadRequestException(s"invalid external entity IRI: $requestedEntity"))
            toIri(internalEntityIri, () => throw BadRequestException(s"Invalid entity IRI: $internalEntityIri"))
        }
    }

    /**
      * Determines whether a URL path is valid for a built-in knora-api v2 ontology.
      *
      * @param urlPath the URL path.
      * @return true if the path is valid for a built-in knora-api v2 ontology.
      */
    def isBuiltInApiV2OntologyUrlPath(urlPath: String): Boolean = {
        urlPath match {
            case BuiltInApiV2OntologyUrlPathRegex(_) => true
            case _ => false
        }
    }

    /**
      * Determines whether a URL path is valid for a project-specific external ontology.
      *
      * @param urlPath the URL path.
      * @return true if the path is valid fora project-specific external ontology.
      */
    def isProjectSpecificApiV2OntologyUrlPath(urlPath: String): Boolean = {
        urlPath match {
            case ProjectSpecificApiV2OntologyUrlPathRegex(_*) => true
            case _ => false
        }
    }
}
