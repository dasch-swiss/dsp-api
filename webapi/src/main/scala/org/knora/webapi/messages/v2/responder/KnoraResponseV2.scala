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
import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.{IRI, Jsonable, OntologyConstants}
import spray.json._


/*case class ResourceValueData(resourceIri: IRI, valueData: Map[IRI, Seq[ValueV2]])
case class ResourceV2(resourceValueData: ResourceValueData, otherStuff: Map[IRI, String])*/


sealed trait ValueV2 {
    /**
      * The IRI of the Knora value type corresponding to the type of this `ValueV2`.
      */
    def valueTypeIri: IRI

    def valueHasString: String

    /**
      * A comment on the value, if any.
      */
    def comment: Option[String]

}

case class ReadValueV2(valueObjectIri: IRI, value: ValueV2)

case class CreateValueV2(resourceIri: IRI, propertyIri: IRI, value: ValueV2)

case class UpdateValueV2(valueObjectIri: IRI, value: ValueV2)

case class DateValueV2(valueHasString: String,
                       valueHasStartJDN: Int,
                       valueHasEndJDN: Int,
                       valueHasStartPrecision: KnoraPrecisionV1.Value,
                       valueHasEndPrecision: KnoraPrecisionV1.Value,
                       valueHasCalendar: KnoraCalendarV1.Value,
                       comment: Option[String]) extends ValueV2 {

    def valueTypeIri = OntologyConstants.KnoraBase.DateValue

}

case class TextValueV2(valueHasString: String, comment: Option[String]) extends ValueV2 {

    def valueTypeIri = OntologyConstants.KnoraBase.TextValue

}

case class IntegerValueV2(valueHasString: String, valueHasInteger: Int, comment: Option[String]) extends ValueV2 {

    def valueTypeIri = OntologyConstants.KnoraBase.ValueHasInteger

}

case class DecimalValueV2(valueHasString: String, valueHasDecimal: BigDecimal, comment: Option[String]) extends ValueV2 {

    def valueTypeIri = OntologyConstants.KnoraBase.DecimalValue

}

case class ResourceV2_(resourceClass: IRI, label: String, valueObjects: Map[IRI, Seq[ValueV2]], resourceInfos: Map[IRI, LiteralV2_] = Map.empty[IRI, LiteralV2_])

case class ReadResourceV2_(resourceIri: IRI, resourceProperties: ResourceV2_)

case class CreateResource(resourceProperties: ResourceV2_)

sealed trait LiteralV2_

case class StringLiteralV2_(value: String) extends LiteralV2_

case class IntegerLiteralV2_(value: Int) extends LiteralV2_

case class DecimalLiteralV2_(value: BigDecimal) extends LiteralV2_

case class BooleanLiteralV2_(value: Boolean) extends LiteralV2_

/**
  * A trait for Knora API V2 response messages. Any response message can be converted into JSON.
  */
trait KnoraResponseV2 extends Jsonable

// Response Messages of the Knora API V2

/**
  * Represents a sequence of resources.
  *
  */
case class ResourcesSequenceV2(numberOfResources: Int, results: Seq[ResourceV2]) extends KnoraResponseV2 {
    def toJsValue = SearchV2JsonProtocol.searchResponseV2Format.write(this)
}

/**
  * Represents a single resource.
  *
  * @param resourceIri   the Iri of the resource.
  * @param resourceClass the class of the resource.
  * @param label         the label of the resource.
  * @param valueObjects  the values belonging to the resource.
  */
case class ResourceV2(resourceIri: IRI, resourceClass: IRI, label: String, valueObjects: Seq[ValueObjectV2])

/**
  * Represents a value object that belongs to a resource.
  *
  * @param valueClass     the class of the value.
  * @param valueLiterals  the literals that belong to the value object and represent a possibly complex value.
  * @param valueObjectIri the Iri of the value object.
  * @param propertyIri    the Iri of the property pointing to the value object from the resource.
  */
case class ValueObjectV2(valueClass: IRI, valueLiterals: Seq[ValueLiteralV2], valueObjectIri: IRI, propertyIri: IRI)

/*
    A trait representing a value literal.
 */
trait ValueLiteralV2 {

    // the value object property connecting the value object and the value literal.
    val valueObjectProperty: IRI

    // turns a `ValueLiteralV2` into a Map[valueObjectProperty -> JsValue]
    // the JsValue is the type specific representation of the literal (Number, String etc.)
    def toMap: Map[IRI, JsValue]
}

/**
  * Represents a String value literal.
  *
  * @param valueObjectProperty the value object property connecting the value object and the value literal.
  * @param value               the literal's type specific value.
  */
case class StringValueLiteralV2(valueObjectProperty: IRI, value: String) extends ValueLiteralV2 {
    def toMap: Map[IRI, JsValue] = Map(valueObjectProperty -> JsString(value))
}

/**
  * Represents an Integer value literal.
  *
  * @param valueObjectProperty the value object property connecting the value object and the value literal.
  * @param value               the literal's type specific value.
  */
case class IntegerValueLiteralV2(valueObjectProperty: IRI, value: Int) extends ValueLiteralV2 {
    def toMap: Map[IRI, JsValue] = Map(valueObjectProperty -> JsNumber(value))
}

/**
  * Represents a Decimal value literal.
  *
  * @param valueObjectProperty the value object property connecting the value object and the value literal.
  * @param value               the literal's type specific value.
  */
case class DecimalValueLiteralV2(valueObjectProperty: IRI, value: BigDecimal) extends ValueLiteralV2 {
    def toMap: Map[IRI, JsValue] = Map(valueObjectProperty -> JsNumber(value))
}

/**
  * Represents a Boolean value literal.
  *
  * @param valueObjectProperty the value object property connecting the value object and the value literal.
  * @param value               the literal's type specific value.
  */
case class BooleanValueLiteralV2(valueObjectProperty: IRI, value: Boolean) extends ValueLiteralV2 {
    def toMap: Map[IRI, JsValue] = Map(valueObjectProperty -> JsBoolean(value))
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about representations of a resource.
  */
object SearchV2JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    // TODO: this is kind of a hack as this should never be called
    // TODO: look for a clean solution
    implicit object literalV2Format extends JsonFormat[ValueLiteralV2] {

        def read(jsonVal: JsValue) = ???

        def write(literalV2: ValueLiteralV2) = ???

    }

    implicit object searchResponseV2Format extends JsonFormat[ResourcesSequenceV2] {

        def read(jsonVal: JsValue) = ???

        def write(searchResultV2: ResourcesSequenceV2) = {

            val resourceResultRows: JsValue = searchResultV2.results.map {
                (resultRow: ResourceV2) =>

                    val valueObjects: Map[IRI, JsValue] = resultRow.valueObjects.foldLeft(Map.empty[IRI, Seq[JsValue]]) {
                        case (acc: Map[String, Seq[JsValue]], valObj: ValueObjectV2) =>
                            if (acc.keySet.contains(valObj.propertyIri)) {
                                // the property Iri already exists, add to it
                                val existingValsforProp: Seq[JsValue] = acc(valObj.propertyIri)

                                // make it a Map so further information can be attached before converting to a JsValue
                                // TODO: look for a better solution that makes use of the JSON formatter of Jsonable
                                val newValueLiteral: Map[IRI, JsValue] = valObj.valueLiterals.flatMap {
                                    case (valueLiteral: ValueLiteralV2) => valueLiteral.toMap
                                }.toMap

                                val newValueObjectForProp: JsValue = (Map("@type" -> valObj.valueClass.toJson, "@id" -> valObj.valueObjectIri.toJson) ++ newValueLiteral).toJson

                                val valuesForProp: Map[IRI, Seq[JsValue]] = Map(valObj.propertyIri -> (existingValsforProp :+ newValueObjectForProp))

                                acc ++ valuesForProp
                            } else {
                                // the property Iri does not exist yet, create it

                                // make it a Map so further information can be attached before converting to a JsValue
                                // TODO: look for a better solution that makes use of the JSON formatter of Jsonable
                                val newValueLiteral: Map[IRI, JsValue] = valObj.valueLiterals.flatMap {
                                    case (valueLiteral: ValueLiteralV2) => valueLiteral.toMap
                                }.toMap

                                val newValueObjectForProp = (Map("@type" -> valObj.valueClass.toJson, "@id" -> valObj.valueObjectIri.toJson) ++ newValueLiteral).toJson

                                val valueObjectForProp: Map[IRI, Vector[JsValue]] = Map(valObj.propertyIri -> Vector(newValueObjectForProp))


                                acc ++ valueObjectForProp
                            }

                    }.map {
                        case (propIri, values) =>
                            propIri -> values.toJson
                    }


                    val values = Map(
                        "@type" -> resultRow.resourceClass.toJson,
                        "name" -> resultRow.label.toJson,
                        "@id" -> resultRow.resourceIri.toJson
                    )

                    values ++ valueObjects

            }.toJson

            val fields = Map(
                "@context" -> Map(
                    "@vocab" -> "http://schema.org/".toJson
                ).toJson,
                "@type" -> "ItemList".toJson,
                "numberOfItems" -> searchResultV2.numberOfResources.toJson,
                "itemListElement" -> resourceResultRows
            )

            JsObject(fields)
        }
    }

    implicit val stringLiteralV2Format: RootJsonFormat[StringValueLiteralV2] = jsonFormat2(StringValueLiteralV2)
    // TODO: this is not used, look for a clean solution
    implicit val integerLiteralV2Format: RootJsonFormat[IntegerValueLiteralV2] = jsonFormat2(IntegerValueLiteralV2)
    // TODO: this is not used, look for a clean solution
    implicit val decimalLiteralV2Format: RootJsonFormat[DecimalValueLiteralV2] = jsonFormat2(DecimalValueLiteralV2)
    // TODO: this is not used, look for a clean solution
    implicit val valueRowV2Format: RootJsonFormat[ValueObjectV2] = jsonFormat4(ValueObjectV2)
    implicit val resourceRowV2Format: RootJsonFormat[ResourceV2] = jsonFormat4(ResourceV2)
}