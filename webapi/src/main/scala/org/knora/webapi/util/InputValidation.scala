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
  * Do save String to expected value type conversions (to be inserted in the SPARQL template).
  * If the conversion fails, the callback function `errorFun` is called
  */
object InputValidation {

    /**
      * Separates the calendar name from the rest of a Knora date.
      */
    val CalendarSeparator: String = ":"

    /**
      * Separates year, month, and day in a Knora date.
      */
    val PrecisionSeparator: String = "-"

    // The expected format of a Knora date.
    // Calendar:YYYY[-MM[-DD]][:YYYY[-MM[-DD]]]
    private val KnoraDateRegex: Regex = ("""^(GREGORIAN|JULIAN)""" +
        CalendarSeparator + // calendar name
        """\d{1,4}(""" + // year
        PrecisionSeparator +
        """\d{1,2}(""" + // month
        PrecisionSeparator +
        """\d{1,2})?)?(""" + // day
        CalendarSeparator + // separator if a period is given
        """\d{1,4}(""" + // year 2
        PrecisionSeparator +
        """\d{1,2}(""" + // month 2
        PrecisionSeparator +
        """\d{1,2})?)?)?$""").r // day 2

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
    private val ColorRegex = "^#(?:[0-9a-fA-F]{3}){1,2}$".r

    // A regex sub-pattern for ontology prefix labels and local entity names. According to
    // <https://www.w3.org/TR/turtle/#prefixed-name>, a prefix label in Turtle must be a valid XML NCName
    // <https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName>. Knora also requires a local entity name to
    // be an XML NCName.
    private val NCNamePattern: String =
    """[\p{L}_][\p{L}0-9_.-]*"""

    // A regex for matching a string containing only an ontology prefix label or a local entity name.
    private val NCNameRegex = ("^" + NCNamePattern + "$").r

    // A regex for project-specific internal ontologies.
    private val ProjectSpecificInternalOntologyRegex: Regex = (
        "^" + OntologyConstants.KnoraInternal.InternalOntologyStart +
            "(" + NCNamePattern + ")$"
        ).r

    // A regex for external knora-api v2 with value object ontologies.
    private val ExternalApiV2WithValueObjectOntologyRegex: Regex = (
        "^" + OntologyConstants.KnoraApi.ApiOntologyStart +
            "(" + NCNamePattern + ")" +
            OntologyConstants.KnoraApiV2WithValueObject.VersionSegment + "$"
        ).r

    // A regex for external knora-api v2 simple ontologies.
    private val ExternalApiV2SimpleOntologyRegex: Regex = (
        "^" + OntologyConstants.KnoraApi.ApiOntologyStart +
            "(" + NCNamePattern + ")" +
            OntologyConstants.KnoraApiV2Simplified.VersionSegment + "$"
        ).r

    // A regex for entity IRIs in project-specific internal ontologies.
    private val InternalOntologyEntityRegex: Regex = (
        "^" + OntologyConstants.KnoraInternal.InternalOntologyStart +
            "(" + NCNamePattern + ")#(" + NCNamePattern + ")$"
        ).r

    // A regex for entity Iris in project-specific external ontologies (knora-api).
    // This works for both cases: with value object and simple.
    private val KnoraApiOntologyEntityRegex = (
        "^" + OntologyConstants.KnoraApi.ApiOntologyStart +
            "(" + NCNamePattern + ")" + "(" + OntologyConstants.KnoraApiV2WithValueObject.VersionSegment + "|" + OntologyConstants.KnoraApiV2Simplified.VersionSegment + ")" +
            "#(" + NCNamePattern + ")$"
        ).r

    // A regex for external knora-api v2 with value object entity Iris.
    private val ExternalApiV2WithValueObjectOntologyEntityRegex: Regex = (
        "^" + OntologyConstants.KnoraApi.ApiOntologyStart +
            "(" + NCNamePattern + ")" + OntologyConstants.KnoraApiV2WithValueObject.VersionSegment +
            "#(" + NCNamePattern + ")$"
        ).r

    // A regex for external knora-api v2 simple entity Iris.
    private val ExternalApiV2SimpleOntologyEntityRegex: Regex = (
        "^" + OntologyConstants.KnoraApi.ApiOntologyStart +
            "(" + NCNamePattern + ")" + OntologyConstants.KnoraApiV2Simplified.VersionSegment +
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
        // TODO: how to deal with dates BC -> ERA

        // TODO: import calendars instead of hardcoding them

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
      * A container for an XML import namespace and its prefix label.
      *
      * @param namespace   the namespace.
      * @param prefixLabel the prefix label.
      */
    case class XmlImportNamespaceInfoV1(namespace: IRI, prefixLabel: String)

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
      * Given the IRI of an internal ontology entity, returns the ontology prefix label.
      *
      * @param internalEntityIri the ontology entity IRI.
      * @param errorFun          a function that throws an exception. It will be called if the form of the string is not
      *                          valid for an internal ontology entity IRI.
      * @return the ontology prefix label specified in the entity IRI.
      */
    def getOntologyPrefixLabelFromInternalEntityIri(internalEntityIri: IRI, errorFun: () => Nothing): String = {
        internalEntityIri match {
            case InternalOntologyEntityRegex(prefixLabel, _) => prefixLabel
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
      * Given the IRI of an internal ontology entity, returns the internal ontology IRI.
      *
      * @param internalEntityIri the ontology entity IRI.
      * @param errorFun          a function that throws an exception. It will be called if the form of the string is not
      *                          valid for an internal ontology entity IRI.
      * @return the ontology IRI portion of the entity IRI.
      */
    def getInternalOntologyIriFromInternalEntityIri(internalEntityIri: IRI, errorFun: () => Nothing): IRI = {
        internalEntityIri match {
            case InternalOntologyEntityRegex(prefixLabel, _) => OntologyConstants.KnoraInternal.InternalOntologyStart + prefixLabel
            case _ => errorFun()
        }
    }

    /**
      * Converts an external ontology name to an internal ontology Iri.
      *
      * @param ontologyName the external ontology name to be converted.
      * @return the internal ontology Iri.
      */
    private def externalOntologyNameToInternalOntologyIri(ontologyName: String): IRI = {
        val internalOntologyName = if (ontologyName == "knora-api") "knora-base" else ontologyName
        OntologyConstants.KnoraInternal.InternalOntologyStart + internalOntologyName
    }


    /**
      * Given the Iri of an external knora-api v2 with value object ontology, returns the internal ontology Iri.
      *
      * @param externalOntologyIri the external ontology Iri.
      * @param errorFun            a function that throws an exception. It will be called if the form of the string is not
      *                            valid for an internal ontology IRI.
      * @return the internal ontology Iri.
      */
    def externalOntologyIriApiV2WithValueObjectToInternalOntologyIri(externalOntologyIri: IRI, errorFun: () => Nothing): IRI = {
        externalOntologyIri match {
            case ExternalApiV2WithValueObjectOntologyRegex(ontologyName) =>
                externalOntologyNameToInternalOntologyIri(ontologyName)
            case _ => errorFun()
        }
    }

    /**
      * Given the Iri of an external knora-api v2 simple ontology, returns the internal ontology Iri.
      *
      * @param externalOntologyIri the external ontology Iri.
      * @param errorFun            a function that throws an exception. It will be called if the form of the string is not
      *                            valid for an internal ontology IRI.
      * @return the internal ontology Iri.
      */
    def externalOntologyIriApiV2SimpleToInternalOntologyIri(externalOntologyIri: IRI, errorFun: () => Nothing): IRI = {
        externalOntologyIri match {
            case ExternalApiV2SimpleOntologyRegex(ontologyName) =>
                externalOntologyNameToInternalOntologyIri(ontologyName)
            case _ => errorFun()
        }
    }

    /**
      * Given the Iri of an internal ontology, returns the knora-api with value object ontology Iri.
      *
      * @param internalOntologyIri the Iri of the internal ontology.
      * @param errorFun            a function that throws an exception. It will be called if the form of the string is not
      *                            valid for an internal ontology IRI.
      * @return the external ontology Iri.
      */
    def internalOntologyIriToApiV2WithValueObjectOntologyIri(internalOntologyIri: IRI, errorFun: () => Nothing): IRI = {
        internalOntologyIri match {
            case ProjectSpecificInternalOntologyRegex(ontologyName) =>
                val apiOntologyName = if (ontologyName == "knora-base") "knora-api" else ontologyName
                OntologyConstants.KnoraApi.ApiOntologyStart + apiOntologyName + OntologyConstants.KnoraApiV2WithValueObject.VersionSegment
            case _ => errorFun()
        }
    }

    /**
      * Converts an external entity name to an internal entity Iri.
      *
      * @param ontology   the name of the ontology the entity belongs.
      * @param entityName the name of the entity.
      * @return the internal entity Iri.
      */
    private def externalEntityNameToInternalEntityIri(ontology: String, entityName: String) = {
        val ontologyName = if (ontology == "knora-api") "knora-base" else ontology
        OntologyConstants.KnoraInternal.InternalOntologyStart + ontologyName + "#" + entityName
    }

    /**
      * Given the Iri of an external knora-api v2 with value object entity, returns the internal entity Iri.
      *
      * @param externalEntityIri an external entity Iri.
      * @param errorFun          a function that throws an exception. It will be called if the form of the string is not
      *                          valid for an internal ontology IRI.
      * @return the internal entity Iri.
      */
    def externalApiV2WithValueObjectEntityIriToInternalEntityIri(externalEntityIri: IRI, errorFun: () => Nothing): IRI = {
        externalEntityIri match {
            case ExternalApiV2WithValueObjectOntologyEntityRegex(ontology, entityName) =>
                externalEntityNameToInternalEntityIri(ontology, entityName)
            case _ => errorFun()
        }
    }

    /**
      * Given the Iri of an external knora-api v2 simple entity, returns the internal entity Iri.
      *
      * @param externalEntityIri an external entity Iri.
      * @param errorFun          a function that throws an exception. It will be called if the form of the string is not
      *                          valid for an internal ontology IRI.
      * @return the internal entity Iri.
      */
    def externalApiV2SimpleEntityIriToInternalEntityIri(externalEntityIri: IRI, errorFun: () => Nothing): IRI = {
        externalEntityIri match {
            case ExternalApiV2SimpleOntologyEntityRegex(ontology, entityName) =>
                externalEntityNameToInternalEntityIri(ontology, entityName)
            case _ => errorFun()
        }
    }

    /**
      * Given the IRI of an internal ontology entity, returns the knora-api v2 with value object entity Iri.
      *
      * @param internalEntityIri the Iri of the internal ontology entity.
      * @param errorFun          a function that throws an exception. It will be called if the form of the string is not
      *                          valid for an internal ontology entity IRI.
      * @return the corresponding knora-api v2 with value object entity Iri.
      */
    def internalEntityIriToApiV2WithValueObjectEntityIri(internalEntityIri: IRI, errorFun: () => Nothing): IRI = {
        internalEntityIri match {
            case InternalOntologyEntityRegex(prefixLabel, entityName) =>
                val apiPrefixLabel = if (prefixLabel == "knora-base") "knora-api" else prefixLabel
                OntologyConstants.KnoraApi.ApiOntologyStart + apiPrefixLabel + OntologyConstants.KnoraApiV2WithValueObject.VersionSegment + "#" + entityName
            case _ => errorFun()
        }
    }

    /**
      * Given the IRI of an internal ontology entity, returns the simplified knora-api v2 entity Iri.
      *
      * @param internalEntityIri the Iri of the internal ontology entity.
      * @param errorFun          a function that throws an exception. It will be called if the form of the string is not
      *                          valid for an internal ontology entity IRI.
      * @return the corresponding simplified knora-api v2.
      */
    def internalEntityIriToSimpleApiV2EntityIri(internalEntityIri: IRI, errorFun: () => Nothing): IRI = {
        internalEntityIri match {
            case InternalOntologyEntityRegex(prefixLabel, entityName) =>
                val apiPrefixLabel = if (prefixLabel == "knora-base") "knora-api" else prefixLabel
                OntologyConstants.KnoraApi.ApiOntologyStart + apiPrefixLabel + OntologyConstants.KnoraApiV2Simplified.VersionSegment + "#" + entityName
            case _ => errorFun()
        }
    }

    /**
      * Given the IRI of an internal ontology entity, returns the local name of the entity.
      *
      * @param internalEntityIri the ontology entity IRI.
      * @param errorFun          a function that throws an exception. It will be called if the form of the string is not
      *                          valid for an internal ontology entity IRI.
      * @return the local name specified in the entity IRI.
      */
    def getEntityNameFromInternalEntityIri(internalEntityIri: IRI, errorFun: () => Nothing): String = {
        internalEntityIri match {
            case InternalOntologyEntityRegex(_, entityName) => entityName
            case _ => errorFun()
        }
    }

    /**
      * Checks whether an IRI is the IRI of an internal ontology entity.
      *
      * @param iri the IRI to be checked.
      * @return `true` if the IRI is the IRI of an internal ontology entity.
      */
    def isInternalEntityIri(iri: IRI): Boolean = {
        iri match {
            case InternalOntologyEntityRegex(_*) => true
            case _ => false
        }
    }

    /**
      * Checks whether an IRI is the IRI of an external ontology entity (knora-api).
      *
      * @param iri the IRI to be checked.
      * @return `true` if the IRI is the IRI of an external ontology entity.
      */
    def isKnoraApiEntityIri(iri: IRI) = {
        iri match {
            case KnoraApiOntologyEntityRegex(_*) => true
            case _ => false
        }
    }

    /**
      * Converts an external knora-api entity Iri (both with value object and simple) to an internal Iri.
      *
      * @param iri      the external Iri to be converted.
      * @param errorFun a function that throws an exception. It will be called if the form of the string is not
      *                 valid for an external ontology or entity IRI.
      * @return an Iri which is not an external knora-api Iri.
      */
    def externalIriToInternalIri(iri: IRI, errorFun: () => Nothing): IRI = {

        iri match {

            case ExternalApiV2SimpleOntologyEntityRegex(ontology, entity) =>
                externalEntityNameToInternalEntityIri(ontology, entity)

            case ExternalApiV2WithValueObjectOntologyEntityRegex(ontology, entity) =>
                externalEntityNameToInternalEntityIri(ontology, entity)

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
      * @param mapPath the path to be checked.
      * @param errorFun a function that throws an exception. It will be called if the path is invalid.
      * @return the same path.
      */
    def toMapPath(mapPath: String, errorFun: () => Nothing): String = {
        val splitPath: Array[String] = mapPath.split('/')

        for (name <- splitPath) {
            InputValidation.toNCName(name, () => errorFun())
        }

        mapPath
    }
}
