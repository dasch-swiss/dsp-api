package org.knora.webapi.messages.v2.responder.searchmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.{IRI, OntologyConstants}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder.{KnoraRequestV2, KnoraResponseV2}
import spray.json._

import scala.collection.immutable.Iterable

/**
  * An abstract trait for messages that can be sent to `SearchResponderV1`.
  */
sealed trait SearchResponderRequestV2 extends KnoraRequestV2 {

    def userProfile: UserProfileV1
}


/**
  * Requests a fulltext search. A successful response will be a [[SearchGetResponseV2]].
  *
  * @param userProfile the profile of the user making the request.
  */
case class FulltextSearchGetRequestV2(searchValue: String,
                                      userProfile: UserProfileV1) extends SearchResponderRequestV2

/**
  * Represents a response to a user search query (both fulltext and extended search)
  *
  */
case class SearchGetResponseV2(nhits: Int, results: Seq[SearchResourceResultRowV2]) extends KnoraResponseV2 {
    def toJsValue = SearchV1JsonProtocol.searchResponseV2Format.write(this)
}


case class SearchResourceResultRowV2(resourceIri: IRI, resourceClass: IRI, label: String, valueObjects: Seq[SearchValueResultRowV2])

case class SearchValueResultRowV2(valueClass: IRI, value: String, valueObjectIri: IRI, propertyIri: IRI)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about representations of a resource.
  */
object SearchV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit object searchResponseV2Format extends JsonFormat[SearchGetResponseV2] {

        def read(jsonVal: JsValue) = ???

        def write(searchResultV2: SearchGetResponseV2) = {

            val resourceResultRows: JsValue = searchResultV2.results.map {
                (resultRow: SearchResourceResultRowV2) =>

                    val valueObjects: Map[IRI, JsValue] = resultRow.valueObjects.foldLeft(Map.empty[IRI, Seq[JsValue]]) {
                        case (acc: Map[String, Seq[JsValue]], valObj: SearchValueResultRowV2) =>
                            if (acc.keySet.contains(valObj.propertyIri)) {
                                // the property Iri already exists, add to it
                                val existingValsforProp: Seq[JsValue] = acc(valObj.propertyIri)

                                acc ++ Map(valObj.propertyIri -> (existingValsforProp :+ Map(OntologyConstants.KnoraBase.ValueHasString -> valObj.value.toJson, "@type" -> valObj.valueClass.toJson, "@id" -> valObj.valueObjectIri.toJson).toJson))

                            } else {
                                // the property Iri does not exist yet, create it
                                acc ++ Map(valObj.propertyIri -> Vector(Map(OntologyConstants.KnoraBase.ValueHasString -> valObj.value.toJson, "@type" -> valObj.valueClass.toJson, "@id" -> valObj.valueObjectIri.toJson).toJson))
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
                "numberOfItems" -> searchResultV2.nhits.toJson,
                "itemListElement" -> resourceResultRows
            )

            JsObject(fields)
        }
    }

    implicit val searchValueResultRowV2Format: RootJsonFormat[SearchValueResultRowV2] = jsonFormat4(SearchValueResultRowV2)
    implicit val searchResourceResultRowV2Format: RootJsonFormat[SearchResourceResultRowV2] = jsonFormat4(SearchResourceResultRowV2)
    //implicit val searchResponseV2Format: RootJsonFormat[SearchGetResponseV2] = jsonFormat2(SearchGetResponseV2)

}