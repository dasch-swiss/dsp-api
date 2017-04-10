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

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.messages.v1.responder.valuemessages.{JulianDayNumberValueV1, KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.util.{DateUtilV1, InputValidation}
import org.knora.webapi.util.DateUtilV1.DateRange
import org.knora.webapi.{IRI, Jsonable, OntologyConstants}
import spray.json._

/**
  * The value of a Knora property.
  * Any implementation of `ValueV2` is an API operation specific wrapper of a `ValueObjectV2`.
  */
sealed trait ValueV2

/**
  * The value of a Knora property read back from the triplestore.
  *
  * @param valueObjectIri the Iri of the value object.
  * @param value          the value.
  */
case class ReadValueV2(valueObjectIri: IRI, value: ValueObjectV2) extends ValueV2

/**
  * The value of a Knora property sent to Knora to be created.
  *
  * @param resourceIri the resource the new value should be attached to.
  * @param propertyIri the property of the new value.
  * @param value       the new value.
  */
case class CreateValueV2(resourceIri: IRI, propertyIri: IRI, value: ValueObjectV2) extends ValueV2

/**
  * The new version of a value of a Knora property to be created.
  *
  * @param valueObjectIri the value object to be updated.
  * @param value          the new version of the value.
  */
case class UpdateValueV2(valueObjectIri: IRI, value: ValueObjectV2) extends ValueV2

/**
  * The value object of a Knora property.
  */
sealed trait ValueObjectV2 {
    /**
      * The IRI of the Knora value type corresponding to the type of this `ValueObjectV2`.
      */
    def valueTypeIri: IRI

    /**
      * The string representation of this `ValueObjectV2`.
      */
    def valueHasString: String

    /**
      * A comment on this `ValueObjectV2`, if any.
      */
    def comment: Option[String]

    /**
      * A representation of the `ValueObjectV2` in JSON.
      */
    def toJsValueMap: Map[IRI, JsValue]

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
  * @param comment                a comment on this `ValueObjectV2`, if any.
  */
case class DateValueObjectV2(valueHasString: String,
                             valueHasStartJDN: Int,
                             valueHasEndJDN: Int,
                             valueHasStartPrecision: KnoraPrecisionV1.Value,
                             valueHasEndPrecision: KnoraPrecisionV1.Value,
                             valueHasCalendar: KnoraCalendarV1.Value,
                             comment: Option[String]) extends ValueObjectV2 {

    def valueTypeIri = OntologyConstants.KnoraBase.DateValue

    /**
      * Represents the JDN format in a string format representing the precision.
      *
      * @return a tuple (dateStartString, dateEndString)
      */
    def toDateStr: (String, String) = {

        val dateStart: String = DateUtilV1.julianDayNumber2DateString(valueHasStartJDN, valueHasCalendar, valueHasStartPrecision)
        val dateEnd: String = DateUtilV1.julianDayNumber2DateString(valueHasEndJDN, valueHasCalendar, valueHasEndPrecision)

        (dateStart, dateEnd)
    }

    /**
      *
      * Generate JDN format from date strings (containing precision).
      *
      * @param dateStartStr the begin of the period.
      * @param dateEndStr the end of the period.
      * @param calendarStr the calendar being used.
      * @return a tuple (dateStartJDN, dateEndJDN).
      */
    def fromDateStr(dateStartStr: String, dateEndStr: String, calendarStr: String): (JulianDayNumberValueV1, JulianDayNumberValueV1) = {

        val calendar: KnoraCalendarV1.Value = KnoraCalendarV1.lookup(calendarStr)

        val dateStart: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(calendar.toString + InputValidation.calendar_separator + dateStartStr)

        val dateEnd: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(calendar.toString + InputValidation.calendar_separator + dateEndStr)


        (dateStart, dateEnd)

    }

    def toJsValueMap: Map[IRI, JsString] = {
        val dateStrings = toDateStr

        Map(
            OntologyConstants.KnoraBase.ValueHasString -> JsString(valueHasString),
            OntologyConstants.KnoraBase.ValueHasCalendar -> JsString(valueHasCalendar.toString),
            "valueHasStartDate" -> JsString(dateStrings._1),
            "valueHasEndDate" -> JsString(dateStrings._2)
        )
    }

}

/**
  * Represents a Knora text value.
  *
  * @param valueHasString the string representation of the text (without markup).
  * @param comment        a comment on this `ValueObjectV2`, if any.
  */
case class TextValueObjectV2(valueHasString: String, comment: Option[String]) extends ValueObjectV2 {

    def valueTypeIri = OntologyConstants.KnoraBase.TextValue

    def toJsValueMap = {
        Map(OntologyConstants.KnoraBase.ValueHasString -> JsString(valueHasString))
    }

}

/**
  * Represents a Knora integer value.
  *
  * @param valueHasString  the string representation of the integer.
  * @param valueHasInteger the integer value.
  * @param comment         a comment on this `ValueObjectV2`, if any.
  */
case class IntegerValueObjectV2(valueHasString: String, valueHasInteger: Int, comment: Option[String]) extends ValueObjectV2 {

    def valueTypeIri = OntologyConstants.KnoraBase.ValueHasInteger

    def toJsValueMap = {
        Map(OntologyConstants.KnoraBase.ValueHasString -> JsString(valueHasString),
            OntologyConstants.KnoraBase.ValueHasInteger -> JsNumber(valueHasInteger))
    }

}

/**
  * Represents a Knora decimal value.
  *
  * @param valueHasString  the string representation of the decimal.
  * @param valueHasDecimal the decimal value.
  * @param comment         a comment on this `ValueObjectV2`, if any.
  */
case class DecimalValueObjectV2(valueHasString: String, valueHasDecimal: BigDecimal, comment: Option[String]) extends ValueObjectV2 {

    def valueTypeIri = OntologyConstants.KnoraBase.DecimalValue

    def toJsValueMap = {
        Map(OntologyConstants.KnoraBase.ValueHasString -> JsString(valueHasString),
            OntologyConstants.KnoraBase.ValueHasInteger -> JsNumber(valueHasDecimal))
    }

}

/**
  * Represents a Knora link value.
  *
  * @param valueHasString the string representation of the referred resource.
  * @param subject the source of the link.
  * @param predicate the link's predicate.
  * @param referredResourceIri the link's target.
  * @param comment a comment on the link.
  * @param referredResource information about the referred resource, if given.
  */
case class LinkValueObjectV2(valueHasString: String, subject: IRI, predicate: IRI, referredResourceIri: IRI, comment: Option[String], referredResource: Option[ReferredResourceV2] = None) extends ValueObjectV2 {

    def valueTypeIri = OntologyConstants.KnoraBase.LinkValue

    def toJsValueMap = {

        // if given, include information about the referred resource
        val referredResourceInfoOption: Map[IRI, JsValue] = if (referredResource.nonEmpty) {
            Map("referredResource" -> JsObject(Map("ReferredResourceType" -> JsString(referredResource.get.resourceClass),
                "ReferredResourceLabel" -> JsString(referredResource.get.label))))
        } else {
            Map.empty[IRI, JsValue]
        }

        Map(OntologyConstants.KnoraBase.ValueHasString -> JsString(valueHasString),
            OntologyConstants.Rdf.Subject -> JsString(subject),
            OntologyConstants.Rdf.Predicate -> JsString(predicate),
            OntologyConstants.KnoraBase.HasLinkTo -> JsString(referredResourceIri)
        ) ++ referredResourceInfoOption
    }

}

/**
  * Represents information about a resource referred to.
  *
  * @param label the label of the referred resource.
  * @param resourceClass the resource class of the referred resource.
  */
case class ReferredResourceV2(label: String, resourceClass: IRI)

/**
  * Represents a Knora resource.
  * Any implementation of `ResourceV2` is API operation specific.
  */
sealed trait ResourceV2 {
    def resourceClass: IRI

    def label: String

    def valueObjects: Map[IRI, Seq[ValueV2]]

    def resourceInfos: Map[IRI, LiteralV2]

}

/**
  * Represents a Knora resource when being read back from the triplestore.
  *
  * @param resourceIri   the Iri of the resource.
  * @param label         the resource's label.
  * @param resourceClass the class the resource belongs to.
  * @param valueObjects  the resource's values.
  * @param resourceInfos additional information attached to the resource.
  */
case class ReadResourceV2(resourceIri: IRI, label: String, resourceClass: IRI, valueObjects: Map[IRI, Seq[ReadValueV2]], resourceInfos: Map[IRI, LiteralV2]) extends ResourceV2

/**
  * Represents a Knora resource that is about to be created.
  *
  * @param label         the resource's label.
  * @param resourceClass the class the resource belongs to.
  * @param valueObjects  the resource's values.
  * @param resourceInfos additional information attached to the resource (literals).
  */
case class CreateResource(label: String, resourceClass: IRI, valueObjects: Map[IRI, Seq[CreateValueV2]], resourceInfos: Map[IRI, LiteralV2]) extends ResourceV2

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
  * A trait for Knora API v2 response messages. Any response message can be converted into JSON.
  */
trait KnoraResponseV2 extends Jsonable

/**
  * Represents a sequence of resources read back from Knora.
  *
  * @param numberOfResources the amount of resources returned.
  * @param resources         a sequence of resources.
  */
case class ReadResourcesSequenceV2(numberOfResources: Int, resources: Seq[ReadResourceV2]) extends KnoraResponseV2 {
    override def toJsValue = ResourcesV2JsonProtocol.readResourcesSequenceV2Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about representations of a resource.
  */
object ResourcesV2JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit object readResourcesSequenceV2Format extends JsonFormat[ReadResourcesSequenceV2] {

        def read(jsonVal: JsValue) = ???

        def write(resourcesSequenceV2: ReadResourcesSequenceV2) = {

            val resources: JsValue = resourcesSequenceV2.resources.map {
                (resource: ReadResourceV2) =>

                    val valueObjects: Map[IRI, JsValue] = resource.valueObjects.map {
                        case (propIri: IRI, readValues: Seq[ReadValueV2]) =>

                            val valuesMap: JsValue = readValues.map {
                                row =>
                                    val valAsMap: Map[IRI, JsValue] = row.value.toJsValueMap
                                    Map("@id" -> JsString(row.valueObjectIri),
                                        "@type" -> JsString(row.value.valueTypeIri)) ++ valAsMap
                            }.toJson

                            (propIri, valuesMap)

                    }

                    Map(
                        "@type" -> resource.resourceClass.toJson,
                        "name" -> resource.label.toJson,
                        "@id" -> resource.resourceIri.toJson
                    ) ++ valueObjects

            }.toJson

            val fields = Map(
                "@context" -> Map(
                    "@vocab" -> "http://schema.org/".toJson
                ).toJson,
                "@type" -> "ItemList".toJson,
                "numberOfItems" -> resourcesSequenceV2.numberOfResources.toJson,
                "itemListElement" -> resources
            )

            JsObject(fields)

        }

    }

}