/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages

import akka.actor.ActorRef
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import spray.json._
import zio.ZLayer
import zio.prelude.Validation

import java.time._
import java.time.temporal.ChronoField
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.matching.Regex

import dsp.errors._
import dsp.valueobjects.Iri
import dsp.valueobjects.IriErrorMessages
import dsp.valueobjects.UuidUtil
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter._
import org.knora.webapi.messages.store.triplestoremessages.SparqlAskRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlAskResponse
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.KnoraContentV2
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.util.Base64UrlCheckDigit
import org.knora.webapi.util.JavaUtil

import XmlPatterns.nCNamePattern
import XmlPatterns.nCNameRegex

/**
 * Provides instances of [[StringFormatter]], as well as string formatting constants.
 */
object StringFormatter {
  // A non-printing delimiter character, Unicode INFORMATION SEPARATOR ONE, that should never occur in data.
  val INFORMATION_SEPARATOR_ONE = '\u001F'

  // A non-printing delimiter character, Unicode INFORMATION SEPARATOR TWO, that should never occur in data.
  val INFORMATION_SEPARATOR_TWO = '\u001E'

  // a separator to be inserted in the XML to separate nodes from one another
  // this separator is only used temporarily while XML is being processed
  val PARAGRAPH_SEPARATOR = '\u2029'

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
  private val ArkVersion: String = "1"

  /**
   * The maximum number of times that `makeUnusedIri` will try to make a new, unused IRI.
   */
  val MAX_IRI_ATTEMPTS: Int = 5

  /**
   * The domain name used to construct Knora IRIs.
   */
  private val IriDomain: String = "rdfh.ch"

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
  def getGeneralInstance: StringFormatter =
    generalInstance match {
      case Some(instance) => instance
      case None           => throw AssertionException("StringFormatter not yet initialised")
    }

  def getInitializedTestInstance: StringFormatter =
    generalInstance match {
      case Some(instance) => instance
      case None           => StringFormatter.initForTest(); getGeneralInstance
    }

  /**
   * Gets the singleton instance of [[StringFormatter]] that can only handle the IRIs in built-in
   * ontologies.
   */
  def getInstanceForConstantOntologies: StringFormatter = instanceForConstantOntologies

  /**
   * Initialises the general instance of [[StringFormatter]].
   *
   * @param config the application's configuration.
   */
  def init(config: AppConfig): Unit =
    this.synchronized {
      generalInstance match {
        case Some(_) => ()
        case None    => generalInstance = Some(new StringFormatter(Some(config)))
      }
    }

  /**
   * Initialises the singleton instance of [[StringFormatter]] for a test.
   */
  private def initForTest(): Unit =
    this.synchronized {
      generalInstance match {
        case Some(_) => ()
        case None    => generalInstance = Some(new StringFormatter(maybeConfig = None, initForTest = true))
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
  private case class SmartIriInfo(
    iriType: IriType,
    projectCode: Option[String] = None,
    ontologyName: Option[String] = None,
    entityName: Option[String] = None,
    resourceID: Option[String] = None,
    valueID: Option[String] = None,
    standoffStartIndex: Option[Int] = None,
    ontologySchema: Option[OntologySchema],
    isBuiltInDef: Boolean = false,
    sharedOntology: Boolean = false
  )

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
  private def getOrCacheSmartIri(iriStr: IRI, creationFun: () => SmartIri): SmartIri =
    smartIriCache.computeIfAbsent(
      iriStr,
      JavaUtil.function({ _: Object => creationFun() })
    )

  val live: ZLayer[AppConfig, Nothing, StringFormatter] = ZLayer.fromFunction { appConfig: AppConfig =>
    StringFormatter.init(appConfig)
    StringFormatter.getGeneralInstance
  }

  val test: ZLayer[Any, Nothing, StringFormatter] = ZLayer.fromFunction { () =>
    StringFormatter.initForTest()
    StringFormatter.getGeneralInstance
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

  def toIri: IRI = toString

  def toInternalIri: InternalIri = InternalIri(toOntologySchema(InternalSchema).toIri)

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
   * Checks that the IRI's ontology schema, if present, corresponds to the specified schema.
   *
   * @param schema The [[ApiV2Schema]] to be allowed.
   * @return `true` if the [[OntologySchema]] is present and matches the specified schema or if the iri does not have a schema.
   */
  def isApiV2Schema(allowedSchema: ApiV2Schema): Boolean = getOntologySchema match {
    case Some(schema) => schema == allowedSchema
    case None         => true
  }

  /**
   * Checks that the IRI's ontology schema, if present, corresponds to the specified schema.
   *
   * @param schema The [[OntologySchema]] which must be present.
   * @return `true` if the [[OntologySchema]] is present and matches the specified schema.
   *         `false` otherwise.
   */
  private def isOntologySchema(schema: OntologySchema): Boolean = getOntologySchema.contains(schema)
  def isApiV2ComplexSchema: Boolean                             = isOntologySchema(ApiV2Complex)

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

  override def equals(obj: scala.Any): Boolean =
    // See the comment at the top of the SmartIri trait.
    obj match {
      case that: SmartIri => this.toString == that.toString
      case _              => false
    }

  override def hashCode: Int = toString.hashCode

  def compare(that: SmartIri): Int = toString.compare(that.toString)

  /**
   * Some Iri contain a UUID as last path segment, which is Base64 encoded. This method returns the UUID, if present.
   * @return The [[UUID]], if present. [[None]] if either the id is not present or it is not a valid base 64 encoded UUID.
   */
  def getUuid: Option[UUID] = toString.split("/").lastOption.flatMap(UuidUtil.base64Decode(_).toOption)
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
class StringFormatter private (
  val maybeConfig: Option[AppConfig],
  maybeKnoraHostAndPort: Option[String] = None,
  initForTest: Boolean = false
) {

  // The host and port number that this Knora server is running on, and that should be used
  // when constructing IRIs for project-specific ontologies.
  private val knoraApiHostAndPort: Option[String] = if (initForTest) {
    // Use the default host and port for automated testing.
    Some("0.0.0.0:3333")
  } else {
    maybeConfig match {
      case Some(config) => Some(config.knoraApi.externalOntologyIriHostAndPort)
      case None         => maybeKnoraHostAndPort
    }
  }

  // The protocol and host that the ARK resolver is running on.
  private val arkResolver: Option[String] = if (initForTest) {
    Some("http://0.0.0.0:3336")
  } else {
    maybeConfig.map(_.ark.resolver)
  }

  // The DaSCH's ARK assigned number.
  private val arkAssignedNumber: Option[Int] = if (initForTest) {
    Some(72163)
  } else {
    maybeConfig.map(_.ark.assignedNumber)
  }

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

  // A regex sub-pattern for project IDs, which must consist of 4 hexadecimal digits.
  private val ProjectIDPattern: String =
    """\p{XDigit}{4,4}"""

  // A regex for matching a string containing the project ID.
  private val ProjectIDRegex: Regex = ("^" + ProjectIDPattern + "$").r

  // A regex for the URL path of an API v2 ontology (built-in or project-specific).
  private val ApiV2OntologyUrlPathRegex: Regex = (
    "^" + "/ontology/((" +
      ProjectIDPattern + ")/)?(" + nCNamePattern + ")(" +
      OntologyConstants.KnoraApiV2Complex.VersionSegment + "|" + OntologyConstants.KnoraApiV2Simple.VersionSegment + ")$"
  ).r

  // The start of the IRI of a project-specific API v2 ontology that is served by this API server.
  private val MaybeProjectSpecificApiV2OntologyStart: Option[String] = knoraApiHostAndPort match {
    case Some(hostAndPort) => Some("http://" + hostAndPort + "/ontology/")
    case None              => None
  }

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
    ("^http://" + IriDomain + "/(" + ProjectIDPattern + ")/(" + Base64UrlPattern + ")$").r

  // A regex that matches a Knora value IRI.
  private val ValueIriRegex: Regex =
    ("^http://" + IriDomain + "/(" + ProjectIDPattern + ")/(" + Base64UrlPattern + ")/values/(" + Base64UrlPattern + ")$").r

  // A regex that matches a Knora standoff IRI.
  private val StandoffIriRegex: Regex =
    ("^http://" + IriDomain + "/(" + ProjectIDPattern + ")/(" + Base64UrlPattern + ")/values/(" + Base64UrlPattern + """)/standoff/(\d+)$""").r

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

    private val iri: IRI = Iri.validateAndEscapeIri(iriStr).getOrElse(errorFun)

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

      val lastSegment     = segments.last
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
            case ResourceIriRegex(projectCode: String, resourceID: String) =>
              // It's a resource IRI.
              SmartIriInfo(
                iriType = KnoraDataIri,
                ontologySchema = None,
                projectCode = Some(projectCode),
                resourceID = Some(resourceID)
              )

            case ValueIriRegex(projectCode: String, resourceID: String, valueID: String) =>
              // It's a value IRI.
              SmartIriInfo(
                iriType = KnoraDataIri,
                ontologySchema = None,
                projectCode = Some(projectCode),
                resourceID = Some(resourceID),
                valueID = Some(valueID)
              )

            case StandoffIriRegex(
                  projectCode: String,
                  resourceID: String,
                  valueID: String,
                  standoffStartIndex: String
                ) =>
              // It's a standoff IRI.
              SmartIriInfo(
                iriType = KnoraDataIri,
                ontologySchema = None,
                projectCode = Some(projectCode),
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
        } else if (iri.startsWith(OntologyConstants.NamedGraphs.DataNamedGraphStart)) {
          // Nothing else to do.
          SmartIriInfo(
            iriType = KnoraDataIri,
            ontologySchema = None
          )
        } else {
          // If this is an entity IRI in a hash namespace, separate the entity name from the namespace.

          val hashPos = iri.lastIndexOf('#')

          val (namespace: String, entityName: Option[String]) = if (hashPos >= 0 && hashPos < iri.length) {
            val namespace  = iri.substring(0, hashPos)
            val entityName = iri.substring(hashPos + 1)

            // Validate the entity name as an NCName.
            if (!nCNameRegex.matches(entityName)) errorFun
            (namespace, Some(entityName))
          } else {
            (iri, None)
          }

          // Remove the URL scheme (http://), and split the remainder of the namespace into slash-delimited segments.
          val body     = namespace.substring(namespace.indexOf("//") + 2)
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
                // default shared ontologies project
                (true, Some(DefaultSharedOntologiesProjectCode))
              } else if (ontologyPath.length == 3) {
                // other shared ontologies project
                (true, Some(validateProjectShortcode(ontologyPath(1), errorFun)))
              } else {
                errorFun
              }
            } else if (ontologyPath.length == 2) {
              // non-shared ontology with project code
              (false, Some(validateProjectShortcode(ontologyPath.head, errorFun)))
            } else {
              // built-in ontology
              (false, None)
            }

            // Extract the ontology name.
            val ontologyName           = ontologyPath.last
            val hasBuiltInOntologyName = isBuiltInOntologyName(ontologyName)

            if (!hasBuiltInOntologyName) {
              ValuesValidator.validateProjectSpecificOntologyName(ontologyName).getOrElse(errorFun)
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

    private lazy val ontologyFromEntity: SmartIri =
      if (isKnoraOntologyIri) {
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

    override def makeEntityIri(entityName: String): SmartIri =
      if (isKnoraOntologyIri) {
        val entityIriStr = iri + "#" + entityName
        getOrCacheSmartIri(entityIriStr, () => new SmartIriImpl(entityIriStr))
      } else {
        throw DataConversionException(s"$iri is not a Knora ontology IRI")
      }

    override def getOntologyName: String =
      iriInfo.ontologyName match {
        case Some(name) => name
        case None       => throw DataConversionException(s"Expected a Knora ontology IRI: $iri")
      }

    override def getEntityName: String =
      iriInfo.entityName match {
        case Some(name) => name
        case None       => throw DataConversionException(s"Expected a Knora entity IRI: $iri")
      }

    override def getOntologySchema: Option[OntologySchema] = iriInfo.ontologySchema

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

    override def toOntologySchema(targetSchema: OntologySchema): SmartIri =
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
                  getOrCacheSmartIri(
                    iriStr = convertedIri,
                    creationFun = { () =>
                      new SmartIriImpl(convertedIri)
                    }
                  )

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

    private def getVersionSegment(targetSchema: ApiV2Schema): String =
      targetSchema match {
        case ApiV2Simple  => OntologyConstants.KnoraApiV2Simple.VersionSegment
        case ApiV2Complex => OntologyConstants.KnoraApiV2Complex.VersionSegment
      }

    private def externalToInternalEntityIri: SmartIri = {
      // Construct the string representation of this IRI in the target schema.
      val internalOntologyName = externalToInternalOntologyName(getOntologyName)
      val entityName           = getEntityName

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
      // Construct the string representation of this IRI in the target schema.
      val entityName            = getEntityName
      val convertedOntologyIri  = getOntologyFromEntity.toOntologySchema(targetSchema)
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
      val ontologyName   = getOntologyName
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
        val convertedIriStr     = getOntologyFromEntity.makeEntityIri(convertedEntityName).toString

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

      val entityName          = getEntityName
      val convertedEntityName = entityName + "Value"
      val convertedIriStr     = getOntologyFromEntity.makeEntityIri(convertedEntityName).toString

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

    override def isKnoraResourceIri: Boolean =
      if (!isKnoraDataIri) {
        false
      } else {
        (iriInfo.projectCode, iriInfo.resourceID, iriInfo.valueID) match {
          case (Some(_), Some(_), None) => true
          case _                        => false
        }
      }

    override def isKnoraValueIri: Boolean =
      if (!isKnoraDataIri) {
        false
      } else {
        (iriInfo.projectCode, iriInfo.resourceID, iriInfo.valueID) match {
          case (Some(_), Some(_), Some(_)) => true
          case _                           => false
        }
      }

    override def isKnoraStandoffIri: Boolean =
      if (!isKnoraDataIri) {
        false
      } else {
        (iriInfo.projectCode, iriInfo.resourceID, iriInfo.valueID, iriInfo.standoffStartIndex) match {
          case (Some(_), Some(_), Some(_), Some(_)) => true
          case _                                    => false
        }
      }

    override def fromResourceIriToArkUrl(maybeTimestamp: Option[Instant] = None): String = {
      if (!isKnoraResourceIri) {
        throw DataConversionException(s"IRI $iri is not a Knora resource IRI")
      }

      val arkUrlTry = Try {
        makeArkUrl(
          projectID = iriInfo.projectCode.get,
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
          projectID = iriInfo.projectCode.get,
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
  def toSmartIriWithErr(iri: IRI, errorFun: => Nothing): SmartIri =
    // Is this a Knora definition IRI?
    if (CacheableIriStarts.exists(start => iri.startsWith(start))) {
      // Yes. Return it from the cache, or cache it if it's not already cached.
      getOrCacheSmartIri(iri, () => new SmartIriImpl(iri, None, errorFun))
    } else {
      // No. Convert it to a SmartIri without caching it.
      new SmartIriImpl(iri, None, errorFun)
    }

  /**
   * Encodes a string for use in JSON, and encloses it in quotation marks.
   *
   * @param s the string to be encoded.
   * @return the encoded string.
   */
  def toJsonEncodedString(s: String): String =
    JsString(s).compactPrint

  /**
   * Formats a Knora ARK timestamp.
   *
   * @param timestamp the timestamp to be formatted.
   * @return a string representation of the timestamp.
   */
  def formatArkTimestamp(timestamp: Instant): String = {
    val offsetDateTime: OffsetDateTime = timestamp.atOffset(ZoneOffset.UTC)

    val year: Int         = offsetDateTime.get(ChronoField.YEAR)
    val month: Int        = offsetDateTime.get(ChronoField.MONTH_OF_YEAR)
    val day: Int          = offsetDateTime.get(ChronoField.DAY_OF_MONTH)
    val hour: Int         = offsetDateTime.get(ChronoField.HOUR_OF_DAY)
    val minute: Int       = offsetDateTime.get(ChronoField.MINUTE_OF_HOUR)
    val second: Int       = offsetDateTime.get(ChronoField.SECOND_OF_MINUTE)
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
   * Returns `true` if an ontology name is reserved for a built-in ontology.
   *
   * @param ontologyName the ontology name to be checked.
   * @return `true` if the ontology name is reserved for a built-in ontology.
   */
  private def isBuiltInOntologyName(ontologyName: String): Boolean =
    OntologyConstants.BuiltInOntologyLabels.contains(ontologyName)

  /**
   * Given a valid internal (built-in or project-specific) ontology name and an optional project code, constructs the
   * corresponding internal ontology IRI.
   *
   * @param internalOntologyName the ontology name.
   * @param projectCode          the project code.
   * @return the ontology IRI.
   */
  private def makeInternalOntologyIriStr(
    internalOntologyName: String,
    isShared: Boolean,
    projectCode: Option[String]
  ): IRI = {
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
  def makeProjectSpecificInternalOntologyIri(
    internalOntologyName: String,
    isShared: Boolean,
    projectCode: String
  ): SmartIri =
    toSmartIri(makeInternalOntologyIriStr(internalOntologyName, isShared, Some(projectCode)))

  /**
   * Converts an internal ontology name to an external ontology name. This only affects `knora-base`, whose
   * external equivalent is `knora-api.`
   *
   * @param ontologyName an internal ontology name.
   * @return the corresponding external ontology name.
   */
  private def internalToExternalOntologyName(ontologyName: String): String =
    if (ontologyName == OntologyConstants.KnoraBase.KnoraBaseOntologyLabel) {
      OntologyConstants.KnoraApi.KnoraApiOntologyLabel
    } else {
      ontologyName
    }

  /**
   * Converts an external ontology name to an internal ontology name. This only affects `knora-api`, whose
   * internal equivalent is `knora-base.`
   *
   * @param ontologyName an external ontology name.
   * @return the corresponding internal ontology name.
   */
  private def externalToInternalOntologyName(ontologyName: String): String =
    if (ontologyName == OntologyConstants.KnoraApi.KnoraApiOntologyLabel) {
      OntologyConstants.KnoraBase.KnoraBaseOntologyLabel
    } else {
      ontologyName
    }

  /**
   * Determines whether a URL path refers to a built-in API v2 ontology (simple or complex).
   *
   * @param urlPath the URL path.
   * @return true if the path refers to a built-in API v2 ontology.
   */
  def isBuiltInApiV2OntologyUrlPath(urlPath: String): Boolean =
    urlPath match {
      case ApiV2OntologyUrlPathRegex(_, _, ontologyName, _) if isBuiltInOntologyName(ontologyName) => true
      case _                                                                                       => false
    }

  /**
   * Determines whether a URL path refers to a project-specific API v2 ontology (simple or complex).
   *
   * @param urlPath the URL path.
   * @return true if the path refers to a project-specific API v2 ontology.
   */
  def isProjectSpecificApiV2OntologyUrlPath(urlPath: String): Boolean =
    urlPath match {
      case ApiV2OntologyUrlPathRegex(_, _, ontologyName, _) if !isBuiltInOntologyName(ontologyName) => true
      case _                                                                                        => false
    }

  /**
   * Given the project shortcode, checks if it is in a valid format, and converts it to upper case.
   *
   * @param shortcode the project's shortcode.
   * @return the shortcode in upper case.
   */
  @deprecated("Use def validateProjectShortcode(String) instead.")
  def validateProjectShortcode(shortcode: String, errorFun: => Nothing): String = // V2 / value objects
    validateProjectShortcode(shortcode).getOrElse(errorFun)

  def validateProjectShortcode(shortcode: String): Option[String] =
    ProjectIDRegex.findFirstIn(shortcode.toUpperCase)

  /**
   * Given the group IRI, checks if it is in a valid format.
   *
   * @param iri the group's IRI.
   * @return the IRI of the list.
   */
  def validateGroupIri(iri: IRI): Validation[ValidationException, IRI] =
    if (Iri.isGroupIri(iri)) Validation.succeed(iri)
    else Validation.fail(ValidationException(s"Invalid IRI: $iri"))

  /**
   * Given the permission IRI, checks if it is in a valid format.
   *
   * @param iri the permission's IRI.
   * @return either the IRI or the error message.
   */
  def validatePermissionIri(iri: IRI): Either[String, IRI] =
    if (Iri.isPermissionIri(iri) && UuidUtil.hasSupportedVersion(iri)) Right(iri)
    else if (Iri.isPermissionIri(iri) && !UuidUtil.hasSupportedVersion(iri)) Left(IriErrorMessages.UuidVersionInvalid)
    else Left(s"Invalid permission IRI: $iri.")

  /**
   * Given an email address, checks if it is in a valid format.
   *
   * @param email the email.
   * @return the email
   */
  def validateEmail(email: String): Option[String] =
    EmailAddressRegex.findFirstIn(email)

  /**
   * Check that the string represents a valid username.
   *
   * @param value    the string to be checked.
   * @return the same string.
   */
  def validateUsername(value: String): Option[String] =
    UsernameRegex.findFirstIn(value)

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
  private def makeArkUrl(
    projectID: String,
    resourceID: String,
    maybeValueUUID: Option[UUID],
    maybeTimestamp: Option[Instant]
  ): String = {

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
    val resourceArkUrl = s"$resolver/ark:/$assignedNumber/$ArkVersion/$projectID/$resourceIDWithCheckDigit"

    // If a value UUID was provided, Base64-encode it, add a check digit, and append the result to the URL.
    val arkUrlWithoutTimestamp = maybeValueUUID match {
      case Some(valueUUID: UUID) => s"$resourceArkUrl/${addCheckDigitAndEscape(UuidUtil.base64Encode(valueUUID))}"
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
   * Constructs a path for accessing a file that has been uploaded to Sipi's temporary storage.
   *
   * @param filename the filename.
   * @return a URL for accessing the file.
   */
  def makeSipiTempFilePath(filename: String): String = s"/tmp/$filename"

  /**
   * Checks whether an IRI already exists in the triplestore.
   *
   * @param iri          the IRI to be checked.
   * @param appActor     a reference to the application actor.
   * @return `true` if the IRI already exists, `false` otherwise.
   */
  def checkIriExists(iri: IRI, appActor: ActorRef)(implicit
    timeout: Timeout,
    executionContext: ExecutionContext
  ): Future[Boolean] =
    for {
      askString <- Future(org.knora.webapi.messages.twirl.queries.sparql.admin.txt.checkIriExists(iri).toString)
      response  <- appActor.ask(SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
    } yield response.result

  /**
   * Attempts to create a new IRI that isn't already used in the triplestore. Will try up to [[MAX_IRI_ATTEMPTS]]
   * times, then throw an exception if an unused IRI could not be created.
   *
   * @param iriFun       a function that generates a random IRI.
   * @param storeManager a reference to the Knora store manager actor.
   */
  def makeUnusedIri(iriFun: => IRI, storeManager: ActorRef, log: Logger)(implicit
    timeout: Timeout,
    executionContext: ExecutionContext
  ): Future[IRI] = {
    def makeUnusedIriRec(attempts: Int): Future[IRI] = {
      val newIri = iriFun

      for {
        iriExists <- checkIriExists(newIri, storeManager)

        result <-
          if (!iriExists) {
            FastFuture.successful(newIri)
          } else if (attempts > 1) {
            log.warn("KnoraIdUtil.makeUnusedIri generated an IRI that already exists in the triplestore, retrying")
            makeUnusedIriRec(attempts - 1)
          } else {
            throw UpdateNotPerformedException(s"Could not make an unused new IRI after $MAX_IRI_ATTEMPTS attempts")
          }
      } yield result
    }

    makeUnusedIriRec(attempts = MAX_IRI_ATTEMPTS)
  }

  /**
   * Creates a new resource IRI based on a UUID.
   *
   * @param projectShortcode the project's shortcode.
   * @return a new resource IRI.
   */
  def makeRandomResourceIri(projectShortcode: String): IRI = {
    val knoraResourceID = UuidUtil.makeRandomBase64EncodedUuid
    s"http://$IriDomain/$projectShortcode/$knoraResourceID"
  }

  /**
   * Creates a new value IRI based on a UUID.
   *
   * @param resourceIri the IRI of the resource that will contain the value.
   * @param givenUUID   the optional given UUID of the value. If not provided, create a random one.
   * @return a new value IRI.
   */
  def makeRandomValueIri(resourceIri: IRI, givenUUID: Option[UUID] = None): IRI = {
    val valueUUID = givenUUID match {
      case Some(uuid: UUID) => UuidUtil.base64Encode(uuid)
      case _                => UuidUtil.makeRandomBase64EncodedUuid
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
    Iri
      .validateAndEscapeIri(mappingIri)
      .getOrElse(throw BadRequestException(s"the created mapping IRI $mappingIri is invalid"))
  }

  /**
   * Creates a random IRI for an element of a mapping based on a mapping IRI.
   *
   * @param mappingIri the IRI of the mapping the element belongs to.
   * @return a new mapping element IRI.
   */
  def makeRandomMappingElementIri(mappingIri: IRI): IRI = {
    val knoraMappingElementUuid = UuidUtil.makeRandomBase64EncodedUuid
    s"$mappingIri/elements/$knoraMappingElementUuid"
  }

  /**
   * Creates an IRI used as a lock for the creation of mappings inside a given project.
   * This method will always return the same IRI for the given project IRI.
   *
   * @param projectIri the IRI of the project the mapping will belong to.
   * @return an IRI used as a lock for the creation of mappings inside a given project.
   */
  def createMappingLockIriForProject(projectIri: IRI): IRI =
    s"$projectIri/mappings"

  /**
   * Creates a new project IRI based on a UUID.
   *
   * @return a new project IRI.
   */
  def makeRandomProjectIri: IRI = {
    val uuid = UuidUtil.makeRandomBase64EncodedUuid
    s"http://$IriDomain/projects/$uuid"
  }

  /**
   * Creates a new group IRI based on a UUID.
   *
   * @param shortcode the required project shortcode.
   * @return a new group IRI.
   */
  def makeRandomGroupIri(shortcode: String): String = {
    val knoraGroupUuid = UuidUtil.makeRandomBase64EncodedUuid
    s"http://$IriDomain/groups/$shortcode/$knoraGroupUuid"
  }

  /**
   * Creates a new person IRI based on a UUID.
   *
   * @return a new person IRI.
   */
  def makeRandomPersonIri: IRI = {
    val knoraPersonUuid = UuidUtil.makeRandomBase64EncodedUuid
    s"http://$IriDomain/users/$knoraPersonUuid"
  }

  /**
   * Creates a new list IRI based on a UUID.
   *
   * @param shortcode the required project shortcode.
   * @return a new list IRI.
   */
  def makeRandomListIri(shortcode: String): String = {
    val knoraListUuid = UuidUtil.makeRandomBase64EncodedUuid
    s"http://$IriDomain/lists/$shortcode/$knoraListUuid"
  }

  /**
   * Creates a new permission IRI based on a UUID.
   *
   * @param shortcode the required project shortcode.
   * @return the IRI of the permission object.
   */
  def makeRandomPermissionIri(shortcode: String): IRI = {
    val knoraPermissionUuid = UuidUtil.makeRandomBase64EncodedUuid
    s"http://$IriDomain/permissions/$shortcode/$knoraPermissionUuid"
  }

  /**
   * Validates a custom value IRI, throwing [[BadRequestException]] if the IRI is not valid.
   *
   * @param customValueIri the custom value IRI to be validated.
   * @param projectCode the project code of the containing resource.
   * @param resourceID the ID of the containing resource.
   * @return the validated IRI.
   */
  def validateCustomValueIri(customValueIri: SmartIri, projectCode: String, resourceID: String): SmartIri = {
    if (!customValueIri.isKnoraValueIri) {
      throw BadRequestException(s"<$customValueIri> is not a Knora value IRI")
    }

    if (!customValueIri.getProjectCode.contains(projectCode)) {
      throw BadRequestException(s"The provided value IRI does not contain the correct project code")
    }

    if (!customValueIri.getResourceID.contains(resourceID)) {
      throw BadRequestException(s"The provided value IRI does not contain the correct resource ID")
    }

    customValueIri
  }

  def isKnoraOntologyIri(iri: SmartIri): Boolean =
    iri.isKnoraApiV2DefinitionIri && OntologyConstants.InternalOntologyLabels.contains(iri.getOntologyName)

  def unescapeStringLiteralSeq(stringLiteralSeq: StringLiteralSequenceV2): StringLiteralSequenceV2 =
    StringLiteralSequenceV2(
      stringLiterals = stringLiteralSeq.stringLiterals.map(stringLiteral =>
        StringLiteralV2(Iri.fromSparqlEncodedString(stringLiteral.value), stringLiteral.language)
      )
    )
  def unescapeOptionalString(optionalString: Option[String]): Option[String] =
    optionalString match {
      case Some(s: String) => Some(Iri.fromSparqlEncodedString(s))
      case None            => None
    }
}
