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

package org.knora.webapi.e2e.v2

import org.knora.webapi.util.jsonld._
import org.knora.webapi.{AssertionException, IRI, OntologyConstants}

object ResponseCheckerR2RV2 {

    private val numberOfItemsMember: IRI = "http://schema.org/numberOfItems"

    private val itemListElementMember: IRI = "http://schema.org/itemListElement"

    private val jsonldId = "@id"

    private val jsonldType = "@type"

    private val noPropertyKeys: Set[IRI] = Set(jsonldId, jsonldType, OntologyConstants.Rdfs.Label)


    /**
      * Converts a single JSON-LD element to a JSON-LD array with one entry.
      * If given element is already an array, it will be returned unchanged.
      *
      * @param element the element to be turned into an array.
      * @return a JSON-LD array.
      */
    private def elementToArray(element: JsonLDValue): JsonLDArray = {
        element match {
            case array: JsonLDArray => array
            case other: JsonLDValue => JsonLDArray(Seq(other))
        }
    }

    /**
      * Compare two value objects.
      *
      * @param expectedValue expected value.
      * @param receivedValue received value.
      */
    private def compareValues(expectedValue: JsonLDValue, receivedValue: JsonLDValue): Unit = {

        assert(expectedValue == receivedValue, s"expected $expectedValue, received $receivedValue")

        // TODO: recurse over target resource if it is a LinkValue

    }

    /**
      * Compares the received resource to the expected.
      *
      * @param expectedResource the expected resource.
      * @param receivedResource the received resource.
      */
    private def compareResources(expectedResource: JsonLDObject, receivedResource: JsonLDObject): Unit = {
        def sortPropertyValues(values: JsonLDArray): JsonLDArray = {
            // Sort by the value object IRI if available, otherwise sort by the value itself.

            val sortedElements = values.value.sortBy {
                case jsonLDObj: JsonLDObject => jsonLDObj.value(jsonldId)
                case other => other
            }

            JsonLDArray(sortedElements)
        }

        assert(expectedResource.value(jsonldId) == receivedResource.value(jsonldId), s"Received resource Iri ${receivedResource.value(jsonldId)} does not match expected Iri ${expectedResource.value(jsonldId)}")

        assert(expectedResource.value(jsonldType) == receivedResource.value(jsonldType), s"Received resource type ${receivedResource.value(jsonldType)} does not match expected type ${expectedResource.value(jsonldType)}")

        assert(expectedResource.value(OntologyConstants.Rdfs.Label) == receivedResource.value(OntologyConstants.Rdfs.Label), s"rdfs:label did not match for ${receivedResource.value(jsonldId)}")

        assert(expectedResource.value.keySet -- noPropertyKeys == receivedResource.value.keySet -- noPropertyKeys, s"property Iris are different for resource ${receivedResource.value(jsonldId)}: expected ${expectedResource.value.keySet -- noPropertyKeys}, received ${receivedResource.value.keySet -- noPropertyKeys}")

        (expectedResource.value -- noPropertyKeys).foreach {
            case (propIri: IRI, expectedValuesForProp: JsonLDValue) =>

                // make sure that the property Iri exists in the received resource
                assert(receivedResource.value.contains(propIri), s"Property $propIri not found in received resource ${receivedResource.value(jsonldId)}")

                val sortedExpectedPropertyValues: JsonLDArray = sortPropertyValues(elementToArray(expectedValuesForProp))
                val sortedReceivedPropertyValues: JsonLDArray = sortPropertyValues(elementToArray(receivedResource.value(propIri)))

                sortedExpectedPropertyValues.value.zip(sortedReceivedPropertyValues.value).foreach {
                    case (expectedVal, receivedVal) =>
                        compareValues(expectedVal, receivedVal)
                }


        }

    }

    /**
      * Compares the received to the expected response.
      *
      * @param expectedResponse expected response.
      * @param receivedResponse received response.
      */
    def compareParsedJSONLD(expectedResponse: JsonLDDocument, receivedResponse: JsonLDDocument): Unit = {

        // make sure the indicated amount of results is correct
        assert(expectedResponse.body.value(numberOfItemsMember).asInstanceOf[JsonLDInt] == receivedResponse.body.value(numberOfItemsMember).asInstanceOf[JsonLDInt], s"numberOfItems did not match: expected ${expectedResponse.body.value(numberOfItemsMember)}, but received ${receivedResponse.body.value(numberOfItemsMember)}")

        // returns a list also if there is only one element
        val expectedResourcesAsArray: JsonLDArray = elementToArray(expectedResponse.body.value(itemListElementMember))

        // returns a list also if there is only one element
        val receivedResourcesAsArray: JsonLDArray = elementToArray(receivedResponse.body.value(itemListElementMember))

        // check that the actual amount of resources returned is correct
        assert(expectedResourcesAsArray.value.size == receivedResourcesAsArray.value.size, s"received list of resources has wrong length")

        // loop over all the given resources and compare them (order of resources is determined by request)
        expectedResourcesAsArray.value.zip(receivedResourcesAsArray.value).foreach {
            case (expectedResource: JsonLDObject, receivedResource: JsonLDObject) =>
                compareResources(expectedResource, receivedResource)

            case (_, other) => throw AssertionException(s"Received resource is the wrong type of JSON element: ${other.getClass.getName}")
        }

    }

    /**
      * Compares the received JSON response to the expected JSON.
      *
      * @param expectedJSONLD expected answer from Knora API V2 as JSONLD.
      * @param receivedJSONLD received answer from Knora Api V2 as JSONLD.
      */
    def compareJSONLD(expectedJSONLD: String, receivedJSONLD: String): Unit = {

        val expectedJsonLDDocument = JsonLDUtil.parseJsonLD(expectedJSONLD)
        val receivedJsonLDDocument = JsonLDUtil.parseJsonLD(receivedJSONLD)

        compareParsedJSONLD(expectedResponse = expectedJsonLDDocument, receivedResponse = receivedJsonLDDocument)

    }

    /**
      * Checks for the number of expected results to be returned.
      *
      * @param receivedJSONLD   the response send back by the search route.
      * @param expectedNumber the expected number of results for the query.
      * @return an assertion that the actual amount of results corresponds with the expected number of results.
      */
    def checkCountQuery(receivedJSONLD: String, expectedNumber: Int): Unit = {

        val receivedJsonLDDocument = JsonLDUtil.parseJsonLD(receivedJSONLD)

        // make sure the indicated amount of results is correct
        assert(receivedJsonLDDocument.body.value(numberOfItemsMember).asInstanceOf[JsonLDInt].value == expectedNumber, s"$numberOfItemsMember is incorrect.")


    }

}