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

package org.knora.webapi.util

import org.knora.webapi._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter.SalsahGuiAttributeDefinition

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
                stringFormatter.validateDate(dateString, throw BadRequestException(s"Not accepted $dateString"))
            }
        }

        "accept GREGORIAN:2017" in {
            val dateString = "GREGORIAN:2017"
            stringFormatter.validateDate(dateString, throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:2017-05" in {
            val dateString = "GREGORIAN:2017-05"
            stringFormatter.validateDate(dateString, throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:2017-05-10" in {
            val dateString = "GREGORIAN:2017-05-10"
            stringFormatter.validateDate(dateString, throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:2017-05-10:2017-05-12" in {
            val dateString = "GREGORIAN:2017-05-10:2017-05-12"
            stringFormatter.validateDate(dateString, throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:500-05-10 BC" in {
            val dateString = "GREGORIAN:500-05-10 BC"
            stringFormatter.validateDate(dateString, throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:500-05-10 AD" in {
            val dateString = "GREGORIAN:500-05-10 AD"
            stringFormatter.validateDate(dateString, throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:500-05-10 BC:5200-05-10 AD" in {
            val dateString = "GREGORIAN:500-05-10 BC:5200-05-10 AD"
            stringFormatter.validateDate(dateString, throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept JULIAN:50 BCE" in {
            val dateString = "JULIAN:50 BCE"
            stringFormatter.validateDate(dateString, throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept JULIAN:1560-05 CE" in {
            val dateString = "JULIAN:1560-05 CE"
            stringFormatter.validateDate(dateString, throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept JULIAN:217-05-10 BCE" in {
            val dateString = "JULIAN:217-05-10 BCE"
            stringFormatter.validateDate(dateString, throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept JULIAN:2017-05-10:2017-05-12" in {
            val dateString = "JULIAN:2017-05-10:2017-05-12"
            stringFormatter.validateDate(dateString, throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept JULIAN:2017:2017-5-12" in {
            val dateString = "JULIAN:2017:2017-5-12"
            stringFormatter.validateDate(dateString, throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept JULIAN:500 BCE:400 BCE" in {
            val dateString = "JULIAN:500 BCE:400 BCE"
            stringFormatter.validateDate(dateString, throw BadRequestException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:10 BC:1 AD" in {
            val dateString = "GREGORIAN:10 BC:1 AD"
            stringFormatter.validateDate(dateString, throw BadRequestException(s"Not accepted $dateString"))
        }

        "not accept month 00" in {
            val dateString = "GREGORIAN:2017-00:2017-02"
            assertThrows[BadRequestException] {
                stringFormatter.validateDate(dateString, throw BadRequestException(s"month 00 in $dateString Not accepted"))
            }
        }

        "not accept day 00" in {
            val dateString = "GREGORIAN:2017-01-00"
            assertThrows[BadRequestException] {
                stringFormatter.validateDate(dateString, throw BadRequestException(s"day 00 in $dateString Not accepted"))
            }
        }

        "not accept year 0" in {
            val dateString = "GREGORIAN:0 BC"
            assertThrows[BadRequestException] {
                stringFormatter.validateDate(dateString, throw BadRequestException(s"Year 0 is Not accepted $dateString"))
            }
        }

        "recognize the url of the dhlab site as a valid IRI" in {
            val testUrl: String = "http://dhlab.unibas.ch/"
            val validIri = stringFormatter.validateAndEscapeIri(testUrl, throw BadRequestException(s"Invalid IRI $testUrl"))
            validIri should be(testUrl)
        }

        "recognize the url of the DaSCH site as a valid IRI" in {
            val testUrl = "http://dasch.swiss"
            val validIri = stringFormatter.validateAndEscapeIri(testUrl, throw BadRequestException(s"Invalid IRI $testUrl"))
            validIri should be(testUrl)
        }

        "convert http://www.knora.org/ontology/knora-base to http://api.knora.org/ontology/knora-api/simple/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/knora-base".toSmartIri
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.isEmpty)

            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
            externalOntologyIri.toString should ===("http://api.knora.org/ontology/knora-api/simple/v2")
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
                externalOntologyIri.isKnoraOntologyIri &&
                externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.isEmpty)
        }

        "convert http://www.knora.org/ontology/knora-base#Resource to http://api.knora.org/ontology/knora-api/simple/v2#Resource" in {
            val internalEntityIri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.isEmpty)

            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
            externalEntityIri.toString should ===("http://api.knora.org/ontology/knora-api/simple/v2#Resource")
            assert(externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.isEmpty)
        }

        "convert http://www.knora.org/ontology/knora-base to http://api.knora.org/ontology/knora-api/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/knora-base".toSmartIri
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.isEmpty)

            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)
            externalOntologyIri.toString should ===("http://api.knora.org/ontology/knora-api/v2")
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalOntologyIri.isKnoraOntologyIri &&
                externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.isEmpty)
        }

        "convert http://www.knora.org/ontology/knora-base#Resource to http://api.knora.org/ontology/knora-api/v2#Resource" in {
            val internalEntityIri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.isEmpty)

            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2WithValueObjects)
            externalEntityIri.toString should ===("http://api.knora.org/ontology/knora-api/v2#Resource")
            assert(externalEntityIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.isEmpty)
        }

        "convert http://api.knora.org/ontology/knora-api/simple/v2 to http://www.knora.org/ontology/knora-base" in {
            val externalOntologyIri = "http://api.knora.org/ontology/knora-api/simple/v2".toSmartIri
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
                externalOntologyIri.isKnoraOntologyIri &&
                externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.isEmpty)

            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/knora-base")
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.isEmpty)
        }

        "convert http://api.knora.org/ontology/knora-api/simple/v2#Resource to http://www.knora.org/ontology/knora-base#Resource" in {
            val externalEntityIri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri
            assert(externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.isEmpty)

            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/knora-base#Resource")
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.isEmpty)
        }

        "convert http://api.knora.org/ontology/knora-api/v2 to http://www.knora.org/ontology/knora-base" in {
            val externalOntologyIri = "http://api.knora.org/ontology/knora-api/v2".toSmartIri
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalOntologyIri.isKnoraOntologyIri &&
                externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.isEmpty)

            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/knora-base")
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.isEmpty)
        }

        "convert http://api.knora.org/ontology/knora-api/v2#Resource to http://www.knora.org/ontology/knora-base#Resource" in {
            val externalEntityIri = "http://api.knora.org/ontology/knora-api/v2#Resource".toSmartIri
            assert(externalEntityIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.isEmpty)

            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/knora-base#Resource")
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.isEmpty)
        }

        "convert http://www.knora.org/ontology/0001/example to http://0.0.0.0:3333/ontology/0001/example/simple/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/0001/example".toSmartIri
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("0001"))

            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
            externalOntologyIri.toString should ===("http://0.0.0.0:3333/ontology/0001/example/simple/v2")
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("0001"))
        }

        "convert http://www.knora.org/ontology/0001/example#ExampleThing to http://0.0.0.0:3333/ontology/0001/example/simple/v2#ExampleThing" in {
            val internalEntityIri = "http://www.knora.org/ontology/0001/example#ExampleThing".toSmartIri
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.contains("0001"))

            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
            externalEntityIri.toString should ===("http://0.0.0.0:3333/ontology/0001/example/simple/v2#ExampleThing")
            assert(externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.contains("0001"))
        }

        "convert http://www.knora.org/ontology/0001/example to http://0.0.0.0:3333/ontology/0001/example/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/0001/example".toSmartIri
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("0001"))

            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)
            externalOntologyIri.toString should ===("http://0.0.0.0:3333/ontology/0001/example/v2")
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("0001"))
        }

        "convert http://www.knora.org/ontology/0001/example#ExampleThing to http://0.0.0.0:3333/ontology/0001/example/v2#ExampleThing" in {
            val internalEntityIri = "http://www.knora.org/ontology/0001/example#ExampleThing".toSmartIri
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.contains("0001"))

            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2WithValueObjects)
            externalEntityIri.toString should ===("http://0.0.0.0:3333/ontology/0001/example/v2#ExampleThing")
            assert(externalEntityIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.contains("0001"))
        }

        "convert http://0.0.0.0:3333/ontology/0001/example/simple/v2 to http://www.knora.org/ontology/0001/example" in {
            val externalOntologyIri = "http://0.0.0.0:3333/ontology/0001/example/simple/v2".toSmartIri
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("0001"))

            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/0001/example")
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("0001"))
        }

        "convert http://0.0.0.0:3333/ontology/0001/example/simple/v2#ExampleThing to http://www.knora.org/ontology/0001/example#ExampleThing" in {
            val externalEntityIri = "http://0.0.0.0:3333/ontology/0001/example/simple/v2#ExampleThing".toSmartIri
            assert(externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.contains("0001"))

            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/0001/example#ExampleThing")
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.contains("0001"))
        }

        "convert http://0.0.0.0:3333/ontology/0001/example/v2 to http://www.knora.org/ontology/0001/example" in {
            val externalOntologyIri = "http://0.0.0.0:3333/ontology/0001/example/v2".toSmartIri
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("0001"))

            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/0001/example")
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("0001"))
        }

        "convert http://0.0.0.0:3333/ontology/0001/example/v2#ExampleThing to http://www.knora.org/ontology/0001/example#ExampleThing" in {
            val externalEntityIri = "http://0.0.0.0:3333/ontology/0001/example/v2#ExampleThing".toSmartIri
            assert(externalEntityIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.contains("0001"))

            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/0001/example#ExampleThing")
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.contains("0001"))
        }

        "convert http://www.knora.org/ontology/incunabula to http://0.0.0.0:3333/ontology/incunabula/simple/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/incunabula".toSmartIri
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.isEmpty)

            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
            externalOntologyIri.toString should ===("http://0.0.0.0:3333/ontology/incunabula/simple/v2")
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.isEmpty)
        }

        "convert http://www.knora.org/ontology/incunabula#book to http://0.0.0.0:3333/ontology/incunabula/simple/v2#book" in {
            val internalEntityIri = "http://www.knora.org/ontology/incunabula#book".toSmartIri
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.isEmpty)

            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
            externalEntityIri.toString should ===("http://0.0.0.0:3333/ontology/incunabula/simple/v2#book")
            assert(externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.isEmpty)
        }

        "convert http://www.knora.org/ontology/incunabula to http://0.0.0.0:3333/ontology/incunabula/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/incunabula".toSmartIri
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.isEmpty)

            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)
            externalOntologyIri.toString should ===("http://0.0.0.0:3333/ontology/incunabula/v2")
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.isEmpty)
        }

        "convert http://www.knora.org/ontology/incunabula#book to http://0.0.0.0:3333/ontology/incunabula/v2#book" in {
            val internalEntityIri = "http://www.knora.org/ontology/incunabula#book".toSmartIri
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.isEmpty)

            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2WithValueObjects)
            externalEntityIri.toString should ===("http://0.0.0.0:3333/ontology/incunabula/v2#book")
            assert(externalEntityIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.isEmpty)
        }

        "convert http://0.0.0.0:3333/ontology/incunabula/simple/v2 to http://www.knora.org/ontology/incunabula" in {
            val externalOntologyIri = "http://0.0.0.0:3333/ontology/incunabula/simple/v2".toSmartIri
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.isEmpty)

            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/incunabula")
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.isEmpty)
        }

        "convert http://0.0.0.0:3333/ontology/incunabula/simple/v2#book to http://www.knora.org/ontology/incunabula#book" in {
            val externalEntityIri = "http://0.0.0.0:3333/ontology/incunabula/simple/v2#book".toSmartIri
            assert(externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.isEmpty)

            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/incunabula#book")
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.isEmpty)
        }

        "convert http://0.0.0.0:3333/ontology/incunabula/v2 to http://www.knora.org/ontology/incunabula" in {
            val externalOntologyIri = "http://0.0.0.0:3333/ontology/incunabula/v2".toSmartIri
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.isEmpty)

            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/incunabula")
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.isEmpty)
        }

        "convert http://0.0.0.0:3333/ontology/incunabula/v2#book to http://www.knora.org/ontology/incunabula#book" in {
            val externalEntityIri = "http://0.0.0.0:3333/ontology/incunabula/v2#book".toSmartIri
            assert(externalEntityIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.isEmpty)

            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/incunabula#book")
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.isEmpty)
        }

        "convert http://www.knora.org/ontology/knora-base#TextValue to http://www.w3.org/2001/XMLSchema#string" in {
            val internalEntityIri = "http://www.knora.org/ontology/knora-base#TextValue".toSmartIri
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.isEmpty)

            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
            assert(externalEntityIri.toString == "http://www.w3.org/2001/XMLSchema#string" && !externalEntityIri.isKnoraIri)
        }

        "not change http://www.w3.org/2001/XMLSchema#string when converting to InternalSchema" in {
            val externalEntityIri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
            assert(!externalEntityIri.isKnoraIri)

            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            assert(internalEntityIri.toString == externalEntityIri.toString && !externalEntityIri.isKnoraIri)
        }

        "parse http://rdfh.ch/0000/0123456789abcdef" in {
            val dataIri = "http://rdfh.ch/0000/0123456789abcdef".toSmartIri
            assert(dataIri.isKnoraDataIri)
        }

        "parse http://data.knora.org/0123456789abcdef" in {
            val dataIri = "http://data.knora.org/0123456789abcdef".toSmartIri
            assert(dataIri.isKnoraDataIri)
        }

        "parse http://www.knora.org/explicit" in {
            val namedGraphIri = "http://www.knora.org/explicit".toSmartIri
            assert(namedGraphIri.isKnoraDataIri)
        }

        "parse http://www.ontotext.com/explicit" in {
            val namedGraphIri = "http://www.ontotext.com/explicit".toSmartIri
            assert(!namedGraphIri.isKnoraIri)
        }

        "parse http://www.w3.org/2001/XMLSchema#integer" in {
            val xsdIri = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
            assert(!xsdIri.isKnoraOntologyIri &&
                !xsdIri.isKnoraDataIri &&
                xsdIri.getOntologySchema.isEmpty &&
                xsdIri.getProjectCode.isEmpty)
        }

        "reject an empty IRI string" in {
            assertThrows[BadRequestException] {
                "".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject the IRI 'foo'" in {
            assertThrows[BadRequestException] {
                "foo".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://" in {
            assertThrows[BadRequestException] {
                "http://".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject ftp://www.knora.org/ontology/incunabula (wrong URL scheme)" in {
            assertThrows[BadRequestException] {
                "ftp://www.knora.org/ontology/incunabula".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject https://www.knora.org/ontology/incunabula (wrong URL scheme)" in {
            assertThrows[BadRequestException] {
                "https://www.knora.org/ontology/incunabula".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://www.knora.org/" in {
            assertThrows[BadRequestException] {
                "http://www.knora.org/".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://api.knora.org/" in {
            assertThrows[BadRequestException] {
                "http://api.knora.org/".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/" in {
            assertThrows[BadRequestException] {
                "http://api.knora.org/".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://www.knora.org/ontology" in {
            assertThrows[BadRequestException] {
                "http://www.knora.org/ontology".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://api.knora.org/ontology" in {
            assertThrows[BadRequestException] {
                "http://api.knora.org/".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology" in {
            assertThrows[BadRequestException] {
                "http://api.knora.org/ontology".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://www.knora.org/ontology/incunabula/v2 (wrong hostname)" in {
            assertThrows[BadRequestException] {
                "http://www.knora.org/ontology/incunabula/v2".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://www.knora.org/ontology/incunabula/simple/v2 (wrong hostname)" in {
            assertThrows[BadRequestException] {
                "http://www.knora.org/ontology/incunabula/v2".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://api.knora.org/ontology/incunabula/v2 (wrong hostname)" in {
            assertThrows[BadRequestException] {
                "http://api.knora.org/ontology/incunabula/v2".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://api.knora.org/ontology/incunabula/simple/v2 (wrong hostname)" in {
            assertThrows[BadRequestException] {
                "http://api.knora.org/ontology/incunabula/simple/v2".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/v2 (invalid ontology name)" in {
            assertThrows[BadRequestException] {
                "http://0.0.0.0:3333/ontology/v2".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/0000/v2 (invalid ontology name)" ignore {
            // TODO: Re-enable when #667 is resolved.

            assertThrows[BadRequestException] {
                "http://0.0.0.0:3333/ontology/0000/v2".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/ontology (invalid ontology name)" in {
            assertThrows[BadRequestException] {
                "http://0.0.0.0:3333/ontology/ontology".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/0000/ontology (invalid ontology name)" in {
            assertThrows[BadRequestException] {
                "http://0.0.0.0:3333/ontology/0000/ontology".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/0000/simple/simple/v2 (invalid ontology name)" in {
            assertThrows[BadRequestException] {
                "http://0.0.0.0:3333/ontology/0000/simple/simple/v2".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/incunabula/v2#1234 (invalid entity name)" in {
            assertThrows[BadRequestException] {
                "http://0.0.0.0:3333/ontology/incunabula/v2#1234".toSmartIriWithErr(throw BadRequestException(s"Invalid IRI"))
            }
        }

        "enable pattern matching with SmartIri" in {
            val input: SmartIri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri

            val isResource = input match {
                case SmartIri(OntologyConstants.KnoraBase.Resource) => true
                case _ => false
            }

            assert(isResource)
        }

        "convert 100,000 IRIs" ignore {
            val totalIris = 100000

            val parseStart = System.currentTimeMillis

            for (i <- 1 to totalIris) {
                val iriStr = s"http://0.0.0.0:3333/ontology/incunabula/v2#class$i"
                val iri = iriStr.toSmartIri.toOntologySchema(InternalSchema)
            }

            val parseEnd = System.currentTimeMillis
            val parseDuration = (parseEnd - parseStart).toDouble
            val parseDurationPerIri = parseDuration / totalIris.toDouble
            println(f"Parse and store $totalIris IRIs, $parseDuration ms, time per IRI $parseDurationPerIri%1.5f ms")

            val retrieveStart = System.currentTimeMillis

            for (i <- 1 to totalIris) {
                val iriStr = s"http://0.0.0.0:3333/ontology/incunabula/v2#class$i"
                val iri = iriStr.toSmartIri.toOntologySchema(InternalSchema)
            }

            val retrieveEnd = System.currentTimeMillis
            val retrieveDuration = (retrieveEnd - retrieveStart).toDouble
            val retrieveDurationPerIri = retrieveDuration / totalIris.toDouble

            println(f"Retrieve time $retrieveDuration ms, time per IRI $retrieveDurationPerIri%1.5f ms")
        }

        "return the data named graph of a project without short code" in {
            val shortname = SharedTestDataV1.incunabulaProjectInfo.shortname
            val expected = s"http://www.knora.org/data/$shortname"
            val result = stringFormatter.projectDataNamedGraph(SharedTestDataV1.incunabulaProjectInfo)
            result should be(expected)
        }

        "return the data named graph of a project with short code" in {
            val shortcode = SharedTestDataV1.imagesProjectInfo.shortcode.get
            val shortname = SharedTestDataV1.imagesProjectInfo.shortname
            val expected = s"http://www.knora.org/data/$shortcode/$shortname"
            val result = stringFormatter.projectDataNamedGraph(SharedTestDataV1.imagesProjectInfo)
            result should be(expected)
        }

        "validate project shortcode" in {
            stringFormatter.validateProjectShortcode("00FF", throw AssertionException("not valid")) should be("00FF")
            stringFormatter.validateProjectShortcode("00ff", throw AssertionException("not valid")) should be("00FF")
            stringFormatter.validateProjectShortcode("12aF", throw AssertionException("not valid")) should be("12AF")

            an[AssertionException] should be thrownBy {
                stringFormatter.validateProjectShortcode("000", throw AssertionException("not valid"))
            }

            an[AssertionException] should be thrownBy {
                stringFormatter.validateProjectShortcode("00000", throw AssertionException("not valid"))
            }

            an[AssertionException] should be thrownBy {
                stringFormatter.validateProjectShortcode("wxyz", throw AssertionException("not valid"))
            }
        }

        "parse the objects of salsah-gui:guiAttributeDefinition" in {
            stringFormatter.toSalsahGuiAttributeDefinition("hlist(required):iri", throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(attributeName = "hlist", isRequired = true, allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Iri)
            )

            stringFormatter.toSalsahGuiAttributeDefinition("numprops:integer", throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(attributeName = "numprops", isRequired = false, allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Integer)
            )

            stringFormatter.toSalsahGuiAttributeDefinition("size:integer", throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(attributeName = "size", isRequired = false, allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Integer)
            )

            stringFormatter.toSalsahGuiAttributeDefinition("maxlength:integer", throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(attributeName = "maxlength", isRequired = false, allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Integer)
            )

            stringFormatter.toSalsahGuiAttributeDefinition("max(required):decimal", throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(attributeName = "max", isRequired = true, allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Decimal)
            )

            stringFormatter.toSalsahGuiAttributeDefinition("min(required):decimal", throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(attributeName = "min", isRequired = true, allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Decimal)
            )

            stringFormatter.toSalsahGuiAttributeDefinition("width:percent", throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(
                    attributeName = "width",
                    isRequired = false,
                    allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Percent
                )
            )

            stringFormatter.toSalsahGuiAttributeDefinition("rows:integer", throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(attributeName = "rows", isRequired = false, allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Integer)
            )

            stringFormatter.toSalsahGuiAttributeDefinition("wrap:string(soft|hard)", throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(
                    attributeName = "wrap",
                    isRequired = false,
                    allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Str,
                    enumeratedValues = Set("soft", "hard")
                )
            )
        }
    }
}
