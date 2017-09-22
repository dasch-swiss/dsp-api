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

package org.knora.webapi.messages.v2.responder

import java.io.{StringReader, StringWriter}
import javax.xml.transform.stream.StreamSource

import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.twirl.StandoffTagV1
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.standoff.StandoffTagUtilV1
import org.knora.webapi.util.{DateUtilV2, InputValidation}

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
case class ReadValueV2(valueIri: IRI, valueContent: ValueContentV2) extends IOValueV2

/**
  * The value of a Knora property sent to Knora to be created.
  *
  * @param resourceIri  the resource the new value should be attached to.
  * @param propertyIri  the property of the new value.
  * @param valueContent the content of the new value.
  */
case class CreateValueV2(resourceIri: IRI, propertyIri: IRI, valueContent: ValueContentV2) extends IOValueV2

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
sealed trait ValueContentV2 {

    /**
      * The IRI of the internal Knora value type (defined in the `knora-base` ontology) corresponding to the type of this `ValueContentV2`.
      */
    def internalValueTypeIri: IRI

    /**
      * The string representation of this `ValueContentV2`.
      */
    def valueHasString: String

    /**
      * A comment on this `ValueContentV2`, if any.
      */
    def comment: Option[String]

    /**
      * A representation of the `ValueContentV2` a [[JsonLDValue]].
      *
      * @param targetSchema the API schema to be used.
      * @param settings the configuration options.
      * @return a [[JsonLDValue]] that can be used to generate JSON-LD representing this value.
      */
    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue

}

/**
  * Represents a Knora date value.
  *
  * @param valueHasString         the string of the date.
  * @param valueHasStartJDN       the start of the date as JDN.
  * @param valueHasEndJDN         the end of the date as JDN.
  * @param valueHasStartPrecision the precision of the start date.
  * @param valueHasEndPrecision   the precision of the end date.
  * @param valueHasCalendar       the calendar of the date.
  * @param comment                a comment on this `DateValueContentV2`, if any.
  */
case class DateValueContentV2(valueHasString: String,
                              valueHasStartJDN: Int,
                              valueHasEndJDN: Int,
                              valueHasStartPrecision: KnoraPrecisionV1.Value,
                              valueHasEndPrecision: KnoraPrecisionV1.Value,
                              valueHasCalendar: KnoraCalendarV1.Value,
                              comment: Option[String]) extends ValueContentV2 {

    def internalValueTypeIri: IRI = OntologyConstants.KnoraBase.DateValue

    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {

        // TODO: check targetSchema and return JSON-LD accordingly.

        JsonLDObject(Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> JsonLDString(valueHasString),
            OntologyConstants.KnoraApiV2WithValueObject.DateValueHasCalendar -> JsonLDString(valueHasCalendar.toString)
        ) ++ toKnoraApiDateValueAssertions)
    }

    /**
      * Create knora-api assertions.
      *
      * @return a Map of knora-api value properties to numbers (year, month, day) representing the date value.
      */
    def toKnoraApiDateValueAssertions: Map[IRI, JsonLDValue] = {
        val startDateAssertions = DateUtilV2.convertJDNToDate(valueHasStartJDN, valueHasStartPrecision, valueHasCalendar).toStartDateAssertions().map {
            case (k: IRI, v: Int) => (k, JsonLDInt(v))
        }

        val endDateAssertions = DateUtilV2.convertJDNToDate(valueHasEndJDN, valueHasEndPrecision, valueHasCalendar).toEndDateAssertions().map {
            case (k: IRI, v: Int) => (k, JsonLDInt(v))
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
case class TextValueContentV2(valueHasString: String, standoff: Option[StandoffAndMapping], comment: Option[String]) extends ValueContentV2 {

    def internalValueTypeIri: IRI = OntologyConstants.KnoraBase.TextValue

    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {

        // TODO: check targetSchema and return JSON-LD accordingly.

        val objectMap: Map[IRI, JsonLDValue] = if (standoff.nonEmpty) {

            val xmlFromStandoff = StandoffTagUtilV1.convertStandoffTagV1ToXML(valueHasString, standoff.get.standoff, standoff.get.mapping)

            // check if there is an XSL transformation
            if (standoff.get.XSLT.nonEmpty) {

                // apply the XSL transformation to xml
                val proc = new net.sf.saxon.s9api.Processor(false)
                val comp = proc.newXsltCompiler()

                val exp = comp.compile(new StreamSource(new StringReader(standoff.get.XSLT.get)))

                val source = try {
                    proc.newDocumentBuilder().build(new StreamSource(new StringReader(xmlFromStandoff)))
                } catch {
                    case e: Exception => throw StandoffConversionException(s"The provided XML could not be parsed: ${e.getMessage}")
                }

                val xmlTransformedStr: StringWriter = new StringWriter()
                val out = proc.newSerializer(xmlTransformedStr)

                val trans = exp.load()
                trans.setInitialContextNode(source)
                trans.setDestination(out)
                trans.transform()

                // the xml was converted to HTML
                Map(OntologyConstants.KnoraApiV2WithValueObject.TextValueAsHtml -> JsonLDString(xmlTransformedStr.toString))
            } else {
                // xml is returned
                Map(
                    OntologyConstants.KnoraApiV2WithValueObject.TextValueAsXml -> JsonLDString(xmlFromStandoff),
                    OntologyConstants.KnoraApiV2WithValueObject.TextValueHasMapping -> JsonLDString(standoff.get.mappingIri)
                )
            }

        } else {
            // no markup given
            Map(OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> JsonLDString(valueHasString))
        }

        JsonLDObject(objectMap)
    }

}

/**
  * Represents standoff and the corresponding mapping.
  * May include an XSL transformation.
  *
  * @param standoff   a sequence of [[StandoffTagV1]].
  * @param mappingIri the IRI of the mapping
  * @param mapping    a mapping between XML and standoff.
  * @param XSLT       an XSL transformation.
  */
case class StandoffAndMapping(standoff: Seq[StandoffTagV1], mappingIri: IRI, mapping: MappingXMLtoStandoff, XSLT: Option[String])

/**
  * Represents a Knora integer value.
  *
  * @param valueHasString  the string representation of the integer.
  * @param valueHasInteger the integer value.
  * @param comment         a comment on this `IntegerValueContentV2`, if any.
  */
case class IntegerValueContentV2(valueHasString: String, valueHasInteger: Int, comment: Option[String]) extends ValueContentV2 {

    def internalValueTypeIri: IRI = OntologyConstants.KnoraBase.IntValue

    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {

        // TODO: check targetSchema and return JSON-LD accordingly.

        JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObject.IntegerValueAsInteger -> JsonLDInt(valueHasInteger)))
    }

}

/**
  * Represents a Knora decimal value.
  *
  * @param valueHasString  the string representation of the decimal.
  * @param valueHasDecimal the decimal value.
  * @param comment         a comment on this `DecimalValueContentV2`, if any.
  */
case class DecimalValueContentV2(valueHasString: String, valueHasDecimal: BigDecimal, comment: Option[String]) extends ValueContentV2 {

    def internalValueTypeIri: IRI = OntologyConstants.KnoraBase.DecimalValue

    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {

        // TODO: check targetSchema and return JSON-LD accordingly.

        JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObject.DecimalValueAsDecimal -> JsonLDDecimal(valueHasDecimal)))
    }

}

/**
  * Represents a Boolean value.
  *
  * @param valueHasString  the string representation of the Boolean.
  * @param valueHasBoolean the Boolean value.
  * @param comment         a comment on this `BooleanValueContentV2`, if any.
  */
case class BooleanValueContentV2(valueHasString: String, valueHasBoolean: Boolean, comment: Option[String]) extends ValueContentV2 {

    def internalValueTypeIri: IRI = OntologyConstants.KnoraBase.BooleanValue

    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        // TODO: check targetSchema and return JSON-LD accordingly.

        JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObject.BooleanValueAsBoolean -> JsonLDBoolean(valueHasBoolean)))
    }
}

/**
  * Represents a Knora geometry value (a 2D-shape).
  *
  * @param valueHasString   a stringified JSON representing a 2D-geometrical shape.
  * @param valueHasGeometry a stringified JSON representing a 2D-geometrical shape.
  * @param comment          a comment on this `GeomValueContentV2`, if any.
  */
case class GeomValueContentV2(valueHasString: String, valueHasGeometry: String, comment: Option[String]) extends ValueContentV2 {

    def internalValueTypeIri: IRI = OntologyConstants.KnoraBase.GeomValue

    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        // TODO: check targetSchema and return JSON-LD accordingly.

        JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObject.GeometryValueAsGeometry -> JsonLDString(valueHasGeometry)))
    }
}


/**
  * Represents a Knora time interval value.
  *
  * @param valueHasString        the string representation of the time interval.
  * @param valueHasIntervalStart the start of the time interval.
  * @param valueHasIntervalEnd   the end of the time interval.
  * @param comment               a comment on this `GeomValueContentV2`, if any.
  */
case class IntervalValueContentV2(valueHasString: String, valueHasIntervalStart: BigDecimal, valueHasIntervalEnd: BigDecimal, comment: Option[String]) extends ValueContentV2 {

    def internalValueTypeIri: IRI = OntologyConstants.KnoraBase.IntervalValue

    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        // TODO: check targetSchema and return JSON-LD accordingly.

        JsonLDObject(Map(
            OntologyConstants.KnoraApiV2WithValueObject.IntervalValueHasStart -> JsonLDDecimal(valueHasIntervalStart),
            OntologyConstants.KnoraApiV2WithValueObject.IntervalValueHasEnd -> JsonLDDecimal(valueHasIntervalEnd)
        ))
    }

}

/**
  * Represents a value pointing to a Knora hierarchical list node.
  *
  * @param valueHasString   the string representation of the hierarchical list node value.
  * @param valueHasListNode the IRI of the hierarchical list node pointed to.
  * @param comment          a comment on this `GeomValueContentV2`, if any.
  */
case class HierarchicalListValueContentV2(valueHasString: String, valueHasListNode: IRI, comment: Option[String]) extends ValueContentV2 {

    def internalValueTypeIri: IRI = OntologyConstants.KnoraBase.ListValue

    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        // TODO: check targetSchema and return JSON-LD accordingly.

        JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObject.HierarchicalListValueAsListNode -> JsonLDString(valueHasListNode)))
    }

}

/**
  * Represents a Knora color value.
  *
  * @param valueHasString the string representation of the color value.
  * @param valueHasColor  a hexadecimal string containing the RGB color value
  * @param comment        a comment on this `ColorValueContentV2`, if any.
  */
case class ColorValueContentV2(valueHasString: String, valueHasColor: String, comment: Option[String]) extends ValueContentV2 {

    def internalValueTypeIri: IRI = OntologyConstants.KnoraBase.ColorValue

    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        // TODO: check targetSchema and return JSON-LD accordingly.

        JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObject.ColorValueAsColor -> JsonLDString(valueHasColor)))
    }

}

/**
  * Represents a Knora URI value.
  *
  * @param valueHasString the string representation of the URI value.
  * @param valueHasUri    the URI value.
  * @param comment        a comment on this `UriValueContentV2`, if any.
  */
case class UriValueContentV2(valueHasString: String, valueHasUri: String, comment: Option[String]) extends ValueContentV2 {

    def internalValueTypeIri: IRI = OntologyConstants.KnoraBase.UriValue

    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        // TODO: check targetSchema and return JSON-LD accordingly.

        JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObject.UriValueAsUri -> JsonLDString(valueHasUri)))
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
case class GeonameValueContentV2(valueHasString: String, valueHasGeonameCode: String, comment: Option[String]) extends ValueContentV2 {

    def internalValueTypeIri: IRI = OntologyConstants.KnoraBase.GeonameValue

    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        // TODO: check targetSchema and return JSON-LD accordingly.

        JsonLDObject(Map(OntologyConstants.KnoraApiV2WithValueObject.GeonameValueAsGeonameCode -> JsonLDString(valueHasGeonameCode)))
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

    /**
      * Creates the path to retrieve the file value on the web.
      *
      * @param settings the configuration options.
      * @return the path to the file as an absolute URL.
      */
    def toURL(settings: SettingsImpl): String
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
case class StillImageFileValueContentV2(valueHasString: String,
                                        internalMimeType: String,
                                        internalFilename: String,
                                        originalFilename: String,
                                        originalMimeType: Option[String],
                                        dimX: Int,
                                        dimY: Int,
                                        qualityLevel: Int,
                                        isPreview: Boolean,
                                        comment: Option[String]) extends FileValueContentV2 with ValueContentV2 {

    def internalValueTypeIri: IRI = OntologyConstants.KnoraBase.StillImageFileValue

    /**
      * Creates the URL to retrieve the file.
      *
      * @return the path to file value as an absolute URL.
      */
    def toURL(settings: SettingsImpl): String = {
        s"${settings.sipiIIIFGetUrl}/$internalFilename/full/$dimX,$dimY/0/default.jpg"
    }

    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        // TODO: check targetSchema and return JSON-LD accordingly.

        val imagePath: String = toURL(settings)

        JsonLDObject(Map(
            OntologyConstants.KnoraApiV2WithValueObject.FileValueAsUrl -> JsonLDString(imagePath),
            OntologyConstants.KnoraApiV2WithValueObject.FileValueIsPreview -> JsonLDBoolean(isPreview),
            OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValueHasDimX -> JsonLDInt(dimX),
            OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValueHasDimY -> JsonLDInt(dimY),
            OntologyConstants.KnoraApiV2WithValueObject.FileValueHasFilename -> JsonLDString(internalFilename),
            OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValueHasIIIFBaseUrl -> JsonLDString(settings.sipiIIIFGetUrl)
        ))
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
case class TextFileValueContentV2(valueHasString: String, internalMimeType: String,
                                  internalFilename: String,
                                  originalFilename: String,
                                  originalMimeType: Option[String],
                                  comment: Option[String]) extends ValueContentV2 {

    def internalValueTypeIri: IRI = OntologyConstants.KnoraBase.TextFileValue

    /**
      * Creates the URL to retrieve the file.
      *
      * @return the path to file value as an absolute URL.
      */
    def toURL(settings: SettingsImpl): String = {
        s"${settings.sipiFileServerGetUrl}/$internalFilename"
    }

    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        // TODO: check targetSchema and return JSON-LD accordingly.

        val imagePath: String = toURL(settings)

        JsonLDObject(Map(
            OntologyConstants.KnoraApiV2WithValueObject.FileValueHasFilename -> JsonLDString(internalFilename),
            OntologyConstants.KnoraApiV2WithValueObject.FileValueAsUrl -> JsonLDString(imagePath)
        ))
    }

}

/**
  * Represents a Knora link value.
  *
  * @param valueHasString      the string representation of the referred resource.
  * @param subject             the source of the link.
  * @param predicate           the link's predicate.
  * @param referredResourceIri the link's target.
  * @param comment             a comment on the link.
  * @param referredResource    information about the referred resource, if given.
  */
case class LinkValueContentV2(valueHasString: String, subject: IRI, predicate: IRI, referredResourceIri: IRI, comment: Option[String], referredResource: Option[ReadResourceV2]) extends ValueContentV2 {

    def internalValueTypeIri: IRI = OntologyConstants.KnoraBase.LinkValue

    def toJsonLDValue(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDValue = {
        // TODO: check targetSchema and return JSON-LD accordingly.

        // check if the referred resource has to be included in the JSON response
        val objectMap: Map[IRI, JsonLDValue] = referredResource match {
            case Some(targetResource: ReadResourceV2) =>
                // include the referred resource as a nested structure
                val referredResourceAsJsonLDValue: JsonLDObject = ReadResourceUtil.createJsonLDObjectFromReadResourceV2(
                    resource = targetResource,
                    targetSchema = targetSchema,
                    settings = settings
                )

                Map(OntologyConstants.KnoraApiV2WithValueObject.LinkValueHasTarget -> referredResourceAsJsonLDValue)

            case None =>
                // just include the referred resource's IRI
                Map(OntologyConstants.KnoraApiV2WithValueObject.LinkValueHasTargetIri -> JsonLDString(referredResourceIri))
        }

        JsonLDObject(objectMap)
    }
}

/**
  * Represents a Knora resource.
  * Any implementation of `ResourceV2` is API operation specific.
  */
sealed trait ResourceV2 {
    def resourceClass: IRI

    def label: String

    /**
      * A map of property IRIs to [[IOValueV2]] objects.
      */
    def values: Map[IRI, Seq[IOValueV2]]

    def resourceInfos: Map[IRI, LiteralV2]

}

/**
  * Represents a Knora resource when being read back from the triplestore.
  *
  * @param resourceIri   the IRI of the resource.
  * @param label         the resource's label.
  * @param resourceClass the class the resource belongs to.
  * @param values        the resource's values.
  * @param resourceInfos additional information attached to the resource.
  */
case class ReadResourceV2(resourceIri: IRI, label: String, resourceClass: IRI, values: Map[IRI, Seq[ReadValueV2]], resourceInfos: Map[IRI, LiteralV2]) extends ResourceV2

/**
  * Represents a Knora resource that is about to be created.
  *
  * @param label         the resource's label.
  * @param resourceClass the class the resource belongs to.
  * @param values        the resource's values.
  * @param resourceInfos additional information attached to the resource (literals).
  */
case class CreateResource(label: String, resourceClass: IRI, values: Map[IRI, Seq[CreateValueV2]], resourceInfos: Map[IRI, LiteralV2]) extends ResourceV2

/**
  * A trait representing literals that may be directly attached to a resource.
  */
sealed trait LiteralV2

/**
  * Represents a string literal attached to a resource.
  *
  * @param value a string literal.
  */
case class StringLiteralV2(value: String) extends LiteralV2

/**
  * Represents an integer literal attached to a resource.
  *
  * @param value an integer literal.
  */
case class IntegerLiteralV2(value: Int) extends LiteralV2

/**
  * Represents a decimal literal attached to a resource.
  *
  * @param value a decimal literal.
  */
case class DecimalLiteralV2(value: BigDecimal) extends LiteralV2

/**
  * Represents a boolean literal attached to a resource.
  *
  * @param value a boolean literal.
  */
case class BooleanLiteralV2(value: Boolean) extends LiteralV2


/**
  *
  * A trait for Knora API V2 response messages. Any response can be converted into JSON or XML.
  *
  */
trait KnoraResponseV2 {

    /**
      * Converts the response to a data structure that can be used to generate JSON-LD.
      *
      * @param targetSchema the Knora API schema to be used in the JSON-LD document.
      * @return a [[JsonLDDocument]] representing the response.
      */
    def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument
}

object ReadResourceUtil {
    /**
      * Creates a JSON-LD object from a [[ReadResourceV2]].
      * This function is also used to provide nested structures (resources refer to other resources).
      *
      * @param resource the resource to be turned into JSON-LD.
      * @return a JSON-LD object.
      */
    def createJsonLDObjectFromReadResourceV2(resource: ReadResourceV2, targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDObject = {
        // TODO: check targetSchema and return JSON-LD accordingly.

        val values: Map[IRI, JsonLDArray] = resource.values.map {
            case (propIri: IRI, readValues: Seq[ReadValueV2]) =>
                val jsonLDValues: Seq[JsonLDObject] = readValues.map {
                    (readValue: ReadValueV2) =>
                        val valAsMap: Map[IRI, JsonLDValue] = readValue.valueContent.toJsonLDValue(targetSchema, settings) match {
                            case jsonLDObject: JsonLDObject => jsonLDObject.value
                            case other => throw AssertionException(s"ValueContentV2.toJsonLDValue should have returned a JsonLDObject, but it returned $other")
                        }

                        JsonLDObject(
                            Map(
                                "@id" -> JsonLDString(readValue.valueIri),
                                "@type" -> JsonLDString(InputValidation.internalEntityIriToApiV2WithValueObjectEntityIri(readValue.valueContent.internalValueTypeIri, () => throw InconsistentTriplestoreDataException(s"internal value type IRI ${readValue.valueContent.internalValueTypeIri} could not be converted to a knora-api v2 with value type IRI")))
                            ) ++ valAsMap
                        )
                }

                (InputValidation.internalEntityIriToApiV2WithValueObjectEntityIri(propIri, () => throw InconsistentTriplestoreDataException(s"internal property $propIri could not be converted to knora-api v2 with value object property IRI")), JsonLDArray(jsonLDValues))

        }

        JsonLDObject(Map(
            "@type" -> JsonLDString(InputValidation.internalEntityIriToApiV2WithValueObjectEntityIri(resource.resourceClass, () => throw InconsistentTriplestoreDataException(s"internal resource class IRI ${resource.resourceClass} could not be converted to a knora-api v2 with value object resource class IRI"))),
            "http://schema.org/name" -> JsonLDString(resource.label),
            "@id" -> JsonLDString(resource.resourceIri)
        ) ++ values)
    }
}

/**
  * Represents a sequence of resources read back from Knora.
  *
  * @param numberOfResources the amount of resources returned.
  * @param resources         a sequence of resources.
  */
case class ReadResourcesSequenceV2(numberOfResources: Int, resources: Seq[ReadResourceV2]) extends KnoraResponseV2 {

    def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        // TODO: check targetSchema and return JSON-LD accordingly.

        // Make JSON-LD prefixes for the project-specific ontologies used in the response.

        val internalProjectSpecificOntologiesUsed: Set[IRI] = resources.flatMap {
            resource =>
                val resourceClass = resource.resourceClass
                val properties = resource.values.keySet
                val resourceOntology = InputValidation.getInternalOntologyIriFromInternalEntityIri(resourceClass, () => throw InconsistentTriplestoreDataException(s"Invalid internal resource class IRI: $resourceClass"))

                val propertyOntologies = properties.map {
                    property => InputValidation.getInternalOntologyIriFromInternalEntityIri(property, () => throw InconsistentTriplestoreDataException(s"Invalid internal property IRI: $property"))
                }

                propertyOntologies + resourceOntology
        }.toSet - OntologyConstants.KnoraBase.KnoraBaseOntologyIri

        val projectSpecificOntologyPrefixes: Map[IRI, JsonLDString] = internalProjectSpecificOntologiesUsed.map {
            internalOntologyIri =>
                val prefix = InputValidation.getOntologyPrefixLabelFromInternalOntologyIri(internalOntologyIri, () => throw InconsistentTriplestoreDataException(s"Invalid internal ontology IRI: $internalOntologyIri"))
                val externalOntologyIri = InputValidation.internalOntologyIriToApiV2WithValueObjectOntologyIri(internalOntologyIri, () => throw InconsistentTriplestoreDataException(s"Invalid internal ontology IRI: $internalOntologyIri"))
                (prefix, JsonLDString(externalOntologyIri + "#"))
        }.toMap

        // TODO: check targetSchema and return JSON-LD accordingly.

        val context = JsonLDObject(Map(
            "schema" -> JsonLDString("http://schema.org/"),
            OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(OntologyConstants.KnoraApiV2WithValueObject.KnoraApiV2PrefixExpansion),
            "rdf" -> JsonLDString("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
            "rdfs" -> JsonLDString("http://www.w3.org/2000/01/rdf-schema#")
        ) ++ projectSpecificOntologyPrefixes)

        val resourcesJsonObjects: Seq[JsonLDObject] = resources.map {
            (resource: ReadResourceV2) => ReadResourceUtil.createJsonLDObjectFromReadResourceV2(
                resource = resource,
                targetSchema = targetSchema,
                settings = settings
            )
        }


        val body = JsonLDObject(Map(
            "@type" -> JsonLDString("http://schema.org/ItemList"),
            "http://schema.org/numberOfItems" -> JsonLDInt(numberOfResources),
            "http://schema.org/itemListElement" -> JsonLDArray(resourcesJsonObjects)
        ))

        JsonLDDocument(body = body, context = context)
    }
}


