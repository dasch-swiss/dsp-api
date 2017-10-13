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

package org.knora.webapi.e2e.v2

import java.util

import com.github.jsonldjava.core.{JsonLdOptions, JsonLdProcessor}
import com.github.jsonldjava.utils.JsonUtils
import org.knora.webapi.util.{JavaUtil, MessageUtil}
import org.knora.webapi.{IRI, InvalidApiJsonException}

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

object ResponseCheckerR2RV2 {

    private val numberOfItemsMember: IRI = "http://schema.org/numberOfItems"

    private val itemListElementMember: IRI = "http://schema.org/itemListElement"

    private val name: IRI = "http://schema.org/name"

    private val jsonldId = "@id"

    private val jsonldType = "@type"

    private val noPropertyKeys: Set[IRI] = Set(jsonldId, jsonldType, name)


    /**
      * Converts a single element to a list with one entry.
      * If given element is already a list, it will be returned unchanged.
      *
      * @param element the element to be turned into a list.
      * @return a list.
      */
    private def elementToList(element: Any): Seq[_] = {

        element match {
            case obj: Map[_, _] => Seq(obj)

            case objSeq: Seq[_] => objSeq

            case other => throw InvalidApiJsonException(s"expected sequence of objects or single object, but $other given")
        }
    }

    private def compareResources(expectedResource: Map[IRI, Any], receivedResource: Map[IRI, Any]) = {

        assert(expectedResource(jsonldId) == receivedResource(jsonldId), s"Received resource Iri ${receivedResource(jsonldId)} does not match expected Iri ${expectedResource(jsonldId)}")

        assert(expectedResource(jsonldType) == receivedResource(jsonldType), s"Received resource type ${receivedResource(jsonldType)} does not match expected type ${expectedResource(jsonldType)}")

        assert(expectedResource(name) == receivedResource(name), s"$name did not match for ${receivedResource(jsonldId)}")

        assert(expectedResource.keySet -- noPropertyKeys == receivedResource.keySet -- noPropertyKeys, s"property Iris are different for resource ${receivedResource(jsonldId)}")

        // TODO: handle LinkValues (recursion)
        (expectedResource -- noPropertyKeys).foreach {
            case (propIri: IRI, expectedValuesForProp: Any) =>

                // make sure that the property Iri exists in the received resource
                assert(receivedResource.contains(propIri), s"Property $propIri not found in received resource ${receivedResource(jsonldId)}")

                // compare the values of property
                assert(expectedValuesForProp == receivedResource(propIri), s"values for property $propIri did not match for ${receivedResource(jsonldId)}")

        }

    }

    /**
      * Compares the received JSON response to the expected JSON.
      *
      * @param expectedJSONLD expected answer from Knora API V2 as JSONLD.
      * @param receivedJSONLD received answer from Knora Api V2 as JSONLD.
      */
    def compareJSONLD(expectedJSONLD: String, receivedJSONLD: String): Unit = {

        val expectedResponseCompactedAsJava = JsonLdProcessor.compact(JsonUtils.fromString(expectedJSONLD), new util.HashMap[String, String](), new JsonLdOptions())

        val expectedResponseAsScala: Map[IRI, Any] = JavaUtil.deepJavatoScala(expectedResponseCompactedAsJava).asInstanceOf[Map[IRI, Any]]

        val receivedResponseCompactedAsJava = JsonLdProcessor.compact(JsonUtils.fromString(receivedJSONLD), new util.HashMap[String, String](), new JsonLdOptions())

        val receivedResponseAsScala: Map[IRI, Any] = JavaUtil.deepJavatoScala(receivedResponseCompactedAsJava).asInstanceOf[Map[IRI, Any]]

        // make sure the indicated amount of results is correct
        assert(expectedResponseAsScala(numberOfItemsMember).asInstanceOf[Int] == receivedResponseAsScala(numberOfItemsMember).asInstanceOf[Int], s"numberOfItems did not match: expected ${expectedResponseAsScala(numberOfItemsMember)}, but received ${receivedResponseAsScala(numberOfItemsMember)}")

        // returns a list also if there is only one element
        val expectedResourcesAsList: Seq[Map[IRI, Any]] = elementToList(expectedResponseAsScala(itemListElementMember)).asInstanceOf[Seq[Map[IRI, Any]]]

        // returns a list also if there is only one element
        val receivedResourcesAsList: Seq[Map[IRI, Any]] = elementToList(receivedResponseAsScala(itemListElementMember)).asInstanceOf[Seq[Map[IRI, Any]]]

        // check that the actual amount of resources returned is correct
        assert(expectedResourcesAsList.size == receivedResourcesAsList.size, s"received list of resources has wrong length")

        // loop over all the given resources and compare them (order of resources is determined by request)
        expectedResourcesAsList.zip(receivedResourcesAsList).foreach {


            case (expectedResource: Map[IRI, Any], receivedResource: Map[IRI, Any]) =>

                assert(expectedResource(jsonldId) == receivedResource(jsonldId), s"Received resource Iri ${receivedResource(jsonldId)} does not match expected Iri ${expectedResource(jsonldId)}")

                assert(expectedResource(jsonldType) == receivedResource(jsonldType), s"Received resource type ${receivedResource(jsonldType)} does not match expected type ${expectedResource(jsonldType)}")

                assert(expectedResource(name) == receivedResource(name), s"$name did not match for ${receivedResource(jsonldId)}")

                assert(expectedResource.keySet -- noPropertyKeys == receivedResource.keySet -- noPropertyKeys, s"property Iris are different for resource ${receivedResource(jsonldId)}")

                // TODO: handle LinkValues (recursion)
                (expectedResource -- noPropertyKeys).foreach {
                    case (propIri: IRI, expectedValuesForProp: Any) =>

                        // make sure that the property Iri exists in the received resource
                        assert(receivedResource.contains(propIri), s"Property $propIri not found in received resource ${receivedResource(jsonldId)}")

                        // compare the values of property
                        assert(expectedValuesForProp == receivedResource(propIri), s"values for property $propIri did not match for ${receivedResource(jsonldId)}")

                }

        }

    }

    /**
      * Checks for the number of expected results to be returned.
      *
      * @param responseJson   the response send back by the search route.
      * @param expectedNumber the expected number of results for the query.
      * @return an assertion that the actual amount of results corresponds with the expected number of results.
      */
    def checkNumberOfItems(responseJson: String, expectedNumber: Int): Unit = {

        val res = JsonUtils.fromString(responseJson)

        val compacted: Map[IRI, Any] = JsonLdProcessor.compact(res, new util.HashMap[String, String](), new JsonLdOptions()).asScala.toMap

        val numberOfItems: Any = compacted.getOrElse(numberOfItemsMember, throw InvalidApiJsonException(s"member '$numberOfItemsMember' not given for search response."))

        assert(numberOfItems.isInstanceOf[Int] && numberOfItems == expectedNumber, s"expected $expectedNumber resources, but $numberOfItems given")

    }

}