/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import java.time.Instant

import org.knora.webapi._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter.SalsahGuiAttributeDefinition

/**
  * Tests [[StringFormatter]].
  */
class StringFormatterSpec extends CoreSpec() {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    "The StringFormatter class" should {

        "not accept 2017-05-10" in {
            val dateString = "2017-05-10"
            assertThrows[AssertionException] {
                stringFormatter.validateDate(dateString, throw AssertionException(s"Not accepted $dateString"))
            }
        }

        "accept GREGORIAN:2017" in {
            val dateString = "GREGORIAN:2017"
            stringFormatter.validateDate(dateString, throw AssertionException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:2017-05" in {
            val dateString = "GREGORIAN:2017-05"
            stringFormatter.validateDate(dateString, throw AssertionException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:2017-05-10" in {
            val dateString = "GREGORIAN:2017-05-10"
            stringFormatter.validateDate(dateString, throw AssertionException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:2017-05-10:2017-05-12" in {
            val dateString = "GREGORIAN:2017-05-10:2017-05-12"
            stringFormatter.validateDate(dateString, throw AssertionException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:500-05-10 BC" in {
            val dateString = "GREGORIAN:500-05-10 BC"
            stringFormatter.validateDate(dateString, throw AssertionException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:500-05-10 AD" in {
            val dateString = "GREGORIAN:500-05-10 AD"
            stringFormatter.validateDate(dateString, throw AssertionException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:500-05-10 BC:5200-05-10 AD" in {
            val dateString = "GREGORIAN:500-05-10 BC:5200-05-10 AD"
            stringFormatter.validateDate(dateString, throw AssertionException(s"Not accepted $dateString"))
        }

        "accept JULIAN:50 BCE" in {
            val dateString = "JULIAN:50 BCE"
            stringFormatter.validateDate(dateString, throw AssertionException(s"Not accepted $dateString"))
        }

        "accept JULIAN:1560-05 CE" in {
            val dateString = "JULIAN:1560-05 CE"
            stringFormatter.validateDate(dateString, throw AssertionException(s"Not accepted $dateString"))
        }

        "accept JULIAN:217-05-10 BCE" in {
            val dateString = "JULIAN:217-05-10 BCE"
            stringFormatter.validateDate(dateString, throw AssertionException(s"Not accepted $dateString"))
        }

        "accept JULIAN:2017-05-10:2017-05-12" in {
            val dateString = "JULIAN:2017-05-10:2017-05-12"
            stringFormatter.validateDate(dateString, throw AssertionException(s"Not accepted $dateString"))
        }

        "accept JULIAN:2017:2017-5-12" in {
            val dateString = "JULIAN:2017:2017-5-12"
            stringFormatter.validateDate(dateString, throw AssertionException(s"Not accepted $dateString"))
        }

        "accept JULIAN:500 BCE:400 BCE" in {
            val dateString = "JULIAN:500 BCE:400 BCE"
            stringFormatter.validateDate(dateString, throw AssertionException(s"Not accepted $dateString"))
        }

        "accept GREGORIAN:10 BC:1 AD" in {
            val dateString = "GREGORIAN:10 BC:1 AD"
            stringFormatter.validateDate(dateString, throw AssertionException(s"Not accepted $dateString"))
        }

        "not accept month 00" in {
            val dateString = "GREGORIAN:2017-00:2017-02"
            assertThrows[AssertionException] {
                stringFormatter.validateDate(dateString, throw AssertionException(s"month 00 in $dateString Not accepted"))
            }
        }

        "not accept day 00" in {
            val dateString = "GREGORIAN:2017-01-00"
            assertThrows[AssertionException] {
                stringFormatter.validateDate(dateString, throw AssertionException(s"day 00 in $dateString Not accepted"))
            }
        }

        "not accept year 0" in {
            val dateString = "GREGORIAN:0 BC"
            assertThrows[AssertionException] {
                stringFormatter.validateDate(dateString, throw AssertionException(s"Year 0 is Not accepted $dateString"))
            }
        }

        "recognize the url of the dhlab site as a valid IRI" in {
            val testUrl: String = "http://dhlab.unibas.ch/"
            val validIri = stringFormatter.validateAndEscapeIri(testUrl, throw AssertionException(s"Invalid IRI $testUrl"))
            validIri should be(testUrl)
        }

        "recognize the url of the DaSCH site as a valid IRI" in {
            val testUrl = "http://dasch.swiss"
            val validIri = stringFormatter.validateAndEscapeIri(testUrl, throw AssertionException(s"Invalid IRI $testUrl"))
            validIri should be(testUrl)
        }

        /////////////////////////////////////
        // Built-in ontologies

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

        //////////////////////////////////////////
        // Non-shared, project-specific ontologies

        "convert http://www.knora.org/ontology/00FF/images to http://0.0.0.0:3333/ontology/00FF/images/simple/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/00FF/images".toSmartIri
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("00FF"))

            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
            externalOntologyIri.toString should ===("http://0.0.0.0:3333/ontology/00FF/images/simple/v2")
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("00FF"))
        }

        "convert http://www.knora.org/ontology/00FF/images#bild to http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild" in {
            val internalEntityIri = "http://www.knora.org/ontology/00FF/images#bild".toSmartIri
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.contains("00FF"))

            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
            externalEntityIri.toString should ===("http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild")
            assert(externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.contains("00FF"))
        }

        "convert http://www.knora.org/ontology/00FF/images to http://0.0.0.0:3333/ontology/00FF/images/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/00FF/images".toSmartIri
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("00FF"))

            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)
            externalOntologyIri.toString should ===("http://0.0.0.0:3333/ontology/00FF/images/v2")
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("00FF"))
        }

        "convert http://www.knora.org/ontology/00FF/images#bild to http://0.0.0.0:3333/ontology/00FF/images/v2#bild" in {
            val internalEntityIri = "http://www.knora.org/ontology/00FF/images#bild".toSmartIri
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.contains("00FF"))

            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2WithValueObjects)
            externalEntityIri.toString should ===("http://0.0.0.0:3333/ontology/00FF/images/v2#bild")
            assert(externalEntityIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.contains("00FF"))
        }

        "convert http://0.0.0.0:3333/ontology/00FF/images/simple/v2 to http://www.knora.org/ontology/00FF/images" in {
            val externalOntologyIri = "http://0.0.0.0:3333/ontology/00FF/images/simple/v2".toSmartIri
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("00FF"))

            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/00FF/images")
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("00FF"))
        }

        "convert http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild to http://www.knora.org/ontology/00FF/images#bild" in {
            val externalEntityIri = "http://0.0.0.0:3333/ontology/00FF/images/simple/v2#bild".toSmartIri
            assert(externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.contains("00FF"))

            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/00FF/images#bild")
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.contains("00FF"))
        }

        "convert http://0.0.0.0:3333/ontology/00FF/images/v2 to http://www.knora.org/ontology/00FF/images" in {
            val externalOntologyIri = "http://0.0.0.0:3333/ontology/00FF/images/v2".toSmartIri
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("00FF"))

            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/00FF/images")
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("00FF"))
        }

        "convert http://0.0.0.0:3333/ontology/00FF/images/v2#bild to http://www.knora.org/ontology/00FF/images#bild" in {
            val externalEntityIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bild".toSmartIri
            assert(externalEntityIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.getProjectCode.contains("00FF"))

            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/00FF/images#bild")
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.getProjectCode.contains("00FF"))
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

        /////////////////////////////////////////////////////////////
        // Shared ontologies in the default shared ontologies project

        "convert http://www.knora.org/ontology/shared/example to http://api.knora.org/ontology/shared/example/simple/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/shared/example".toSmartIri
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.isKnoraSharedDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("0000"))

            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
            externalOntologyIri.toString should ===("http://api.knora.org/ontology/shared/example/simple/v2")
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.isKnoraSharedDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("0000"))
        }

        "convert http://www.knora.org/ontology/shared/example#Person to http://api.knora.org/ontology/shared/example/simple/v2#Person" in {
            val internalEntityIri = "http://www.knora.org/ontology/shared/example#Person".toSmartIri
            assert(internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.isKnoraSharedDefinitionIri &&
                internalEntityIri.getProjectCode.contains("0000"))

            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
            externalEntityIri.toString should ===("http://api.knora.org/ontology/shared/example/simple/v2#Person")
            assert(externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.isKnoraSharedDefinitionIri &&
                externalEntityIri.getProjectCode.contains("0000"))
        }

        "convert http://www.knora.org/ontology/shared/example to http://api.knora.org/ontology/shared/example/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/shared/example".toSmartIri
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.isKnoraSharedDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("0000"))

            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)
            externalOntologyIri.toString should ===("http://api.knora.org/ontology/shared/example/v2")
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.isKnoraSharedDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("0000"))
        }

        "convert http://www.knora.org/ontology/shared/example#Person to http://api.knora.org/ontology/shared/example/v2#Person" in {
            val internalEntityIri = "http://www.knora.org/ontology/shared/example#Person".toSmartIri
            assert(internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.isKnoraSharedDefinitionIri &&
                internalEntityIri.getProjectCode.contains("0000"))

            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2WithValueObjects)
            externalEntityIri.toString should ===("http://api.knora.org/ontology/shared/example/v2#Person")
            assert(externalEntityIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.isKnoraSharedDefinitionIri &&
                externalEntityIri.getProjectCode.contains("0000"))
        }

        "convert http://api.knora.org/ontology/shared/example/simple/v2 to http://www.knora.org/ontology/shared/example" in {
            val externalOntologyIri = "http://api.knora.org/ontology/shared/example/simple/v2".toSmartIri
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.isKnoraSharedDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("0000"))

            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/shared/example")
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.isKnoraSharedDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("0000"))
        }

        "convert http://api.knora.org/ontology/shared/example/simple/v2#Person to http://www.knora.org/ontology/shared/example#Person" in {
            val externalEntityIri = "http://api.knora.org/ontology/shared/example/simple/v2#Person".toSmartIri
            assert(externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.isKnoraSharedDefinitionIri &&
                externalEntityIri.getProjectCode.contains("0000"))

            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/shared/example#Person")
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.isKnoraSharedDefinitionIri &&
                internalEntityIri.getProjectCode.contains("0000"))
        }

        "convert http://api.knora.org/ontology/shared/example/v2 to http://www.knora.org/ontology/shared/example" in {
            val externalOntologyIri = "http://api.knora.org/ontology/shared/example/v2".toSmartIri
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.isKnoraSharedDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("0000"))

            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/shared/example")
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.isKnoraSharedDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("0000"))
        }

        "convert http://api.knora.org/ontology/shared/example/v2#Person to http://www.knora.org/ontology/shared/example#Person" in {
            val externalEntityIri = "http://api.knora.org/ontology/shared/example/v2#Person".toSmartIri
            assert(externalEntityIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.isKnoraSharedDefinitionIri &&
                externalEntityIri.getProjectCode.contains("0000"))

            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/shared/example#Person")
            assert(internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.isKnoraSharedDefinitionIri &&
                internalEntityIri.getProjectCode.contains("0000"))
        }

        ///////////////////////////////////////////////////////////////
        // Shared ontologies in a non-default shared ontologies project

        "convert http://www.knora.org/ontology/shared/0111/example to http://api.knora.org/ontology/shared/0111/example/simple/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/shared/0111/example".toSmartIri
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.isKnoraSharedDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("0111"))

            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2Simple)
            externalOntologyIri.toString should ===("http://api.knora.org/ontology/shared/0111/example/simple/v2")
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.isKnoraSharedDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("0111"))
        }

        "convert http://www.knora.org/ontology/shared/0111/example#Person to http://api.knora.org/ontology/shared/0111/example/simple/v2#Person" in {
            val internalEntityIri = "http://www.knora.org/ontology/shared/0111/example#Person".toSmartIri
            assert(internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.isKnoraSharedDefinitionIri &&
                internalEntityIri.getProjectCode.contains("0111"))

            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2Simple)
            externalEntityIri.toString should ===("http://api.knora.org/ontology/shared/0111/example/simple/v2#Person")
            assert(externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.isKnoraSharedDefinitionIri &&
                externalEntityIri.getProjectCode.contains("0111"))
        }

        "convert http://www.knora.org/ontology/shared/0111/example to http://api.knora.org/ontology/shared/0111/example/v2" in {
            val internalOntologyIri = "http://www.knora.org/ontology/shared/0111/example".toSmartIri
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.isKnoraSharedDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("0111"))

            val externalOntologyIri = internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)
            externalOntologyIri.toString should ===("http://api.knora.org/ontology/shared/0111/example/v2")
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.isKnoraSharedDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("0111"))
        }

        "convert http://www.knora.org/ontology/shared/0111/example#Person to http://api.knora.org/ontology/shared/0111/example/v2#Person" in {
            val internalEntityIri = "http://www.knora.org/ontology/shared/0111/example#Person".toSmartIri
            assert(internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.isKnoraSharedDefinitionIri &&
                internalEntityIri.getProjectCode.contains("0111"))

            val externalEntityIri = internalEntityIri.toOntologySchema(ApiV2WithValueObjects)
            externalEntityIri.toString should ===("http://api.knora.org/ontology/shared/0111/example/v2#Person")
            assert(externalEntityIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalEntityIri.isKnoraApiV2EntityIri &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.isKnoraSharedDefinitionIri &&
                externalEntityIri.getProjectCode.contains("0111"))
        }

        "convert http://api.knora.org/ontology/shared/0111/example/simple/v2 to http://www.knora.org/ontology/shared/0111/example" in {
            val externalOntologyIri = "http://api.knora.org/ontology/shared/0111/example/simple/v2".toSmartIri
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2Simple) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.isKnoraSharedDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("0111"))

            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/shared/0111/example")
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.isKnoraSharedDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("0111"))
        }

        "convert http://api.knora.org/ontology/shared/0111/example/simple/v2#Person to http://www.knora.org/ontology/shared/0111/example#Person" in {
            val externalEntityIri = "http://api.knora.org/ontology/shared/0111/example/simple/v2#Person".toSmartIri
            assert(externalEntityIri.getOntologySchema.contains(ApiV2Simple) &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.isKnoraSharedDefinitionIri &&
                externalEntityIri.getProjectCode.contains("0111"))

            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/shared/0111/example#Person")
            assert(internalEntityIri.getOntologySchema.contains(InternalSchema) &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.isKnoraSharedDefinitionIri &&
                internalEntityIri.getProjectCode.contains("0111"))
        }

        "convert http://api.knora.org/ontology/shared/0111/example/v2 to http://www.knora.org/ontology/shared/0111/example" in {
            val externalOntologyIri = "http://api.knora.org/ontology/shared/0111/example/v2".toSmartIri
            assert(externalOntologyIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                externalOntologyIri.isKnoraOntologyIri &&
                !externalOntologyIri.isKnoraBuiltInDefinitionIri &&
                externalOntologyIri.isKnoraSharedDefinitionIri &&
                externalOntologyIri.getProjectCode.contains("0111"))

            val internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            internalOntologyIri.toString should ===("http://www.knora.org/ontology/shared/0111/example")
            assert(internalOntologyIri.getOntologySchema.contains(InternalSchema) &&
                internalOntologyIri.isKnoraOntologyIri &&
                !internalOntologyIri.isKnoraBuiltInDefinitionIri &&
                internalOntologyIri.isKnoraSharedDefinitionIri &&
                internalOntologyIri.getProjectCode.contains("0111"))
        }

        "convert http://api.knora.org/ontology/shared/0111/example/v2#Person to http://www.knora.org/ontology/shared/0111/example#Person" in {
            val externalEntityIri = "http://api.knora.org/ontology/shared/0111/example/v2#Person".toSmartIri
            assert(externalEntityIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
                !externalEntityIri.isKnoraBuiltInDefinitionIri &&
                externalEntityIri.isKnoraSharedDefinitionIri &&
                externalEntityIri.getProjectCode.contains("0111"))

            val internalEntityIri = externalEntityIri.toOntologySchema(InternalSchema)
            internalEntityIri.toString should ===("http://www.knora.org/ontology/shared/0111/example#Person")
            assert(internalEntityIri.isKnoraInternalEntityIri &&
                !internalEntityIri.isKnoraBuiltInDefinitionIri &&
                internalEntityIri.isKnoraSharedDefinitionIri &&
                internalEntityIri.getProjectCode.contains("0111"))
        }

        /////////////////////

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

        "parse http://rdfh.ch/0123456789abcdef" in {
            val dataIri = "http://rdfh.ch/0123456789abcdef".toSmartIri
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

        "validate import namespace with project shortcode" in {
            val defaultNamespace = "http://api.knora.org/ontology/0801/biblio/xml-import/v1#"
            stringFormatter.xmlImportNamespaceToInternalOntologyIriV1(
                defaultNamespace, throw AssertionException("Invalid XML import namespace")
            ).toString should be ("http://www.knora.org/ontology/0801/biblio")
        }

        "validate internal ontology path" in {
            val urlPath = "/ontology/knora-api/simple/v2"
            stringFormatter.isBuiltInApiV2OntologyUrlPath(urlPath) should be (true)
        }


        "reject an empty IRI string" in {
            assertThrows[AssertionException] {
                "".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject the IRI 'foo'" in {
            assertThrows[AssertionException] {
                "foo".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://" in {
            assertThrows[AssertionException] {
                "http://".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject ftp://www.knora.org/ontology/00FF/images (wrong URL scheme)" in {
            assertThrows[AssertionException] {
                "ftp://www.knora.org/ontology/00FF/images".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject https://www.knora.org/ontology/00FF/images (wrong URL scheme)" in {
            assertThrows[AssertionException] {
                "https://www.knora.org/ontology/00FF/images".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://www.knora.org/" in {
            assertThrows[AssertionException] {
                "http://www.knora.org/".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://api.knora.org/" in {
            assertThrows[AssertionException] {
                "http://api.knora.org/".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/" in {
            assertThrows[AssertionException] {
                "http://api.knora.org/".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://www.knora.org/ontology" in {
            assertThrows[AssertionException] {
                "http://www.knora.org/ontology".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://api.knora.org/ontology" in {
            assertThrows[AssertionException] {
                "http://api.knora.org/".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology" in {
            assertThrows[AssertionException] {
                "http://api.knora.org/ontology".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://www.knora.org/ontology/00FF/images/v2 (wrong hostname)" in {
            assertThrows[AssertionException] {
                "http://www.knora.org/ontology/00FF/images/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://www.knora.org/ontology/00FF/images/simple/v2 (wrong hostname)" in {
            assertThrows[AssertionException] {
                "http://www.knora.org/ontology/00FF/images/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://api.knora.org/ontology/00FF/images/v2 (wrong hostname)" in {
            assertThrows[AssertionException] {
                "http://api.knora.org/ontology/00FF/images/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://api.knora.org/ontology/00FF/images/simple/v2 (wrong hostname)" in {
            assertThrows[AssertionException] {
                "http://api.knora.org/ontology/00FF/images/simple/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/v2 (invalid ontology name)" in {
            assertThrows[AssertionException] {
                "http://0.0.0.0:3333/ontology/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/0000/v2 (invalid ontology name)" in {
            assertThrows[AssertionException] {
                "http://0.0.0.0:3333/ontology/0000/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/ontology (invalid ontology name)" in {
            assertThrows[AssertionException] {
                "http://0.0.0.0:3333/ontology/ontology".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/0000/ontology (invalid ontology name)" in {
            assertThrows[AssertionException] {
                "http://0.0.0.0:3333/ontology/0000/ontology".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/0000/simple/simple/v2 (invalid ontology name)" in {
            assertThrows[AssertionException] {
                "http://0.0.0.0:3333/ontology/0000/simple/simple/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/00FF/images/v2#1234 (invalid entity name)" in {
            assertThrows[AssertionException] {
                "http://0.0.0.0:3333/ontology/00FF/images/v2#1234".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/images/v2 (missing project shortcode in ontology IRI)" in {
            assertThrows[AssertionException] {
                "http://0.0.0.0:3333/ontology/0000/simple/simple/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/images/simple/v2 (missing project shortcode in ontology IRI)" in {
            assertThrows[AssertionException] {
                "http://0.0.0.0:3333/ontology/images/simple/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/images/v2#bild (missing project shortcode in entity IRI)" in {
            assertThrows[AssertionException] {
                "http://0.0.0.0:3333/ontology/images/v2#bild".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/images/simple/v2#bild (missing project shortcode in entity IRI)" in {
            assertThrows[AssertionException] {
                "http://0.0.0.0:3333/ontology/images/simple/v2#bild".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
            }
        }

        "reject http://0.0.0.0:3333/ontology/shared/example/v2 (shared project code with local hostname in ontology IRI)" in {
            assertThrows[AssertionException] {
                "http://0.0.0.0:3333/ontology/shared/example/v2".toSmartIriWithErr(throw AssertionException(s"Invalid IRI"))
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
                val iriStr = s"http://0.0.0.0:3333/ontology/00FF/images/v2#class$i"
                val iri = iriStr.toSmartIri.toOntologySchema(InternalSchema)
            }

            val parseEnd = System.currentTimeMillis
            val parseDuration = (parseEnd - parseStart).toDouble
            val parseDurationPerIri = parseDuration / totalIris.toDouble
            println(f"Parse and store $totalIris IRIs, $parseDuration ms, time per IRI $parseDurationPerIri%1.5f ms")

            val retrieveStart = System.currentTimeMillis

            for (i <- 1 to totalIris) {
                val iriStr = s"http://0.0.0.0:3333/ontology/00FF/images/v2#class$i"
                val iri = iriStr.toSmartIri.toOntologySchema(InternalSchema)
            }

            val retrieveEnd = System.currentTimeMillis
            val retrieveDuration = (retrieveEnd - retrieveStart).toDouble
            val retrieveDurationPerIri = retrieveDuration / totalIris.toDouble

            println(f"Retrieve time $retrieveDuration ms, time per IRI $retrieveDurationPerIri%1.5f ms")
        }

        "return the data named graph of a project with short code" in {
            val shortcode = SharedTestDataV1.imagesProjectInfo.shortcode
            val shortname = SharedTestDataV1.imagesProjectInfo.shortname
            val expected = s"http://www.knora.org/data/$shortcode/$shortname"
            val result = stringFormatter.projectDataNamedGraph(SharedTestDataV1.imagesProjectInfo)
            result should be(expected)

            // check consistency of our test data
            stringFormatter.projectDataNamedGraphV2(SharedTestDataADM.anythingProject) should be (SharedOntologyTestDataADM.ANYTHING_DATA_IRI)
            stringFormatter.projectDataNamedGraphV2(SharedTestDataADM.imagesProject) should be (SharedOntologyTestDataADM.IMAGES_DATA_IRI)
            stringFormatter.projectDataNamedGraphV2(SharedTestDataADM.beolProject) should be (SharedOntologyTestDataADM.BEOL_DATA_IRI)
            stringFormatter.projectDataNamedGraphV2(SharedTestDataADM.incunabulaProject) should be (SharedOntologyTestDataADM.INCUNABULA_DATA_IRI)
            stringFormatter.projectDataNamedGraphV2(SharedTestDataADM.dokubibProject) should be (SharedOntologyTestDataADM.DOKUBIB_DATA_IRI)
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
            val hlistDef = "hlist(required):iri"

            stringFormatter.toSalsahGuiAttributeDefinition(hlistDef, throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(
                    attributeName = "hlist",
                    isRequired = true,
                    allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Iri,
                    unparsedString = hlistDef
                )
            )

            val numpropsDef = "numprops:integer"

            stringFormatter.toSalsahGuiAttributeDefinition(numpropsDef, throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(
                    attributeName = "numprops",
                    isRequired = false,
                    allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Integer,
                    unparsedString = numpropsDef
                )
            )

            val sizeDef = "size:integer"

            stringFormatter.toSalsahGuiAttributeDefinition(sizeDef, throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(
                    attributeName = "size",
                    isRequired = false,
                    allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Integer,
                    unparsedString = sizeDef
                )
            )

            val maxlengthDef = "maxlength:integer"

            stringFormatter.toSalsahGuiAttributeDefinition(maxlengthDef, throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(
                    attributeName = "maxlength",
                    isRequired = false,
                    allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Integer,
                    unparsedString = maxlengthDef
                )
            )

            val maxDef = "max(required):decimal"

            stringFormatter.toSalsahGuiAttributeDefinition(maxDef, throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(
                    attributeName = "max",
                    isRequired = true,
                    allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Decimal,
                    unparsedString = maxDef
                )
            )

            val minDef = "min(required):decimal"

            stringFormatter.toSalsahGuiAttributeDefinition(minDef, throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(
                    attributeName = "min",
                    isRequired = true,
                    allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Decimal,
                    unparsedString = minDef
                )
            )

            val widthDef = "width:percent"

            stringFormatter.toSalsahGuiAttributeDefinition(widthDef, throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(
                    attributeName = "width",
                    isRequired = false,
                    allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Percent,
                    unparsedString = widthDef
                )
            )

            val rowsDef = "rows:integer"

            stringFormatter.toSalsahGuiAttributeDefinition(rowsDef, throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(
                    attributeName = "rows",
                    isRequired = false,
                    allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Integer,
                    unparsedString = rowsDef
                )
            )

            val wrapDef = "wrap:string(soft|hard)"

            stringFormatter.toSalsahGuiAttributeDefinition(wrapDef, throw AssertionException("not valid")) should ===(
                SalsahGuiAttributeDefinition(
                    attributeName = "wrap",
                    isRequired = false,
                    allowedType = OntologyConstants.SalsahGui.SalsahGuiAttributeType.Str,
                    enumeratedValues = Set("soft", "hard"),
                    unparsedString = wrapDef
                )
            )
        }

        "generate an ARK URL for a resource IRI without a timestamp" in {
            val resourceIri: IRI = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
            val arkUrl = resourceIri.toSmartIri.fromResourceIriToArkUrl(None)
            assert(arkUrl == "http://ark.dasch.swiss/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAT")
        }

        "generate an ARK URL for a resource IRI with a timestamp" in {
            val resourceIri: IRI = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
            val arkUrl = resourceIri.toSmartIri.fromResourceIriToArkUrl(Some(Instant.parse("2018-12-07T00:00:00Z")))
            assert(arkUrl == "http://ark.dasch.swiss/ark:/72163/1/0001/cmfk1DMHRBiR4=_6HXpEFAT.2018-12-07T00:00:00Z")
        }
    }
}
