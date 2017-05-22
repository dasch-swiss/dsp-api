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
import java.{lang, util}
import javax.xml.transform.stream.StreamSource

import com.github.jsonldjava.core._
import com.github.jsonldjava.utils._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.twirl.StandoffTagV1
import org.knora.webapi.util.DateUtilV2
import org.knora.webapi.util.standoff.StandoffTagUtilV1

import scala.collection.JavaConverters._

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
      * The IRI of the Knora value type corresponding to the type of this `ValueContentV2`.
      */
    def valueTypeIri: IRI

    /**
      * The string representation of this `ValueContentV2`.
      */
    def valueHasString: String

    /**
      * A comment on this `ValueContentV2`, if any.
      */
    def comment: Option[String]

    /**
      * A representation of the `ValueContentV2` as a Map value property Iri to typed value.
      */
    def toApiV2WithValueObject: Map[IRI, Any]

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

    def valueTypeIri = OntologyConstants.KnoraBase.DateValue

    /**
      * Create knora-api assertions.
      *
      * @return a Map of knora-api value properties to numbers (year, month, day) representing the date value.
      */
    def toKnoraApiDateValueAssertions: Map[IRI, Int] = {
        DateUtilV2.convertDateValueContentV2ToKnoraApiDateAssertions(this)
    }

    def toApiV2WithValueObject: Map[IRI, Any] = {

        val knoraApiDateAssertions: Map[IRI, Int] = toKnoraApiDateValueAssertions

        Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> valueHasString,
            OntologyConstants.KnoraApiV2WithValueObject.DateValueHasCalendar -> valueHasCalendar.toString
        ) ++ knoraApiDateAssertions
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

    def valueTypeIri = OntologyConstants.KnoraBase.TextValue

    def toApiV2WithValueObject: Map[IRI, String] = {

        if (standoff.nonEmpty) {

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
                Map(OntologyConstants.KnoraApiV2WithValueObject.TextValueAsHtml -> xmlTransformedStr.toString)
            } else {
                // xml is returned
                Map(
                    OntologyConstants.KnoraApiV2WithValueObject.TextValueAsXml -> xmlFromStandoff,
                    OntologyConstants.KnoraApiV2WithValueObject.TextValueHasMapping -> standoff.get.mappingIri
                )
            }

        } else {
            // no markup given
            Map(OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> valueHasString)
        }

    }

}

/**
  * Represents standoff and the corresponding mapping.
  * May include an XSL transformation.
  *
  * @param standoff   a sequence of [[StandoffTagV1]].
  * @param mappingIri the Iri of the mapping
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

    def valueTypeIri = OntologyConstants.KnoraBase.IntValue

    def toApiV2WithValueObject: Map[IRI, Int] = {
        Map(
            OntologyConstants.KnoraApiV2WithValueObject.IntegerValueAsInteger -> valueHasInteger)
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

    def valueTypeIri = OntologyConstants.KnoraBase.DecimalValue

    def toApiV2WithValueObject: Map[IRI, BigDecimal] = {
        Map(
            OntologyConstants.KnoraApiV2WithValueObject.DecimalValueAsDecimal -> valueHasDecimal
        )
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

    def valueTypeIri = OntologyConstants.KnoraBase.BooleanValue

    def toApiV2WithValueObject: Map[IRI, Boolean] = {
        Map(
            OntologyConstants.KnoraApiV2WithValueObject.BooleanValueAsBoolean -> valueHasBoolean
        )
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

    def valueTypeIri = OntologyConstants.KnoraBase.GeomValue

    def toApiV2WithValueObject: Map[IRI, String] = {
        Map(
            OntologyConstants.KnoraApiV2WithValueObject.GeometryValueAsGeometry -> valueHasGeometry
        )
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

    def valueTypeIri = OntologyConstants.KnoraBase.IntervalValue

    def toApiV2WithValueObject: Map[IRI, BigDecimal] = {
        Map(
            OntologyConstants.KnoraApiV2WithValueObject.IntervalValueHasStart -> valueHasIntervalStart,
            OntologyConstants.KnoraApiV2WithValueObject.IntervalValueHasEnd -> valueHasIntervalEnd
        )
    }

}

/**
  * Represents a value pointing to a Knora hierarchical list node.
  *
  * @param valueHasString   the string representation of the hierarchical list node value.
  * @param valueHasListNode the Iri of the hierarchical list node pointed to.
  * @param comment          a comment on this `GeomValueContentV2`, if any.
  */
case class HierarchicalListValueContentV2(valueHasString: String, valueHasListNode: IRI, comment: Option[String]) extends ValueContentV2 {

    def valueTypeIri = OntologyConstants.KnoraBase.ListValue

    def toApiV2WithValueObject: Map[IRI, IRI] = {
        Map(
            OntologyConstants.KnoraApiV2WithValueObject.HierarchicalListValueAsListNode -> valueHasListNode
        )
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

    def valueTypeIri = OntologyConstants.KnoraBase.ColorValue

    def toApiV2WithValueObject: Map[IRI, String] = {
        Map(
            OntologyConstants.KnoraApiV2WithValueObject.ColorValueAsColor -> valueHasColor
        )
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

    def valueTypeIri = OntologyConstants.KnoraBase.UriValue

    def toApiV2WithValueObject: Map[IRI, String] = {
        Map(
            OntologyConstants.KnoraApiV2WithValueObject.UriValueAsUri -> valueHasUri
        )
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

    def valueTypeIri = OntologyConstants.KnoraBase.GeonameValue

    def toApiV2WithValueObject: Map[IRI, String] = {
        Map(
            OntologyConstants.KnoraApiV2WithValueObject.GeonameValueAsGeonameCode -> valueHasGeonameCode
        )
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
      * @return the path to the file as an absolute URL.
      */
    def toURL(): String
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
  * @param settings         settings object to access configurations.
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
                                        comment: Option[String],
                                        settings: SettingsImpl) extends FileValueContentV2 with ValueContentV2 {

    def valueTypeIri = OntologyConstants.KnoraBase.StillImageFileValue

    /**
      * Creates the URL to retrieve the file.
      *
      * @return the path to file value as an absolute URL.
      */
    def toURL: String = {
        s"${settings.sipiIIIFGetUrl}/${internalFilename}/full/${dimX},${dimY}/0/default.jpg"
    }

    def toApiV2WithValueObject: Map[IRI, Any] = {
        val imagePath: String = toURL

        Map(
            OntologyConstants.KnoraApiV2WithValueObject.FileValueAsUrl -> imagePath,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueIsPreview -> isPreview,
            OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValueHasDimX -> dimX,
            OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValueHasDimY -> dimY,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueHasFilename -> internalFilename,
            OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValueHasIIIFBaseUrl -> settings.sipiIIIFGetUrl
        )
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
  * @param settings         settings object to access configurations.
  */
case class TextFileValueContentV2(valueHasString: String, internalMimeType: String,
                                  internalFilename: String,
                                  originalFilename: String,
                                  originalMimeType: Option[String],
                                  comment: Option[String],
                                  settings: SettingsImpl) extends ValueContentV2 {

    def valueTypeIri = OntologyConstants.KnoraBase.TextFileValue

    /**
      * Creates the URL to retrieve the file.
      *
      * @return the path to file value as an absolute URL.
      */
    def toURL: String = {
        s"${settings.sipiFileServerGetUrl}/${internalFilename}"
    }

    def toApiV2WithValueObject: Map[IRI, String] = {
        val imagePath: String = toURL

        Map(
            OntologyConstants.KnoraApiV2WithValueObject.FileValueHasFilename -> internalFilename,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueAsUrl -> imagePath
        )
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

    def valueTypeIri = OntologyConstants.KnoraBase.LinkValue

    def toApiV2WithValueObject: Map[IRI, Object] = {
        // check if the referred resource has to be included in the JSON response
        referredResource match {
            case Some(targetResource: ReadResourceV2) =>
                // include the referred resource as a nested structure
                val referredResourceAsJsValue: util.Map[IRI, Object] = ReadResourceUtil.createValueMapFromReadResourceV2(targetResource)
                Map(OntologyConstants.KnoraApiV2WithValueObject.LinkValueHasTarget -> referredResourceAsJsValue)

            case None =>
                // just include the referred resource's Iri
                Map(OntologyConstants.KnoraApiV2WithValueObject.LinkValueHasTargetIri -> referredResourceIri)
        }
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
  * @param resourceIri   the Iri of the resource.
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
      * Serialize the response to JSON-LD.
      *
      * @return a string in JSON-LD format.
      */
    def toJSONLD: String

    /**
      * Serialize the response to XML.
      *
      * @return a string in XML format.
      */
    def toXML: String

    /**
      * The Knora webapi settings.
      */
    def settings: SettingsImpl
}

case class ReadNamedGraphsV2(namedGraphs: Set[IRI], settings: SettingsImpl) extends KnoraResponseV2 {

    def toJSONLD: String = {

        val context = new util.HashMap[String, String]()
        context.put(OntologyConstants.KnoraApi.KnoraApiOntologyLabel, OntologyConstants.KnoraApiV2WithValueObject.KnoraApiV2PrefixExpansion)

        val json: util.HashMap[String, Object] = new util.HashMap[String, Object]()

        val namedGraphIris: util.List[IRI] = namedGraphs.toSeq.asJava

        json.put("http://www.knora.org/ontology/knora-api#namedGraphs", namedGraphIris)

        val compacted = JsonLdProcessor.compact(json, context, new JsonLdOptions())

        JsonUtils.toPrettyString(compacted)

    }

    def toXML = ???
}

/**
  * Return information about ontology entities.
  *
  * @param ontologies      named graphs and their resource classes.
  * @param resourceClasses information about resource classes.
  * @param properties      information about properties.
  * @param language        the preferred language in which the information should be returned.
  *
  */
case class ReadEntityDefinitionsV2(ontologies: Map[IRI, Set[IRI]] = Map.empty[IRI, Set[IRI]],
                                   resourceClasses: Map[IRI, ResourceEntityInfoV1] = Map.empty[IRI, ResourceEntityInfoV1],
                                   properties: Map[IRI, PropertyEntityInfoV1] = Map.empty[IRI, PropertyEntityInfoV1], language: String, settings: SettingsImpl) extends KnoraResponseV2 {

    /**
      * Gets an object from predicate infos.
      *
      * @param predicateInfos information about predicates.
      * @param predicateIri   the Iri of the predicate whose object should be returned.
      * @param lang           the language in which the object has to be returned.
      * @return the requested object.
      */
    // TODO: use fallback language
    private def getObjectFromPredicateInfo(predicateInfos: Map[IRI, PredicateInfoV1], predicateIri: IRI, lang: Option[String] = None): Map[IRI, String] = {

        val fallbackLang: String = settings.fallbackLanguage

        predicateInfos.get(predicateIri) match {
            case Some(pred: PredicateInfoV1) =>
                if (lang.nonEmpty) {
                    // search for information in the given language

                    pred.objectsWithLang.get(lang.get) match {
                        case Some(obj: String) =>
                            Map(predicateIri -> obj)
                        case None =>
                            // information does not exist in the given language, try fallback language
                            pred.objectsWithLang.get(fallbackLang) match {
                                case Some(obj: String) =>
                                    Map(predicateIri -> obj)
                                case None => Map.empty[IRI, String]
                            }

                    }
                } else {

                    pred.objects.headOption match {
                        case Some(obj: String) =>
                            Map(predicateIri -> obj)

                        case None => Map.empty[IRI, String]
                    }
                }

            case None => Map.empty[IRI, String]
        }

    }

    def toJSONLD = {

        val context = new util.HashMap[String, String]()
        context.put(OntologyConstants.KnoraApi.KnoraApiOntologyLabel, OntologyConstants.KnoraApiV2WithValueObject.KnoraApiV2PrefixExpansion)
        context.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#")

        val json: util.HashMap[String, Object] = new util.HashMap[String, Object]()

        // ontologies

        val ontos: util.Map[IRI, util.List[IRI]] = ontologies.map {
            case (namedGraphIri: IRI, resourceClassIris: Set[IRI]) =>

                val resClassIris = seqAsJavaList(resourceClassIris.toSeq)

                namedGraphIri -> resClassIris

        }.asJava

        json.put("http://www.knora.org/ontology/knora-api#ontologies", ontos)

        // resource classes

        val resClasses = resourceClasses.map {
            case (resClassIri: IRI, resourceEntity: ResourceEntityInfoV1) =>

                val cardinalities = resourceEntity.cardinalities.map {

                    case (propertyIri: IRI, cardinality: Cardinality.Value) =>

                        val prop2card: (IRI, Int) = cardinality match {
                            case Cardinality.MayHaveMany =>
                                OntologyConstants.Owl.MinCardinality -> 0

                            case Cardinality.MayHaveOne =>
                                OntologyConstants.Owl.MaxCardinality -> 1

                            case Cardinality.MustHaveOne =>
                                OntologyConstants.Owl.Cardinality -> 1

                            case Cardinality.MustHaveSome =>
                                OntologyConstants.Owl.MinCardinality -> 1
                        }

                        Map(
                            "@type" -> OntologyConstants.Owl.Restriction,
                            OntologyConstants.Owl.OnProperty -> propertyIri,
                            prop2card
                        ).asJava
                }.toSeq.asJava

                resClassIri -> (Map(
                    "@id" -> resourceEntity.resourceClassIri,
                    "http://www.knora.org/ontology/knora-api#ontology" -> resourceEntity.ontologyIri,
                    "@type" -> OntologyConstants.Owl.Class, // TODO: does this need to be coming from the triplestore?
                    OntologyConstants.Rdfs.SubClassOf -> cardinalities
                ) ++ getObjectFromPredicateInfo(resourceEntity.predicates, OntologyConstants.KnoraBase.ResourceIcon)
                    ++ getObjectFromPredicateInfo(resourceEntity.predicates, OntologyConstants.Rdfs.Label, Some(language))
                    ++ getObjectFromPredicateInfo(resourceEntity.predicates, OntologyConstants.Rdfs.Comment, Some(language))).asJava
        }.asJava

        json.put("http://www.knora.org/ontology/knora-api#resourceClasses", resClasses)

        // properties

        val props: util.Map[IRI, util.Map[IRI, IRI]] = properties.map {
            case (propIri: IRI, propEntity: PropertyEntityInfoV1) =>

                propIri -> (Map(
                    "@id" -> propEntity.propertyIri,
                    "ontology" -> propEntity.ontologyIri
                ) ++ getObjectFromPredicateInfo(propEntity.predicates, OntologyConstants.Rdf.Type)
                    ++ getObjectFromPredicateInfo(propEntity.predicates, OntologyConstants.Rdfs.Label, Some(language))
                    ++ getObjectFromPredicateInfo(propEntity.predicates, OntologyConstants.Rdfs.Comment, Some(language))
                    ++ getObjectFromPredicateInfo(propEntity.predicates, OntologyConstants.KnoraBase.ObjectClassConstraint)).asJava


        }.asJava

        json.put("http://www.knora.org/ontology/knora-api#properties", props)

        val compacted = JsonLdProcessor.compact(json, context, new JsonLdOptions())

        JsonUtils.toPrettyString(compacted)


    }

    def toXML = ???

}

object ReadResourceUtil {
    /**
      * Creates a map of value properties to [[Object]] from a [[ReadResourceV2]].
      * This function is also used to provide bested structures (resources refer to other resources).
      *
      * @param resource the resource to be turned into `JsValue`.
      * @return a map of value properties to `JsValue`.
      */
    def createValueMapFromReadResourceV2(resource: ReadResourceV2): util.Map[IRI, Object] = {

        val values: Map[IRI, util.List[util.Map[IRI, Any]]] = resource.values.map {
            case (propIri: IRI, readValues: Seq[ReadValueV2]) =>
                val valuesMap: util.List[util.Map[IRI, Any]] = readValues.map {
                    (readValue: ReadValueV2) =>
                        val valAsMap: Map[IRI, Any] = readValue.valueContent.toApiV2WithValueObject

                        (Map("@id" -> readValue.valueIri,
                            "@type" -> readValue.valueContent.valueTypeIri) ++ valAsMap).asJava
                }.asJava

                (propIri, valuesMap)

        }

        (Map(
            "@type" -> resource.resourceClass,
            "http://schema.org/name" -> resource.label,
            "@id" -> resource.resourceIri
        ) ++ values).asJava

    }
}

/**
  * Represents a sequence of resources read back from Knora.
  *
  * @param numberOfResources the amount of resources returned.
  * @param resources         a sequence of resources.
  */
case class ReadResourcesSequenceV2(numberOfResources: Int, resources: Seq[ReadResourceV2], settings: SettingsImpl) extends KnoraResponseV2 {

    def toJSONLD = {

        val context = new util.HashMap[String, String]()
        context.put("@vocab", "http://schema.org/")
        context.put(OntologyConstants.KnoraApi.KnoraApiOntologyLabel, OntologyConstants.KnoraApiV2WithValueObject.KnoraApiV2PrefixExpansion)
        context.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#")

        val json: util.HashMap[String, Object] = new util.HashMap[String, Object]()

        val resSeq: util.List[util.Map[IRI, Object]] = resources.map {
            (resource: ReadResourceV2) =>

                ReadResourceUtil.createValueMapFromReadResourceV2(resource)

        }.asJava

        json.put("@type", "ItemList")

        json.put("http://schema.org/numberOfItems", new lang.Integer(numberOfResources))

        json.put("http://schema.org/itemListElement", resSeq)

        val compacted = JsonLdProcessor.compact(json, context, new JsonLdOptions())

        JsonUtils.toPrettyString(compacted)

    }

    def toXML = ???

}


