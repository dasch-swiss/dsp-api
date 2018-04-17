/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import java.text.ParseException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

import com.google.gwt.safehtml.shared.UriUtils._
import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoV1
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.messages.v2.responder.KnoraContentV2
import org.knora.webapi.twirl.StandoffTagV1
import org.knora.webapi.util.JavaUtil.Optional
import spray.json.JsonParser

import scala.util.control.Exception._
import scala.util.matching.Regex

/**
  * Provides the singleton instance of [[StringFormatter]], as well as string formatting constants.
  */
object StringFormatter {

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

    /**
      * A container for an XML import namespace and its prefix label.
      *
      * @param namespace   the namespace.
      * @param prefixLabel the prefix label.
      */
    case class XmlImportNamespaceInfoV1(namespace: IRI, prefixLabel: String)

    /**
      * Represents a parsed object of the property `salsah-gui:guiAttributeDefinition`.
      *
      * @param attributeName    the name of the attribute.
      * @param isRequired       `true` if the attribute is required.
      * @param allowedType      the type of the attribute's value.
      * @param enumeratedValues the allowed values, if this is an enumerated string attribute.
      */
    case class SalsahGuiAttributeDefinition(attributeName: String,
                                            isRequired: Boolean,
                                            allowedType: OntologyConstants.SalsahGui.SalsahGuiAttributeType.Value,
                                            enumeratedValues: Set[String] = Set.empty[String],
                                            unparsedString: String)

    /**
      * Represents a parsed object of the property `salsah-gui:guiAttribute`.
      *
      * @param attributeName  the name of the attribute.
      * @param attributeValue the value of the attribute.
      */
    case class SalsahGuiAttribute(attributeName: String, attributeValue: SalsahGuiAttributeValue)

    /**
      * Represents a parsed value of an attribute that is the object of the property `salsah-gui:guiAttribute`.
      */
    sealed trait SalsahGuiAttributeValue {
        def attributeType: OntologyConstants.SalsahGui.SalsahGuiAttributeType.Value
    }

    /**
      * Represents a parsed integer value of an attribute that is the object of the property `salsah-gui:guiAttribute`.
      *
      * @param value the integer value.
      */
    case class SalsahGuiIntegerAttributeValue(value: Int) extends SalsahGuiAttributeValue {
        override val attributeType: OntologyConstants.SalsahGui.SalsahGuiAttributeType.Value = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Integer
    }

    /**
      * Represents a parsed percent value of an attribute that is the object of the property `salsah-gui:guiAttribute`.
      *
      * @param value the percent value.
      */
    case class SalsahGuiPercentAttributeValue(value: Int) extends SalsahGuiAttributeValue {
        override val attributeType: OntologyConstants.SalsahGui.SalsahGuiAttributeType.Value = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Percent
    }

    /**
      * Represents a parsed decimal value of an attribute that is the object of the property `salsah-gui:guiAttribute`.
      *
      * @param value the decimal value.
      */
    case class SalsahGuiDecimalAttributeValue(value: BigDecimal) extends SalsahGuiAttributeValue {
        override val attributeType: OntologyConstants.SalsahGui.SalsahGuiAttributeType.Value = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Decimal
    }

    /**
      * Represents a parsed string value of an attribute that is the object of the property `salsah-gui:guiAttribute`.
      *
      * @param value the string value.
      */
    case class SalsahGuiStringAttributeValue(value: String) extends SalsahGuiAttributeValue {
        override val attributeType: OntologyConstants.SalsahGui.SalsahGuiAttributeType.Value = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Str
    }

    /**
      * Represents a parsed IRI value of an attribute that is the object of the property `salsah-gui:guiAttribute`.
      *
      * @param value the IRI value.
      */
    case class SalsahGuiIriAttributeValue(value: IRI) extends SalsahGuiAttributeValue {
        override val attributeType: OntologyConstants.SalsahGui.SalsahGuiAttributeType.Value = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Iri
    }

    /*

    In order to parse project-specific API v2 ontology IRIs, the StringFormatter
    class needs the Knora API server's hostname, which is set in application.conf,
    which is not read until the Akka ActorSystem starts. Therefore, IRI parsing is
    done in the StringFormatter class, rather than in the StringFormatter object.

    There are two instances of StringFormatter, defined below.

     */

    /**
      * The instance of [[StringFormatter]] that is initialised after the ActorSystem starts,
      * and can parse project-specific API v2 ontology IRIs. This instance is used almost
      * everywhere in the API server.
      */
    private var generalInstance: Option[StringFormatter] = None

    /**
      * The instance of [[StringFormatter]] that can be used as soon as the JVM starts, but
      * can't parse project-specific API v2 ontology IRIs. This instance is used
      * only to initialise the hard-coded API v2 ontologies [[org.knora.webapi.messages.v2.responder.ontologymessages.KnoraApiV2Simple]]
      * and [[org.knora.webapi.messages.v2.responder.ontologymessages.KnoraApiV2WithValueObjects]].
      */
    private val instanceForConstantOntologies = new StringFormatter(None)

    /**
      * Gets the singleton instance of [[StringFormatter]] that handles IRIs from data.
      */
    def getGeneralInstance: StringFormatter = {
        generalInstance match {
            case Some(instance) => instance
            case None => throw AssertionException("StringFormatter not yet initialised")
        }
    }

    /**
      * Gets the singleton instance of [[StringFormatter]] that can only handle the IRIs in built-in
      * ontologies.
      */
    def getInstanceForConstantOntologies: StringFormatter = instanceForConstantOntologies

    /**
      * Initialises the general instance of [[StringFormatter]].
      *
      * @param settings the application settings.
      */
    def init(settings: SettingsImpl): Unit = {
        this.synchronized {
            generalInstance match {
                case Some(_) => ()
                case None => generalInstance = Some(new StringFormatter(Some(settings.externalKnoraApiHostPort)))
            }
        }
    }

    /**
      * Initialises the singleton instance of [[StringFormatter]] for a test.
      */
    def initForTest(): Unit = {
        this.synchronized {
            generalInstance match {
                case Some(_) => ()
                case None => generalInstance = Some(new StringFormatter(Some("0.0.0.0:3333")))
            }
        }
    }

    /**
      * Indicates whether the IRI is a data IRI, a definition IRI, or an IRI of an unknown type.
      */
    private sealed trait IriType

    /**
      * Indicates that the IRI is a data IRI.
      */
    private case object KnoraDataIri extends IriType

    /**
      * Indicates that the IRI is an ontology or ontology entity IRI.
      */
    private case object KnoraDefinitionIri extends IriType

    /**
      * Indicates that the type of the IRI is unknown.
      */
    private case object UnknownIriType extends IriType

    /**
      * Holds information extracted from the IRI.
      *
      * @param iriType        the type of the IRI.
      * @param projectCode    the IRI's project code, if any.
      * @param ontologyName   the IRI's ontology name, if any.
      * @param entityName     the IRI's entity name, if any.
      * @param ontologySchema the IRI's ontology schema, or `None` if it is not a Knora definition IRI.
      * @param isBuiltInDef   `true` if the IRI refers to a built-in Knora ontology or ontology entity.
      */
    private case class SmartIriInfo(iriType: IriType,
                                    projectCode: Option[String] = None,
                                    ontologyName: Option[String] = None,
                                    entityName: Option[String] = None,
                                    ontologySchema: Option[OntologySchema],
                                    isBuiltInDef: Boolean = false)

    /**
      * A cache that maps IRI strings to [[SmartIri]] instances. To keep the cache from getting too large,
      * only IRIs from known ontologies are cached.
      */
    private lazy val smartIriCache = new ConcurrentHashMap[IRI, SmartIri](2048)

    /**
      * Gets a cached smart IRI, or constructs and caches one.
      *
      * @param iriStr      the IRI in string form.
      * @param creationFun a function that creates the smart IRI to be cached.
      * @return the smart IRI.
      */
    private def getOrCacheSmartIri(iriStr: IRI, creationFun: () => SmartIri): SmartIri = {
        smartIriCache.computeIfAbsent(
            iriStr,
            JavaUtil.function({ _ => creationFun() })
        )
    }
}

/**
  * Represents a parsed IRI with Knora-specific functionality. To construct a `SmartIri`,
  * `import org.knora.webapi.util.IriConversions.ConvertibleIri`, then call one of the methods that
  * it implicitly defines on `String`, e.g.:
  *
  * - "http://knora.example.org/ontology/0000/example#Something".toSmartIri
  * - "http://knora.example.org/ontology/0000/example#Something".toSmartIriWithErr(throw BadRequestException("Invalid IRI"))
  */
sealed trait SmartIri extends Ordered[SmartIri] with KnoraContentV2[SmartIri] {

    /*

    The smart IRI implementation, SmartIriImpl, is nested in the StringFormatter
    class because it uses the Knora API server's hostname, which isn't available
    until the Akka ActorSystem has started. However, this means that the type of a
    SmartIriImpl instance is dependent on the instance of StringFormatter that
    constructed it. Therefore, you can't compare two instances of SmartIriImpl
    created by two different instances of StringFormatter.

    To make it possible to compare smart IRI objects, the publicly visible smart IRI
    type is the SmartIri trait. Since SmartIri is a top-level definition, two instances
    of SmartIri can be compared, even if they were made by different instances of
    StringFormatter. To make this work, SmartIri provides its own equals and hashCode
    methods, which delegate to the string representation of the IRI.

     */

    /**
      * Returns `true` if this is a Knora data or definition IRI.
      */
    def isKnoraIri: Boolean

    /**
      * Returns `true` if this is a Knora data IRI.
      */
    def isKnoraDataIri: Boolean

    /**
      * Returns `true` if this is a Knora ontology or entity IRI.
      */
    def isKnoraDefinitionIri: Boolean

    /**
      * Returns `true` if this is a built-in Knora ontology or entity IRI.
      *
      * @return
      */
    def isKnoraBuiltInDefinitionIri: Boolean

    /**
      * Returns `true` if this is an internal Knora ontology or entity IRI.
      *
      * @return
      */
    def isKnoraInternalDefinitionIri: Boolean

    /**
      * Returns `true` if this is an internal Knora ontology entity IRI.
      */
    def isKnoraInternalEntityIri: Boolean

    /**
      * Returns `true` if this is a Knora ontology IRI.
      */
    def isKnoraOntologyIri: Boolean

    /**
      * Returns `true` if this is a Knora entity IRI.
      */
    def isKnoraEntityIri: Boolean

    /**
      * Returns `true` if this is a Knora API v2 ontology or entity IRI.
      */
    def isKnoraApiV2DefinitionIri: Boolean

    /**
      * Returns `true` if this is a Knora API v2 ontology entity IRI.
      */
    def isKnoraApiV2EntityIri: Boolean

    /**
      * Returns the IRI's project code, if any.
      */
    def getProjectCode: Option[String]

    /**
      * If this is an ontology entity IRI, returns its ontology IRI.
      */
    def getOntologyFromEntity: SmartIri

    /**
      * If this is a Knora ontology or entity IRI, returns the name of the ontology. Otherwise, throws [[DataConversionException]].
      */
    def getOntologyName: String

    /**
      * If this is a Knora entity IRI, returns the name of the entity. Otherwise, throws [[DataConversionException]].
      */
    def getEntityName: String

    /**
      * If this is a Knora ontology IRI, constructs a Knora entity IRI based on it. Otherwise, throws [[DataConversionException]].
      *
      * @param entityName the name of the entity.
      */
    def makeEntityIri(entityName: String): SmartIri

    /**
      * Returns the IRI's [[OntologySchema]], or `None` if this is not a Knora definition IRI.
      */
    def getOntologySchema: Option[OntologySchema]

    /**
      * Converts this IRI to another ontology schema.
      *
      * @param targetSchema the target schema.
      */
    override def toOntologySchema(targetSchema: OntologySchema): SmartIri

    /**
      * Constructs a prefix label that can be used to shorten this IRI's namespace in formats such as Turtle and JSON-LD.
      */
    def getPrefixLabel: String

    /**
      * If this is the IRI of a link value property, returns the IRI of the corresponding link property. Throws
      * [[DataConversionException]] if this IRI is not a Knora entity IRI.
      */
    def fromLinkValuePropToLinkProp: SmartIri

    /**
      * If this is the IRI of a link property, returns the IRI of the corresponding link value property. Throws
      * [[DataConversionException]] if this IRI is not a Knora entity IRI.
      *
      * @return
      */
    def fromLinkPropToLinkValueProp: SmartIri

    override def equals(obj: scala.Any): Boolean = {
        // See the comment at the top of the SmartIri trait.
        obj match {
            case that: SmartIri => this.toString == that.toString
            case _ => false
        }
    }

    override def hashCode: Int = toString.hashCode

    def compare(that: SmartIri): Int = toString.compare(that.toString)
}

/**
  * Provides `apply` and `unapply` methods to for `SmartIri`.
  */
object SmartIri {
    def apply(iriStr: IRI)(implicit stringFormatter: StringFormatter): SmartIri = stringFormatter.toSmartIri(iriStr)

    def unapply(iri: SmartIri): Option[String] = Some(iri.toString)
}

/**
  * Provides automatic conversion of IRI strings to [[SmartIri]] objects. See [[https://www.scala-lang.org/api/current/scala/AnyVal.html]]
  * for details.
  */
object IriConversions {

    implicit class ConvertibleIri(val self: IRI) extends AnyVal {
        /**
          * Converts an IRI string to a [[SmartIri]].
          */
        def toSmartIri(implicit stringFormatter: StringFormatter): SmartIri = stringFormatter.toSmartIri(self)

        /**
          * Converts an IRI string to a [[SmartIri]]. If the string cannot be converted, a function is called to report
          * the error. Use this function to parse IRIs from client input.
          *
          * @param errorFun A function that throws an exception. It will be called if the string cannot be converted.
          */
        def toSmartIriWithErr(errorFun: => Nothing)(implicit stringFormatter: StringFormatter): SmartIri = stringFormatter.toSmartIriWithErr(self, errorFun)

        /**
          * Converts an IRI string to a [[SmartIri]], verifying that the resulting [[SmartIri]] is a Knora internal definition IRI,
          * and throwing [[DataConversionException]] otherwise.
          */
        def toKnoraInternalSmartIri(implicit stringFormatter: StringFormatter): SmartIri = stringFormatter.toSmartIri(self, requireInternal = true)
    }

}

/**
  * Handles string parsing, formatting, conversion, and validation.
  */
class StringFormatter private(val knoraApiHostAndPort: Option[String]) {

    import StringFormatter._

    // Valid URL schemes.
    private val schemes = Array("http", "https")

    // A validator for URLs.
    private val urlValidator = new UrlValidator(schemes, UrlValidator.ALLOW_LOCAL_URLS) // local urls are URL-encoded Knora IRIs as part of the whole URL

    // The hostname used in internal Knora IRIs.
    private val InternalIriHostname = "www.knora.org"

    // The hostname used in built-in Knora API v2 IRIs.
    private val BuiltInKnoraApiHostname = "api.knora.org"

    // The strings that Knora data IRIs can start with.
    private val DataIriStarts: Set[String] = Set(
        "http://" + KnoraIdUtil.IriDomain + "/",
        "http://data.knora.org/"
    )

    // The beginnings of Knora definition IRIs that we know we can cache.
    private val KnoraDefinitionIriStarts = (Set(
        InternalIriHostname,
        BuiltInKnoraApiHostname
    ) ++ knoraApiHostAndPort).map(hostname => "http://" + hostname)

    // The beginnings of all definition IRIs that we know we can cache.
    private val CacheableIriStarts = KnoraDefinitionIriStarts ++ Set(
        OntologyConstants.Rdf.RdfPrefixExpansion,
        OntologyConstants.Rdfs.RdfsPrefixExpansion,
        OntologyConstants.Xsd.XsdPrefixExpansion,
        OntologyConstants.Owl.OwlPrefixExpansion
    )

    // Reserved words used in Knora API v2 IRI version segments.
    private val versionSegmentWords = Set("simple", "v2")

    // Reserved words that cannot be used in project-specific ontology names.
    private val reservedIriWords = Set("knora", "ontology") ++ versionSegmentWords

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

    // A regex sub-pattern for project IDs, which must consist of 4 hexadecimal digits.
    private val ProjectIDPattern: String =
        """\p{XDigit}{4,4}"""

    // A regex for matching a string containing the project ID.
    private val ProjectIDRegex: Regex = ("^" + ProjectIDPattern + "$").r

    // A regex for the URL path of an API v2 ontology (built-in or project-specific).
    private val ApiV2OntologyUrlPathRegex: Regex = (
        "^" + "/ontology/((" +
            ProjectIDPattern + ")/)?(" + NCNamePattern + ")(" +
            OntologyConstants.KnoraApiV2WithValueObjects.VersionSegment + "|" + OntologyConstants.KnoraApiV2Simple.VersionSegment + ")$"
        ).r

    // The start of the IRI of a project-specific API v2 ontology that is served by this API server.
    private val MaybeProjectSpecificApiV2OntologyStart: Option[String] = knoraApiHostAndPort match {
        case Some(hostAndPort) => Some("http://" + hostAndPort + "/ontology/")
        case None => None
    }

    // A regex for a project-specific XML import namespace.
    private val ProjectSpecificXmlImportNamespaceRegex: Regex = (
        "^" + OntologyConstants.KnoraXmlImportV1.ProjectSpecificXmlImportNamespace.XmlImportNamespaceStart + "((" +
            ProjectIDPattern + ")/)?(" + NCNamePattern + ")" +
            OntologyConstants.KnoraXmlImportV1.ProjectSpecificXmlImportNamespace.XmlImportNamespaceEnd + "$"
        ).r

    // In XML import data, a property from another ontology is referred to as prefixLabel__localName. The prefix label
    // may start with a project ID (prefixed with 'p') and a hyphen. This regex parses that pattern.
    private val PropertyFromOtherOntologyInXmlImportRegex: Regex = (

        "^(p(" + ProjectIDPattern + ")-)?(" + NCNamePattern + ")__(" + NCNamePattern + ")$"
        ).r

    // In XML import data, a standoff link tag that refers to a resource described in the import must have the
    // form defined by this regex.
    private val StandoffLinkReferenceToClientIDForResourceRegex: Regex = (
        "^ref:(" + NCNamePattern + ")$"
        ).r

    private val ApiVersionNumberRegex: Regex = "^v[0-9]+.*$".r

    // Parses an object of salsah-gui:guiAttributeDefinition.
    private val SalsahGuiAttributeDefinitionRegex: Regex =
        """^(\p{L}+)(\(required\))?:(\p{L}+)(\(([\p{L}\|]+)\))?$""".r

    // Parses an object of salsa-gui:guiAttribute.
    private val SalsahGuiAttributeRegex: Regex =
        """^(\p{L}+)=(.+)$""".r

    /**
      * The information that is stored about non-Knora IRIs.
      */
    private val UnknownIriInfo = SmartIriInfo(
        iriType = UnknownIriType,
        projectCode = None,
        ontologyName = None,
        entityName = None,
        ontologySchema = None
    )

    /**
      * The implementation of [[SmartIri]]. An instance of this class can only be constructed by [[StringFormatter]].
      * The constructor validates and parses the IRI.
      *
      * @param iriStr        the IRI string to be parsed.
      * @param parsedIriInfo if this smart IRI is the result of a conversion from another smart IRI, information
      *                      about the IRI being constructed.
      * @param errorFun      a function that throws an exception. It will be called if the IRI is invalid.
      */
    private class SmartIriImpl(iriStr: IRI, parsedIriInfo: Option[SmartIriInfo], errorFun: => Nothing) extends SmartIri {
        def this(iriStr: IRI) = this(iriStr, None, throw DataConversionException(s"Couldn't parse IRI: $iriStr"))

        def this(iriStr: IRI, parsedIriInfo: Option[SmartIriInfo]) = this(iriStr, parsedIriInfo, throw DataConversionException(s"Couldn't parse IRI: $iriStr"))

        private val iri: IRI = validateAndEscapeIri(iriStr, errorFun)

        /**
          * Determines the API v2 schema of an external IRI.
          *
          * @param segments the segments of the namespace.
          * @return the IRI's API schema.
          */
        private def parseApiV2VersionSegments(segments: Vector[String]): ApiV2Schema = {
            if (segments.length < 2) {
                errorFun
            }

            val lastSegment = segments.last
            val lastTwoSegments = segments.slice(segments.length - 2, segments.length)

            if (lastTwoSegments == Vector("simple", "v2")) {
                ApiV2Simple
            } else if (lastSegment == "v2") {
                ApiV2WithValueObjects
            } else {
                errorFun
            }
        }

        // Extract Knora-specific information from the IRI.
        private val iriInfo: SmartIriInfo = parsedIriInfo match {
            case Some(info) =>
                // This smart IRI is the result of a conversion from another smart IRI. Use the SmartIriInfo
                // we were given.
                info

            case None =>
                // Parse the IRI from scratch.
                if (isKnoraDataIriStr(iri) ||
                    iri.startsWith(OntologyConstants.NamedGraphs.DataNamedGraphStart) ||
                    iri == OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph) {
                    // This is a Knora data or named graph IRI. Nothing else to do.
                    SmartIriInfo(
                        iriType = KnoraDataIri,
                        ontologySchema = None
                    )
                } else {
                    // If this is an entity IRI in a hash namespace, separate the entity name from the namespace.

                    val hashPos = iri.lastIndexOf('#')

                    val (namespace: String, entityName: Option[String]) = if (hashPos >= 0 && hashPos < iri.length) {
                        (iri.substring(0, hashPos), Some(validateNCName(iri.substring(hashPos + 1), errorFun)))
                    } else {
                        (iri, None)
                    }

                    // Remove the URL scheme (http://), and split the remainder of the namespace into slash-delimited segments.
                    val body = namespace.substring(namespace.indexOf("//") + 2)
                    val segments = body.split('/').toVector

                    // The segments must contain at least a hostname.
                    if (segments.isEmpty) {
                        errorFun
                    }

                    // Determine the ontology schema by looking at the hostname and the version segment.

                    val hostname = segments.head

                    val (ontologySchema: Option[OntologySchema], hasProjectSpecificHostname: Boolean) = hostname match {
                        case InternalIriHostname => (Some(InternalSchema), false)
                        case BuiltInKnoraApiHostname => (Some(parseApiV2VersionSegments(segments)), false)

                        case _ =>
                            // If our StringFormatter instance was initialised with the Knora API server's hostname,
                            // use that to identify project-specific Knora API v2 IRIs.
                            knoraApiHostAndPort match {
                                case Some(hostAndPort) =>
                                    if (hostname == hostAndPort) {
                                        (Some(parseApiV2VersionSegments(segments)), true)
                                    } else {
                                        // If we don't recognise the hostname, this isn't a Knora IRI.
                                        (None, false)
                                    }

                                case None =>
                                    // If we don't have the Knora API server's hostname (because we're using the
                                    // StringFormatter instance for constant ontologies), we can't recognise
                                    // project-specific Knora API v2 IRIs.
                                    (None, false)
                            }
                    }

                    // If this is a Knora definition IRI, get its name and optional project code.
                    if (ontologySchema.nonEmpty) {
                        // A Knora definition IRI must start with "http://" and have "ontology" as its second segment.
                        if (!(iri.startsWith("http://") && segments.length >= 3 && segments(1) == "ontology")) {
                            errorFun
                        }

                        // Determine the length of the version segment, if any.
                        val versionSegmentsLength = ontologySchema match {
                            case Some(InternalSchema) => 0
                            case Some(ApiV2WithValueObjects) => 1
                            case Some(ApiV2Simple) => 2
                            case None => throw AssertionException("Unreachable code")
                        }

                        // Make a Vector containing just the optional project code and the ontology name.
                        val projectCodeAndOntologyName: Vector[String] = segments.slice(2, segments.length - versionSegmentsLength)

                        if (projectCodeAndOntologyName.isEmpty || projectCodeAndOntologyName.length > 2) {
                            errorFun
                        }

                        if (projectCodeAndOntologyName.exists(segment => versionSegmentWords.contains(segment))) {
                            errorFun
                        }

                        // Extract the project code.
                        val projectCode: Option[String] = if (projectCodeAndOntologyName.length == 2) {
                            Some(validateProjectShortcode(projectCodeAndOntologyName.head, errorFun))
                        } else {
                            None
                        }

                        // Extract the ontology name.
                        val ontologyName = projectCodeAndOntologyName.last
                        val hasBuiltInOntologyName = isBuiltInOntologyName(ontologyName)

                        if (!hasBuiltInOntologyName) {
                            validateProjectSpecificOntologyName(ontologyName, errorFun)
                        }

                        if ((hasProjectSpecificHostname && hasBuiltInOntologyName) ||
                            (hostname == BuiltInKnoraApiHostname && !hasBuiltInOntologyName)) {
                            errorFun
                        }

                        SmartIriInfo(
                            iriType = KnoraDefinitionIri,
                            projectCode = projectCode,
                            ontologyName = Some(ontologyName),
                            entityName = entityName,
                            ontologySchema = ontologySchema,
                            isBuiltInDef = hasBuiltInOntologyName
                        )
                    } else {
                        UnknownIriInfo
                    }
                }
        }

        override def toString: String = iri

        override def isKnoraIri: Boolean = iriInfo.iriType != UnknownIriType

        override def isKnoraDataIri: Boolean = iriInfo.iriType == KnoraDataIri

        override def isKnoraDefinitionIri: Boolean = iriInfo.iriType == KnoraDefinitionIri

        override def isKnoraInternalDefinitionIri: Boolean = iriInfo.iriType == KnoraDefinitionIri && iriInfo.ontologySchema.contains(InternalSchema)

        override def isKnoraInternalEntityIri: Boolean = isKnoraInternalDefinitionIri && isKnoraEntityIri

        override def isKnoraApiV2DefinitionIri: Boolean = iriInfo.iriType == KnoraDefinitionIri && (iriInfo.ontologySchema match {
            case Some(_: ApiV2Schema) => true
            case _ => false
        })

        override def isKnoraApiV2EntityIri: Boolean = isKnoraApiV2DefinitionIri && isKnoraEntityIri

        override def isKnoraBuiltInDefinitionIri: Boolean = iriInfo.isBuiltInDef

        override def isKnoraOntologyIri: Boolean = iriInfo.iriType == KnoraDefinitionIri && iriInfo.ontologyName.nonEmpty && iriInfo.entityName.isEmpty

        override def isKnoraEntityIri: Boolean = iriInfo.iriType == KnoraDefinitionIri && iriInfo.entityName.nonEmpty

        override def getProjectCode: Option[String] = iriInfo.projectCode

        lazy val ontologyFromEntity: SmartIri = if (isKnoraOntologyIri) {
            throw DataConversionException(s"$iri is not a Knora entity IRI")
        } else {
            val lastHashPos = iri.lastIndexOf('#')

            val entityDelimPos = if (lastHashPos >= 0) {
                lastHashPos
            } else {
                val lastSlashPos = iri.lastIndexOf('/')

                if (lastSlashPos < iri.length - 1) {
                    lastSlashPos
                } else {
                    throw DataConversionException(s"Can't interpret IRI $iri as an entity IRI")
                }
            }

            val convertedIriStr = iri.substring(0, entityDelimPos)

            getOrCacheSmartIri(convertedIriStr, () => new SmartIriImpl(convertedIriStr))
        }

        override def getOntologyFromEntity: SmartIri = ontologyFromEntity

        override def makeEntityIri(entityName: String): SmartIri = {
            if (isKnoraOntologyIri) {
                val entityIriStr = iri + "#" + validateNCName(entityName, throw DataConversionException(s"Invalid entity name: $entityName"))
                getOrCacheSmartIri(entityIriStr, () => new SmartIriImpl(entityIriStr))
            } else {
                throw DataConversionException(s"$iri is not a Knora ontology IRI")
            }
        }

        override def getOntologyName: String = {
            iriInfo.ontologyName match {
                case Some(name) => name
                case None => throw DataConversionException(s"Expected a Knora ontology IRI: $iri")
            }
        }

        override def getEntityName: String = {
            iriInfo.entityName match {
                case Some(name) => name
                case None => throw DataConversionException(s"Expected a Knora entity IRI: $iri")
            }
        }

        override def getOntologySchema: Option[OntologySchema] = iriInfo.ontologySchema

        override def getPrefixLabel: String = {
            val prefix = new StringBuilder

            iriInfo.projectCode match {
                case Some(id) => prefix.append('p').append(id).append('-')
                case None => ()
            }

            val ontologyName = getOntologyName

            // TODO: remove this when unil.ch have converted their ontology names to NCNames (#667).
            if (!ontologyName(0).isLetter) {
                prefix.append("onto")
            }

            prefix.append(ontologyName).toString
        }

        override def toOntologySchema(targetSchema: OntologySchema): SmartIri = {
            if (!isKnoraDefinitionIri || iriInfo.ontologySchema.contains(targetSchema)) {
                this
            } else {
                if (isKnoraOntologyIri) {
                    if (iriInfo.ontologySchema.contains(InternalSchema)) {
                        targetSchema match {
                            case externalSchema: ApiV2Schema => internalToExternalOntologyIri(externalSchema)
                            case _ => throw DataConversionException(s"Cannot convert $iri to $targetSchema")
                        }
                    } else if (targetSchema == InternalSchema) {
                        externalToInternalOntologyIri
                    } else {
                        throw DataConversionException(s"Cannot convert $iri to $targetSchema")
                    }
                } else if (isKnoraEntityIri) {
                    // Can we do an automatic replacement of one IRI with another?
                    OntologyConstants.CorrespondingIris.get((iriInfo.ontologySchema.get, targetSchema)) match {
                        case Some(predicateMap: Map[IRI, IRI]) =>
                            predicateMap.get(iri) match {
                                case Some(convertedIri) =>
                                    // Yes. Return the corresponding IRI in the target schema.
                                    getOrCacheSmartIri(
                                        iriStr = convertedIri,
                                        creationFun = {
                                            () => new SmartIriImpl(convertedIri)
                                        })

                                case None =>
                                    // No. Convert the IRI using a formal procedure.
                                    if (iriInfo.ontologySchema.contains(InternalSchema)) {
                                        targetSchema match {
                                            case externalSchema: ApiV2Schema => internalToExternalEntityIri(externalSchema)
                                            case _ => throw DataConversionException(s"Cannot convert $iri to $targetSchema")
                                        }
                                    } else if (targetSchema == InternalSchema) {
                                        externalToInternalEntityIri
                                    } else {
                                        throw DataConversionException(s"Cannot convert $iri to $targetSchema")
                                    }
                            }

                        case None => throw DataConversionException(s"Cannot convert $iri to $targetSchema")
                    }
                } else {
                    throw AssertionException(s"IRI $iri is a Knora IRI, but is neither an ontology IRI nor an entity IRI")
                }
            }
        }

        private def getVersionSegment(targetSchema: ApiV2Schema): String = {
            targetSchema match {
                case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.VersionSegment
                case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.VersionSegment
            }
        }

        private def externalToInternalEntityIri: SmartIri = {
            // Construct the string representation of this IRI in the target schema.
            val ontologyName = getOntologyName
            val entityName = getEntityName

            val internalOntologyIri = new StringBuilder(OntologyConstants.KnoraInternal.InternalOntologyStart)

            iriInfo.projectCode match {
                case Some(projectCode) => internalOntologyIri.append(projectCode).append('/')
                case None => ()
            }

            val convertedIriStr = internalOntologyIri.append(externalToInternalOntologyName(ontologyName)).append("#").append(entityName).toString

            // Get it from the cache, or construct it and cache it if it's not there.
            getOrCacheSmartIri(
                iriStr = convertedIriStr,
                creationFun = {
                    () =>
                        val convertedSmartIriInfo = iriInfo.copy(
                            ontologyName = Some(externalToInternalOntologyName(getOntologyName)),
                            ontologySchema = Some(InternalSchema)
                        )

                        new SmartIriImpl(
                            iriStr = convertedIriStr,
                            parsedIriInfo = Some(convertedSmartIriInfo)
                        )
                }
            )
        }

        private def internalToExternalEntityIri(targetSchema: ApiV2Schema): SmartIri = {
            //Construct the string representation of this IRI in the target schema.
            val entityName = getEntityName
            val convertedOntologyIri = getOntologyFromEntity.toOntologySchema(targetSchema)
            val convertedEntityIriStr = convertedOntologyIri.toString + "#" + entityName

            // Get it from the cache, or construct it and cache it if it's not there.
            getOrCacheSmartIri(
                iriStr = convertedEntityIriStr,
                creationFun = {
                    () =>
                        val convertedSmartIriInfo = iriInfo.copy(
                            ontologyName = Some(internalToExternalOntologyName(getOntologyName)),
                            ontologySchema = Some(targetSchema)
                        )

                        new SmartIriImpl(
                            iriStr = convertedEntityIriStr,
                            parsedIriInfo = Some(convertedSmartIriInfo)
                        )
                }
            )
        }

        private def internalToExternalOntologyIri(targetSchema: ApiV2Schema): SmartIri = {
            val ontologyName = getOntologyName
            val versionSegment = getVersionSegment(targetSchema)

            val convertedIriStr: IRI = if (isKnoraBuiltInDefinitionIri) {
                OntologyConstants.KnoraApi.ApiOntologyStart + internalToExternalOntologyName(ontologyName) + versionSegment
            } else {
                val projectSpecificApiV2OntologyStart = MaybeProjectSpecificApiV2OntologyStart match {
                    case Some(ontologyStart) => ontologyStart
                    case None => throw AssertionException("Format of project-specific IRIs was not initialised")
                }

                val externalOntologyIri = new StringBuilder(projectSpecificApiV2OntologyStart)

                iriInfo.projectCode match {
                    case Some(projectCode) => externalOntologyIri.append(projectCode).append('/')
                    case None => ()
                }

                externalOntologyIri.append(ontologyName).append(versionSegment).toString
            }

            getOrCacheSmartIri(
                iriStr = convertedIriStr,
                creationFun = {
                    () =>
                        val convertedSmartIriInfo = iriInfo.copy(
                            ontologyName = Some(internalToExternalOntologyName(getOntologyName)),
                            ontologySchema = Some(targetSchema)
                        )

                        new SmartIriImpl(
                            iriStr = convertedIriStr,
                            parsedIriInfo = Some(convertedSmartIriInfo)
                        )
                }
            )
        }

        private lazy val asInternalOntologyIri: SmartIri = {
            val convertedIriStr = makeProjectSpecificInternalOntologyIriStr(
                internalOntologyName = externalToInternalOntologyName(getOntologyName),
                projectCode = iriInfo.projectCode
            )

            getOrCacheSmartIri(
                iriStr = convertedIriStr,
                creationFun = {
                    () =>
                        val convertedSmartIriInfo = iriInfo.copy(
                            ontologyName = Some(externalToInternalOntologyName(getOntologyName)),
                            ontologySchema = Some(InternalSchema)
                        )

                        new SmartIriImpl(
                            iriStr = convertedIriStr,
                            parsedIriInfo = Some(convertedSmartIriInfo)
                        )
                }
            )
        }

        private def externalToInternalOntologyIri: SmartIri = asInternalOntologyIri

        private lazy val asLinkProp: SmartIri = {
            if (!isKnoraEntityIri) {
                throw DataConversionException(s"IRI $iri is not a Knora entity IRI, so it cannot be a link value property IRI")
            }

            val entityName = getEntityName

            if (entityName.endsWith("Value")) {
                val convertedEntityName = entityName.substring(0, entityName.length - "Value".length)
                val convertedIriStr = getOntologyFromEntity.makeEntityIri(convertedEntityName).toString

                getOrCacheSmartIri(
                    iriStr = convertedIriStr,
                    creationFun = {
                        () =>
                            val convertedSmartIriInfo = iriInfo.copy(
                                entityName = Some(convertedEntityName)
                            )

                            new SmartIriImpl(
                                iriStr = convertedIriStr,
                                parsedIriInfo = Some(convertedSmartIriInfo)
                            )
                    }
                )
            } else {
                throw InconsistentTriplestoreDataException(s"Link value predicate IRI $iri does not end with 'Value'")
            }
        }

        override def fromLinkValuePropToLinkProp: SmartIri = asLinkProp

        private lazy val asLinkValueProp: SmartIri = {
            if (!isKnoraEntityIri) {
                throw DataConversionException(s"IRI $iri is not a Knora entity IRI, so it cannot be a link property IRI")
            }

            val entityName = getEntityName
            val convertedEntityName = entityName + "Value"
            val convertedIriStr = getOntologyFromEntity.makeEntityIri(convertedEntityName).toString

            getOrCacheSmartIri(
                iriStr = convertedIriStr,
                creationFun = {
                    () =>
                        val convertedSmartIriInfo = iriInfo.copy(
                            entityName = Some(convertedEntityName)
                        )

                        new SmartIriImpl(
                            iriStr = convertedIriStr,
                            parsedIriInfo = Some(convertedSmartIriInfo)
                        )
                }
            )
        }

        override def fromLinkPropToLinkValueProp: SmartIri = asLinkValueProp
    }

    /**
      * Constructs a [[SmartIri]] by validating and parsing a string representing an IRI. Throws
      * [[DataConversionException]] if the IRI is invalid.
      *
      * @param iri the IRI string to be parsed.
      */
    def toSmartIri(iri: IRI, requireInternal: Boolean = false): SmartIri = {
        // Is this a Knora definition IRI?
        val smartIri: SmartIri = if (CacheableIriStarts.exists(start => iri.startsWith(start))) {
            // Yes. Return it from the cache, or cache it if it's not already cached.
            getOrCacheSmartIri(iri, () => new SmartIriImpl(iri))
        } else {
            // No. Convert it to a SmartIri without caching it.
            new SmartIriImpl(iri)
        }

        if (requireInternal && !smartIri.getOntologySchema.contains(InternalSchema)) {
            throw DataConversionException(s"$smartIri is not an internal IRI")
        } else {
            smartIri
        }
    }

    /**
      * Constructs a [[SmartIri]] by validating and parsing a string representing an IRI.
      *
      * @param iri      the IRI string to be parsed.
      * @param errorFun a function that throws an exception. It will be called if the IRI is invalid.
      */
    def toSmartIriWithErr(iri: IRI, errorFun: => Nothing): SmartIri = {
        // Is this a Knora definition IRI?
        if (CacheableIriStarts.exists(start => iri.startsWith(start))) {
            // Yes. Return it from the cache, or cache it if it's not already cached.
            getOrCacheSmartIri(iri, () => new SmartIriImpl(iri, None, errorFun))
        } else {
            // No. Convert it to a SmartIri without caching it.
            new SmartIriImpl(iri, None, errorFun)
        }
    }

    /**
      * Checks that a string represents a valid integer.
      *
      * @param s        the string to be checked.
      * @param errorFun a function that throws an exception. It will be called if the string does not represent a
      *                 valid integer.
      * @return the integer value of the string.
      */
    def validateInt(s: String, errorFun: => Nothing): Int = {
        try {
            s.toInt
        } catch {
            case _: Exception => errorFun // value could not be converted to an Integer
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
    def validateBigDecimal(s: String, errorFun: => Nothing): BigDecimal = {
        try {
            BigDecimal(s)
        } catch {
            case _: Exception => errorFun // value could not be converted to a decimal
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
    def validateDateTime(s: String, errorFun: => Nothing): String = {
        // check if a string corresponds to the expected format `dateTimeFormat`

        try {
            val formatter = DateTimeFormat.forPattern(dateTimeFormat)
            DateTime.parse(s, formatter).toString(formatter)
        } catch {
            case _: Exception => errorFun // value could not be converted to a valid DateTime using the specified format
        }
    }

    /**
      * Returns `true` if a string is an IRI.
      *
      * @param s the string to be checked.
      * @return `true` if the string is an IRI.
      */
    def isIri(s: String): Boolean = {
        urlValidator.isValid(s)
    }

    /**
      * Checks that a string represents a valid IRI. Also encodes the IRI, preserving existing %-escapes.
      *
      * @param s        the string to be checked.
      * @param errorFun a function that throws an exception. It will be called if the string does not represent a valid
      *                 IRI.
      * @return the same string.
      */
    def validateAndEscapeIri(s: String, errorFun: => Nothing): IRI = {
        val urlEncodedStr = encodeAllowEscapes(s)

        if (urlValidator.isValid(urlEncodedStr)) {
            urlEncodedStr
        } else {
            errorFun
        }
    }

    /**
      * Check that an optional string represents a valid IRI.
      *
      * @param maybeString the optional string to be checked.
      * @param errorFun    a function that throws an exception. It will be called if the string does not represent a valid
      *                    IRI.
      * @return the same optional string.
      */
    def toOptionalIri(maybeString: Option[String], errorFun: => Nothing): Option[IRI] = {
        maybeString match {
            case Some(s) => Some(validateAndEscapeIri(s, errorFun))
            case None => None
        }
    }

    /**
      * Returns `true` if an IRI string looks like a Knora data IRI.
      *
      * @param iri the IRI to be checked.
      */
    def isKnoraDataIriStr(iri: IRI): Boolean = {
        DataIriStarts.exists(startStr => iri.startsWith(startStr))
    }

    /**
      * Returns `true` if an IRI string looks like a Knora project IRI
      *
      * @param iri the IRI to be checked.
      */
    def isKnoraProjectIriStr(iri: IRI): Boolean = {
        iri.startsWith("http://" + KnoraIdUtil.IriDomain + "/projects/")
    }

    /**
      * Returns `true` if an IRI string looks like a Knora list IRI.
      *
      * @param iri the IRI to be checked.
      */
    def isKnoraListIriStr(iri: IRI): Boolean = {
        iri.startsWith("http://" + KnoraIdUtil.IriDomain + "/lists/")
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
    def validateStandoffLinkResourceReference(s: String, acceptClientIDs: Boolean, errorFun: => Nothing): IRI = {
        if (acceptClientIDs) {
            s match {
                case StandoffLinkReferenceToClientIDForResourceRegex(_) => s
                case _ => validateAndEscapeIri(s, errorFun)
            }
        } else {
            validateAndEscapeIri(s, errorFun)
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
    def toRealStandoffLinkTargetResourceIri(iri: IRI, clientResourceIDsToResourceIris: Map[String, IRI]): IRI = {
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
      * @return the same string, escaped or unescaped as requested.
      */
    def toSparqlEncodedString(s: String, errorFun: => Nothing): String = {
        if (s.isEmpty || s.contains("\r")) errorFun

        // http://www.morelab.deusto.es/code_injection/

        StringUtils.replaceEach(
            s,
            SparqlEscapeInput,
            SparqlEscapeOutput
        )
    }

    /**
      * Unescapes a string that has been escaped for SPARQL.
      *
      * @param s the string to be unescaped.
      * @return the unescaped string.
      */
    def fromSparqlEncodedString(s: String): String = {
        StringUtils.replaceEach(
            s,
            SparqlEscapeOutput,
            SparqlEscapeInput
        )
    }

    /**
      * Parses an object of `salsah-gui:guiAttributeDefinition`.
      *
      * @param s        the string to be parsed.
      * @param errorFun a function that throws an exception. It will be called if the string is invalid.
      * @return a [[SalsahGuiAttributeDefinition]].
      */
    def toSalsahGuiAttributeDefinition(s: String, errorFun: => Nothing): SalsahGuiAttributeDefinition = {
        s match {
            case SalsahGuiAttributeDefinitionRegex(attributeName, Optional(maybeRequired), allowedTypeStr, _, Optional(maybeEnumeratedValuesStr)) =>
                val allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.lookup(allowedTypeStr)

                val enumeratedValues: Set[String] = maybeEnumeratedValuesStr match {
                    case Some(enumeratedValuesStr) =>
                        if (allowedType != OntologyConstants.SalsahGui.SalsahGuiAttributeType.Str) {
                            errorFun
                        }

                        enumeratedValuesStr.split('|').toSet

                    case None => Set.empty[String]
                }

                SalsahGuiAttributeDefinition(
                    attributeName = attributeName,
                    isRequired = maybeRequired.nonEmpty,
                    allowedType = allowedType,
                    enumeratedValues = enumeratedValues,
                    unparsedString = s
                )

            case _ => errorFun
        }
    }

    /**
      * Parses an object of `salsah-gui:guiAttribute`.
      *
      * @param s             the string to be parsed.
      * @param attributeDefs the values of `salsah-gui:guiAttributeDefinition` for the property.
      * @param errorFun      a function that throws an exception. It will be called if the string is invalid.
      * @return a [[SalsahGuiAttribute]].
      */
    def toSalsahGuiAttribute(s: String, attributeDefs: Set[SalsahGuiAttributeDefinition], errorFun: => Nothing): SalsahGuiAttribute = {
        // Try to parse the expression using a regex.
        s match {
            case SalsahGuiAttributeRegex(attributeName: String, attributeValue: String) =>
                // The regex matched. Get the attribute definition corresponding to the attribute name.
                val attributeDef = attributeDefs.find(_.attributeName == attributeName).getOrElse(errorFun)

                // Try to parse the value as the type given in the attribute definition.
                val maybeParsedAttrValue: Option[SalsahGuiAttributeValue] = attributeDef.allowedType match {
                    case OntologyConstants.SalsahGui.SalsahGuiAttributeType.Integer =>
                        catching(classOf[NumberFormatException]).opt(attributeValue.toInt).map(SalsahGuiIntegerAttributeValue)

                    case OntologyConstants.SalsahGui.SalsahGuiAttributeType.Decimal =>
                        catching(classOf[NumberFormatException]).opt(BigDecimal(attributeValue)).map(SalsahGuiDecimalAttributeValue)

                    case OntologyConstants.SalsahGui.SalsahGuiAttributeType.Percent =>
                        if (attributeValue.endsWith("%")) {
                            val intStr = attributeValue.stripSuffix("%")
                            catching(classOf[NumberFormatException]).opt(intStr.toInt).map(SalsahGuiPercentAttributeValue)
                        } else {
                            None
                        }

                    case OntologyConstants.SalsahGui.SalsahGuiAttributeType.Iri =>
                        if (attributeValue.startsWith("<") && attributeValue.endsWith(">")) {
                            val iriWithoutAngleBrackets = attributeValue.substring(1, attributeValue.length - 1)

                            catching(classOf[ParseException]).opt(validateAndEscapeIri(
                                iriWithoutAngleBrackets,
                                throw new ParseException("Couldn't parse IRI", 1))
                            ).map(SalsahGuiIriAttributeValue)
                        } else {
                            None
                        }

                    case OntologyConstants.SalsahGui.SalsahGuiAttributeType.Str =>
                        if (attributeDef.enumeratedValues.nonEmpty && !attributeDef.enumeratedValues.contains(attributeValue)) {
                            errorFun
                        }

                        Some(SalsahGuiStringAttributeValue(attributeValue))

                    case _ => None
                }

                maybeParsedAttrValue match {
                    case Some(parsedAttrValue) => SalsahGuiAttribute(attributeName = attributeName, attributeValue = parsedAttrValue)
                    case None => errorFun
                }

            case _ =>
                // The expression couldn't be parsed.
                errorFun
        }
    }

    /**
      * Validates an OWL cardinality value, which must be 0 or 1 in Knora, and returns the corresponding integer.
      *
      * @param s        the string to be validated.
      * @param errorFun a function that throws an exception. It will be called if the string is invalid.
      * @return the corresponding integer value.
      */
    def validateCardinalityValue(s: String, errorFun: => Nothing): Int = {
        s match {
            case "0" => 0
            case "1" => 1
            case _ => errorFun
        }
    }

    /**
      * Parses an ISO-8601 instant and returns an instance of [[Instant]].
      *
      * @param s        the string to be parsed.
      * @param errorFun a function that throws an exception. It will be called if the string cannot be parsed.
      * @return an [[Instant]].
      */
    def toInstant(s: String, errorFun: => Nothing): Instant = {
        try {
            Instant.parse(s)
        } catch {
            case _: Exception => errorFun
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
    def validateGeometryString(s: String, errorFun: => Nothing): String = {
        // TODO: For now, we just make sure that the string is valid JSON. We should stop storing JSON in the triplestore, and represent geometry in RDF instead (issue 169).

        try {
            JsonParser(s)
            s
        } catch {
            case _: Exception => errorFun
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
    def validateColor(s: String, errorFun: => Nothing): String = {
        ColorRegex.findFirstIn(s) match {
            case Some(dateStr) => dateStr
            case None => errorFun // not a valid color hex value string
        }
    }

    /**
      * Checks that the format of a Knora date string is valid.
      *
      * @param s        a Knora date string.
      * @param errorFun a function that throws an exception. It will be called if the date's format is invalid.
      * @return the same string.
      */
    def validateDate(s: String, errorFun: => Nothing): String = {
        // if the pattern doesn't match (=> None), the date string is formally invalid
        // Please note that this is a mere formal validation,
        // the actual validity check is done in `DateUtilV1.dateString2DateRange`
        KnoraDateRegex.findFirstIn(s) match {
            case Some(value) => value
            case None => errorFun // calling this function throws an error
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
    def validateBoolean(s: String, errorFun: => Nothing): Boolean = {
        try {
            s.toBoolean
        } catch {
            case _: Exception => errorFun // value could not be converted to Boolean
        }
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
    def optionStringToBoolean(maybe: Option[String], errorFun: => Nothing): Boolean = {
        try {
            maybe.exists(_.toBoolean)
        } catch {
            case _: IllegalArgumentException => errorFun
        }
    }

    /**
      * Checks that a string is a valid XML [[https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName NCName]].
      *
      * @param ncName   the string to be checked.
      * @param errorFun a function that throws an exception. It will be called if the string is invalid.
      * @return the same string.
      */
    def validateNCName(ncName: String, errorFun: => Nothing): String = {
        NCNameRegex.findFirstIn(ncName) match {
            case Some(value) => value
            case None => errorFun
        }
    }

    /**
      * Returns `true` if an ontology name is reserved for a built-in ontology.
      *
      * @param ontologyName the ontology name to be checked.
      * @return `true` if the ontology name is reserved for a built-in ontology.
      */
    def isBuiltInOntologyName(ontologyName: String): Boolean = {
        OntologyConstants.BuiltInOntologyLabels.contains(ontologyName)
    }

    /**
      * Checks that a name is valid as a project-specific ontology name.
      *
      * @param ontologyName the ontology name to be checked.
      * @param errorFun     a function that throws an exception. It will be called if the name is invalid.
      * @return the same ontology name.
      */
    def validateProjectSpecificOntologyName(ontologyName: String, errorFun: => Nothing): String = {
        // TODO: Uncomment this when unil.ch have renamed their ontologies to use NCNames (#667).
        /*
        ontologyName match {
            case NCNameRegex(_*) => ()
            case _ => errorFun
        }
        */

        val lowerCaseOntologyName = ontologyName.toLowerCase

        lowerCaseOntologyName match {
            case ApiVersionNumberRegex(_*) => errorFun
            case _ => ()
        }

        if (isBuiltInOntologyName(ontologyName)) {
            errorFun
        }

        for (reservedIriWord <- reservedIriWords) {
            if (lowerCaseOntologyName.contains(reservedIriWord)) {
                errorFun
            }
        }

        ontologyName
    }

    /**
      * Given a valid internal ontology name and an optional project code, constructs the corresponding internal
      * ontology IRI.
      *
      * @param internalOntologyName the ontology name.
      * @param projectCode          the project code.
      * @return the ontology IRI.
      */
    private def makeProjectSpecificInternalOntologyIriStr(internalOntologyName: String, projectCode: Option[String]): IRI = {
        val internalOntologyIri = new StringBuilder(OntologyConstants.KnoraInternal.InternalOntologyStart)

        projectCode match {
            case Some(code) => internalOntologyIri.append(code).append('/')
            case None => ()
        }

        internalOntologyIri.append(internalOntologyName).toString
    }

    /**
      * Given a valid internal ontology name and an optional project code, constructs the corresponding internal
      * ontology IRI.
      *
      * @param internalOntologyName the ontology name.
      * @param projectCode          the project code.
      * @return the ontology IRI.
      */
    def makeProjectSpecificInternalOntologyIri(internalOntologyName: String, projectCode: Option[String]): SmartIri = {
        toSmartIri(makeProjectSpecificInternalOntologyIriStr(internalOntologyName, projectCode))
    }

    /**
      * Converts an internal ontology name to an external ontology name. This only affects `knora-base`, whose
      * external equivalent is `knora-api.`
      *
      * @param ontologyName an internal ontology name.
      * @return the corresponding external ontology name.
      */
    private def internalToExternalOntologyName(ontologyName: String): String = {
        if (ontologyName == OntologyConstants.KnoraBase.KnoraBaseOntologyLabel) {
            OntologyConstants.KnoraApi.KnoraApiOntologyLabel
        } else {
            ontologyName
        }
    }

    /**
      * Converts an external ontology name to an internal ontology name. This only affects `knora-api`, whose
      * internal equivalent is `knora-base.`
      *
      * @param ontologyName an external ontology name.
      * @return the corresponding internal ontology name.
      */
    private def externalToInternalOntologyName(ontologyName: String): String = {
        if (ontologyName == OntologyConstants.KnoraApi.KnoraApiOntologyLabel) {
            OntologyConstants.KnoraBase.KnoraBaseOntologyLabel
        } else {
            ontologyName
        }
    }

    /**
      * Converts the IRI of a project-specific internal ontology (used in the triplestore) to an XML prefix label and
      * namespace for use in data import.
      *
      * @param internalOntologyIri the IRI of the project-specific internal ontology. Any trailing # character will be
      *                            stripped before the conversion.
      * @return the corresponding XML prefix label and import namespace.
      */
    def internalOntologyIriToXmlNamespaceInfoV1(internalOntologyIri: SmartIri): XmlImportNamespaceInfoV1 = {
        val namespace = new StringBuilder(OntologyConstants.KnoraXmlImportV1.ProjectSpecificXmlImportNamespace.XmlImportNamespaceStart)

        internalOntologyIri.getProjectCode match {
            case Some(projectCode) => namespace.append(projectCode).append('/')
            case None => ()
        }

        namespace.append(internalOntologyIri.getOntologyName).append(OntologyConstants.KnoraXmlImportV1.ProjectSpecificXmlImportNamespace.XmlImportNamespaceEnd)
        XmlImportNamespaceInfoV1(namespace = namespace.toString, prefixLabel = internalOntologyIri.getPrefixLabel)
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
    def xmlImportNamespaceToInternalOntologyIriV1(namespace: String, errorFun: => Nothing): SmartIri = {
        namespace match {
            case ProjectSpecificXmlImportNamespaceRegex(_, Optional(projectCode), ontologyName) if !isBuiltInOntologyName(ontologyName) =>
                makeProjectSpecificInternalOntologyIri(
                    internalOntologyName = externalToInternalOntologyName(ontologyName),
                    projectCode = projectCode
                )

            case _ => errorFun
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
    def xmlImportElementNameToInternalOntologyIriV1(namespace: String, elementLabel: String, errorFun: => Nothing): IRI = {
        val ontologyIri = xmlImportNamespaceToInternalOntologyIriV1(namespace, errorFun)
        ontologyIri + "#" + elementLabel
    }

    /**
      * In XML import data, a property from another ontology is referred to as `prefixLabel__localName`. The prefix label
      * may start with a project ID (prefixed with 'p') and a hyphen. This function attempts to parse a property name in
      * that format.
      *
      * @param prefixLabelAndLocalName a string that may refer to a property in the format `prefixLabel__localName`.
      * @return if successful, a `Some` containing the entity's internal IRI, otherwise `None`.
      */
    def toPropertyIriFromOtherOntologyInXmlImport(prefixLabelAndLocalName: String): Option[IRI] = {
        prefixLabelAndLocalName match {
            case PropertyFromOtherOntologyInXmlImportRegex(_, Optional(maybeProjectID), prefixLabel, localName) =>
                maybeProjectID match {
                    case Some(projectID) =>
                        Some(s"${OntologyConstants.KnoraInternal.InternalOntologyStart}$projectID/$prefixLabel#$localName")

                    case None =>
                        Some(s"${OntologyConstants.KnoraInternal.InternalOntologyStart}$prefixLabel#$localName")
                }

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
    def validateMapPath(mapPath: String, errorFun: => Nothing): String = {
        val splitPath: Array[String] = mapPath.split('/')

        for (name <- splitPath) {
            validateNCName(name, errorFun)
        }

        mapPath
    }

    /**
      * Determines whether a URL path refers to a built-in API v2 ontology (simple or complex).
      *
      * @param urlPath the URL path.
      * @return true if the path refers to a built-in API v2 ontology.
      */
    def isBuiltInApiV2OntologyUrlPath(urlPath: String): Boolean = {
        urlPath match {
            case ApiV2OntologyUrlPathRegex(_, _, ontologyName, _) if isBuiltInOntologyName(ontologyName) => true
            case _ => false
        }
    }

    /**
      * Determines whether a URL path refers to a project-specific API v2 ontology (simple or complex).
      *
      * @param urlPath the URL path.
      * @return true if the path refers to a project-specific API v2 ontology.
      */
    def isProjectSpecificApiV2OntologyUrlPath(urlPath: String): Boolean = {
        urlPath match {
            case ApiV2OntologyUrlPathRegex(_, _, ontologyName, _) if !isBuiltInOntologyName(ontologyName) => true
            case _ => false
        }
    }

    /**
      * Given the projectInfo calculates the project's data named graph.
      *
      * @param projectInfo the project's [[ProjectInfoV1]].
      * @return the IRI of the project's data named graph.
      */
    def projectDataNamedGraph(projectInfo: ProjectInfoV1): IRI = {
        if (projectInfo.shortcode.isDefined) {
            OntologyConstants.NamedGraphs.DataNamedGraphStart + "/" + projectInfo.shortcode.get + "/" + projectInfo.shortname
        } else {
            OntologyConstants.NamedGraphs.DataNamedGraphStart + "/" + projectInfo.shortname
        }
    }

    /**
      * Given the [[ProjectADM]] calculates the project's data named graph.
      *
      * @param project the project's [[ProjectADM]].
      * @return the IRI of the project's data named graph.
      */
    def projectDataNamedGraphV2(project: ProjectADM): IRI = {
        if (project.shortcode.isDefined) {
            OntologyConstants.NamedGraphs.DataNamedGraphStart + "/" + project.shortcode.get + "/" + project.shortname
        } else {
            OntologyConstants.NamedGraphs.DataNamedGraphStart + "/" + project.shortname
        }
    }

    /**
      * Given the project shortcode, checks if it is in a valid format, and converts it to upper case.
      *
      * @param shortcode the project's shortcode.
      * @return the short ode in upper case.
      */
    def validateProjectShortcode(shortcode: String, errorFun: => Nothing): String = {
        ProjectIDRegex.findFirstIn(shortcode.toUpperCase) match {
            case Some(value) => value
            case None => errorFun
        }
    }
}
