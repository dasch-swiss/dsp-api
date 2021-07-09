/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages

import java.nio.ByteBuffer
import java.text.ParseException
import java.time._
import java.time.format.DateTimeFormatter
import java.time.temporal.{ChronoField, TemporalAccessor}
import java.util.concurrent.ConcurrentHashMap
import java.util.{Base64, UUID}

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.Timeout
import com.google.gwt.safehtml.shared.UriUtils._
import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import org.knora.webapi._
import org.knora.webapi.exceptions._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants.SalsahGui
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.store.triplestoremessages.{
  SparqlAskRequest,
  SparqlAskResponse,
  StringLiteralSequenceV2,
  StringLiteralV2
}
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoV1
import org.knora.webapi.messages.v2.responder.KnoraContentV2
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.util.{Base64UrlCheckDigit, JavaUtil}
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.Exception._
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

/**
  * Provides instances of [[StringFormatter]], as well as string formatting constants.
  */
object StringFormatter {

  // A non-printing delimiter character, Unicode INFORMATION SEPARATOR ONE, that should never occur in data.
  val INFORMATION_SEPARATOR_ONE = '\u001F'

  // A non-printing delimiter character, Unicode INFORMATION SEPARATOR TWO, that should never occur in data.
  val INFORMATION_SEPARATOR_TWO = '\u001E'

  // A non-printing delimiter character, Unicode INFORMATION SEPARATOR THREE, that should never occur in data.
  val INFORMATION_SEPARATOR_THREE = '\u001D'

  // A non-printing delimiter character, Unicode INFORMATION SEPARATOR FOUR, that should never occur in data.
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
    * String representation of the name of the Gregorian calendar.
    */
  val CalendarGregorian: String = "GREGORIAN"

  /**
    * String representation of the name of the Julian calendar.
    */
  val CalendarJulian: String = "JULIAN"

  /**
    * String representation of the name of the Islamic calendar.
    */
  val CalendarIslamic: String = "ISLAMIC"

  /**
    * String representation of day precision in a date.
    */
  val PrecisionDay: String = "DAY"

  /**
    * String representation of month precision in a date.
    */
  val PrecisionMonth: String = "MONTH"

  /**
    * String representation of year precision in a date.
    */
  val PrecisionYear: String = "YEAR"

  /**
    * The version number of the current version of Knora's ARK URL format.
    */
  val ArkVersion: String = "2"

  /**
    * The length of the canonical representation of a UUID.
    */
  val CanonicalUuidLength = 36

  /**
    * The length of a Base64-encoded UUID.
    */
  val Base64UuidLength = 22

  /**
    * The maximum number of times that `makeUnusedIri` will try to make a new, unused IRI.
    */
  val MAX_IRI_ATTEMPTS: Int = 5

  /**
    * The domain name used to construct Knora IRIs.
    */
  val IriDomain: String = "rdfh.ch"

  /**
    * A keyword used in IRI entity names to introduce a collection type annotation for client code generation.
    */
  val ClientCollectionTypeKeyword: String = "collection:"

  /**
    * A string found in IRIs representing collection type annotations for client code generation.
    */
  val ClientCollectionEntityNameStart: String = "#" + ClientCollectionTypeKeyword

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
    override val attributeType: OntologyConstants.SalsahGui.SalsahGuiAttributeType.Value =
      OntologyConstants.SalsahGui.SalsahGuiAttributeType.Integer
  }

  /**
    * Represents a parsed percent value of an attribute that is the object of the property `salsah-gui:guiAttribute`.
    *
    * @param value the percent value.
    */
  case class SalsahGuiPercentAttributeValue(value: Int) extends SalsahGuiAttributeValue {
    override val attributeType: OntologyConstants.SalsahGui.SalsahGuiAttributeType.Value =
      OntologyConstants.SalsahGui.SalsahGuiAttributeType.Percent
  }

  /**
    * Represents a parsed decimal value of an attribute that is the object of the property `salsah-gui:guiAttribute`.
    *
    * @param value the decimal value.
    */
  case class SalsahGuiDecimalAttributeValue(value: BigDecimal) extends SalsahGuiAttributeValue {
    override val attributeType: OntologyConstants.SalsahGui.SalsahGuiAttributeType.Value =
      OntologyConstants.SalsahGui.SalsahGuiAttributeType.Decimal
  }

  /**
    * Represents a parsed string value of an attribute that is the object of the property `salsah-gui:guiAttribute`.
    *
    * @param value the string value.
    */
  case class SalsahGuiStringAttributeValue(value: String) extends SalsahGuiAttributeValue {
    override val attributeType: OntologyConstants.SalsahGui.SalsahGuiAttributeType.Value =
      OntologyConstants.SalsahGui.SalsahGuiAttributeType.Str
  }

  /**
    * Represents a parsed IRI value of an attribute that is the object of the property `salsah-gui:guiAttribute`.
    *
    * @param value the IRI value.
    */
  case class SalsahGuiIriAttributeValue(value: IRI) extends SalsahGuiAttributeValue {
    override val attributeType: OntologyConstants.SalsahGui.SalsahGuiAttributeType.Value =
      OntologyConstants.SalsahGui.SalsahGuiAttributeType.Iri
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
    * only to initialise the hard-coded API v2 ontologies [[org.knora.webapi.messages.v2.responder.ontologymessages.KnoraBaseToApiV2SimpleTransformationRules]]
    * and [[org.knora.webapi.messages.v2.responder.ontologymessages.KnoraBaseToApiV2ComplexTransformationRules]].
    */
  private val instanceForConstantOntologies = new StringFormatter(None)

  /**
    * Gets the singleton instance of [[StringFormatter]] that handles IRIs from data.
    */
  def getGeneralInstance: StringFormatter = {
    generalInstance match {
      case Some(instance) => instance
      case None           => throw AssertionException("StringFormatter not yet initialised")
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
  def init(settings: KnoraSettingsImpl): Unit = {
    this.synchronized {
      generalInstance match {
        case Some(_) => ()
        case None    => generalInstance = Some(new StringFormatter(Some(settings)))
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
        case None    => generalInstance = Some(new StringFormatter(maybeSettings = None, initForTest = true))
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
    * @param iriType            the type of the IRI.
    * @param projectCode        the IRI's project code, if any.
    * @param ontologyName       the IRI's ontology name, if any.
    * @param entityName         the IRI's entity name, if any.
    * @param resourceID         if this is a resource IRI or value IRI, its resource ID.
    * @param valueID            if this is a value IRI, its value ID.
    * @param standoffStartIndex if this is a standoff IRI, its start index.
    * @param ontologySchema     the IRI's ontology schema, or `None` if it is not a Knora definition IRI.
    * @param isBuiltInDef       `true` if the IRI refers to a built-in Knora ontology or ontology entity.
    */
  private case class SmartIriInfo(iriType: IriType,
                                  projectCode: Option[String] = None,
                                  ontologyName: Option[String] = None,
                                  entityName: Option[String] = None,
                                  resourceID: Option[String] = None,
                                  valueID: Option[String] = None,
                                  standoffStartIndex: Option[Int] = None,
                                  ontologySchema: Option[OntologySchema],
                                  isBuiltInDef: Boolean = false,
                                  sharedOntology: Boolean = false)

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
      JavaUtil.function({ _ =>
        creationFun()
      })
    )
  }
}

/**
  * Represents a parsed IRI with Knora-specific functionality. To construct a `SmartIri`,
  * `import org.knora.webapi.messages.StringFormatter.IriConversions.ConvertibleIri`, then call one of the methods that
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
    * Returns this IRI as a string in angle brackets.
    */
  def toSparql: String

  /**
    * Returns `true` if this is a Knora data or definition IRI.
    */
  def isKnoraIri: Boolean

  /**
    * Returns `true` if this is a Knora data IRI.
    */
  def isKnoraDataIri: Boolean

  /**
    * Returns `true` if this is a Knora resource IRI.
    */
  def isKnoraResourceIri: Boolean

  /**
    * Returns `true` if this is a Knora value IRI.
    */
  def isKnoraValueIri: Boolean

  /**
    * Returns `true` if this is a Knora standoff IRI.
    */
  def isKnoraStandoffIri: Boolean

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
    * Returns `true` if this IRI belongs to a shared ontology.
    */
  def isKnoraSharedDefinitionIri: Boolean

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
    * Returns the IRI's resource ID, if any.
    */
  def getResourceID: Option[String]

  /**
    * Returns the IRI's value ID, if any.
    */
  def getValueID: Option[String]

  /**
    * Returns the IRI's standoff start index, if any.
    */
  def getStandoffStartIndex: Option[Int]

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
    * Checks that the IRI's ontology schema, if present, corresponds to the specified schema. If the IRI
    * has no schema, does nothing. If the IRI has a schema that's different to the specified schema, calls
    * `errorFun`.
    *
    * @param allowedSchema the schema to be allowed.
    * @param errorFun      a function that throws an exception. It will be called if the IRI has a different schema
    *                      to the one specified.
    * @return the same IRI
    */
  def checkApiV2Schema(allowedSchema: ApiV2Schema, errorFun: => Nothing): SmartIri

  /**
    * Converts this IRI to another ontology schema.
    *
    * @param targetSchema the target schema.
    */
  override def toOntologySchema(targetSchema: OntologySchema): SmartIri

  /**
    * Constructs a short prefix label for the ontology that the IRI belongs to.
    */
  def getShortPrefixLabel: String

  /**
    * Constructs a longer prefix label than the one returned by `getShortPrefixLabel`, which may be needed
    * if there are ontology name collisions.
    */
  def getLongPrefixLabel: String

  /**
    * If this is the IRI of a link value property, returns the IRI of the corresponding link property. Throws
    * [[DataConversionException]] if this IRI is not a Knora entity IRI.
    */
  def fromLinkValuePropToLinkProp: SmartIri

  /**
    * If this is the IRI of a link property, returns the IRI of the corresponding link value property. Throws
    * [[DataConversionException]] if this IRI is not a Knora entity IRI.
    */
  def fromLinkPropToLinkValueProp: SmartIri

  /**
    * If this is a Knora data IRI representing a resource, returns an ARK URL for the resource. Throws
    * [[DataConversionException]] if this IRI is not a Knora resource IRI.
    *
    * @param maybeTimestamp an optional timestamp indicating the point in the resource's version history that the ARK URL should
    *                       cite.
    */
  def fromResourceIriToArkUrl(maybeTimestamp: Option[Instant] = None): String

  /**
    * If this is a Knora data IRI representing a value, returns an ARK URL for the value. Throws
    * [[DataConversionException]] if this IRI is not a Knora value IRI.
    *
    * @param maybeTimestamp an optional timestamp indicating the point in the value's version history that the ARK URL should
    *                       cite.
    */
  def fromValueIriToArkUrl(valueUUID: UUID, maybeTimestamp: Option[Instant] = None): String

  override def equals(obj: scala.Any): Boolean = {
    // See the comment at the top of the SmartIri trait.
    obj match {
      case that: SmartIri => this.toString == that.toString
      case _              => false
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
    def toSmartIriWithErr(errorFun: => Nothing)(implicit stringFormatter: StringFormatter): SmartIri =
      stringFormatter.toSmartIriWithErr(self, errorFun)
  }

}

/**
  * Handles string parsing, formatting, conversion, and validation.
  */
class StringFormatter private (val maybeSettings: Option[KnoraSettingsImpl] = None,
                               maybeKnoraHostAndPort: Option[String] = None,
                               initForTest: Boolean = false) {

  import StringFormatter._

  private val base64Encoder = Base64.getUrlEncoder.withoutPadding
  private val base64Decoder = Base64.getUrlDecoder

  // The host and port number that this Knora server is running on, and that should be used
  // when constructing IRIs for project-specific ontologies.
  private val knoraApiHostAndPort: Option[String] = if (initForTest) {
    // Use the default host and port for automated testing.
    Some("0.0.0.0:3333")
  } else {
    maybeSettings match {
      case Some(settings) => Some(settings.externalOntologyIriHostAndPort)
      case None           => maybeKnoraHostAndPort
    }
  }

  // The protocol and host that the ARK resolver is running on.
  private val arkResolver: Option[String] = if (initForTest) {
    Some("http://0.0.0.0:3336")
  } else {
    maybeSettings.map(_.arkResolver)
  }

  // The DaSCH's ARK assigned number.
  private val arkAssignedNumber: Option[Int] = if (initForTest) {
    Some(72163)
  } else {
    maybeSettings.map(_.arkAssignedNumber)
  }

  // Valid URL schemes.
  private val schemes = Array("http", "https")

  // A validator for URLs.
  private val urlValidator = new UrlValidator(schemes, UrlValidator.ALLOW_LOCAL_URLS) // local urls are URL-encoded Knora IRIs as part of the whole URL

  // The hostname used in internal Knora IRIs.
  private val InternalIriHostname = "www.knora.org"

  // The hostname used in built-in and shared Knora API v2 IRIs.
  private val CentralKnoraApiHostname = "api.knora.org"

  // The strings that Knora data IRIs can start with.
  private val DataIriStarts: Set[String] = Set(
    "http://" + IriDomain + "/"
  )

  // The project code of the default shared ontologies project.
  private val DefaultSharedOntologiesProjectCode = "0000"

  // The beginnings of Knora definition IRIs that we know we can cache.
  private val KnoraDefinitionIriStarts = (Set(
    InternalIriHostname,
    CentralKnoraApiHostname
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
  private val reservedIriWords = Set("knora", "ontology", "rdf", "rdfs", "owl", "xsd", "schema", "shared") ++ versionSegmentWords

  // The expected format of a Knora date.
  // Calendar:YYYY[-MM[-DD]][ EE][:YYYY[-MM[-DD]][ EE]]
  // EE being the era: one of BC or AD
  private val KnoraDateRegex: Regex = ("""^(GREGORIAN|JULIAN|ISLAMIC)""" +
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
      OntologyConstants.KnoraApiV2Complex.VersionSegment + "|" + OntologyConstants.KnoraApiV2Simple.VersionSegment + ")$"
  ).r

  // The start of the IRI of a project-specific API v2 ontology that is served by this API server.
  private val MaybeProjectSpecificApiV2OntologyStart: Option[String] = knoraApiHostAndPort match {
    case Some(hostAndPort) => Some("http://" + hostAndPort + "/ontology/")
    case None              => None
  }

  // A regex for a project-specific XML import namespace.
  private val ProjectSpecificXmlImportNamespaceRegex: Regex = (
    "^" + OntologyConstants.KnoraXmlImportV1.ProjectSpecificXmlImportNamespace.XmlImportNamespaceStart +
      "(shared/)?((" + ProjectIDPattern + ")/)?(" + NCNamePattern + ")" +
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

  // A regex for matching a string containing an email address.
  private val EmailAddressRegex: Regex =
    """^.+@.+$""".r

  // A regex sub-pattern matching the random IDs generated by KnoraIdUtil, which are Base64-encoded
  // using the "URL and Filename safe" Base 64 alphabet, without padding, as specified in Table 2 of
  // RFC 4648.
  private val Base64UrlPattern = "[A-Za-z0-9_-]+"

  // Calculates check digits for resource IDs in ARK URLs.
  private val base64UrlCheckDigit = new Base64UrlCheckDigit

  // A regex that matches a Knora resource IRI.
  private val ResourceIriRegex: Regex =
    ("^http://" + IriDomain + "/resources/(" + Base64UrlPattern + ")$").r

  // A regex that matches a Knora value IRI.
  private val ValueIriRegex: Regex =
    ("^http://" + IriDomain + "/resources/(" + Base64UrlPattern + ")/values/(" + Base64UrlPattern + ")$").r

  // A regex that matches a Knora standoff IRI.
  private val StandoffIriRegex: Regex =
    ("^http://" + IriDomain + "/resources/(" + Base64UrlPattern + ")/values/(" + Base64UrlPattern + """)/standoff/(\d+)$""").r

  // A regex that parses a Knora ARK timestamp.
  private val ArkTimestampRegex: Regex =
    """^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(\d{1,9})?Z$""".r

  // A regex that finds trailing zeroes.
  private val TrailingZerosRegex: Regex =
    """0+$""".r

  /**
    * A regex that matches a valid username
    * - 4 - 50 characters long
    * - Only contains alphanumeric characters, underscore and dot.
    * - Underscore and dot can't be at the end or start of a username
    * - Underscore or dot can't be used multiple times in a row
    */
  private val UsernameRegex: Regex =
    """^(?=.{4,50}$)(?![_.])(?!.*[_.]{2})[a-zA-Z0-9._]+(?<![_.])$""".r

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

    def this(iriStr: IRI, parsedIriInfo: Option[SmartIriInfo]) =
      this(iriStr, parsedIriInfo, throw DataConversionException(s"Couldn't parse IRI: $iriStr"))

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
        ApiV2Complex
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
        if (DataIriStarts.exists(startStr => iri.startsWith(startStr))) {
          // This is a Knora data IRI. What sort of data IRI is it?
          iri match {
            case ResourceIriRegex(resourceID: String) =>
              // It's a resource IRI.
              SmartIriInfo(
                iriType = KnoraDataIri,
                ontologySchema = None,
                resourceID = Some(resourceID)
              )

            case ValueIriRegex(resourceID: String, valueID: String) =>
              // It's a value IRI.
              SmartIriInfo(
                iriType = KnoraDataIri,
                ontologySchema = None,
                resourceID = Some(resourceID),
                valueID = Some(valueID)
              )

            case StandoffIriRegex(resourceID: String, valueID: String, standoffStartIndex: String) =>
              // It's a standoff IRI.
              SmartIriInfo(
                iriType = KnoraDataIri,
                ontologySchema = None,
                resourceID = Some(resourceID),
                valueID = Some(valueID),
                standoffStartIndex = Some(standoffStartIndex.toInt)
              )

            case _ =>
              // It's some other kind of data IRI; nothing else to do.
              SmartIriInfo(
                iriType = KnoraDataIri,
                ontologySchema = None
              )
          }
        } else if (iri.startsWith(OntologyConstants.NamedGraphs.DataNamedGraphStart) ||
                   iri == OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph) {
          // Nothing else to do.
          SmartIriInfo(
            iriType = KnoraDataIri,
            ontologySchema = None
          )
        } else {
          // If this is an entity IRI in a hash namespace, separate the entity name from the namespace.

          val hashPos = iri.lastIndexOf('#')

          val (namespace: String, entityName: Option[String]) = if (hashPos >= 0 && hashPos < iri.length) {
            val namespace = iri.substring(0, hashPos)
            val entityName = iri.substring(hashPos + 1)

            // Validate the entity name as an NCName.
            (namespace, Some(validateNCName(entityName, errorFun)))
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
            case InternalIriHostname     => (Some(InternalSchema), false)
            case CentralKnoraApiHostname => (Some(parseApiV2VersionSegments(segments)), false)

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
              case Some(ApiV2Complex)   => 1
              case Some(ApiV2Simple)    => 2
              case None                 => throw AssertionException("Unreachable code")
            }

            // Make a Vector containing just the optional 'shared' specification, the optional project code, and the ontology name.
            val ontologyPath: Vector[String] = segments.slice(2, segments.length - versionSegmentsLength)

            if (ontologyPath.isEmpty || ontologyPath.length > 3) {
              errorFun
            }

            if (ontologyPath.exists(segment => versionSegmentWords.contains(segment))) {
              errorFun
            }

            // Determine whether the ontology is shared, and get its project code, if any.
            val (sharedOntology: Boolean, projectCode: Option[String]) = if (ontologyPath.head == "shared") {
              if (ontologyPath.length == 2) {
                (true, Some(DefaultSharedOntologiesProjectCode)) // default shared ontologies project
              } else if (ontologyPath.length == 3) {
                (true, Some(validateProjectShortcode(ontologyPath(1), errorFun))) // other shared ontologies project
              } else {
                errorFun
              }
            } else if (ontologyPath.length == 2) {
              (false, Some(validateProjectShortcode(ontologyPath.head, errorFun))) // non-shared ontology with project code
            } else {
              (false, None) // built-in ontology
            }

            // Extract the ontology name.
            val ontologyName = ontologyPath.last
            val hasBuiltInOntologyName = isBuiltInOntologyName(ontologyName)

            if (!hasBuiltInOntologyName) {
              validateProjectSpecificOntologyName(ontologyName, errorFun)
            }

            // If the IRI has the hostname for project-specific ontologies, it can't refer to a built-in or shared ontology.
            if (hasProjectSpecificHostname && (hasBuiltInOntologyName || sharedOntology)) {
              errorFun
            }

            // If the IRI has the hostname for built-in and shared ontologies, it must refer to a built-in or shared ontology.
            if (hostname == CentralKnoraApiHostname && !(hasBuiltInOntologyName || sharedOntology)) {
              errorFun
            }

            // A project code is required in project-specific definition IRIs.
            if (hasProjectSpecificHostname && projectCode.isEmpty) {
              errorFun
            }

            SmartIriInfo(
              iriType = KnoraDefinitionIri,
              projectCode = projectCode,
              ontologyName = Some(ontologyName),
              entityName = entityName,
              ontologySchema = ontologySchema,
              isBuiltInDef = hasBuiltInOntologyName,
              sharedOntology = sharedOntology
            )
          } else {
            UnknownIriInfo
          }
        }
    }

    override def toString: String = iri

    override def toSparql: String = "<" + iri + ">"

    override def isKnoraIri: Boolean = iriInfo.iriType != UnknownIriType

    override def isKnoraDataIri: Boolean = iriInfo.iriType == KnoraDataIri

    override def isKnoraDefinitionIri: Boolean = iriInfo.iriType == KnoraDefinitionIri

    override def isKnoraSharedDefinitionIri: Boolean = isKnoraDefinitionIri && iriInfo.sharedOntology

    override def isKnoraInternalDefinitionIri: Boolean =
      iriInfo.iriType == KnoraDefinitionIri && iriInfo.ontologySchema.contains(InternalSchema)

    override def isKnoraInternalEntityIri: Boolean = isKnoraInternalDefinitionIri && isKnoraEntityIri

    override def isKnoraApiV2DefinitionIri: Boolean =
      iriInfo.iriType == KnoraDefinitionIri && (iriInfo.ontologySchema match {
        case Some(_: ApiV2Schema) => true
        case _                    => false
      })

    override def isKnoraApiV2EntityIri: Boolean = isKnoraApiV2DefinitionIri && isKnoraEntityIri

    override def isKnoraBuiltInDefinitionIri: Boolean = iriInfo.isBuiltInDef

    override def isKnoraOntologyIri: Boolean =
      iriInfo.iriType == KnoraDefinitionIri && iriInfo.ontologyName.nonEmpty && iriInfo.entityName.isEmpty

    override def isKnoraEntityIri: Boolean = iriInfo.iriType == KnoraDefinitionIri && iriInfo.entityName.nonEmpty

    override def getProjectCode: Option[String] = iriInfo.projectCode

    override def getResourceID: Option[String] = iriInfo.resourceID

    override def getValueID: Option[String] = iriInfo.valueID

    override def getStandoffStartIndex: Option[Int] = iriInfo.standoffStartIndex

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
        val entityIriStr = iri + "#" + entityName
        getOrCacheSmartIri(entityIriStr, () => new SmartIriImpl(entityIriStr))
      } else {
        throw DataConversionException(s"$iri is not a Knora ontology IRI")
      }
    }

    override def getOntologyName: String = {
      iriInfo.ontologyName match {
        case Some(name) => name
        case None       => throw DataConversionException(s"Expected a Knora ontology IRI: $iri")
      }
    }

    override def getEntityName: String = {
      iriInfo.entityName match {
        case Some(name) => name
        case None       => throw DataConversionException(s"Expected a Knora entity IRI: $iri")
      }
    }

    override def getOntologySchema: Option[OntologySchema] = iriInfo.ontologySchema

    override def checkApiV2Schema(allowedSchema: ApiV2Schema, errorFun: => Nothing): SmartIri = {
      iriInfo.ontologySchema match {
        case Some(schema) =>
          if (schema == allowedSchema) {
            this
          } else {
            errorFun
          }

        case None => this
      }
    }

    override def getShortPrefixLabel: String = getOntologyName

    override def getLongPrefixLabel: String = {
      val prefix = new StringBuilder

      iriInfo.projectCode match {
        case Some(id) => prefix.append('p').append(id).append('-')
        case None     => ()
      }

      val ontologyName = getOntologyName

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
              case _                           => throw DataConversionException(s"Cannot convert $iri to $targetSchema")
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
                  getOrCacheSmartIri(iriStr = convertedIri, creationFun = { () =>
                    new SmartIriImpl(convertedIri)
                  })

                case None =>
                  // No. Convert the IRI using a formal procedure.
                  if (iriInfo.ontologySchema.contains(InternalSchema)) {
                    targetSchema match {
                      case externalSchema: ApiV2Schema => internalToExternalEntityIri(externalSchema)
                      case _                           => throw DataConversionException(s"Cannot convert $iri to $targetSchema")
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
        case ApiV2Simple  => OntologyConstants.KnoraApiV2Simple.VersionSegment
        case ApiV2Complex => OntologyConstants.KnoraApiV2Complex.VersionSegment
      }
    }

    private def externalToInternalEntityIri: SmartIri = {
      // Construct the string representation of this IRI in the target schema.
      val internalOntologyName = externalToInternalOntologyName(getOntologyName)
      val entityName = getEntityName

      val internalOntologyIri = makeInternalOntologyIriStr(
        internalOntologyName = internalOntologyName,
        isShared = iriInfo.sharedOntology,
        projectCode = iriInfo.projectCode
      )

      val convertedIriStr = new StringBuilder(internalOntologyIri).append("#").append(entityName).toString

      // Get it from the cache, or construct it and cache it if it's not there.
      getOrCacheSmartIri(
        iriStr = convertedIriStr,
        creationFun = { () =>
          val convertedSmartIriInfo = iriInfo.copy(
            ontologyName = Some(internalOntologyName),
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
        creationFun = { () =>
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
      } else if (isKnoraSharedDefinitionIri) {
        val externalOntologyIri = new StringBuilder(OntologyConstants.KnoraApi.ApiOntologyStart).append("shared/")

        iriInfo.projectCode match {
          case Some(projectCode) =>
            if (projectCode != DefaultSharedOntologiesProjectCode) {
              externalOntologyIri.append(projectCode).append('/')
            }

          case None => ()
        }

        externalOntologyIri.append(ontologyName).append(versionSegment).toString
      } else {
        val projectSpecificApiV2OntologyStart = MaybeProjectSpecificApiV2OntologyStart match {
          case Some(ontologyStart) => ontologyStart
          case None                => throw AssertionException("Format of project-specific IRIs was not initialised")
        }

        val externalOntologyIri = new StringBuilder(projectSpecificApiV2OntologyStart)

        iriInfo.projectCode match {
          case Some(projectCode) => externalOntologyIri.append(projectCode).append('/')
          case None              => ()
        }

        externalOntologyIri.append(ontologyName).append(versionSegment).toString
      }

      getOrCacheSmartIri(
        iriStr = convertedIriStr,
        creationFun = { () =>
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
      val convertedIriStr = makeInternalOntologyIriStr(
        internalOntologyName = externalToInternalOntologyName(getOntologyName),
        isShared = iriInfo.sharedOntology,
        projectCode = iriInfo.projectCode
      )

      getOrCacheSmartIri(
        iriStr = convertedIriStr,
        creationFun = { () =>
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
          creationFun = { () =>
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
        throw InconsistentRepositoryDataException(s"Link value predicate IRI $iri does not end with 'Value'")
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
        creationFun = { () =>
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

    override def isKnoraResourceIri: Boolean = {
      if (!isKnoraDataIri) {
        false
      } else {
        (iriInfo.resourceID, iriInfo.valueID) match {
          case (Some(_), None) => true
          case _               => false
        }
      }
    }

    override def isKnoraValueIri: Boolean = {
      if (!isKnoraDataIri) {
        false
      } else {
        (iriInfo.resourceID, iriInfo.valueID) match {
          case (Some(_), Some(_)) => true
          case _                  => false
        }
      }
    }

    override def isKnoraStandoffIri: Boolean = {
      if (!isKnoraDataIri) {
        false
      } else {
        (iriInfo.resourceID, iriInfo.valueID, iriInfo.standoffStartIndex) match {
          case (Some(_), Some(_), Some(_)) => true
          case _                           => false
        }
      }
    }

    override def fromResourceIriToArkUrl(maybeTimestamp: Option[Instant] = None): String = {
      if (!isKnoraResourceIri) {
        throw DataConversionException(s"IRI $iri is not a Knora resource IRI")
      }

      val arkUrlTry = Try {
        makeArkUrl(
          resourceID = iriInfo.resourceID.get,
          maybeValueUUID = None,
          maybeTimestamp = maybeTimestamp
        )

      }

      arkUrlTry match {
        case Success(arkUrl) => arkUrl
        case Failure(ex)     => throw DataConversionException(s"Can't generate ARK URL for IRI <$iri>: ${ex.getMessage}")
      }
    }

    override def fromValueIriToArkUrl(valueUUID: UUID, maybeTimestamp: Option[Instant] = None): String = {
      if (!isKnoraValueIri) {
        throw DataConversionException(s"IRI $iri is not a Knora value IRI")
      }

      val arkUrlTry = Try {
        makeArkUrl(
          resourceID = iriInfo.resourceID.get,
          maybeValueUUID = Some(valueUUID),
          maybeTimestamp = maybeTimestamp
        )

      }

      arkUrlTry match {
        case Success(arkUrl) => arkUrl
        case Failure(ex)     => throw DataConversionException(s"Can't generate ARK URL for IRI <$iri>: ${ex.getMessage}")
      }
    }
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
      case None    => None
    }
  }

  /**
    * Returns `true` if an IRI string looks like a Knora project IRI
    *
    * @param iri the IRI to be checked.
    */
  def isKnoraProjectIriStr(iri: IRI): Boolean = {
    isIri(iri) && (iri.startsWith("http://" + IriDomain + "/projects/") || isKnoraBuiltInProjectIriStr(iri))
  }

  /**
    * Returns `true` if an IRI string looks like a Knora built-in IRI:
    *  - http://www.knora.org/ontology/knora-admin#SystemProject
    *  - http://www.knora.org/ontology/knora-admin#SharedOntologiesProject
    *
    * @param iri the IRI to be checked.
    */
  def isKnoraBuiltInProjectIriStr(iri: IRI): Boolean = {

    val builtInProjects = Seq(
      OntologyConstants.KnoraAdmin.SystemProject,
      OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject
    )

    isIri(iri) && builtInProjects.contains(iri)
  }

  /**
    * Returns `true` if an IRI string looks like a Knora list IRI.
    *
    * @param iri the IRI to be checked.
    */
  def isKnoraListIriStr(iri: IRI): Boolean = {
    isIri(iri) && iri.startsWith("http://" + IriDomain + "/lists/")
  }

  /**
    * Returns `true` if an IRI string looks like a Knora user IRI.
    *
    * @param iri the IRI to be checked.
    */
  def isKnoraUserIriStr(iri: IRI): Boolean = {
    isIri(iri) && iri.startsWith("http://" + IriDomain + "/users/")
  }

  /**
    * Returns `true` if an IRI string looks like a Knora group IRI.
    *
    * @param iri the IRI to be checked.
    */
  def isKnoraGroupIriStr(iri: IRI): Boolean = {
    isIri(iri) && iri.startsWith("http://" + IriDomain + "/groups/")
  }

  /**
    * Returns `true` if an IRI string looks like a Knora permission IRI.
    *
    * @param iri the IRI to be checked.
    */
  def isKnoraPermissionIriStr(iri: IRI): Boolean = {
    isIri(iri) && iri.startsWith("http://" + IriDomain + "/permissions/")
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
        case _                                                  => validateAndEscapeIri(s, errorFun)
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
      case _                                                  => false
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
      case StandoffLinkReferenceToClientIDForResourceRegex(clientResourceID) =>
        clientResourceIDsToResourceIris(clientResourceID)
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
    * Encodes a string for use in JSON, and encloses it in quotation marks.
    *
    * @param s the string to be encoded.
    * @return the encoded string.
    */
  def toJsonEncodedString(s: String): String = {
    JsString(s).compactPrint
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
      case SalsahGuiAttributeDefinitionRegex(attributeName, required, allowedTypeStr, _, enumeratedValuesStr) =>
        val allowedType: SalsahGui.SalsahGuiAttributeType.Value =
          OntologyConstants.SalsahGui.SalsahGuiAttributeType.lookup(allowedTypeStr)

        val enumeratedValues: Set[String] = Option(enumeratedValuesStr) match {
          case Some(enumeratedValuesStr) =>
            if (allowedType != OntologyConstants.SalsahGui.SalsahGuiAttributeType.Str) {
              errorFun
            }

            enumeratedValuesStr.split('|').toSet

          case None => Set.empty[String]
        }

        SalsahGuiAttributeDefinition(
          attributeName = attributeName,
          isRequired = Option(required).nonEmpty,
          allowedType = allowedType,
          enumeratedValues = enumeratedValues,
          unparsedString = s
        )

      case _ =>
        errorFun
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
  def toSalsahGuiAttribute(s: String,
                           attributeDefs: Set[SalsahGuiAttributeDefinition],
                           errorFun: => Nothing): SalsahGuiAttribute = {
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

              catching(classOf[ParseException])
                .opt(validateAndEscapeIri(iriWithoutAngleBrackets, throw new ParseException("Couldn't parse IRI", 1)))
                .map(SalsahGuiIriAttributeValue)
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
          case Some(parsedAttrValue) =>
            SalsahGuiAttribute(attributeName = attributeName, attributeValue = parsedAttrValue)
          case None => errorFun
        }

      case _ =>
        // The expression couldn't be parsed.
        errorFun
    }
  }

  /**
    * Parses an `xsd:dateTimeStamp`.
    *
    * @param s        the string to be parsed.
    * @param errorFun a function that throws an exception. It will be called if the string cannot be parsed.
    * @return an [[Instant]].
    */
  def xsdDateTimeStampToInstant(s: String, errorFun: => Nothing): Instant = {
    try {
      val accessor: TemporalAccessor = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(s)
      Instant.from(accessor)
    } catch {
      case _: Exception => errorFun
    }
  }

  /**
    * Parses a Knora ARK timestamp.
    *
    * @param timestampStr the string to be parsed.
    * @param errorFun     a function that throws an exception. It will be called if the string cannot be parsed.
    * @return an [[Instant]].
    */
  def arkTimestampToInstant(timestampStr: String, errorFun: => Nothing): Instant = {
    timestampStr match {
      case ArkTimestampRegex(year, month, day, hour, minute, second, fraction) =>
        val nanoOfSecond: Int = Option(fraction) match {
          case None => 0

          case Some(definedFraction) =>
            // Pad the nano-of-second with trailing zeroes so it has 9 digits, then convert it
            // to an integer.
            definedFraction.padTo(9, '0').toInt
        }

        try {
          val accessor: TemporalAccessor = OffsetDateTime.of(
            year.toInt,
            month.toInt,
            day.toInt,
            hour.toInt,
            minute.toInt,
            second.toInt,
            nanoOfSecond,
            ZoneOffset.UTC
          )

          Instant.from(accessor)
        } catch {
          case _: Exception => errorFun
        }

      case _ => errorFun
    }
  }

  /**
    * Formats a Knora ARK timestamp.
    *
    * @param timestamp the timestamp to be formatted.
    * @return a string representation of the timestamp.
    */
  def formatArkTimestamp(timestamp: Instant): String = {
    val offsetDateTime: OffsetDateTime = timestamp.atOffset(ZoneOffset.UTC)

    val year: Int = offsetDateTime.get(ChronoField.YEAR)
    val month: Int = offsetDateTime.get(ChronoField.MONTH_OF_YEAR)
    val day: Int = offsetDateTime.get(ChronoField.DAY_OF_MONTH)
    val hour: Int = offsetDateTime.get(ChronoField.HOUR_OF_DAY)
    val minute: Int = offsetDateTime.get(ChronoField.MINUTE_OF_HOUR)
    val second: Int = offsetDateTime.get(ChronoField.SECOND_OF_MINUTE)
    val nanoOfSecond: Int = offsetDateTime.get(ChronoField.NANO_OF_SECOND)

    val fractionStr: IRI = if (nanoOfSecond > 0) {
      // Convert the nano-of-second to a 9-digit string representation, then strip trailing zeroes.
      val nineDigitNanoOfSecond = f"$nanoOfSecond%09d"
      TrailingZerosRegex.replaceAllIn(nineDigitNanoOfSecond, "")
    } else {
      ""
    }

    f"$year%04d$month%02d$day%02dT$hour%02d$minute%02d$second%02d${fractionStr}Z"
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
      case None          => errorFun // not a valid color hex value string
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
      case None        => errorFun // calling this function throws an error
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
    * @param standoffTags The list of [[StandoffTagV2]].
    * @return a set of Iris referred to in the [[StandoffTagV2]].
    */
  def getResourceIrisFromStandoffTags(standoffTags: Seq[StandoffTagV2]): Set[IRI] = {
    standoffTags.foldLeft(Set.empty[IRI]) {
      case (acc: Set[IRI], standoffNode: StandoffTagV2) =>
        if (standoffNode.dataType.contains(StandoffDataTypeClasses.StandoffLinkTag)) {
          val maybeTargetIri: Option[IRI] = standoffNode.attributes.collectFirst {
            case iriTagAttr: StandoffTagIriAttributeV2
                if iriTagAttr.standoffPropertyIri.toString == OntologyConstants.KnoraBase.StandoffTagHasLink =>
              iriTagAttr.value
          }

          acc + maybeTargetIri.getOrElse(throw NotFoundException(s"No link found in $standoffNode"))
        } else {
          acc
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
    * Converts a string to a boolean.
    *
    * @param s        the string to be converted.
    * @param errorFun a function that throws an exception. It will be called if the string cannot be parsed
    *                 as a boolean value.
    * @return a Boolean.
    */
  def toBoolean(s: String, errorFun: => Nothing): Boolean = {
    try {
      s.toBoolean
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
      case None        => errorFun
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
    ontologyName match {
      case NCNameRegex(_*) => ()
      case _               => errorFun
    }

    val lowerCaseOntologyName = ontologyName.toLowerCase

    lowerCaseOntologyName match {
      case ApiVersionNumberRegex(_*) => errorFun
      case _                         => ()
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
    * Given a valid internal (built-in or project-specific) ontology name and an optional project code, constructs the
    * corresponding internal ontology IRI.
    *
    * @param internalOntologyName the ontology name.
    * @param projectCode          the project code.
    * @return the ontology IRI.
    */
  private def makeInternalOntologyIriStr(internalOntologyName: String,
                                         isShared: Boolean,
                                         projectCode: Option[String]): IRI = {
    val internalOntologyIri = new StringBuilder(OntologyConstants.KnoraInternal.InternalOntologyStart)

    if (isShared) {
      internalOntologyIri.append("/shared")
    }

    projectCode match {
      case Some(code) =>
        if (code != DefaultSharedOntologiesProjectCode) {
          internalOntologyIri.append('/').append(code)
        }

      case None => ()
    }

    internalOntologyIri.append('/').append(internalOntologyName).toString
  }

  /**
    * Given a valid internal ontology name and an optional project code, constructs the corresponding internal
    * ontology IRI.
    *
    * @param internalOntologyName the ontology name.
    * @param projectCode          the project code.
    * @return the ontology IRI.
    */
  def makeProjectSpecificInternalOntologyIri(internalOntologyName: String,
                                             isShared: Boolean,
                                             projectCode: String): SmartIri = {
    toSmartIri(makeInternalOntologyIriStr(internalOntologyName, isShared, Some(projectCode)))
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
    val namespace = new StringBuilder(
      OntologyConstants.KnoraXmlImportV1.ProjectSpecificXmlImportNamespace.XmlImportNamespaceStart)

    if (internalOntologyIri.isKnoraSharedDefinitionIri) {
      namespace.append("shared/")
    }

    internalOntologyIri.getProjectCode match {
      case Some(projectCode) =>
        if (projectCode != DefaultSharedOntologiesProjectCode) {
          namespace.append(projectCode).append('/')
        }

      case None => ()
    }

    namespace
      .append(internalOntologyIri.getOntologyName)
      .append(OntologyConstants.KnoraXmlImportV1.ProjectSpecificXmlImportNamespace.XmlImportNamespaceEnd)
    XmlImportNamespaceInfoV1(namespace = namespace.toString, prefixLabel = internalOntologyIri.getLongPrefixLabel)
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
      case ProjectSpecificXmlImportNamespaceRegex(shared, _, projectCode, ontologyName)
          if !isBuiltInOntologyName(ontologyName) =>
        val isShared = Option(shared).nonEmpty

        val definedProjectCode = Option(projectCode) match {
          case Some(code) => code
          case None       => if (isShared) DefaultSharedOntologiesProjectCode else errorFun
        }

        makeProjectSpecificInternalOntologyIri(
          internalOntologyName = externalToInternalOntologyName(ontologyName),
          isShared = isShared,
          projectCode = definedProjectCode
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
  def xmlImportElementNameToInternalOntologyIriV1(namespace: String,
                                                  elementLabel: String,
                                                  errorFun: => Nothing): IRI = {
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
      case PropertyFromOtherOntologyInXmlImportRegex(_, projectID, prefixLabel, localName) =>
        Option(projectID) match {
          case Some(definedProjectID) =>
            // Is this ia shared ontology?
            // TODO: when multiple shared project ontologies are supported, this will need to be done differently.
            if (definedProjectID == DefaultSharedOntologiesProjectCode) {
              Some(s"${OntologyConstants.KnoraInternal.InternalOntologyStart}/shared/$prefixLabel#$localName")
            } else {
              Some(
                s"${OntologyConstants.KnoraInternal.InternalOntologyStart}/$definedProjectID/$prefixLabel#$localName")
            }

          case None =>
            if (prefixLabel == OntologyConstants.KnoraXmlImportV1.KnoraXmlImportNamespacePrefixLabel) {
              Some(s"${OntologyConstants.KnoraBase.KnoraBasePrefixExpansion}$localName")
            } else {
              throw BadRequestException(s"Invalid prefix label and local name: $prefixLabelAndLocalName")
            }
        }

      case _ => None
    }
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
      case _                                                                                       => false
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
      case _                                                                                        => false
    }
  }

  /**
    * Given the projectInfo calculates the project's data named graph.
    *
    * @param projectInfo the project's [[ProjectInfoV1]].
    * @return the IRI of the project's data named graph.
    */
  def projectDataNamedGraphV1(projectInfo: ProjectInfoV1): IRI = {
    OntologyConstants.NamedGraphs.DataNamedGraphStart + "/" + projectInfo.shortcode + "/" + projectInfo.shortname
  }

  /**
    * Given the [[ProjectADM]] calculates the project's data named graph.
    *
    * @param project the project's [[ProjectADM]].
    * @return the IRI of the project's data named graph.
    */
  def projectDataNamedGraphV2(project: ProjectADM): IRI = {
    OntologyConstants.NamedGraphs.DataNamedGraphStart + "/" + project.shortcode + "/" + project.shortname
  }

  /**
    * Given the [[ProjectADM]] calculates the project's metadata named graph.
    *
    * @param project the project's [[ProjectADM]].
    * @return the IRI of the project's metadata named graph.
    */
  def projectMetadataNamedGraphV2(project: ProjectADM): IRI = {
    OntologyConstants.NamedGraphs.DataNamedGraphStart + "/" + project.shortcode + "/" + project.shortname + "/metadata"
  }

  /**
    * Check that the supplied IRI represents a valid project IRI.
    *
    * @param iri      the string to be checked.
    * @param errorFun a function that throws an exception. It will be called if the string does not represent a valid
    *                 project IRI.
    * @return the same string but escaped.
    */
  def validateAndEscapeProjectIri(iri: IRI, errorFun: => Nothing): IRI = {
    if (isKnoraProjectIriStr(iri)) {
      toSparqlEncodedString(iri, errorFun)
    } else {
      errorFun
    }
  }

  /**
    * Check that an optional string represents a valid project IRI.
    *
    * @param maybeString the optional string to be checked.
    * @param errorFun    a function that throws an exception. It will be called if the string does not represent a valid
    *                    project IRI.
    * @return the same optional string but escaped.
    */
  def validateAndEscapeOptionalProjectIri(maybeString: Option[String], errorFun: => Nothing): Option[IRI] = {
    maybeString match {
      case Some(s) => Some(validateAndEscapeProjectIri(s, errorFun))
      case None    => None
    }
  }

  /**
    * Check that the string represents a valid project shortname.
    *
    * @param value    the string to be checked.
    * @param errorFun a function that throws an exception. It will be called if the string does not represent a valid
    *                 project shortname.
    * @return the same string.
    */
  def validateAndEscapeProjectShortname(value: String, errorFun: => Nothing): String = {
    NCNameRegex.findFirstIn(value) match {
      case Some(shortname) => toSparqlEncodedString(shortname, errorFun)
      case None            => errorFun
    }
  }

  /**
    * Check that an optional string represents a valid project shortname.
    *
    * @param maybeString the optional string to be checked.
    * @param errorFun    a function that throws an exception. It will be called if the string does not represent a valid
    *                    project shortname.
    * @return the same optional string.
    */
  def validateAndEscapeOptionalProjectShortname(maybeString: Option[String], errorFun: => Nothing): Option[String] = {
    maybeString match {
      case Some(s) => Some(validateAndEscapeProjectShortname(s, errorFun))
      case None    => None
    }
  }

  /**
    * Given the project shortcode, checks if it is in a valid format, and converts it to upper case.
    *
    * @param shortcode the project's shortcode.
    * @return the shortcode in upper case.
    */
  def validateProjectShortcode(shortcode: String, errorFun: => Nothing): String = {
    ProjectIDRegex.findFirstIn(shortcode.toUpperCase) match {
      case Some(value) => value
      case None        => errorFun
    }
  }

  /**
    * Given the project shortcode, checks if it is in a valid format, and converts it to upper case.
    *
    * @param shortcode the project's shortcode.
    * @return the shortcode in upper case.
    */
  def validateProjectShortcodeOption(shortcode: String): Option[String] = {
    ProjectIDRegex.findFirstIn(shortcode.toUpperCase) match {
      case Some(value) => Some(value)
      case None        => None
    }
  }

  /**
    * Check that a string represents a valid project shortcode.
    *
    * @param shortcode the optional string to be checked.
    * @param errorFun  a function that throws an exception. It will be called if the string does not represent a valid
    *                  project shortcode.
    * @return the same string.
    */
  def validateAndEscapeProjectShortcode(shortcode: String, errorFun: => Nothing): String = {
    ProjectIDRegex.findFirstIn(shortcode.toUpperCase) match {
      case Some(definedShortcode) => toSparqlEncodedString(definedShortcode, errorFun)
      case None                   => errorFun
    }
  }

  /**
    * Check that an optional string represents a valid project shortcode.
    *
    * @param maybeString the optional string to be checked.
    * @param errorFun    a function that throws an exception. It will be called if the string does not represent a valid
    *                    project shortcode.
    * @return the same optional string.
    */
  def validateAndEscapeOptionalProjectShortcode(maybeString: Option[String], errorFun: => Nothing): Option[String] = {
    maybeString match {
      case Some(s) => Some(validateAndEscapeProjectShortcode(s, errorFun))
      case None    => None
    }
  }

  def escapeOptionalString(maybeString: Option[String], errorFun: => Nothing): Option[String] = {
    maybeString match {
      case Some(s) =>
        Some(toSparqlEncodedString(s, errorFun))
      case None => None
    }
  }

  /**
    * Given the list IRI, checks if it is in a valid format.
    *
    * @param iri the list's IRI.
    * @return the IRI of the list.
    */
  def validateListIri(iri: IRI, errorFun: => Nothing): IRI = {
    if (isKnoraListIriStr(iri)) {
      iri
    } else {
      errorFun
    }
  }

  /**
    * Given the optional list IRI, checks if it is in a valid format.
    *
    * @param maybeIri the optional list's IRI to be checked.
    * @return the same optional IRI.
    */
  def validateOptionalListIri(maybeIri: Option[IRI], errorFun: => Nothing): Option[IRI] = {
    maybeIri match {
      case Some(iri) => Some(validateListIri(iri, errorFun))
      case None      => None
    }
  }

  /**
    * Given the group IRI, checks if it is in a valid format.
    *
    * @param iri the group's IRI.
    * @return the IRI of the list.
    */
  def validateGroupIri(iri: IRI, errorFun: => Nothing): IRI = {
    if (isKnoraGroupIriStr(iri)) {
      iri
    } else {
      errorFun
    }
  }

  /**
    * Given the optional group IRI, checks if it is in a valid format.
    *
    * @param maybeIri the optional group's IRI to be checked.
    * @return the same optional IRI.
    */
  def validateOptionalGroupIri(maybeIri: Option[IRI], errorFun: => Nothing): Option[IRI] = {
    maybeIri match {
      case Some(iri) => Some(validateGroupIri(iri, errorFun))
      case None      => None
    }
  }

  /**
    * Given the permission IRI, checks if it is in a valid format.
    *
    * @param iri the permission's IRI.
    * @return the IRI of the list.
    */
  def validatePermissionIri(iri: IRI, errorFun: => Nothing): IRI = {
    if (isKnoraPermissionIriStr(iri)) {
      iri
    } else {
      errorFun
    }
  }

  /**
    * Given the optional permission IRI, checks if it is in a valid format.
    *
    * @param maybeIri the optional permission's IRI to be checked.
    * @return the same optional IRI.
    */
  def validateOptionalPermissionIri(maybeIri: Option[IRI], errorFun: => Nothing): Option[IRI] = {
    maybeIri match {
      case Some(iri) => Some(validatePermissionIri(iri, errorFun))
      case None      => None
    }
  }

  /**
    * Check that the supplied IRI represents a valid user IRI.
    *
    * @param iri      the string to be checked.
    * @param errorFun a function that throws an exception. It will be called if the string does not represent a valid
    *                 user IRI.
    * @return the same string.
    */
  def validateUserIri(iri: IRI, errorFun: => Nothing): IRI = {
    if (isKnoraUserIriStr(iri)) {
      iri
    } else {
      errorFun
    }
  }

  /**
    * Given the optional user IRI, checks if it is in a valid format.
    *
    * @param maybeIri the optional user's IRI to be checked.
    * @return the same optional IRI.
    */
  def validateOptionalUserIri(maybeIri: Option[IRI], errorFun: => Nothing): Option[IRI] = {
    maybeIri match {
      case Some(iri) => Some(validateUserIri(iri, errorFun))
      case None      => None
    }
  }

  /**
    * Check that the supplied IRI represents a valid user IRI.
    *
    * @param iri      the string to be checked.
    * @param errorFun a function that throws an exception. It will be called if the string does not represent a valid
    *                 user IRI.
    * @return the same string but escaped.
    */
  def validateAndEscapeUserIri(iri: IRI, errorFun: => Nothing): String = {
    if (isKnoraUserIriStr(iri)) {
      toSparqlEncodedString(iri, errorFun)
    } else {
      errorFun
    }
  }

  /**
    * Check that an optional string represents a valid user IRI.
    *
    * @param maybeString the optional string to be checked.
    * @param errorFun    a function that throws an exception. It will be called if the string does not represent a valid
    *                    user IRI.
    * @return the same optional string.
    */
  def validateAndEscapeOptionalUserIri(maybeString: Option[String], errorFun: => Nothing): Option[String] = {
    maybeString match {
      case Some(s) => Some(validateAndEscapeUserIri(s, errorFun))
      case None    => None
    }
  }

  /**
    * Given an email address, checks if it is in a valid format.
    *
    * @param email the email.
    * @return the email
    */
  def validateEmail(email: String): Option[String] = {
    EmailAddressRegex.findFirstIn(email)
  }

  /**
    * Given an email address, checks if it is in a valid format.
    *
    * @param email the email.
    * @return the email
    */
  def validateEmailAndThrow(email: String, errorFun: => Nothing): String = {
    EmailAddressRegex.findFirstIn(email) match {
      case Some(value) => value
      case None        => errorFun
    }
  }

  /**
    * Check that an optional string represents a valid email address.
    *
    * @param maybeString the optional string to be checked.
    * @param errorFun    a function that throws an exception. It will be called if the string does not represent a valid
    *                    email address.
    * @return the same optional string.
    */
  def validateAndEscapeOptionalEmail(maybeString: Option[String], errorFun: => Nothing): Option[String] = {
    maybeString match {
      case Some(s) => Some(toSparqlEncodedString(validateEmailAndThrow(s, errorFun), errorFun))
      case None    => None
    }
  }

  /**
    * Check that the string represents a valid username.
    *
    * @param value    the string to be checked.
    * @param errorFun a function that throws an exception. It will be called if the string does not represent a valid
    *                 username.
    * @return the same string.
    */
  def validateUsername(value: String, errorFun: => Nothing): String = {
    UsernameRegex.findFirstIn(value) match {
      case Some(username) => username
      case None           => errorFun
    }
  }

  /**
    * Check that the string represents a valid username and escape any special characters.
    *
    * @param value    the string to be checked.
    * @param errorFun a function that throws an exception. It will be called if the string does not represent a valid
    *                 username.
    * @return the same string with escaped special characters.
    */
  def validateAndEscapeUsername(value: String, errorFun: => Nothing): String = {
    UsernameRegex.findFirstIn(value) match {
      case Some(username) => toSparqlEncodedString(username, errorFun)
      case None           => errorFun
    }
  }

  /**
    * Check that an optional string represents a valid username.
    *
    * @param maybeString the optional string to be checked.
    * @param errorFun    a function that throws an exception. It will be called if the string does not represent a valid
    *                    username.
    * @return the same optional string.
    */
  def validateAndEscapeOptionalUsername(maybeString: Option[String], errorFun: => Nothing): Option[String] = {
    maybeString match {
      case Some(s) => Some(validateAndEscapeUsername(s, errorFun))
      case None    => None
    }
  }

  /**
    * Generates an ARK URL for a resource or value, as per [[https://tools.ietf.org/html/draft-kunze-ark-18]].
    *
    * @param projectID      the shortcode of the project that the resource belongs to.
    * @param resourceID     the resource's ID (the last component of its IRI).
    * @param maybeValueUUID if this is an ARK URL for a value, the value's UUID.
    * @param maybeTimestamp a timestamp indicating the point in the resource's version history that the ARK URL should
    *                       cite.
    * @return an ARK URL that can be resolved to obtain the resource or value.
    */
  private def makeArkUrl(resourceID: String,
                         maybeValueUUID: Option[UUID] = None,
                         maybeTimestamp: Option[Instant] = None): String = {

    /**
      * Adds a check digit to a Base64-encoded ID, and escapes '-' as '=', because '-' can be ignored in ARK URLs.
      *
      * @param id a Base64-encoded ID.
      * @return the ID with a check digit added.
      */
    def addCheckDigitAndEscape(id: String): String = {
      val checkDigitTry: Try[String] = Try {
        base64UrlCheckDigit.calculate(id)
      }

      val checkDigit: String = checkDigitTry match {
        case Success(digit) => digit
        case Failure(ex)    => throw DataConversionException(ex.getMessage)
      }

      val idWithCheckDigit = id + checkDigit
      idWithCheckDigit.replace('-', '=')
    }

    val (resolver: String, assignedNumber: Int) = (arkResolver, arkAssignedNumber) match {
      case (Some(definedHost: String), Some(definedAssignedNumber: Int)) => (definedHost, definedAssignedNumber)
      case _                                                             => throw AssertionException(s"StringFormatter has not been initialised with system settings")
    }

    // Calculate a check digit for the resource ID.
    val resourceIDWithCheckDigit: String = addCheckDigitAndEscape(resourceID)

    // Construct an ARK URL for the resource, without a value UUID and without a timestamp.
    val resourceArkUrl = s"$resolver/ark:/$assignedNumber/$ArkVersion/$resourceIDWithCheckDigit"

    // If a value UUID was provided, Base64-encode it, add a check digit, and append the result to the URL.
    val arkUrlWithoutTimestamp = maybeValueUUID match {
      case Some(valueUUID: UUID) => s"$resourceArkUrl/${addCheckDigitAndEscape(base64EncodeUuid(valueUUID))}"
      case None                  => resourceArkUrl
    }

    maybeTimestamp match {
      case Some(timestamp) =>
        // Format the timestamp and append it to the URL as an ARK object variant.
        arkUrlWithoutTimestamp + "." + formatArkTimestamp(timestamp)

      case None =>
        arkUrlWithoutTimestamp
    }
  }

  /**
    * Constructs a URL for accessing a file that has been uploaded to Sipi's temporary storage.
    *
    * @param settings the application settings.
    * @param filename the filename.
    * @return a URL for accessing the file.
    */
  def makeSipiTempFileUrl(settings: KnoraSettingsImpl, filename: String): String = {
    s"${settings.internalSipiBaseUrl}/tmp/$filename"
  }

  /**
    * Checks whether an IRI already exists in the triplestore.
    *
    * @param iri          the IRI to be checked.
    * @param storeManager a reference to the store manager.
    * @return `true` if the IRI already exists, `false` otherwise.
    */
  def checkIriExists(iri: IRI, storeManager: ActorRef)(implicit timeout: Timeout,
                                                       executionContext: ExecutionContext): Future[Boolean] = {
    for {
      askString <- Future(org.knora.webapi.messages.twirl.queries.sparql.admin.txt.checkIriExists(iri).toString)
      response <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
    } yield response.result
  }

  /**
    * Attempts to create a new IRI that isn't already used in the triplestore. Will try up to [[MAX_IRI_ATTEMPTS]]
    * times, then throw an exception if an unused IRI could not be created.
    *
    * @param iriFun       a function that generates a random IRI.
    * @param storeManager a reference to the Knora store manager actor.
    */
  def makeUnusedIri(iriFun: => IRI, storeManager: ActorRef, log: LoggingAdapter)(
      implicit timeout: Timeout,
      executionContext: ExecutionContext): Future[IRI] = {
    def makeUnusedIriRec(attempts: Int): Future[IRI] = {
      val newIri = iriFun

      for {
        iriExists <- checkIriExists(newIri, storeManager)

        result <- if (!iriExists) {
          FastFuture.successful(newIri)
        } else if (attempts > 1) {
          log.warning("KnoraIdUtil.makeUnusedIri generated an IRI that already exists in the triplestore, retrying")
          makeUnusedIriRec(attempts - 1)
        } else {
          throw UpdateNotPerformedException(s"Could not make an unused new IRI after $MAX_IRI_ATTEMPTS attempts")
        }
      } yield result
    }

    makeUnusedIriRec(attempts = MAX_IRI_ATTEMPTS)
  }

  /**
    * Generates a type 4 UUID using [[java.util.UUID]], and Base64-encodes it using a URL and filename safe
    * Base64 encoder from [[java.util.Base64]], without padding. This results in a 22-character string that
    * can be used as a unique identifier in IRIs.
    *
    * @return a random, Base64-encoded UUID.
    */
  def makeRandomBase64EncodedUuid: String = {
    val uuid = UUID.randomUUID
    base64EncodeUuid(uuid)
  }

  /**
    * Base64-encodes a [[UUID]] using a URL and filename safe Base64 encoder from [[java.util.Base64]],
    * without padding. This results in a 22-character string that can be used as a unique identifier in IRIs.
    *
    * @param uuid the [[UUID]] to be encoded.
    * @return a 22-character string representing the UUID.
    */
  def base64EncodeUuid(uuid: UUID): String = {
    val bytes = Array.ofDim[Byte](16)
    val byteBuffer = ByteBuffer.wrap(bytes)
    byteBuffer.putLong(uuid.getMostSignificantBits)
    byteBuffer.putLong(uuid.getLeastSignificantBits)
    base64Encoder.encodeToString(bytes)
  }

  /**
    * Decodes a Base64-encoded UUID.
    *
    * @param base64Uuid the Base64-encoded UUID to be decoded.
    * @return the equivalent [[UUID]].
    */
  def base64DecodeUuid(base64Uuid: String): UUID = {
    val bytes = base64Decoder.decode(base64Uuid)
    val byteBuffer = ByteBuffer.wrap(bytes)
    new UUID(byteBuffer.getLong, byteBuffer.getLong)
  }

  /**
    * Validates and decodes a Base64-encoded UUID.
    *
    * @param base64Uuid the UUID to be validated.
    * @param errorFun   a function that throws an exception. It will be called if the string cannot be parsed.
    * @return the decoded UUID.
    */
  def validateBase64EncodedUuid(base64Uuid: String, errorFun: => Nothing): UUID = {
    val decodeTry = Try {
      base64DecodeUuid(base64Uuid)
    }

    decodeTry match {
      case Success(uuid) => uuid
      case Failure(_)    => errorFun
    }
  }

  /**
    * Encodes a [[UUID]] as a string in one of two formats:
    *
    * - The canonical 36-character format.
    * - The 22-character Base64-encoded format returned by [[base64EncodeUuid]].
    *
    * @param uuid      the UUID to be encoded.
    * @param useBase64 if `true`, uses Base64 encoding.
    * @return the encoded UUID.
    */
  def encodeUuid(uuid: UUID, useBase64: Boolean): String = {
    if (useBase64) {
      base64EncodeUuid(uuid)
    } else {
      uuid.toString
    }
  }

  /**
    * Calls `decodeUuidWithErr`, throwing [[InconsistentRepositoryDataException]] if the string cannot be parsed.
    */
  def decodeUuid(uuidStr: String): UUID = {
    decodeUuidWithErr(uuidStr, throw InconsistentRepositoryDataException(s"Invalid UUID: $uuidStr"))
  }

  /**
    * Decodes a string representing a UUID in one of two formats:
    *
    * - The canonical 36-character format.
    * - The 22-character Base64-encoded format returned by [[base64EncodeUuid]].
    *
    * Shorter strings are padded with leading zeroes to 22 characters and parsed in Base64 format
    * (this is non-reversible, and is needed only for working with test data).
    *
    * @param uuidStr  the string to be decoded.
    * @param errorFun a function that throws an exception. It will be called if the string cannot be parsed.
    * @return the decoded [[UUID]].
    */
  def decodeUuidWithErr(uuidStr: String, errorFun: => Nothing): UUID = {
    if (uuidStr.length == CanonicalUuidLength) {
      UUID.fromString(uuidStr)
    } else if (uuidStr.length == Base64UuidLength) {
      base64DecodeUuid(uuidStr)
    } else if (uuidStr.length < Base64UuidLength) {
      base64DecodeUuid(uuidStr.reverse.padTo(Base64UuidLength, '0').reverse)
    } else {
      errorFun
    }
  }

  /**
    * If an IRI ends with a UUID, validates that it is a Base64-encoded UUID. If it is a valid UUID, returns it. Otherwise,
    * makes a random UUID and returns that.
    *
    * @param givenIRI the IRI of an entity.
    * @return a Base64-encoded UUID.
    */
  def getUUIDFromIriOrMakeRandom(givenIRI: IRI): UUID = {
    val ending: String = givenIRI.split('/').last
    if (ending.length == Base64UuidLength) {
      val decodeTry = Try {
        base64DecodeUuid(ending)
      }
      decodeTry match {
        case Success(_) => base64DecodeUuid(ending)
        case Failure(_) => UUID.randomUUID
      }
    } else {
      UUID.randomUUID
    }
  }

  /**
    * Checks if a string is the right length to be a canonical or Base64-encoded UUID.
    *
    * @param idStr the string to check.
    * @return `true` if the string is the right length to be a canonical or Base64-encoded UUID.
    */
  def couldBeUuid(idStr: String): Boolean = {
    idStr.length == CanonicalUuidLength || idStr.length == Base64UuidLength
  }

  /**
    * Creates a new resource IRI based on a UUID. If a resource UUID is given, uses that for making resource IRI.
    * Otherwise, makes a random UUID for the resource and uses it for making resource IRI.
    * @param resourceUUID the optional UUID for a resource.
    * @return a new resource IRI.
    */
  def makeResourceIri(resourceUUID: Option[UUID] = None): IRI = {
    val knoraResourceID = resourceUUID match {
      case Some(uuid: UUID) => base64EncodeUuid(uuid)
      case None             => makeRandomBase64EncodedUuid
    }
    s"http://$IriDomain/resources/$knoraResourceID"
  }

  /**
    * Creates a new value IRI based on a UUID.
    *
    * @param resourceIri the IRI of the resource that will contain the value.
    * @param givenUUID   the optional given UUID of the value. If not provided, create a random one.
    * @return a new value IRI.
    */
  def makeValueIri(resourceIri: IRI, givenUUID: Option[UUID] = None): IRI = {
    val valueUUID = givenUUID match {
      case Some(uuid: UUID) => base64EncodeUuid(uuid)
      case _                => makeRandomBase64EncodedUuid
    }
    s"$resourceIri/values/$valueUUID"
  }

  /**
    * Creates a mapping IRI based on a project IRI and a mapping name.
    *
    * @param projectIri the IRI of the project the mapping will belong to.
    * @return a mapping IRI.
    */
  def makeProjectMappingIri(projectIri: IRI, mappingName: String): IRI = {
    val mappingIri = s"$projectIri/mappings/$mappingName"
    // check that the mapping IRI is valid (mappingName is user input)
    validateAndEscapeIri(mappingIri, throw BadRequestException(s"the created mapping IRI $mappingIri is invalid"))
  }

  /**
    * Creates a random IRI for an element of a mapping based on a mapping IRI.
    *
    * @param mappingIri the IRI of the mapping the element belongs to.
    * @return a new mapping element IRI.
    */
  def makeRandomMappingElementIri(mappingIri: IRI): IRI = {
    val knoraMappingElementUuid = makeRandomBase64EncodedUuid
    s"$mappingIri/elements/$knoraMappingElementUuid"
  }

  /**
    * Creates an IRI used as a lock for the creation of mappings inside a given project.
    * This method will always return the same IRI for the given project IRI.
    *
    * @param projectIri the IRI of the project the mapping will belong to.
    * @return an IRI used as a lock for the creation of mappings inside a given project.
    */
  def createMappingLockIriForProject(projectIri: IRI): IRI = {
    s"$projectIri/mappings"
  }

  /**
    * Creates a new project IRI based on a UUID or project shortcode.
    *
    * @param shortcode the required project shortcode.
    * @return a new project IRI.
    */
  def makeRandomProjectIri(shortcode: String): IRI = {
    s"http://$IriDomain/projects/$shortcode"
  }

  /**
    * Creates a new group IRI based on a UUID.
    *
    * @param shortcode the required project shortcode.
    * @return a new group IRI.
    */
  def makeRandomGroupIri(shortcode: String): String = {
    val knoraGroupUuid = makeRandomBase64EncodedUuid
    s"http://$IriDomain/groups/$shortcode/$knoraGroupUuid"
  }

  /**
    * Creates a new person IRI based on a UUID.
    *
    * @return a new person IRI.
    */
  def makeRandomPersonIri: IRI = {
    val knoraPersonUuid = makeRandomBase64EncodedUuid
    s"http://$IriDomain/users/$knoraPersonUuid"
  }

  /**
    * Creates a new list IRI based on a UUID.
    *
    * @param shortcode the required project shortcode.
    * @return a new list IRI.
    */
  def makeRandomListIri(shortcode: String): String = {
    val knoraListUuid = makeRandomBase64EncodedUuid
    s"http://$IriDomain/lists/$shortcode/$knoraListUuid"
  }

  /**
    * Creates a new standoff tag IRI based on a UUID.
    *
    * @param valueIri   the IRI of the text value containing the standoff tag.
    * @param startIndex the standoff tag's start index.
    * @return a standoff tag IRI.
    */
  def makeStandoffTagIri(valueIri: IRI, startIndex: Int): IRI = {
    s"$valueIri/standoff/$startIndex"
  }

  /**
    * Converts the IRI of a property that points to a resource into the IRI of the corresponding link value property.
    *
    * @param linkPropertyIri the IRI of the property that points to a resource.
    * @return the IRI of the corresponding link value property.
    */
  def linkPropertyIriToLinkValuePropertyIri(linkPropertyIri: IRI): IRI = {
    implicit val stringFormatter: StringFormatter = this

    linkPropertyIri.toSmartIri.fromLinkPropToLinkValueProp.toString
  }

  /**
    * Converts the IRI of a property that points to a `knora-base:LinkValue` into the IRI of the corresponding link property.
    *
    * @param linkValuePropertyIri the IRI of the property that points to the `LinkValue`.
    * @return the IRI of the corresponding link property.
    */
  def linkValuePropertyIriToLinkPropertyIri(linkValuePropertyIri: IRI): IRI = {
    implicit val stringFormatter: StringFormatter = this

    linkValuePropertyIri.toSmartIri.fromLinkValuePropToLinkProp.toString
  }

  /**
    * Creates a new permission IRI based on a UUID.
    *
    * @param shortcode the required project shortcode.
    * @return the IRI of the permission object.
    */
  def makeRandomPermissionIri(shortcode: String): IRI = {
    val knoraPermissionUuid = makeRandomBase64EncodedUuid
    s"http://$IriDomain/permissions/$shortcode/$knoraPermissionUuid"
  }

  /**
    * Converts a camel-case string like `FooBar` into a string like `foo-bar`.
    *
    * @param str       the string to be converted.
    * @param separator the word separator (defaults to `-`).
    * @return the converted string.
    */
  def camelCaseToSeparatedLowerCase(str: String, separator: String = "-"): String = {
    str
      .replaceAll(
        "([A-Z]+)([A-Z][a-z])",
        "$1" + separator + "$2"
      )
      .replaceAll(
        "([a-z\\d])([A-Z])",
        "$1" + separator + "$2"
      )
      .toLowerCase
  }

  /**
    * Validates a custom value IRI, throwing [[BadRequestException]] if the IRI is not valid.
    *
    * @param customValueIri the custom value IRI to be validated.
    * @param resourceID the ID of the containing resource.
    * @return the validated IRI.
    */
  def validateCustomValueIri(customValueIri: SmartIri, resourceID: String): SmartIri = {
    if (!customValueIri.isKnoraValueIri) {
      throw BadRequestException(s"<$customValueIri> is not a Knora value IRI")
    }

    if (!customValueIri.getResourceID.contains(resourceID)) {
      throw BadRequestException(s"The provided value IRI does not contain the correct resource ID")
    }

    customValueIri
  }

  /**
    * Throws [[BadRequestException]] if a Knora API v2 definition API has an ontology name that can only be used
    * in the internal schema.
    *
    * @param iri the IRI to be checked.
    */
  def checkExternalOntologyName(iri: SmartIri): Unit = {
    if (iri.isKnoraApiV2DefinitionIri && OntologyConstants.InternalOntologyLabels.contains(iri.getOntologyName)) {
      throw BadRequestException(s"Internal ontology <$iri> cannot be served")
    }
  }

  def unescapeStringLiteralSeq(stringLiteralSeq: StringLiteralSequenceV2): StringLiteralSequenceV2 = {
    StringLiteralSequenceV2(
      stringLiterals = stringLiteralSeq.stringLiterals.map(stringLiteral =>
        StringLiteralV2(value = fromSparqlEncodedString(stringLiteral.value), language = stringLiteral.language))
    )
  }
  def unescapeOptionalString(optionalString: Option[String]): Option[String] = {
    optionalString match {
      case Some(s: String) => Some(fromSparqlEncodedString(s))
      case None            => None
    }
  }
}
