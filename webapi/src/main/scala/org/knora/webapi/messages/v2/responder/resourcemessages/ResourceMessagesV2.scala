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

package org.knora.webapi.messages.v2.responder.resourcemessages

import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.twirl.StandoffTagV2
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.standoff.{StandoffTagUtilV2, XMLUtil}
import org.knora.webapi.util.{DateUtilV2, SmartIri, StringFormatter}

/**
  * An abstract trait for messages that can be sent to `ResourcesResponderV2`.
  */
sealed trait ResourcesResponderRequestV2 extends KnoraRequestV2 {
    /**
      * The user that made the request.
      */
    def requestingUser: UserADM
}

/**
  * Requests a description of a resource. A successful response will be a [[ReadResourcesSequenceV2]].
  *
  * @param resourceIris   the IRIs of the resources to be queried.
  * @param requestingUser the user making the request.
  */
case class ResourcesGetRequestV2(resourceIris: Seq[IRI], requestingUser: UserADM) extends ResourcesResponderRequestV2

/**
  * Requests a preview of a resource. A successful response will be a [[ReadResourcesSequenceV2]].
  *
  * @param resourceIris   the Iris of the resources to obtain a preview for.
  * @param requestingUser the user making the request.
  */
case class ResourcesPreviewGetRequestV2(resourceIris: Seq[IRI], requestingUser: UserADM) extends ResourcesResponderRequestV2

/**
  * Requests a resource as TEI/XML. A successful response will be a [[ResourceTEIGetResponseV2]].
  *
  * @param resourceIri the IRI of the resource to be returned in TEI/XML.
  * @param requestingUser the user making the request.
  */
case class ResourceTEIGetRequestV2(resourceIri: IRI, requestingUser: UserADM) extends ResourcesResponderRequestV2

/**
  * Represents a Knora resource as TEI/XML.
  *
  * @param header the header of the TEI document.
  * @param body the body of the TEI document.
  */
case class ResourceTEIGetResponseV2(header: String, body: String) {

    def toXML = {

        s"""<?xml version="1.0" encoding="UTF-8"?>
          |<TEI version="3.3.0" xmlns="http://www.tei-c.org/ns/1.0">
          | $header
            $body
          |</TEI>
        """.stripMargin
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
case class ReadValueV2(valueIri: IRI, valueContent: ValueContentV2) extends IOValueV2 {
    /**
      * Converts this value to the specified ontology schema.
      *
      * @param targetSchema the target schema.
      */
    def toOntologySchema(targetSchema: OntologySchema): ReadValueV2 = {
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
                                ("@id" -> JsonLDString(valueIri)) +
                                ("@type" -> JsonLDString(valueContent.valueType.toString))
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
            case ApiV2Simple => JsonLDString(valueHasString)

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
            case ApiV2Simple => JsonLDString(valueHasDecimal.toString)

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.DecimalValueAsDecimal -> JsonLDString(valueHasDecimal.toString)))
        }
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
            case ApiV2Simple => JsonLDString(valueHasGeometry)

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.GeometryValueAsGeometry -> JsonLDString(valueHasGeometry)))
        }
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
            case ApiV2Simple => JsonLDString(valueHasString)

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(
                    OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasStart -> JsonLDString(valueHasIntervalStart.toString),
                    OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasEnd -> JsonLDString(valueHasIntervalEnd.toString)
                ))
        }
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
            case ApiV2Simple => JsonLDString(valueHasColor)

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.ColorValueAsColor -> JsonLDString(valueHasColor)))
        }
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
            case ApiV2Simple => JsonLDString(valueHasUri)

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.UriValueAsUri -> JsonLDString(valueHasUri)))
        }
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
            case ApiV2Simple => JsonLDString(valueHasGeonameCode)

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObjects.GeonameValueAsGeonameCode -> JsonLDString(valueHasGeonameCode)))
        }
    }
}

/**
  * An abstract trait representing any file value.
  *
  */
sealed trait FileValueContentV2 {
    val internalMimeType: String
    val internalFilename: String
    val originalFilename: String
    val originalMimeType: Option[String]
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
            case ApiV2Simple => JsonLDString(imagePath)

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
                                  comment: Option[String]) extends ValueContentV2 {

    override def toOntologySchema(targetSchema: OntologySchema): ValueContentV2 = {
        copy(
            valueType = valueType.toOntologySchema(targetSchema)
        )
    }

    override def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        val imagePath: String = s"${settings.externalSipiFileServerGetUrl}/$internalFilename"

        targetSchema match {
            case ApiV2Simple => JsonLDString(imagePath)

            case ApiV2WithValueObjects =>
                JsonLDObject(Map(
                    OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename -> JsonLDString(internalFilename),
                    OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl -> JsonLDString(imagePath)
                ))
        }
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
  * Represents a Knora resource. Any implementation of `ResourceV2` is API operation specific.
  */
sealed trait ResourceV2 {
    /**
      * The IRI of the resource class.
      */
    def resourceClass: SmartIri

    /**
      * The resource's `rdfs:label`.
      */
    def label: String

    /**
      * A map of property IRIs to [[IOValueV2]] objects.
      */
    def values: Map[SmartIri, Seq[IOValueV2]]
}

/**
  * Represents a Knora resource when being read back from the triplestore.
  *
  * @param resourceIri   the IRI of the resource.
  * @param label         the resource's label.
  * @param resourceClass the class the resource belongs to.
  * @param values        a map of property IRIs to values.
  */
case class ReadResourceV2(resourceIri: IRI,
                          label: String,
                          resourceClass: SmartIri,
                          values: Map[SmartIri, Seq[ReadValueV2]]) extends ResourceV2 {
    def toOntologySchema(targetSchema: ApiV2Schema): ReadResourceV2 = {
        copy(
            resourceClass = resourceClass.toOntologySchema(targetSchema),
            values = values.map {
                case (propertyIri, readValues) =>
                    val propertyIriInTargetSchema = propertyIri.toOntologySchema(targetSchema)

                    // In the simple schema, use link properties instead of link value properties.
                    val adaptedPropertyIri = if (targetSchema == ApiV2Simple) {
                        val isLinkProp = readValues.forall {
                            readValue =>
                                readValue.valueContent match {
                                    case _: LinkValueContentV2 => true
                                    case _ => false
                                }
                        }

                        if (isLinkProp) {
                            propertyIriInTargetSchema.fromLinkValuePropToLinkProp
                        } else {
                            propertyIriInTargetSchema
                        }
                    } else {
                        propertyIriInTargetSchema
                    }

                    adaptedPropertyIri -> readValues.map(_.toOntologySchema(targetSchema))
            }
        )
    }

    def toJsonLD(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDObject = {
        if (!resourceClass.getOntologySchema.contains(targetSchema)) {
            throw DataConversionException(s"ReadClassInfoV2 for resource $resourceIri is not in schema $targetSchema")
        }

        val propertiesAndValuesAsJsonLD: Map[IRI, JsonLDArray] = values.map {
            case (propIri: SmartIri, readValues: Seq[ReadValueV2]) =>
                val valuesAsJsonLD: Seq[JsonLDValue] = readValues.map(_.toJsonLD(targetSchema, settings))
                propIri.toString -> JsonLDArray(valuesAsJsonLD)
        }

        JsonLDObject(Map(
            "@id" -> JsonLDString(resourceIri),
            "@type" -> JsonLDString(resourceClass.toString),
            OntologyConstants.Rdfs.Label -> JsonLDString(label)
        ) ++ propertiesAndValuesAsJsonLD)
    }
}

/**
  * Represents a Knora resource that is about to be created.
  *
  * @param label         the resource's label.
  * @param resourceClass the class the resource belongs to.
  * @param values        the resource's values.
  */
case class CreateResource(label: String, resourceClass: SmartIri, values: Map[SmartIri, Seq[CreateValueV2]]) extends ResourceV2

/**
  * Represents a sequence of resources read back from Knora.
  *
  * @param numberOfResources the amount of resources returned.
  * @param resources         a sequence of resources.
  */
case class ReadResourcesSequenceV2(numberOfResources: Int, resources: Seq[ReadResourceV2]) extends KnoraResponseV2 {

    def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        // Generate JSON-LD for the resources.

        val resourcesInTargetSchema = resources.map(_.toOntologySchema(targetSchema))

        val resourcesJsonObjects: Seq[JsonLDObject] = resourcesInTargetSchema.map {
            resource: ReadResourceV2 => resource.toJsonLD(targetSchema = targetSchema, settings = settings)
        }

        // Make JSON-LD prefixes for the project-specific ontologies used in the response.

        val projectSpecificOntologiesUsed: Set[SmartIri] = resourcesInTargetSchema.flatMap {
            resource =>
                val resourceOntology = resource.resourceClass.getOntologyFromEntity

                val propertyOntologies = resource.values.keySet.map {
                    property => property.getOntologyFromEntity
                }

                propertyOntologies + resourceOntology
        }.toSet.filter(!_.isKnoraBuiltInDefinitionIri)

        val projectSpecificOntologyPrefixes: Map[String, JsonLDString] = projectSpecificOntologiesUsed.map {
            ontologyIri => ontologyIri.getPrefixLabel -> JsonLDString(ontologyIri + "#")
        }.toMap

        // Make the knora-api prefix for the target schema.

        val knoraApiPrefixExpansion = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion
        }

        // Make the JSON-LD document.

        val context = JsonLDObject(Map(
            "rdf" -> JsonLDString("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
            "rdfs" -> JsonLDString("http://www.w3.org/2000/01/rdf-schema#"),
            "schema" -> JsonLDString("http://schema.org/"),
            OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(knoraApiPrefixExpansion)
        ) ++ projectSpecificOntologyPrefixes)

        val body = JsonLDObject(Map(
            "@type" -> JsonLDString("http://schema.org/ItemList"),
            "http://schema.org/numberOfItems" -> JsonLDInt(numberOfResources),
            "http://schema.org/itemListElement" -> JsonLDArray(resourcesJsonObjects)
        ))

        JsonLDDocument(body = body, context = context)
    }
}
