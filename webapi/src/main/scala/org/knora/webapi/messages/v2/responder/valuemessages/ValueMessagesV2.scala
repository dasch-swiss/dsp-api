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

package org.knora.webapi.messages.v2.responder.valuemessages

import java.time.Instant
import java.util.UUID

import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.valuemessages.{JulianDayNumberValueV1, KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.twirl.{StandoffTagAttributeV2, StandoffTagInternalReferenceAttributeV2, StandoffTagV2}
import org.knora.webapi.util.DateUtilV2.{DateYearMonthDay, KnoraEraV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.standoff.{StandoffTagUtilV2, XMLUtil}
import org.knora.webapi.util._

/**
  * A tagging trait for requests handled by [[org.knora.webapi.responders.v2.ValuesResponderV2]].
  */
sealed trait ValuesResponderRequestV2 extends KnoraRequestV2

/**
  * Requests the creation of a value.
  *
  * @param createValue    a [[CreateValueV2]] representing the value to be created. A successful response will be
  *                       a [[CreateValueResponseV2]].
  * @param requestingUser the user making the request.
  * @param apiRequestID   the API request ID.
  */
case class CreateValueRequestV2(createValue: CreateValueV2,
                                requestingUser: UserADM,
                                apiRequestID: UUID) extends ValuesResponderRequestV2

/**
  * Constructs [[CreateValueRequestV2]] based on JSON-LD input.
  */
object CreateValueRequestV2 extends KnoraJsonLDRequestReaderV2[CreateValueRequestV2] {
    override def fromJsonLD(jsonLDDocument: JsonLDDocument, apiRequestID: UUID, requestingUser: UserADM): CreateValueRequestV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val resourceIri = jsonLDDocument.requireIriInObject(JsonLDConstants.ID, stringFormatter.toSmartIriWithErr)

        if (!resourceIri.isKnoraDataIri) {
            throw BadRequestException(s"Invalid resource IRI: $resourceIri")
        }

        val resourceClassIri = jsonLDDocument.requireIriInObject(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)

        if (!(resourceClassIri.isKnoraEntityIri && resourceClassIri.getOntologySchema.contains(ApiV2WithValueObjects))) {
            throw BadRequestException(s"Invalid resource class IRI: $resourceClassIri")
        }

        val resourceProps: Map[IRI, JsonLDValue] = jsonLDDocument.body.value - JsonLDConstants.ID - JsonLDConstants.TYPE

        if (resourceProps.isEmpty) {
            throw BadRequestException("No value submitted")
        }

        if (resourceProps.size > 1) {
            throw BadRequestException(s"Only one value can be created per request using this route")
        }

        val createValue: CreateValueV2 = resourceProps.head match {
            case (key: IRI, jsonLDValue: JsonLDValue) =>
                val propertyIri = key.toSmartIriWithErr(throw BadRequestException(s"Invalid property IRI: $key"))

                if (!(propertyIri.isKnoraEntityIri && propertyIri.getOntologySchema.contains(ApiV2WithValueObjects))) {
                    throw BadRequestException(s"Invalid property IRI: $propertyIri")
                }

                val valueContent: ValueContentV2 = jsonLDValue match {
                    case jsonLDObject: JsonLDObject => ValueContentV2.fromJsonLDObject(jsonLDObject)
                    case _ => throw BadRequestException(s"Invalid value for $propertyIri")
                }

                CreateValueV2(
                    resourceIri = resourceIri.toString,
                    propertyIri = propertyIri,
                    valueContent = valueContent
                )
        }

        CreateValueRequestV2(
            createValue = createValue,
            apiRequestID = apiRequestID,
            requestingUser = requestingUser
        )
    }
}

/**
  * Represents a successful response to a [[CreateValueRequestV2]].
  *
  * @param valueIri the IRI of the value that was created.
  */
case class CreateValueResponseV2(valueIri: IRI) extends KnoraResponseV2 {
    override def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        if (targetSchema != ApiV2WithValueObjects) {
            throw AssertionException(s"CreateValueResponseV2 can only be returned in the complex schema")
        }

        JsonLDDocument(
            body = JsonLDObject(
                Map(
                    JsonLDConstants.ID -> JsonLDUtil.iriToJsonLDObject(valueIri)
                )
            )
        )
    }
}

/**
  * The value of a Knora property in the context of some particular input or output operation.
  * Any implementation of `IOValueV2` is an API operation-specific wrapper of a `ValueContentV2`.
  */
sealed trait IOValueV2

/**
  * The value of a Knora property read back from the triplestore.
  *
  * @param valueIri          the IRI of the value.
  * @param attachedToUser    the user that created the value.
  * @param attachedToProject the project that the value's resource belongs to.
  * @param permissions       the permissions that the value grants to user groups.
  * @param valueContent      the content of the value.
  */
case class ReadValueV2(valueIri: IRI,
                       attachedToUser: IRI,
                       attachedToProject: IRI,
                       permissions: String,
                       valueCreationDate: Instant,
                       valueContent: ValueContentV2,
                       valueHasRefCount: Option[Int] = None) extends IOValueV2 with KnoraReadV2[ReadValueV2] {
    /**
      * Converts this value to the specified ontology schema.
      *
      * @param targetSchema the target schema.
      */
    override def toOntologySchema(targetSchema: ApiV2Schema): ReadValueV2 = {
        copy(valueContent = valueContent.toOntologySchema(targetSchema))
    }

    /**
      * Converts this value to JSON-LD.
      *
      * @param targetSchema the target schema.
      * @param settings     the application settings.
      * @return a JSON-LD representation of this value.
      */
    def toJsonLD(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        val valueContentAsJsonLD = valueContent.toJsonLDValue(targetSchema, settings)

        // In the complex schema, add the value's IRI and type to the JSON-LD object that represents it.
        targetSchema match {
            case ApiV2WithValueObjects =>
                // In the complex schema, the value must be represented as a JSON-LD object.
                valueContentAsJsonLD match {
                    case jsonLDObject: JsonLDObject =>
                        // Add the value's IRI and type.
                        JsonLDObject(
                            jsonLDObject.value +
                                (JsonLDConstants.ID -> JsonLDString(valueIri)) +
                                (JsonLDConstants.TYPE -> JsonLDString(valueContent.valueType.toString))
                        )

                    case other =>
                        throw AssertionException(s"Expected value $valueIri to be a represented as a JSON-LD object in the complex schema, but found $other")
                }

            case ApiV2Simple => valueContentAsJsonLD
        }
    }
}

/**
  * The value of a Knora property sent to Knora to be created.
  *
  * @param resourceIri  the resource the new value should be attached to.
  * @param propertyIri  the property of the new value.
  * @param valueContent the content of the new value.
  */
case class CreateValueV2(resourceIri: IRI, propertyIri: SmartIri, valueContent: ValueContentV2) extends IOValueV2

/**
  * A new version of a value of a Knora property to be created.
  *
  * @param valueIri     the IRI of the value to be updated.
  * @param valueContent the content of the new version of the value.
  */
case class UpdateValueV2(valueIri: IRI, valueContent: ValueContentV2) extends IOValueV2

/**
  * The IRI and content of a new value or value version whose existence in the triplestore needs to be verified.
  *
  * @param newValueIri the IRI that was assigned to the new value.
  * @param value the content of the new value.
  */
case class UnverifiedValueV2(newValueIri: IRI, value: ValueContentV2)

/**
  * The content of the value of a Knora property.
  */
sealed trait ValueContentV2 extends KnoraContentV2[ValueContentV2] {
    protected implicit def stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
      * The IRI of the value type.
      */
    def valueType: SmartIri

    /**
      * The string representation of this `ValueContentV2`.
      */
    def valueHasString: String

    /**
      * A comment on this `ValueContentV2`, if any.
      */
    def comment: Option[String]

    /**
      * Converts this value to the specified ontology schema.
      *
      * @param targetSchema the target schema.
      */
    def toOntologySchema(targetSchema: OntologySchema): ValueContentV2

    /**
      * A representation of the `ValueContentV2` as a [[JsonLDValue]].
      *
      * @param targetSchema the API schema to be used.
      * @param settings     the configuration options.
      * @return a [[JsonLDValue]] that can be used to generate JSON-LD representing this value.
      */
    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue

    /**
      * Undoes the SPARQL-escaping of strings in this [[ValueContentV2]].
      *
      * @return the same [[ValueContentV2]] with its strings unescaped.
      */
    def unescape: ValueContentV2

    /**
      * Returns `true` if creating this [[ValueContentV2]] as a new value would duplicate the specified other value.
      * This means that if resource `R` has property `P` with value `V1`, and `V1` would dupliate `V2`, the API server
      * should not add another instance of property `P` with value `V2`. It does not necessarily mean that `V1 == V2`.
      *
      * @param that a [[ValueContentV2]] in the same resource, as read from the triplestore.
      * @return `true` if `other` would duplicate `this`.
      */
    def wouldDuplicateOtherValue(that: ValueContentV2): Boolean

    /**
      * Returns `true` if this [[UpdateValueV2]] would be redundant as a new version of an existing value. This means
      * that if resource `R` has property `P` with value `V1`, and `V2` would duplicate `V1`, we should not add `V2`
      * as a new version of `V1`. It does not necessarily mean that `V1 == V2`.
      *
      * @param currentVersion the current version of the value, as read from the triplestore.
      * @return `true` if this [[UpdateValueV2]] would duplicate `currentVersion`.
      */
    def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean
}

/**
  * A trait for objects that can convert JSON-LD objects into value content objects (subclasses of [[ValueContentV2]]).
  *
  * @tparam C a subclass of [[ValueContentV2]].
  */
trait ValueContentReaderV2[C <: ValueContentV2] {
    /**
      * Converts a JSON-LD object to a subclass of [[ValueContentV2]].
      *
      * @param jsonLDObject the JSON-LD object.
      * @return a subclass of [[ValueContentV2]].
      */
    def fromJsonLDObject(jsonLDObject: JsonLDObject): C
}

/**
  * Generates instances of value content classes (subclasses of [[ValueContentV2]]) from JSON-LD input.
  */
object ValueContentV2 extends ValueContentReaderV2[ValueContentV2] {
    def fromJsonLDObject(jsonLDObject: JsonLDObject): ValueContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val valueType: SmartIri = jsonLDObject.requireIriInObject(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)

        valueType.toString match {
            case OntologyConstants.KnoraApiV2WithValueObjects.TextValue => TextValueContentV2.fromJsonLDObject(jsonLDObject: JsonLDObject)
            case OntologyConstants.KnoraApiV2WithValueObjects.IntValue => IntegerValueContentV2.fromJsonLDObject(jsonLDObject: JsonLDObject)
            case OntologyConstants.KnoraApiV2WithValueObjects.DecimalValue => DecimalValueContentV2.fromJsonLDObject(jsonLDObject: JsonLDObject)
            case OntologyConstants.KnoraApiV2WithValueObjects.BooleanValue => BooleanValueContentV2.fromJsonLDObject(jsonLDObject: JsonLDObject)
            case OntologyConstants.KnoraApiV2WithValueObjects.DateValue => DateValueContentV2.fromJsonLDObject(jsonLDObject: JsonLDObject)
            case OntologyConstants.KnoraApiV2WithValueObjects.GeomValue => GeomValueContentV2.fromJsonLDObject(jsonLDObject: JsonLDObject)
            case OntologyConstants.KnoraApiV2WithValueObjects.IntervalValue => IntervalValueContentV2.fromJsonLDObject(jsonLDObject: JsonLDObject)
            case OntologyConstants.KnoraApiV2WithValueObjects.LinkValue => LinkValueContentV2.fromJsonLDObject(jsonLDObject: JsonLDObject)
            case OntologyConstants.KnoraApiV2WithValueObjects.ListValue => HierarchicalListValueContentV2.fromJsonLDObject(jsonLDObject: JsonLDObject)
            case OntologyConstants.KnoraApiV2WithValueObjects.UriValue => UriValueContentV2.fromJsonLDObject(jsonLDObject: JsonLDObject)
            case OntologyConstants.KnoraApiV2WithValueObjects.GeonameValue => GeonameValueContentV2.fromJsonLDObject(jsonLDObject: JsonLDObject)
            case OntologyConstants.KnoraApiV2WithValueObjects.ColorValue => ColorValueContentV2.fromJsonLDObject(jsonLDObject: JsonLDObject)
            case other => throw NotImplementedException(s"Parsing of JSON-LD value type not implemented: $other")
        }
    }
}

/**
  * Represents a Knora date value.
  *
  * @param valueHasStartJDN       the start of the date as JDN.
  * @param valueHasEndJDN         the end of the date as JDN.
  * @param valueHasStartPrecision the precision of the start date.
  * @param valueHasEndPrecision   the precision of the end date.
  * @param valueHasCalendar       the calendar of the date.
  * @param comment                a comment on this `DateValueContentV2`, if any.
  */
case class DateValueContentV2(valueType: SmartIri,
                              valueHasStartJDN: Int,
                              valueHasEndJDN: Int,
                              valueHasStartPrecision: KnoraPrecisionV1.Value,
                              valueHasEndPrecision: KnoraPrecisionV1.Value,
                              valueHasCalendar: KnoraCalendarV1.Value,
                              comment: Option[String]) extends ValueContentV2 {
    // We compute valueHasString instead of taking it from the triplestore, because the
    // string literal in the triplestore isn't in API v2 format.
    override lazy val valueHasString: String = {
        val startDate = DateUtilV2.jdnToDateYearMonthDay(
            julianDayNumber = valueHasStartJDN,
            precision = valueHasStartPrecision,
            calendar = valueHasCalendar
        )

        val endDate = DateUtilV2.jdnToDateYearMonthDay(
            julianDayNumber = valueHasEndJDN,
            precision = valueHasEndPrecision,
            calendar = valueHasCalendar
        )

        DateUtilV2.dateRangeToString(
            startDate = startDate,
            endDate = endDate,
            calendar = valueHasCalendar
        )
    }

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        copy(
            valueType = valueType.toOntologySchema(targetSchema)
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple =>
                JsonLDUtil.datatypeValueToJsonLDObject(
                    value = valueHasString,
                    datatype = OntologyConstants.KnoraApiV2Simple.Date
                )

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(
                    OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> JsonLDString(valueHasString),
                    OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar -> JsonLDString(valueHasCalendar.toString)
                ) ++ toComplexDateValueAssertions)
        }
    }

    /**
      * Create knora-api assertions.
      *
      * @return a Map of [[ApiV2WithValueObjects]] value properties to numbers (year, month, day) representing the date value.
      */
    def toComplexDateValueAssertions: Map[IRI, JsonLDValue] = {

        val startDateConversion = DateUtilV2.jdnToDateYearMonthDay(valueHasStartJDN, valueHasStartPrecision, valueHasCalendar)

        val startDateAssertions = startDateConversion.toStartDateAssertions.map {
            case (k: IRI, v: Int) => (k, JsonLDInt(v))

        } ++ startDateConversion.toStartEraAssertion.map {

            case (k: IRI, v: String) => (k, JsonLDString(v))
        }
        val endDateConversion = DateUtilV2.jdnToDateYearMonthDay(valueHasEndJDN, valueHasEndPrecision, valueHasCalendar)

        val endDateAssertions = endDateConversion.toEndDateAssertions.map {
            case (k: IRI, v: Int) => (k, JsonLDInt(v))

        } ++ endDateConversion.toEndEraAssertion.map {

            case (k: IRI, v: String) => (k, JsonLDString(v))
        }

        startDateAssertions ++ endDateAssertions
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatDateValue: DateValueContentV2 =>
                valueHasStartJDN == thatDateValue.valueHasStartJDN &&
                    valueHasEndJDN == thatDateValue.valueHasEndJDN &&
                    valueHasStartPrecision == thatDateValue.valueHasStartPrecision &&
                    valueHasEndPrecision == thatDateValue.valueHasEndPrecision &&
                    valueHasCalendar == thatDateValue.valueHasCalendar

            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
        }
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = wouldDuplicateOtherValue(currentVersion)
}

/**
  * Constructs [[DateValueContentV2]] objects based on JSON-LD input.
  */
object DateValueContentV2 extends ValueContentReaderV2[DateValueContentV2] {
    override def fromJsonLDObject(jsonLDObject: JsonLDObject): DateValueContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        /**
          * Given an optional month and an optional day of the month, determines the precision of a date.
          *
          * @param maybeMonth an optional month.
          * @param maybeDay   an optional day of the month.
          * @return the precision of the date.
          */
        def getPrecision(maybeMonth: Option[Int], maybeDay: Option[Int]): KnoraPrecisionV1.Value = {
            (maybeMonth, maybeMonth) match {
                case (Some(_), Some(_)) => KnoraPrecisionV1.DAY
                case (Some(_), None) => KnoraPrecisionV1.MONTH
                case (None, None) => KnoraPrecisionV1.YEAR
                case (None, Some(day)) => throw BadRequestException(s"Invalid date: day $day is given but month is missing")
            }
        }

        val calendar: KnoraCalendarV1.Value = jsonLDObject.requireString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar, stringFormatter.validateCalendar)

        val dateValueHasStartYear: Int = jsonLDObject.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear).value
        val maybeDateValueHasStartMonth: Option[Int] = jsonLDObject.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth).map(_.value)
        val maybeDateValueHasStartDay: Option[Int] = jsonLDObject.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay).map(_.value)
        val maybeDateValueHasStartEra: Option[KnoraEraV2.Value] = jsonLDObject.maybeString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra, stringFormatter.validateEra)
        val startPrecision: KnoraPrecisionV1.Value = getPrecision(maybeDateValueHasStartMonth, maybeDateValueHasStartDay)

        val dateValueHasEndYear: Int = jsonLDObject.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear).value
        val maybeDateValueHasEndMonth: Option[Int] = jsonLDObject.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth).map(_.value)
        val maybeDateValueHasEndDay: Option[Int] = jsonLDObject.maybeInt(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay).map(_.value)
        val maybeDateValueHasEndEra: Option[KnoraEraV2.Value] = jsonLDObject.maybeString(OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra, stringFormatter.validateEra)
        val endPrecision: KnoraPrecisionV1.Value = getPrecision(maybeDateValueHasEndMonth, maybeDateValueHasEndDay)

        val startDate = DateYearMonthDay(
            year = dateValueHasStartYear,
            month = maybeDateValueHasStartMonth.getOrElse(1),
            day = maybeDateValueHasStartDay.getOrElse(1),
            era = maybeDateValueHasStartEra.getOrElse(KnoraEraV2.CE),
            precision = startPrecision
        )

        val endDate = DateYearMonthDay(
            year = dateValueHasEndYear,
            month = maybeDateValueHasEndMonth.getOrElse(12),
            day = maybeDateValueHasEndDay.getOrElse(31),
            era = maybeDateValueHasEndEra.getOrElse(KnoraEraV2.CE),
            precision = endPrecision
        )

        // TODO: convert the date range to start and end JDNs without first converting it to a string.

        val dateRangeStr = DateUtilV2.dateRangeToString(
            startDate = startDate,
            endDate = endDate,
            calendar = calendar
        )

        val julianDateRange: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(dateRangeStr)

        val maybeComment: Option[String] = jsonLDObject.maybeString(OntologyConstants.KnoraApiV2WithValueObjects.ValueHasComment, stringFormatter.toSparqlEncodedString)

        DateValueContentV2(
            valueType = OntologyConstants.KnoraBase.DateValue.toSmartIri,
            valueHasStartJDN = julianDateRange.dateval1,
            valueHasEndJDN = julianDateRange.dateval2,
            valueHasStartPrecision = startPrecision,
            valueHasEndPrecision = endPrecision,
            valueHasCalendar = calendar,
            comment = maybeComment
        )
    }
}

/**
  * Represents a [[StandoffTagV2]] for a standoff tag of a certain type (standoff tag class) that is about to be created in the triplestore.
  *
  * @param standoffNode           the standoff node to be created.
  * @param standoffTagInstanceIri the standoff node's IRI.
  * @param startParentIri         the IRI of the parent of the start tag.
  * @param endParentIri           the IRI of the parent of the end tag, if any.
  */
case class CreateStandoffTagV2InTriplestore(standoffNode: StandoffTagV2, standoffTagInstanceIri: IRI, startParentIri: Option[IRI] = None, endParentIri: Option[IRI] = None)


/**
  * Represents a Knora text value.
  *
  * @param valueHasString the string representation of the text (without markup).
  * @param standoffAndMapping       a [[StandoffAndMapping]], if any.
  * @param comment        a comment on this `TextValueContentV2`, if any.
  */
case class TextValueContentV2(valueType: SmartIri,
                              valueHasString: String,
                              valueHasLanguage: Option[String] = None,
                              standoffAndMapping: Option[StandoffAndMapping],
                              comment: Option[String]) extends ValueContentV2 {
    private val knoraIdUtil = new KnoraIdUtil

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        copy(
            valueType = valueType.toOntologySchema(targetSchema)
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple =>
                valueHasLanguage match {
                    case Some(lang) =>
                        // In the simple schema, if this text value specifies a language, return it using a JSON-LD
                        // @language key as per <https://json-ld.org/spec/latest/json-ld/#string-internationalization>.
                        JsonLDUtil.objectWithLangToJsonLDObject(
                            obj = valueHasString,
                            lang = lang
                        )

                    case None => JsonLDString(valueHasString)
                }

            case ApiV2WithValueObjects =>
                val objectMap: Map[IRI, JsonLDValue] = if (standoffAndMapping.nonEmpty) {

                    val xmlFromStandoff = StandoffTagUtilV2.convertStandoffTagV2ToXML(valueHasString, standoffAndMapping.get.standoff, standoffAndMapping.get.mapping)

                    // check if there is an XSL transformation
                    if (standoffAndMapping.get.XSLT.nonEmpty) {

                        val xmlTransformed: String = XMLUtil.applyXSLTransformation(xmlFromStandoff, standoffAndMapping.get.XSLT.get)

                        // the xml was converted to HTML
                        Map(OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsHtml -> JsonLDString(xmlTransformed))
                    } else {
                        // xml is returned
                        Map(
                            OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsXml -> JsonLDString(xmlFromStandoff),
                            OntologyConstants.KnoraApiV2WithValueObjects.TextValueHasMapping -> JsonLDString(standoffAndMapping.get.mappingIri)
                        )
                    }

                } else {
                    // no markup given
                    Map(OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> JsonLDString(valueHasString))
                }

                // In the complex schema, if this text value specifies a language, return it using the predicate
                // knora-api:textValueHasLanguage.
                val objectMapWithLanguage: Map[IRI, JsonLDValue] = valueHasLanguage match {
                    case Some(lang) =>
                        objectMap + (OntologyConstants.KnoraApiV2WithValueObjects.TextValueHasLanguage -> JsonLDString(lang))
                    case None =>
                        objectMap
                }

                JsonLDObject(objectMapWithLanguage)
        }
    }


    /**
      * A convenience method that creates an IRI for each [[StandoffTagV2]] and resolves internal references to standoff node Iris.
      *
      * @return a list of [[CreateStandoffTagV2InTriplestore]] each representing a [[StandoffTagV2]] object
      *         along with is standoff tag class and IRI that is going to identify it in the triplestore.
      */
    def prepareForSparqlInsert(valueIri: IRI): Seq[CreateStandoffTagV2InTriplestore] = {

        standoffAndMapping match {
            case Some(definedStandoffAndMapping) =>
                // create an IRI for each standoff tag
                // internal references to XML ids are not resolved yet
                val standoffTagsWithOriginalXMLIDs: Seq[CreateStandoffTagV2InTriplestore] = definedStandoffAndMapping.standoff.map {
                    case (standoffNode: StandoffTagV2) =>
                        CreateStandoffTagV2InTriplestore(
                            standoffNode = standoffNode,
                            standoffTagInstanceIri = knoraIdUtil.makeRandomStandoffTagIri(valueIri) // generate IRI for new standoff node
                        )
                }

                // collect all the standoff tags that contain XML ids and
                // map the XML ids to standoff node Iris
                val iDsToStandoffNodeIris: Map[IRI, IRI] = standoffTagsWithOriginalXMLIDs.filter {
                    (standoffTag: CreateStandoffTagV2InTriplestore) =>
                        // filter those tags out that have an XML id
                        standoffTag.standoffNode.originalXMLID.isDefined
                }.map {
                    (standoffTagWithID: CreateStandoffTagV2InTriplestore) =>
                        // return the XML id as a key and the standoff IRI as the value
                        standoffTagWithID.standoffNode.originalXMLID.get -> standoffTagWithID.standoffTagInstanceIri
                }.toMap

                // Map the start index of each tag to its IRI, so we can resolve references to parent tags as references to
                // tag IRIs. We only care about start indexes here, because only hierarchical tags can be parents, and
                // hierarchical tags don't have end indexes.
                val startIndexesToStandoffNodeIris: Map[Int, IRI] = standoffTagsWithOriginalXMLIDs.map {
                    tagWithIndex => tagWithIndex.standoffNode.startIndex -> tagWithIndex.standoffTagInstanceIri
                }.toMap

                // resolve the original XML ids to standoff Iris every the `StandoffTagInternalReferenceAttributeV1`
                val standoffTagsWithNodeReferences: Seq[CreateStandoffTagV2InTriplestore] = standoffTagsWithOriginalXMLIDs.map {
                    (standoffTag: CreateStandoffTagV2InTriplestore) =>

                        // resolve original XML ids to standoff node Iris for `StandoffTagInternalReferenceAttributeV1`
                        val attributesWithStandoffNodeIriReferences: Seq[StandoffTagAttributeV2] = standoffTag.standoffNode.attributes.map {
                            (attributeWithOriginalXMLID: StandoffTagAttributeV2) =>
                                attributeWithOriginalXMLID match {
                                    case refAttr: StandoffTagInternalReferenceAttributeV2 =>
                                        // resolve the XML id to the corresponding standoff node IRI
                                        refAttr.copy(value = iDsToStandoffNodeIris(refAttr.value))
                                    case attr => attr
                                }
                        }

                        val startParentIndex: Option[Int] = standoffTag.standoffNode.startParentIndex
                        val endParentIndex: Option[Int] = standoffTag.standoffNode.endParentIndex

                        // return standoff tag with updated attributes
                        standoffTag.copy(
                            standoffNode = standoffTag.standoffNode.copy(attributes = attributesWithStandoffNodeIriReferences),
                            startParentIri = startParentIndex.map(parentIndex => startIndexesToStandoffNodeIris(parentIndex)), // If there's a start parent index, get its IRI, otherwise None
                            endParentIri = endParentIndex.map(parentIndex => startIndexesToStandoffNodeIris(parentIndex)) // If there's an end parent index, get its IRI, otherwise None
                        )
                }

                standoffTagsWithNodeReferences

            case None => Seq.empty[CreateStandoffTagV2InTriplestore]

        }

    }

    override def unescape: ValueContentV2 = {
        copy(
            valueHasString = stringFormatter.fromSparqlEncodedString(valueHasString),
            comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr))
        )
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        // It doesn't make sense for a resource to have two different text values associated with the same property,
        // containing the same text but different markup.
        that match {
            case thatTextValue: TextValueContentV2 =>
                // unescape valueHasString since it contains escaped sequences while the string returned by the triplestore does not
                stringFormatter.fromSparqlEncodedString(valueHasString) == thatTextValue.valueHasString

            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
        }
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
        // It's OK to add a new version of a text value as long as something has been changed in it, even if it's only the markup.
        currentVersion match {
            case thatTextValue: TextValueContentV2 =>
                // unescape valueHasString since it contains escaped sequences while the string returned by the triplestore does not
                val valueHasStringIdentical: Boolean = stringFormatter.fromSparqlEncodedString(valueHasString) == thatTextValue.valueHasString

                // compare standoff nodes (sort them first, since the order does not make any difference) and the XML-to-standoff mapping IRI
                val (standoffIdentical, sameMapping): (Boolean, Boolean) = (standoffAndMapping, thatTextValue.standoffAndMapping) match {
                    case (Some(thisStandoffAndMapping), Some(thatStandoffAndMapping)) =>
                        val thisStandoffSorted: Seq[StandoffTagV2] = thisStandoffAndMapping.standoff.sortBy(standoffNode => (standoffNode.standoffTagClassIri, standoffNode.startPosition))
                        val thatStandoffSorted: Seq[StandoffTagV2] = thatStandoffAndMapping.standoff.sortBy(standoffNode => (standoffNode.standoffTagClassIri, standoffNode.startPosition))

                        val sameStandoff: Boolean = thisStandoffSorted.size == thatStandoffSorted.size && thisStandoffSorted.zip(thatStandoffSorted).forall {
                            case (thisStandoffTag, thatStandoffTag) => thisStandoffTag.equalsWithoutUuid(thatStandoffTag)
                        }

                        (sameStandoff, thisStandoffAndMapping.mappingIri == thatStandoffAndMapping.mappingIri)

                    case _ => (false, false)
                }

                valueHasStringIdentical && standoffIdentical && sameMapping

            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
        }
    }
}

/**
  * Constructs [[TextValueContentV2]] objects based on JSON-LD input.
  */
object TextValueContentV2 extends ValueContentReaderV2[TextValueContentV2] {
    override def fromJsonLDObject(jsonLDObject: JsonLDObject): TextValueContentV2 = {
        // TODO: figure out how to do this.
        throw NotImplementedException(s"Reading of ${getClass.getName} from JSON-LD input not implemented")
    }
}

/**
  * Represents standoff and the corresponding mapping.
  * May include an XSL transformation.
  *
  * @param standoff   a sequence of [[StandoffTagV2]].
  * @param mappingIri the IRI of the mapping
  * @param mapping    a mapping between XML and standoff.
  * @param XSLT       an XSL transformation.
  */
case class StandoffAndMapping(standoff: Seq[StandoffTagV2], mappingIri: IRI, mapping: MappingXMLtoStandoff, XSLT: Option[String])

/**
  * Represents a Knora integer value.
  *
  * @param valueHasString  the string representation of the integer.
  * @param valueHasInteger the integer value.
  * @param comment         a comment on this `IntegerValueContentV2`, if any.
  */
case class IntegerValueContentV2(valueType: SmartIri,
                                 valueHasString: String,
                                 valueHasInteger: Int,
                                 comment: Option[String]) extends ValueContentV2 {

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        copy(
            valueType = valueType.toOntologySchema(targetSchema)
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple => JsonLDInt(valueHasInteger)

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.IntValueAsInt -> JsonLDInt(valueHasInteger)))

        }
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatIntegerValue: IntegerValueContentV2 => valueHasInteger == thatIntegerValue.valueHasInteger
            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
        }
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = wouldDuplicateOtherValue(currentVersion)
}

/**
  * Constructs [[IntegerValueContentV2]] objects based on JSON-LD input.
  */
object IntegerValueContentV2 extends ValueContentReaderV2[IntegerValueContentV2] {
    override def fromJsonLDObject(jsonLDObject: JsonLDObject): IntegerValueContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val intValueAsInt: Int = jsonLDObject.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.IntValueAsInt).value
        val maybeComment: Option[String] = jsonLDObject.maybeString(OntologyConstants.KnoraApiV2WithValueObjects.ValueHasComment, stringFormatter.toSparqlEncodedString)

        IntegerValueContentV2(
            valueType = OntologyConstants.KnoraBase.IntValue.toSmartIri,
            valueHasString = intValueAsInt.toString,
            valueHasInteger = intValueAsInt,
            comment = maybeComment
        )
    }
}

/**
  * Represents a Knora decimal value.
  *
  * @param valueHasString  the string representation of the decimal.
  * @param valueHasDecimal the decimal value.
  * @param comment         a comment on this `DecimalValueContentV2`, if any.
  */
case class DecimalValueContentV2(valueType: SmartIri,
                                 valueHasString: String,
                                 valueHasDecimal: BigDecimal,
                                 comment: Option[String]) extends ValueContentV2 {

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        copy(
            valueType = valueType.toOntologySchema(targetSchema)
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple =>
                JsonLDUtil.datatypeValueToJsonLDObject(
                    value = valueHasDecimal.toString,
                    datatype = OntologyConstants.Xsd.Decimal
                )

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.DecimalValueAsDecimal -> JsonLDString(valueHasDecimal.toString)))
        }
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatDecimalValue: DecimalValueContentV2 => valueHasDecimal == thatDecimalValue.valueHasDecimal
            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
        }
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = wouldDuplicateOtherValue(currentVersion)
}

/**
  * Constructs [[DecimalValueContentV2]] objects based on JSON-LD input.
  */
object DecimalValueContentV2 extends ValueContentReaderV2[DecimalValueContentV2] {
    override def fromJsonLDObject(jsonLDObject: JsonLDObject): DecimalValueContentV2 = {
        // TODO
        throw NotImplementedException(s"Reading of ${getClass.getName} from JSON-LD input not implemented")
    }
}

/**
  * Represents a Boolean value.
  *
  * @param valueHasString  the string representation of the Boolean.
  * @param valueHasBoolean the Boolean value.
  * @param comment         a comment on this `BooleanValueContentV2`, if any.
  */
case class BooleanValueContentV2(valueType: SmartIri,
                                 valueHasString: String,
                                 valueHasBoolean: Boolean,
                                 comment: Option[String]) extends ValueContentV2 {

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        copy(
            valueType = valueType.toOntologySchema(targetSchema)
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple => JsonLDBoolean(valueHasBoolean)

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.BooleanValueAsBoolean -> JsonLDBoolean(valueHasBoolean)))
        }
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        // Always returns true, because it doesn't make sense to have two instances of the same boolean property.
        true
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = {
        currentVersion match {
            case thatBooleanValue: BooleanValueContentV2 => valueHasBoolean == thatBooleanValue.valueHasBoolean
            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${currentVersion.valueType}>")
        }
    }
}

/**
  * Constructs [[BooleanValueContentV2]] objects based on JSON-LD input.
  */
object BooleanValueContentV2 extends ValueContentReaderV2[BooleanValueContentV2] {
    override def fromJsonLDObject(jsonLDObject: JsonLDObject): BooleanValueContentV2 = {
        // TODO
        throw NotImplementedException(s"Reading of ${getClass.getName} from JSON-LD input not implemented")
    }
}

/**
  * Represents a Knora geometry value (a 2D-shape).
  *
  * @param valueHasString   a stringified JSON representing a 2D-geometrical shape.
  * @param valueHasGeometry a stringified JSON representing a 2D-geometrical shape.
  * @param comment          a comment on this `GeomValueContentV2`, if any.
  */
case class GeomValueContentV2(valueType: SmartIri,
                              valueHasString: String,
                              valueHasGeometry: String,
                              comment: Option[String]) extends ValueContentV2 {

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        copy(
            valueType = valueType.toOntologySchema(targetSchema)
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple =>
                JsonLDUtil.datatypeValueToJsonLDObject(
                    value = valueHasGeometry,
                    datatype = OntologyConstants.KnoraApiV2Simple.Geom
                )

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.GeometryValueAsGeometry -> JsonLDString(valueHasGeometry)))
        }
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatGeomValue: GeomValueContentV2 => valueHasGeometry == thatGeomValue.valueHasGeometry
            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
        }
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = wouldDuplicateOtherValue(currentVersion)
}

/**
  * Constructs [[GeomValueContentV2]] objects based on JSON-LD input.
  */
object GeomValueContentV2 extends ValueContentReaderV2[GeomValueContentV2] {
    override def fromJsonLDObject(jsonLDObject: JsonLDObject): GeomValueContentV2 = {
        // TODO
        throw NotImplementedException(s"Reading of ${getClass.getName} from JSON-LD input not implemented")
    }
}


/**
  * Represents a Knora time interval value.
  *
  * @param valueHasString        the string representation of the time interval.
  * @param valueHasIntervalStart the start of the time interval.
  * @param valueHasIntervalEnd   the end of the time interval.
  * @param comment               a comment on this `IntervalValueContentV2`, if any.
  */
case class IntervalValueContentV2(valueType: SmartIri,
                                  valueHasString: String,
                                  valueHasIntervalStart: BigDecimal,
                                  valueHasIntervalEnd: BigDecimal,
                                  comment: Option[String]) extends ValueContentV2 {

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        copy(
            valueType = valueType.toOntologySchema(targetSchema)
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple =>
                JsonLDUtil.datatypeValueToJsonLDObject(
                    value = valueHasString,
                    datatype = OntologyConstants.KnoraApiV2Simple.Interval
                )

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(
                    OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasStart -> JsonLDString(valueHasIntervalStart.toString),
                    OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasEnd -> JsonLDString(valueHasIntervalEnd.toString)
                ))
        }
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatIntervalValueContent: IntervalValueContentV2 =>
                valueHasIntervalStart == thatIntervalValueContent.valueHasIntervalStart &&
                    valueHasIntervalEnd == thatIntervalValueContent.valueHasIntervalEnd

            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
        }
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = wouldDuplicateOtherValue(currentVersion)
}

/**
  * Constructs [[IntervalValueContentV2]] objects based on JSON-LD input.
  */
object IntervalValueContentV2 extends ValueContentReaderV2[IntervalValueContentV2] {
    override def fromJsonLDObject(jsonLDObject: JsonLDObject): IntervalValueContentV2 = {
        // TODO
        throw NotImplementedException(s"Reading of ${getClass.getName} from JSON-LD input not implemented")
    }
}

/**
  * Represents a value pointing to a Knora hierarchical list node.
  *
  * @param valueHasString   the string representation of the hierarchical list node value.
  * @param valueHasListNode the IRI of the hierarchical list node pointed to.
  * @param listNodeLabel    the label of the hierarchical list node pointed to.
  * @param comment          a comment on this `HierarchicalListValueContentV2`, if any.
  */
case class HierarchicalListValueContentV2(valueType: SmartIri,
                                          valueHasString: String,
                                          valueHasListNode: IRI,
                                          listNodeLabel: String,
                                          comment: Option[String],
                                          ontologySchema: OntologySchema) extends ValueContentV2 {

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        copy(
            valueType = valueType.toOntologySchema(targetSchema),
            ontologySchema = targetSchema
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple => JsonLDString(listNodeLabel)

            case ApiV2WithValueObjects =>
                JsonLDObject(
                    Map(
                        OntologyConstants.KnoraApiV2WithValueObjects.ListValueAsListNode -> JsonLDUtil.iriToJsonLDObject(valueHasListNode),
                        OntologyConstants.KnoraApiV2WithValueObjects.ListValueAsListNodeLabel -> JsonLDString(listNodeLabel)
                    )
                )
        }
    }

    override def unescape: ValueContentV2 = {
        copy(
            listNodeLabel = stringFormatter.fromSparqlEncodedString(listNodeLabel),
            comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr))
        )
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatListContent: HierarchicalListValueContentV2 => valueHasListNode == thatListContent.valueHasListNode
            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
        }
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = wouldDuplicateOtherValue(currentVersion)
}

/**
  * Constructs [[HierarchicalListValueContentV2]] objects based on JSON-LD input.
  */
object HierarchicalListValueContentV2 extends ValueContentReaderV2[HierarchicalListValueContentV2] {
    override def fromJsonLDObject(jsonLDObject: JsonLDObject): HierarchicalListValueContentV2 = {
        // TODO
        throw NotImplementedException(s"Reading of ${getClass.getName} from JSON-LD input not implemented")
    }
}


/**
  * Represents a Knora color value.
  *
  * @param valueHasString the string representation of the color value.
  * @param valueHasColor  a hexadecimal string containing the RGB color value
  * @param comment        a comment on this `ColorValueContentV2`, if any.
  */
case class ColorValueContentV2(valueType: SmartIri,
                               valueHasString: String,
                               valueHasColor: String,
                               comment: Option[String]) extends ValueContentV2 {

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        copy(
            valueType = valueType.toOntologySchema(targetSchema)
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple =>
                JsonLDUtil.datatypeValueToJsonLDObject(
                    value = valueHasColor,
                    datatype = OntologyConstants.KnoraApiV2Simple.Color
                )

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.ColorValueAsColor -> JsonLDString(valueHasColor)))
        }
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatColorContent: ColorValueContentV2 => valueHasColor == thatColorContent.valueHasColor
            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
        }
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = wouldDuplicateOtherValue(currentVersion)
}

/**
  * Constructs [[ColorValueContentV2]] objects based on JSON-LD input.
  */
object ColorValueContentV2 extends ValueContentReaderV2[ColorValueContentV2] {
    override def fromJsonLDObject(jsonLDObject: JsonLDObject): ColorValueContentV2 = {
        // TODO
        throw NotImplementedException(s"Reading of ${getClass.getName} from JSON-LD input not implemented")
    }
}

/**
  * Represents a Knora URI value.
  *
  * @param valueHasString the string representation of the URI value.
  * @param valueHasUri    the URI value.
  * @param comment        a comment on this `UriValueContentV2`, if any.
  */
case class UriValueContentV2(valueType: SmartIri,
                             valueHasString: String,
                             valueHasUri: String,
                             comment: Option[String]) extends ValueContentV2 {

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        copy(
            valueType = valueType.toOntologySchema(targetSchema)
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple =>
                JsonLDUtil.datatypeValueToJsonLDObject(
                    value = valueHasUri,
                    datatype = OntologyConstants.Xsd.Uri
                )

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.UriValueAsUri -> JsonLDString(valueHasUri)))
        }
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatUriContent: UriValueContentV2 => valueHasUri == thatUriContent.valueHasUri
            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
        }
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = wouldDuplicateOtherValue(currentVersion)
}

/**
  * Constructs [[UriValueContentV2]] objects based on JSON-LD input.
  */
object UriValueContentV2 extends ValueContentReaderV2[UriValueContentV2] {
    override def fromJsonLDObject(jsonLDObject: JsonLDObject): UriValueContentV2 = {
        // TODO
        throw NotImplementedException(s"Reading of ${getClass.getName} from JSON-LD input not implemented")
    }
}

/**
  *
  * Represents a Knora geoname value.
  *
  * @param valueHasString      the string representation of the geoname value.
  * @param valueHasGeonameCode the geoname code.
  * @param comment             a comment on this `GeonameValueContentV2`, if any.
  */
case class GeonameValueContentV2(valueType: SmartIri,
                                 valueHasString: String,
                                 valueHasGeonameCode: String,
                                 comment: Option[String]) extends ValueContentV2 {

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        copy(
            valueType = valueType.toOntologySchema(targetSchema)
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple =>
                JsonLDUtil.datatypeValueToJsonLDObject(
                    value = valueHasGeonameCode,
                    datatype = OntologyConstants.KnoraApiV2Simple.Geoname
                )

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.GeonameValueAsGeonameCode -> JsonLDString(valueHasGeonameCode)))
        }
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatGeonameContent: GeonameValueContentV2 => valueHasGeonameCode == thatGeonameContent.valueHasGeonameCode
            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
        }
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = wouldDuplicateOtherValue(currentVersion)
}

/**
  * Constructs [[GeonameValueContentV2]] objects based on JSON-LD input.
  */
object GeonameValueContentV2 extends ValueContentReaderV2[GeonameValueContentV2] {
    override def fromJsonLDObject(jsonLDObject: JsonLDObject): GeonameValueContentV2 = {
        // TODO
        throw NotImplementedException(s"Reading of ${getClass.getName} from JSON-LD input not implemented")
    }
}

/**
  * An abstract trait representing any file value.
  */
sealed trait FileValueContentV2 {
    val internalMimeType: String
    val internalFilename: String
    val originalFilename: String
    val originalMimeType: Option[String]

    protected def toJsonLDValueinSimpleSchema(imagePath: String): JsonLDObject = {
        JsonLDUtil.datatypeValueToJsonLDObject(
            value = imagePath,
            datatype = OntologyConstants.KnoraApiV2Simple.File
        )
    }
}

/**
  * Represents an image file. Please note that the file itself is managed by Sipi.
  *
  * @param valueHasString   the string representation of the image file value.
  * @param internalMimeType the mime type of the file corresponding to this image file value.
  * @param internalFilename the name of the file corresponding to this image file value.
  * @param originalFilename the original mime type of the image file before importing it.
  * @param originalMimeType the original name of the image file before importing it.
  * @param dimX             the with of the the image file corresponding to this file value in pixels.
  * @param dimY             the height of the the image file corresponding to this file value in pixels.
  * @param qualityLevel     the quality (resolution) of the the image file corresponding to this file value (scale 10-100)
  * @param isPreview        indicates if the file value represents a preview image (thumbnail).
  * @param comment          a comment on this `StillImageFileValueContentV2`, if any.
  */
case class StillImageFileValueContentV2(valueType: SmartIri,
                                        valueHasString: String,
                                        internalMimeType: String,
                                        internalFilename: String,
                                        originalFilename: String,
                                        originalMimeType: Option[String],
                                        dimX: Int,
                                        dimY: Int,
                                        qualityLevel: Int,
                                        isPreview: Boolean,
                                        comment: Option[String]) extends FileValueContentV2 with ValueContentV2 {

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        copy(
            valueType = valueType.toOntologySchema(targetSchema)
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        val imagePath: String = s"${settings.externalSipiIIIFGetUrl}/$internalFilename/full/$dimX,$dimY/0/default.jpg"

        targetSchema match {
            case ApiV2Simple => toJsonLDValueinSimpleSchema(imagePath)

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(
                    OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl -> JsonLDString(imagePath),
                    OntologyConstants.KnoraApiV2WithValueObjects.FileValueIsPreview -> JsonLDBoolean(isPreview),
                    OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimX -> JsonLDInt(dimX),
                    OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimY -> JsonLDInt(dimY),
                    OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename -> JsonLDString(internalFilename),
                    OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasIIIFBaseUrl -> JsonLDString(settings.externalSipiIIIFGetUrl)
                ))
        }
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatStillImage: StillImageFileValueContentV2 =>
                internalMimeType == thatStillImage.internalMimeType &&
                    internalFilename == thatStillImage.internalFilename &&
                    originalFilename == thatStillImage.originalFilename &&
                    originalMimeType == thatStillImage.originalMimeType &&
                    dimX == thatStillImage.dimX &&
                    dimY == thatStillImage.dimY &&
                    qualityLevel == thatStillImage.qualityLevel &&
                    isPreview == thatStillImage.isPreview

            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
        }
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = wouldDuplicateOtherValue(currentVersion)
}

/**
  * Constructs [[StillImageFileValueContentV2]] objects based on JSON-LD input.
  */
object StillImageFileValueContentV2 extends ValueContentReaderV2[StillImageFileValueContentV2] {
    override def fromJsonLDObject(jsonLDObject: JsonLDObject): StillImageFileValueContentV2 = {
        // TODO
        throw NotImplementedException(s"Reading of ${getClass.getName} from JSON-LD input not implemented")
    }
}

/**
  * Represents a text file value. Please note that the file itself is managed by Sipi.
  *
  * @param valueHasString   the string representation of the text file value.
  * @param internalMimeType the mime type of the file corresponding to this text file value.
  * @param internalFilename the name of the file corresponding to this text file value.
  * @param originalFilename the original mime type of the text file before importing it.
  * @param originalMimeType the original name of the text file before importing it.
  * @param comment          a comment on this `TextFileValueContentV2`, if any.
  */
case class TextFileValueContentV2(valueType: SmartIri,
                                  valueHasString: String,
                                  internalMimeType: String,
                                  internalFilename: String,
                                  originalFilename: String,
                                  originalMimeType: Option[String],
                                  comment: Option[String]) extends FileValueContentV2 with ValueContentV2 {

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        copy(
            valueType = valueType.toOntologySchema(targetSchema)
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        val imagePath: String = s"${settings.externalSipiFileServerGetUrl}/$internalFilename"

        targetSchema match {
            case ApiV2Simple => toJsonLDValueinSimpleSchema(imagePath)

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(
                    OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename -> JsonLDString(internalFilename),
                    OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl -> JsonLDString(imagePath)
                ))
        }
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatTextFile: TextFileValueContentV2 =>
                internalMimeType == thatTextFile.internalMimeType &&
                    internalFilename == thatTextFile.internalFilename &&
                    originalFilename == thatTextFile.originalFilename &&
                    originalMimeType == thatTextFile.originalMimeType

            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
        }
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = wouldDuplicateOtherValue(currentVersion)
}

/**
  * Constructs [[TextFileValueContentV2]] objects based on JSON-LD input.
  */
object TextFileValueContentV2 extends ValueContentReaderV2[TextFileValueContentV2] {
    override def fromJsonLDObject(jsonLDObject: JsonLDObject): TextFileValueContentV2 = {
        // TODO
        throw NotImplementedException(s"Reading of ${getClass.getName} from JSON-LD input not implemented")
    }
}


/**
  * Represents a Knora link value.
  *
  * @param valueHasString the string representation of the referred resource.
  * @param subject        the IRI of the link's source resource.
  * @param predicate      the link's predicate.
  * @param target         the IRI of the link's target resource.
  * @param comment        a comment on the link.
  * @param incomingLink   indicates if it is an incoming link.
  * @param nestedResource information about the nested resource, if given.
  */
case class LinkValueContentV2(valueType: SmartIri,
                              valueHasString: String,
                              subject: IRI,
                              predicate: SmartIri,
                              target: IRI,
                              comment: Option[String],
                              incomingLink: Boolean,
                              nestedResource: Option[ReadResourceV2]) extends ValueContentV2 {

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        val convertedNestedResource = nestedResource.map {
            nested =>
                val targetApiSchema: ApiV2Schema = targetSchema match {
                    case apiSchema: ApiV2Schema => apiSchema
                    case _ => throw AssertionException(s"Can't convert a nested resource to $targetSchema")
                }

                nested.toOntologySchema(targetApiSchema)
        }

        copy(
            valueType = valueType.toOntologySchema(targetSchema),
            nestedResource = convertedNestedResource
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        targetSchema match {
            case ApiV2Simple => JsonLDUtil.iriToJsonLDObject(target)

            case ApiV2WithValueObjects =>
                // check if the referred resource has to be included in the JSON response
                val objectMap: Map[IRI, JsonLDValue] = nestedResource match {
                    case Some(targetResource: ReadResourceV2) =>
                        // include the nested resource in the response
                        val referredResourceAsJsonLDValue: JsonLDObject = targetResource.toJsonLD(
                            targetSchema = targetSchema,
                            settings = settings
                        )

                        // check whether the nested resource is the target or the source of the link
                        if (!incomingLink) {
                            Map(OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTarget -> referredResourceAsJsonLDValue)
                        } else {
                            Map(OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasSource -> referredResourceAsJsonLDValue)
                        }
                    case None =>
                        // check whether it is an outgoing or incoming link
                        if (!incomingLink) {
                            Map(OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTargetIri -> JsonLDUtil.iriToJsonLDObject(target))
                        } else {
                            Map(OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasSourceIri -> JsonLDUtil.iriToJsonLDObject(subject))
                        }
                }

                JsonLDObject(objectMap)
        }
    }

    override def unescape: ValueContentV2 = {
        copy(comment = comment.map(commentStr => stringFormatter.fromSparqlEncodedString(commentStr)))
    }

    override def wouldDuplicateOtherValue(that: ValueContentV2): Boolean = {
        that match {
            case thatLinkValue: LinkValueContentV2 =>
                subject == thatLinkValue.subject &&
                predicate == thatLinkValue.predicate &&
                target == thatLinkValue.target

            case _ => throw AssertionException(s"Can't compare a <$valueType> to a <${that.valueType}>")
        }
    }

    override def wouldDuplicateCurrentVersion(currentVersion: ValueContentV2): Boolean = wouldDuplicateOtherValue(currentVersion)
}

/**
  * Constructs [[LinkValueContentV2]] objects based on JSON-LD input.
  */
object LinkValueContentV2 extends ValueContentReaderV2[LinkValueContentV2] {
    override def fromJsonLDObject(jsonLDObject: JsonLDObject): LinkValueContentV2 = {
        // TODO
        throw NotImplementedException(s"Reading of ${getClass.getName} from JSON-LD input not implemented")
    }
}
