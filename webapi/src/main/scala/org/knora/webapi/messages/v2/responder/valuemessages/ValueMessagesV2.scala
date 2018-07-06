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

import java.util.UUID

import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.valuemessages.{JulianDayNumberValueV1, KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.twirl.StandoffTagV2
import org.knora.webapi.util.DateUtilV2.{DateYearMonthDay, KnoraEraV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.standoff.{StandoffTagUtilV2, XMLUtil}
import org.knora.webapi.util.{DateUtilV1, DateUtilV2, SmartIri, StringFormatter}

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
  * @param valueIri     the IRI of the value.
  * @param valueContent the content of the value.
  */
case class ReadValueV2(valueIri: IRI, valueContent: ValueContentV2) extends IOValueV2 with KnoraReadV2[ReadValueV2] {
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
}

/**
  * A trait for objects that can convert JSON-LD objects into value content objects (subclasses of [[ValueContentV2]]).
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
          * @param maybeDay an optional day of the month.
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
  * Represents a Knora text value.
  *
  * @param valueHasString the string representation of the text (without markup).
  * @param standoff       a [[StandoffAndMapping]], if any.
  * @param comment        a comment on this `TextValueContentV2`, if any.
  */
case class TextValueContentV2(valueType: SmartIri,
                              valueHasString: String,
                              valueHasLanguage: Option[String] = None,
                              standoff: Option[StandoffAndMapping],
                              comment: Option[String]) extends ValueContentV2 {

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
                val objectMap: Map[IRI, JsonLDValue] = if (standoff.nonEmpty) {

                    val xmlFromStandoff = StandoffTagUtilV2.convertStandoffTagV2ToXML(valueHasString, standoff.get.standoff, standoff.get.mapping)

                    // check if there is an XSL transformation
                    if (standoff.get.XSLT.nonEmpty) {

                        val xmlTransformed: String = XMLUtil.applyXSLTransformation(xmlFromStandoff, standoff.get.XSLT.get)

                        // the xml was converted to HTML
                        Map(OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsHtml -> JsonLDString(xmlTransformed))
                    } else {
                        // xml is returned
                        Map(
                            OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsXml -> JsonLDString(xmlFromStandoff),
                            OntologyConstants.KnoraApiV2WithValueObjects.TextValueHasMapping -> JsonLDString(standoff.get.mappingIri)
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

