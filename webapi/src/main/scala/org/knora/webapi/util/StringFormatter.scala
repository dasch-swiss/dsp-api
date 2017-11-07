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

import com.google.gwt.safehtml.shared.UriUtils._
import org.apache.commons.lang3.StringUtils
import org.apache.commons.validator.routines.UrlValidator
import org.eclipse.rdf4j
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.twirl.StandoffTagV1
import org.knora.webapi.util.JavaUtil.Optional
import spray.json.JsonParser

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


    private var maybeInstance: Option[StringFormatter] = None

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
                case None => maybeInstance = Some(new StringFormatter(settings.knoraApiHttpBaseUrl))
            }
        }
    }

    /**
      * Initialises the singleton instance of [[StringFormatter]] for a test.
      */
    def initForTest(): Unit = {
        this.synchronized {
            maybeInstance match {
                case Some(_) => ()
                case None => maybeInstance = Some(new StringFormatter("http://0.0.0.0:3333"))
            }
        }
    }
}

/**
  * Represents a parsed IRI.
  */
sealed trait SmartIri {
    /**
      * Returns `true` if this is a Knora ontology or entity IRI.
      */
    def isKnoraIri: Boolean

    /**
      * Returns `true` if this is a built-in Knora ontology or entity IRI.
      * @return
      */
    def isBuiltInIri: Boolean

    /**
      * Returns `true` if this is a Knora ontology IRI.
      */
    def isKnoraOntologyIri: Boolean

    /**
      * Returns `true` if this is a Knora entity IRI.
      */
    def isKnoraEntityIri: Boolean

    /**
      * Returns the IRI's project code, if any.
      */
    def getProjectCode: Option[String]

    /**
      * If this is a Knora entity IRI, returns its ontology IRI. If this is a Knora ontology IRI, returns the same IRI.
      * Otherwise, throws [[DataConversionException]].
      */
    def getOntology: SmartIri

    /**
      * If this is a Knora ontology or entity IRI, returns the name of the ontology. Otherwise, throws [[DataConversionException]].
      */
    def getOntologyName: String

    /**
      * If this is a Knora entity IRI, returns the name of the entity. Otherwise, throws [[DataConversionException]].
      */
    def getEntityName: String

    /**
      * Returns the IRI's [[OntologySchema]].
      */
    def getOntologySchema: OntologySchema

    /**
      * Returns `true` if this is a Knora API v2 ontology or entity IRI.
      */
    def isApiV2Iri: Boolean

    /**
      * Converts this IRI to another ontology schema.
      *
      * @param targetSchema the target schema.
      */
    def toOntologySchema(targetSchema: OntologySchema): SmartIri

    /**
      * Constructs a prefix label that can be used to shorten this IRI's namespace in formats such as Turtle and JSON-LD.
      */
    def getPrefixLabel: String
}

/**
  * Handles string formatting and validation.
  */
class StringFormatter private(val knoraApiHttpBaseUrl: String) {

    import StringFormatter._

    private val valueFactory = SimpleValueFactory.getInstance()

    /**
      * The implementation of [[SmartIri]]. An instance of this class can only be constructed by [[StringFormatter]].
      * The constructor validates and parses the IRI.
      *
      * @param iri the IRI string to be parsed.
      * @param errorFun a function that throws an exception. It will be called if the IRI is invalid.
      */
    private class SmartIriImpl(iri: IRI, errorFun: () => Nothing) extends SmartIri {
        def this(iri: IRI) = this(iri, () => throw DataConversionException(s"Couldn't parse IRI: $iri"))

        private val rdfIri: rdf4j.model.IRI = try {
            valueFactory.createIRI(validateIri(iri, errorFun))
        } catch {
            case _: Exception => errorFun()
        }

        private case class SmartIriInfo(projectCode: Option[String],
                                        ontologyName: Option[String],
                                        entityName: Option[String],
                                        ontologySchema: OntologySchema,
                                        isBuiltIn: Boolean)

        private val iriInfo: SmartIriInfo = iri match {
            case InternalOntologyRegex(_, Optional(projectCode), ontologyName) =>
                SmartIriInfo(
                    projectCode = projectCode,
                    ontologyName = Some(ontologyName),
                    entityName = None,
                    ontologySchema = InternalSchema,
                    isBuiltIn = isBuiltInOntologyName(ontologyName)
                )

            case InternalOntologyEntityRegex(_, Optional(projectCode), ontologyName, entityName) =>
                SmartIriInfo(
                    projectCode = projectCode,
                    ontologyName = Some(ontologyName),
                    entityName = Some(entityName),
                    ontologySchema = InternalSchema,
                    isBuiltIn = isBuiltInOntologyName(ontologyName)
                )

            case BuiltInApiV2SimpleOntologyRegex(ontologyName) =>
                if (!isBuiltInOntologyName(ontologyName)) {
                    errorFun()
                }

                SmartIriInfo(
                    projectCode = None,
                    ontologyName = Some(ontologyName),
                    entityName = None,
                    ontologySchema = ApiV2Simple,
                    isBuiltIn = true
                )

            case BuiltInApiV2WithValueObjectsOntologyRegex(ontologyName) =>
                if (!isBuiltInOntologyName(ontologyName)) {
                    errorFun()
                }

                SmartIriInfo(
                    projectCode = None,
                    ontologyName = Some(ontologyName),
                    entityName = None,
                    ontologySchema = ApiV2WithValueObjects,
                    isBuiltIn = true
                )

            case BuiltInApiV2SimpleOntologyEntityRegex(ontologyName, entityName) =>
                if (!isBuiltInOntologyName(ontologyName)) {
                    errorFun()
                }

                SmartIriInfo(
                    projectCode = None,
                    ontologyName = Some(ontologyName),
                    entityName = Some(entityName),
                    ontologySchema = ApiV2Simple,
                    isBuiltIn = true
                )

            case BuiltInApiV2WithValueObjectsOntologyEntityRegex(ontologyName, entityName) =>
                if (!isBuiltInOntologyName(ontologyName)) {
                    errorFun()
                }

                SmartIriInfo(
                    projectCode = None,
                    ontologyName = Some(ontologyName),
                    entityName = Some(entityName),
                    ontologySchema = ApiV2WithValueObjects,
                    isBuiltIn = true
                )

            case ProjectSpecificApiV2SimpleOntologyRegex(_, Optional(projectCode), ontologyName) =>
                if (isBuiltInOntologyName(ontologyName)) {
                    errorFun()
                }

                SmartIriInfo(
                    projectCode = projectCode,
                    ontologyName = Some(ontologyName),
                    entityName = None,
                    ontologySchema = ApiV2Simple,
                    isBuiltIn = false
                )

            case ProjectSpecificApiV2WithValueObjectsOntologyRegex(_, Optional(projectCode), ontologyName) =>
                if (isBuiltInOntologyName(ontologyName)) {
                    errorFun()
                }

                SmartIriInfo(
                    projectCode = projectCode,
                    ontologyName = Some(ontologyName),
                    entityName = None,
                    ontologySchema = ApiV2WithValueObjects,
                    isBuiltIn = false
                )

            case ProjectSpecificApiV2SimpleOntologyEntityRegex(_, Optional(projectCode), ontologyName, entityName) =>
                if (isBuiltInOntologyName(ontologyName)) {
                    errorFun()
                }

                SmartIriInfo(
                    projectCode = projectCode,
                    ontologyName = Some(ontologyName),
                    entityName = Some(entityName),
                    ontologySchema = ApiV2Simple,
                    isBuiltIn = false
                )

            case ProjectSpecificApiV2WithValueObjectsOntologyEntityRegex(_, Optional(projectCode), ontologyName, entityName) =>
                if (isBuiltInOntologyName(ontologyName)) {
                    errorFun()
                }

                SmartIriInfo(
                    projectCode = projectCode,
                    ontologyName = Some(ontologyName),
                    entityName = Some(entityName),
                    ontologySchema = ApiV2WithValueObjects,
                    isBuiltIn = false
                )

            case _ => SmartIriInfo(
                projectCode = None,
                ontologyName = None,
                entityName = None,
                ontologySchema = NonKnoraSchema,
                isBuiltIn = false
            )
        }

        override def toString: String = rdfIri.toString

        override def hashCode: Int = rdfIri.hashCode

        override def isKnoraIri: Boolean = iriInfo.ontologySchema != NonKnoraSchema

        override def isBuiltInIri: Boolean = iriInfo.isBuiltIn

        override def isKnoraOntologyIri: Boolean = iriInfo.ontologyName.nonEmpty && iriInfo.entityName.isEmpty

        override def isKnoraEntityIri: Boolean = iriInfo.entityName.nonEmpty

        override def getProjectCode: Option[String] = iriInfo.projectCode

        override def getOntology: SmartIri = {
            if (isKnoraOntologyIri) {
                this
            } else if (isKnoraEntityIri) {
                new SmartIriImpl(rdfIri.getNamespace.stripSuffix("#"))
            } else {
                throw DataConversionException(s"Can't get ontology from non-Knora IRI $rdfIri")
            }
        }

        override def getOntologyName: String = {
            iriInfo.ontologyName match {
                case Some(name) => name
                case None => throw DataConversionException(s"Expected a Knora ontology IRI: $rdfIri")
            }
        }

        override def getEntityName: String = {
            iriInfo.entityName match {
                case Some(name) => name
                case None => throw DataConversionException(s"Expected a Knora entity IRI: $rdfIri")
            }
        }

        override def getOntologySchema: OntologySchema = iriInfo.ontologySchema

        override def getPrefixLabel: String = {
            val prefix = new StringBuilder

            iriInfo.projectCode match {
                case Some(id) => prefix.append('p').append(id).append('-')
                case None => ()
            }

            prefix.append(getOntologyName).toString
        }

        override def isApiV2Iri: Boolean = iriInfo.ontologySchema match {
            case _: ApiV2Schema => true
            case _ => false
        }

        override def toOntologySchema(targetSchema: OntologySchema): SmartIri = {
            if (targetSchema == NonKnoraSchema) {
                throw DataConversionException(s"Cannot convert IRI to non-Knora schema: $rdfIri")
            }

            if (!isKnoraIri || iriInfo.ontologySchema == targetSchema) {
                this
            } else {
                if (isKnoraOntologyIri) {
                    if (iriInfo.ontologySchema == InternalSchema) {
                        targetSchema match {
                            case externalSchema: ApiV2Schema => internalToExternalOntologyIri(externalSchema)
                            case _ => throw DataConversionException(s"Cannot convert $rdfIri to $targetSchema")
                        }
                    } else if (targetSchema == InternalSchema) {
                        externalToInternalOntologyIri
                    } else {
                        throw DataConversionException(s"Cannot convert IRI $rdfIri from ${iriInfo.ontologySchema} to $targetSchema")
                    }
                } else if (isKnoraEntityIri) {
                    if (iriInfo.ontologySchema == InternalSchema) {
                        targetSchema match {
                            case externalSchema: ApiV2Schema => internalToExternalEntityIri(externalSchema)
                            case _ => throw DataConversionException(s"Cannot convert $rdfIri to $targetSchema")
                        }
                    } else if (targetSchema == InternalSchema) {
                        externalToInternalEntityIri
                    } else {
                        throw DataConversionException(s"Cannot convert $rdfIri to $targetSchema")
                    }
                } else {
                    throw AssertionException(s"IRI $rdfIri is a Knora IRI, but is neither an ontology IRI nor an entity IRI")
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
            val ontologyName = getOntologyName
            val entityName = getEntityName

            val internalOntologyIri = new StringBuilder(OntologyConstants.KnoraInternal.InternalOntologyStart)

            iriInfo.projectCode match {
                case Some(projectCode) => internalOntologyIri.append(projectCode).append('/')
                case None => ()
            }

            val convertedIriStr = internalOntologyIri.append(externalToInternalOntologyName(ontologyName)).append("#").append(entityName).toString
            new SmartIriImpl(convertedIriStr)
        }


        private def internalToExternalEntityIri(targetSchema: ApiV2Schema): SmartIri = {
            val entityName = getEntityName
            val convertedOntologyIri = getOntology.toOntologySchema(targetSchema)
            val convertedEntityIriStr = convertedOntologyIri.toString + "#" + entityName
            new SmartIriImpl(convertedEntityIriStr)
        }

        private def internalToExternalOntologyIri(targetSchema: ApiV2Schema): SmartIri = {
            val ontologyName = getOntologyName
            val versionSegment = getVersionSegment(targetSchema)

            val convertedIriStr: IRI = if (isBuiltInIri) {
                OntologyConstants.KnoraApi.ApiOntologyStart + internalToExternalOntologyName(ontologyName) + versionSegment
            } else {
                val externalOntologyIri = new StringBuilder(ProjectSpecificApiV2OntologyStart)

                iriInfo.projectCode match {
                    case Some(projectCode) => externalOntologyIri.append(projectCode).append('/')
                    case None => ()
                }

                externalOntologyIri.append(ontologyName).append(versionSegment).toString
            }

            new SmartIriImpl(convertedIriStr)
        }

        private def externalToInternalOntologyIri: SmartIri = {
            val ontologyName = getOntologyName

            val internalOntologyIri = new StringBuilder(OntologyConstants.KnoraInternal.InternalOntologyStart)

            iriInfo.projectCode match {
                case Some(projectCode) => internalOntologyIri.append(projectCode).append('/')
                case None => ()
            }

            val convertedIriStr: IRI = internalOntologyIri.append(externalToInternalOntologyName(ontologyName)).toString
            new SmartIriImpl(convertedIriStr)
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
    }

    /**
      * Constructs a [[SmartIri]] by validating and parsing a string representing an IRI. Throws
      * [[DataConversionException]] if the IRI is invalid.
      *
      * @param iri the IRI string to be parsed.
      */
    def toSmartIri(iri: IRI): SmartIri = {
        new SmartIriImpl(iri)
    }

    /**
      * Constructs a [[SmartIri]] by validating and parsing a string representing an IRI.
      *
      * @param iri the IRI string to be parsed.
      * @param errorFun a function that throws an exception. It will be called if the IRI is invalid.
      */
    def toSmartIriWithErr(iri: IRI, errorFun: () => Nothing): SmartIri = {
        new SmartIriImpl(iri, errorFun)
    }

    // Reserved words that cannot be used in project-specific ontology names.
    private val reservedIriWords = Set("knora", "ontology", "simple")

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

    // A regex sub-pattern for project ideas, which consist of at least 4 hexadecimal digits.
    private val ProjectIDPattern: String =
        """\p{XDigit}{4,}"""

    // A regex for matching a string containing only an ontology prefix label or a local entity name.
    private val NCNameRegex: Regex = ("^" + NCNamePattern + "$").r

    // A regex for the URL path of an API v2 ontology (built-in or project-specific).
    private val ApiV2OntologyUrlPathRegex: Regex = (
        "^" + "/ontology/((" +
            ProjectIDPattern + ")/)?(" + NCNamePattern + ")(" +
            OntologyConstants.KnoraApiV2WithValueObjects.VersionSegment + "|" + OntologyConstants.KnoraApiV2Simple.VersionSegment + ")$"
        ).r

    // A regex for any internal ontology (built-in or project-specific).
    private val InternalOntologyRegex: Regex = (
        "^" + OntologyConstants.KnoraInternal.InternalOntologyStart + "((" +
            ProjectIDPattern + ")/)?(" + NCNamePattern + ")$"
        ).r

    // A regex for the IRI of an entity in any internal ontology (built-in or project-specific).
    private val InternalOntologyEntityRegex: Regex = (
        "^" + OntologyConstants.KnoraInternal.InternalOntologyStart + "((" +
            ProjectIDPattern + ")/)?(" + NCNamePattern + ")#(" + NCNamePattern + ")$"
        ).r

    // A regex for the IRI of any built-in API v2 simple ontology (knora-api, salsah-gui, etc.).
    private val BuiltInApiV2SimpleOntologyRegex: Regex = (
        "^" + OntologyConstants.KnoraApi.ApiOntologyHostname + "/ontology/(" +
            NCNamePattern + ")" + OntologyConstants.KnoraApiV2Simple.VersionSegment + "$"
        ).r

    // A regex for the IRI of any built-in API v2 with value objects ontology (knora-api, salsah-gui, etc.).
    private val BuiltInApiV2WithValueObjectsOntologyRegex: Regex = (
        "^" + OntologyConstants.KnoraApi.ApiOntologyHostname + "/ontology/(" +
            NCNamePattern + ")" + OntologyConstants.KnoraApiV2WithValueObjects.VersionSegment + "$"
        ).r

    // A regex for the IRI of an entity in any built-in API v2 simple ontology (knora-api, salsah-gui, etc.).
    private val BuiltInApiV2SimpleOntologyEntityRegex = (
        "^" + OntologyConstants.KnoraApi.ApiOntologyHostname + "/ontology/(" +
            NCNamePattern + ")" + OntologyConstants.KnoraApiV2Simple.VersionSegment + "#(" + NCNamePattern + ")$"
        ).r

    // A regex for the IRI of an entity in any built-in API v2 with value objects ontology (knora-api, salsah-gui, etc.).
    private val BuiltInApiV2WithValueObjectsOntologyEntityRegex = (
        "^" + OntologyConstants.KnoraApi.ApiOntologyHostname + "/ontology/(" +
            NCNamePattern + ")" + OntologyConstants.KnoraApiV2WithValueObjects.VersionSegment + "#(" + NCNamePattern + ")$"
        ).r

    // The start of the IRI of a project-specific API v2 ontology that is served by this API server.
    private val ProjectSpecificApiV2OntologyStart: String = knoraApiHttpBaseUrl + "/ontology/"

    // A regex for the IRI of a project-specific API v2 ontology that is served by this server, with the simple schema.
    private val ProjectSpecificApiV2SimpleOntologyRegex: Regex = (
        "^" + ProjectSpecificApiV2OntologyStart + "((" +
            ProjectIDPattern + ")/)?(" + NCNamePattern + ")" + OntologyConstants.KnoraApiV2Simple.VersionSegment + "$"
        ).r

    // A regex for the IRI of a project-specific API v2 ontology that is served by this server, with the value object schema.
    private val ProjectSpecificApiV2WithValueObjectsOntologyRegex: Regex = (
        "^" + ProjectSpecificApiV2OntologyStart + "((" +
            ProjectIDPattern + ")/)?(" + NCNamePattern + ")" + OntologyConstants.KnoraApiV2WithValueObjects.VersionSegment + "$"
        ).r

    // A regex for the IRI of an entity in a project-specific API v2 simple ontology that is served by this server.
    private val ProjectSpecificApiV2SimpleOntologyEntityRegex: Regex = (
        "^" + ProjectSpecificApiV2OntologyStart + "((" +
            ProjectIDPattern + ")/)?(" + NCNamePattern + ")" + OntologyConstants.KnoraApiV2Simple.VersionSegment +
            "#(" + NCNamePattern + ")$"
        ).r

    // A regex for the IRI of an entity in a project-specific API v2 with value objects ontology that is served by this server.
    private val ProjectSpecificApiV2WithValueObjectsOntologyEntityRegex: Regex = (
        "^" + ProjectSpecificApiV2OntologyStart + "((" +
            ProjectIDPattern + ")/)?(" + NCNamePattern + ")" + OntologyConstants.KnoraApiV2WithValueObjects.VersionSegment +
            "#(" + NCNamePattern + ")$"
        ).r

    // A regex for a project-specific XML import namespace.
    private val ProjectSpecificXmlImportNamespaceRegex: Regex = (
        "^" + OntologyConstants.KnoraXmlImportV1.ProjectSpecificXmlImportNamespace.XmlImportNamespaceStart + "((" +
            ProjectIDPattern + ")/)?(" + NCNamePattern + ")" +
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

    private val ApiVersionNumberRegex: Regex = "^v[0-9]+.*$".r

    // Valid URL schemes.
    private val schemes = Array("http", "https")

    // A validator for URLs.
    private val urlValidator = new UrlValidator(schemes, UrlValidator.ALLOW_LOCAL_URLS) // local urls are URL-encoded Knora IRIs as part of the whole URL

    /**
      * Checks that a string represents a valid integer.
      *
      * @param s        the string to be checked.
      * @param errorFun a function that throws an exception. It will be called if the string does not represent a
      *                 valid integer.
      * @return the integer value of the string.
      */
    def validateInt(s: String, errorFun: () => Nothing): Int = {
        try {
            s.toInt
        } catch {
            case _: Exception => errorFun() // value could not be converted to an Integer
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
    def validateBigDecimal(s: String, errorFun: () => Nothing): BigDecimal = {
        try {
            BigDecimal(s)
        } catch {
            case _: Exception => errorFun() // value could not be converted to a decimal
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
    def validateDateTime(s: String, errorFun: () => Nothing): String = {
        // check if a string corresponds to the expected format `dateTimeFormat`

        try {
            val formatter = DateTimeFormat.forPattern(dateTimeFormat)
            DateTime.parse(s, formatter).toString(formatter)
        } catch {
            case _: Exception => errorFun() // value could not be converted to a valid DateTime using the specified format
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
    def validateIri(s: String, errorFun: () => Nothing): IRI = {
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
    def validateStandoffLinkResourceReference(s: String, acceptClientIDs: Boolean, errorFun: () => Nothing): IRI = {
        if (acceptClientIDs) {
            s match {
                case StandoffLinkReferenceToClientIDForResourceRegex(_) => s
                case _ => validateIri(s, () => errorFun())
            }
        } else {
            validateIri(s, () => errorFun())
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
    def validateGeometryString(s: String, errorFun: () => Nothing): String = {
        // TODO: For now, we just make sure that the string is valid JSON. We should stop storing JSON in the triplestore, and represent geometry in RDF instead (issue 169).

        try {
            JsonParser(s)
            s
        } catch {
            case _: Exception => errorFun()
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
    def validateColor(s: String, errorFun: () => Nothing): String = {
        ColorRegex.findFirstIn(s) match {
            case Some(dateStr) => dateStr
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
    def validateDate(s: String, errorFun: () => Nothing): String = {
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
    def validateBoolean(s: String, errorFun: () => Nothing): Boolean = {
        try {
            s.toBoolean
        } catch {
            case _: Exception => errorFun() // value could not be converted to Boolean
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
    def optionStringToBoolean(maybe: Option[String], errorFun: () => Nothing): Boolean = {
        try {
            maybe.exists(_.toBoolean)
        } catch {
            case _: IllegalArgumentException => errorFun()
        }
    }

    /**
      * Checks that a string is a valid XML [[https://www.w3.org/TR/1999/REC-xml-names-19990114/#NT-NCName NCName]].
      *
      * @param ncName   the string to be checked.
      * @param errorFun a function that throws an exception. It will be called if the string is invalid.
      * @return the same string.
      */
    def validateNCName(ncName: String, errorFun: () => Nothing): String = {
        NCNameRegex.findFirstIn(ncName) match {
            case Some(value) => value
            case None => errorFun()
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
    def validateProjectSpecificOntologyName(ontologyName: String, errorFun: () => Nothing): String = {
        ontologyName match {
            case NCNameRegex(_*) => ()
            case _ => errorFun()
        }

        val lowerCaseOntologyName = ontologyName.toLowerCase

        lowerCaseOntologyName match {
            case ApiVersionNumberRegex(_*) => errorFun()
            case _ => ()
        }

        if (isBuiltInOntologyName(ontologyName)) {
            errorFun()
        }

        for (reservedIriWord <- reservedIriWords) {
            if (lowerCaseOntologyName.contains(reservedIriWord)) {
                errorFun()
            }
        }

        ontologyName
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
    def xmlImportNamespaceToInternalOntologyIriV1(namespace: String, errorFun: () => Nothing): SmartIri = {
        namespace match {
            case ProjectSpecificXmlImportNamespaceRegex(_, _, ontologyName) if !isBuiltInOntologyName(ontologyName) =>
                val namespaceIri = toSmartIri(namespace.stripSuffix("#"), () => throw BadRequestException(s"Invalid XML namespace: $namespace"))
                namespaceIri.toOntologySchema(InternalSchema)

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
    def validateMapPath(mapPath: String, errorFun: () => Nothing): String = {
        val splitPath: Array[String] = mapPath.split('/')

        for (name <- splitPath) {
            validateNCName(name, () => errorFun())
        }

        mapPath
    }

    /**
      * Given an ontology IRI requested by the user, converts it to the IRI of an ontology that the ontology responder knows about.
      *
      * @param requestedOntology the IRI of the ontology that the user requested.
      * @return the IRI of an ontology that the ontology responder can provide.
      */
    def requestedOntologyToOntologyForResponder(requestedOntology: SmartIri): SmartIri = {
        if (OntologyConstants.ConstantOntologies.contains(requestedOntology.toString)) {
            // The client is asking about a constant ontology, so don't translate its IRI.
            requestedOntology
        } else {
            // The client is asking about a non-constant ontology. Translate its IRI to an internal ontology IRI.
            requestedOntology.toOntologySchema(InternalSchema)
        }
    }

    /**
      * Given an ontology entity IRI requested by the user, converts it to the IRI of an entity that the ontology responder knows about.
      *
      * @param requestedEntity the IRI of the entity that the user requested.
      * @return the IRI of an entity that the ontology responder can provide.
      */
    def requestedEntityToEntityForResponder(requestedEntity: SmartIri): SmartIri = {
        if (OntologyConstants.ConstantOntologies.contains(requestedEntity.getOntology.toString)) {
            // The client is asking about an entity in a constant ontology, so don't translate its IRI.
            requestedEntity
        } else {
            // The client is asking about a non-constant entity. Translate its IRI to an internal entity IRI.
            requestedEntity.toOntologySchema(InternalSchema)
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
}
