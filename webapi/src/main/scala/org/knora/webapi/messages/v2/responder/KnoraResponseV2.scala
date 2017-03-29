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
import org.knora.webapi.{IRI, Jsonable, OntologyConstants}
import spray.json._

/**
  * A trait for Knora API v1 response messages. Any response message can be converted into JSON.
  */
trait KnoraResponseV2 extends Jsonable

// Response Messages of the Knora API V2

/**
  * Represents a sequence of resources.
  *
  */
case class ResourcesV2(numberOfResources: Int, results: Seq[ResourceRowV2]) extends KnoraResponseV2 {
    def toJsValue = SearchV2JsonProtocol.searchResponseV2Format.write(this)
}

/**
  * Represents a resource.
  *
  * @param resourceIri
  * @param resourceClass
  * @param label
  * @param valueObjects
  */
case class ResourceRowV2(resourceIri: IRI, resourceClass: IRI, label: String, valueObjects: Seq[ValueRowV2])

/**
  * Represents a value that belongs to a resource.
  *
  * @param valueClass
  * @param value
  * @param valueObjectIri
  * @param propertyIri
  */
case class ValueRowV2(valueClass: IRI, value: Map[IRI, String], valueObjectIri: IRI, propertyIri: IRI)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about representations of a resource.
  */
object SearchV2JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit object searchResponseV2Format extends JsonFormat[ResourcesV2] {

        def read(jsonVal: JsValue) = ???

        def write(searchResultV2: ResourcesV2) = {

            val resourceResultRows: JsValue = searchResultV2.results.map {
                (resultRow: ResourceRowV2) =>

                    val valueObjects: Map[IRI, JsValue] = resultRow.valueObjects.foldLeft(Map.empty[IRI, Seq[JsValue]]) {
                        case (acc: Map[String, Seq[JsValue]], valObj: ValueRowV2) =>
                            if (acc.keySet.contains(valObj.propertyIri)) {
                                // the property Iri already exists, add to it
                                val existingValsforProp: Seq[JsValue] = acc(valObj.propertyIri)

                                val newValueLiteral: Map[String, JsValue] = valObj.value.map {
                                    case (valueProp, valueLiteral) => (valueProp, valueLiteral.toJson)
                                }

                                val newValueObjectForProp: JsValue = (Map("@type" -> valObj.valueClass.toJson, "@id" -> valObj.valueObjectIri.toJson) ++ newValueLiteral).toJson

                                val valuesForProp: Map[IRI, Seq[JsValue]] = Map(valObj.propertyIri -> (existingValsforProp :+ newValueObjectForProp))

                                acc ++ valuesForProp
                            } else {
                                // the property Iri does not exist yet, create it
                                val newValueLiteral: Map[String, JsValue] = valObj.value.map {
                                    case (valueProp, valueLiteral) => (valueProp, valueLiteral.toJson)
                                }

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

    implicit val searchValueResultRowV2Format: RootJsonFormat[ValueRowV2] = jsonFormat4(ValueRowV2)
    implicit val searchResourceResultRowV2Format: RootJsonFormat[ResourceRowV2] = jsonFormat4(ResourceRowV2)
}