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

package org.knora.webapi.util

import org.knora.webapi._
import org.knora.webapi.util.IriConversions._

/**
  * Tests [[StringFormatter]].
  *
  * Calendar:YYYY[-MM[-DD]][ EE][:YYYY[-MM[-DD]][ EE]]
  */
class StringFormatterSpec extends CoreSpec() {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    "The StringFormatter class" should {

        "not accept 2017-05-10" in {
            val dateString = "2017-05-10"
            assertThrows[BadRequestException] {
                stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Not accepted $dateString"))
            }
        }

        "accept GREGORIAN:2017" in {
            val dateString = "GREGORIAN:2017"
            stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:2017-05" in {
            val dateString = "GREGORIAN:2017-05"
            stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:2017-05-10" in {
            val dateString = "GREGORIAN:2017-05-10"
            stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:2017-05-10:2017-05-12" in {
            val dateString = "GREGORIAN:2017-05-10:2017-05-12"
            stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:500-05-10 BC" in {
            val dateString = "GREGORIAN:500-05-10 BC"
            stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:500-05-10 AD" in {
            val dateString = "GREGORIAN:500-05-10 AD"
            stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:500-05-10 BC:5200-05-10 AD" in {
            val dateString = "GREGORIAN:500-05-10 BC:5200-05-10 AD"
            stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept JULIAN:50 BCE" in {
            val dateString = "JULIAN:50 BCE"
            stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept JULIAN:1560-05 CE" in {
            val dateString = "JULIAN:1560-05 CE"
            stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept JULIAN:217-05-10 BCE" in {
            val dateString = "JULIAN:217-05-10 BCE"
            stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept JULIAN:2017-05-10:2017-05-12" in {
            val dateString = "JULIAN:2017-05-10:2017-05-12"
            stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept JULIAN:2017:2017-5-12" in {
            val dateString = "JULIAN:2017:2017-5-12"
            stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Not accepted $dateString"))
        }
        
        "accept JULIAN:500 BCE:400 BCE" in {
            val dateString = "JULIAN:500 BCE:400 BCE"
            stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Not accepted $dateString"))
        }
        
        "accept GREGORIAN:10 BC:1 AD" in {
            val dateString = "GREGORIAN:10 BC:1 AD"
            stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Not accepted $dateString"))
        }
        
        "not accept month 00" in {
            val dateString = "GREGORIAN:2017-00:2017-02"
            assertThrows[BadRequestException] {
                stringFormatter.validateDate(dateString, () => throw BadRequestException(s"month 00 in $dateString Not accepted" ))
            }
        }
        
        "not accept day 00" in {
            val dateString = "GREGORIAN:2017-01-00"
            assertThrows[BadRequestException] {
                stringFormatter.validateDate(dateString, () => throw BadRequestException(s"day 00 in $dateString Not accepted" ))
            }
        }
        
        "not accept year 0" in {
            val dateString = "GREGORIAN:0 BC"
            assertThrows[BadRequestException] {
                stringFormatter.validateDate(dateString, () => throw BadRequestException(s"Year 0 is Not accepted $dateString"))
            }
        }

        "recognize the url of the dhlab site as a valid IRI" in {
            val testUrl: String = "http://dhlab.unibas.ch/"
            val validIri = stringFormatter.validateAndEscapeIri(testUrl, () => throw BadRequestException(s"Invalid IRI $testUrl"))
            validIri should be(testUrl)
        }

        "recognize the url of the DaSCH site as a valid IRI" in {
            val testUrl = "http://dasch.swiss"
            val validIri = stringFormatter.validateAndEscapeIri(testUrl, () => throw BadRequestException(s"Invalid IRI $testUrl"))
            validIri should be(testUrl)
        }

        "convert http://www.knora.org/ontology/knora-base to http://api.knora.org/ontology/knora-api/simple/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/knora-base".toSmartIri
            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
            externalOntologyIri.toString should ===("http://api.knora.org/ontology/knora-api/simple/v2")
        }

        "convert http://www.knora.org/ontology/knora-base#Resource to http://api.knora.org/ontology/knora-api/simple/v2#Resource" in {
            val internalEntityIri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri
            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
            externalEntityIri.toString should ===("http://api.knora.org/ontology/knora-api/simple/v2#Resource")
        }

        "convert http://www.knora.org/ontology/knora-base to http://api.knora.org/ontology/knora-api/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/knora-base".toSmartIri
            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)
            externalOntologyIri.toString should ===("http://api.knora.org/ontology/knora-api/v2")
        }

        "convert http://www.knora.org/ontology/knora-base#Resource to http://api.knora.org/ontology/knora-api/v2#Resource" in {
            val internalEntityIri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri
            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2WithValueObjects)
            externalEntityIri.toString should ===("http://api.knora.org/ontology/knora-api/v2#Resource")
        }

        "convert http://api.knora.org/ontology/knora-api/simple/v2 to http://www.knora.org/ontology/knora-base" in {
            val externalOntologyIri = "http://api.knora.org/ontology/knora-api/simple/v2".toSmartIri
            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/knora-base")
        }

        "convert http://api.knora.org/ontology/knora-api/simple/v2#Resource to http://www.knora.org/ontology/knora-base#Resource" in {
            val externalEntityIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri
            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/knora-base#Resource")
        }

        "convert http://api.knora.org/ontology/knora-api/v2 to http://www.knora.org/ontology/knora-base" in {
            val externalOntologyIri = "http://api.knora.org/ontology/knora-api/v2".toSmartIri
            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/knora-base")
        }

        "convert http://api.knora.org/ontology/knora-api/v2#Resource to http://www.knora.org/ontology/knora-base#Resource" in {
            val externalEntityIri = "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri
            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/knora-base#Resource")
        }

        "convert http://www.knora.org/ontology/0001/example to http://0.0.0.0:3333/ontology/0001/example/simple/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/0001/example".toSmartIri
            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
            externalOntologyIri.toString should ===("http://0.0.0.0:3333/ontology/0001/example/simple/v2")
        }

        "convert http://www.knora.org/ontology/0001/example#ExampleThing to http://0.0.0.0:3333/ontology/0001/example/simple/v2#ExampleThing" in {
            val internalEntityIri = "http://www.knora.org/ontology/0001/example#ExampleThing".toSmartIri
            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
            externalEntityIri.toString should ===("http://0.0.0.0:3333/ontology/0001/example/simple/v2#ExampleThing")
        }

        "convert http://www.knora.org/ontology/0001/example to http://0.0.0.0:3333/ontology/0001/example/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/0001/example".toSmartIri
            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)
            externalOntologyIri.toString should ===("http://0.0.0.0:3333/ontology/0001/example/v2")
        }

        "convert http://www.knora.org/ontology/0001/example#ExampleThing to http://0.0.0.0:3333/ontology/0001/example/v2#ExampleThing" in {
            val internalEntityIri = "http://www.knora.org/ontology/0001/example#ExampleThing".toSmartIri
            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2WithValueObjects)
            externalEntityIri.toString should ===("http://0.0.0.0:3333/ontology/0001/example/v2#ExampleThing")
        }

        "convert http://0.0.0.0:3333/ontology/0001/example/simple/v2 to http://www.knora.org/ontology/0001/example" in {
            val externalOntologyIri = "http://0.0.0.0:3333/ontology/0001/example/simple/v2".toSmartIri
            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/0001/example")
        }

        "convert http://0.0.0.0:3333/ontology/0001/example/simple/v2#ExampleThing to http://www.knora.org/ontology/0001/example#ExampleThing" in {
            val externalEntityIri = "http://0.0.0.0:3333/ontology/0001/example/simple/v2#ExampleThing".toSmartIri
            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/0001/example#ExampleThing")
        }

        "convert http://0.0.0.0:3333/ontology/0001/example/v2 to http://www.knora.org/ontology/0001/example" in {
            val externalOntologyIri = "http://0.0.0.0:3333/ontology/0001/example/v2".toSmartIri
            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/0001/example")
        }

        "convert http://0.0.0.0:3333/ontology/0001/example/v2#ExampleThing to http://www.knora.org/ontology/0001/example#ExampleThing" in {
            val externalEntityIri = "http://0.0.0.0:3333/ontology/0001/example/v2#ExampleThing".toSmartIri
            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/0001/example#ExampleThing")
        }

        "convert http://www.knora.org/ontology/incunabula to http://0.0.0.0:3333/ontology/incunabula/simple/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/incunabula".toSmartIri
            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
            externalOntologyIri.toString should ===("http://0.0.0.0:3333/ontology/incunabula/simple/v2")
        }

        "convert http://www.knora.org/ontology/incunabula#book to http://0.0.0.0:3333/ontology/incunabula/simple/v2#book" in {
            val internalEntityIri = "http://www.knora.org/ontology/incunabula#book".toSmartIri
            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
            externalEntityIri.toString should ===("http://0.0.0.0:3333/ontology/incunabula/simple/v2#book")
        }

        "convert http://www.knora.org/ontology/incunabula to http://0.0.0.0:3333/ontology/incunabula/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/incunabula".toSmartIri
            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)
            externalOntologyIri.toString should ===("http://0.0.0.0:3333/ontology/incunabula/v2")
        }

        "convert http://www.knora.org/ontology/incunabula#book to http://0.0.0.0:3333/ontology/incunabula/v2#book" in {
            val internalEntityIri = "http://www.knora.org/ontology/incunabula#book".toSmartIri
            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2WithValueObjects)
            externalEntityIri.toString should ===("http://0.0.0.0:3333/ontology/incunabula/v2#book")
        }

        "convert http://0.0.0.0:3333/ontology/incunabula/simple/v2 to http://www.knora.org/ontology/incunabula" in {
            val externalOntologyIri = "http://0.0.0.0:3333/ontology/incunabula/simple/v2".toSmartIri
            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/incunabula")
        }

        "convert http://0.0.0.0:3333/ontology/incunabula/simple/v2#book to http://www.knora.org/ontology/incunabula#book" in {
            val externalEntityIri = "http://0.0.0.0:3333/ontology/incunabula/simple/v2#book".toSmartIri
            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/incunabula#book")
        }

        "convert http://0.0.0.0:3333/ontology/incunabula/v2 to http://www.knora.org/ontology/incunabula" in {
            val externalOntologyIri = "http://0.0.0.0:3333/ontology/incunabula/v2".toSmartIri
            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/incunabula")
        }

        "convert http://0.0.0.0:3333/ontology/incunabula/v2#book to http://www.knora.org/ontology/incunabula#book" in {
            val externalEntityIri = "http://0.0.0.0:3333/ontology/incunabula/v2#book".toSmartIri
            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/incunabula#book")
        }

        /*
        "convert 100,000 IRIs" in {
            val totalIris = 100000

            val parseStart = System.currentTimeMillis

            for (i <- 1 to totalIris) {
                val iriStr = s"http://0.0.0.0:3333/ontology/incunabula/v2#class$i"
                val iri = iriStr.toSmartIri.toOntologySchema(InternalSchema)
            }

            val parseEnd = System.currentTimeMillis
            val parseDuration = (parseEnd - parseStart).toDouble
            val parseDurationPerIri = parseDuration / totalIris.toDouble
            println(s"Parse and store $totalIris IRIs, $parseDuration ms, time per IRI $parseDurationPerIri ms")

            val retrieveStart = System.currentTimeMillis

            for (i <- 1 to totalIris) {
                val iriStr = s"http://0.0.0.0:3333/ontology/incunabula/v2#class$i"
                val iri = iriStr.toSmartIri.toOntologySchema(InternalSchema)
            }

            val retrieveEnd = System.currentTimeMillis
            val retrieveDuration = (retrieveEnd - retrieveStart).toDouble
            val retrieveDurationPerIri = retrieveDuration / totalIris.toDouble

            println(s"Retrieve time $retrieveDuration ms, time per IRI $retrieveDurationPerIri")
        }
        */

        "return the data named graph of a project without short code" in {
            val shortname = SharedAdminTestData.incunabulaProjectInfo.shortname
            val expected = s"http://www.knora.org/data/$shortname"
            val result = stringFormatter.projectDataNamedGraph(SharedAdminTestData.incunabulaProjectInfo)
            result should be(expected)
        }

        "return the data named graph of a project with short code" in {
            val shortcode = SharedAdminTestData.imagesProjectInfo.shortcode.get
            val shortname = SharedAdminTestData.imagesProjectInfo.shortname
            val expected = s"http://www.knora.org/data/$shortcode/$shortname"
            val result = stringFormatter.projectDataNamedGraph(SharedAdminTestData.imagesProjectInfo)
            result should be(expected)
        }

        "validate project shortcode" in {
            stringFormatter.validateProjectShortcode("00FF", () => throw AssertionException("not valid")) should be ("00FF")
            stringFormatter.validateProjectShortcode("00ff", () => throw AssertionException("not valid")) should be ("00FF")
            stringFormatter.validateProjectShortcode("12aF", () => throw AssertionException("not valid")) should be ("12AF")

            an [AssertionException] should be thrownBy {
                stringFormatter.validateProjectShortcode("000", () => throw AssertionException("not valid"))
            }

            an [AssertionException] should be thrownBy {
                stringFormatter.validateProjectShortcode("00000", () => throw AssertionException("not valid"))
            }

            an [AssertionException] should be thrownBy {
                stringFormatter.validateProjectShortcode("wxyz", () => throw AssertionException("not valid"))
            }
        }

    }
}
