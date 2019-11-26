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

package org.knora.webapi.util.clientapi

import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{SmartIri, StringFormatter}
import org.knora.webapi.{ClientApiGenerationException, CoreSpec, OntologyConstants}

/**
  * Tests [[ClientCollectionTypeParser]].
  */
class ClientCollectionTypeParserSpec extends CoreSpec() {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    private val ontologyIri: SmartIri = OntologyConstants.KnoraAdminV2.KnoraAdminOntologyIri.toSmartIri
    private val permissionClassIri: SmartIri = ontologyIri.makeEntityIri("Permission")
    private val permissionClassRef = ClassRef(className = "Permission", classIri = permissionClassIri)

    "The ClientCollectionTypeParser class" should {
        "parse Array[String]" in {
            val collectionType = ClientCollectionTypeParser.parse(typeStr = "Array[String]", ontologyIri = ontologyIri)
            assert(collectionType == ArrayType(elementType = StringDatatype))
            assert(collectionType.getClassIri.isEmpty)
        }

        "parse Array[URI]" in {
            val collectionType = ClientCollectionTypeParser.parse(typeStr = "Array[URI]", ontologyIri = ontologyIri)
            assert(collectionType == ArrayType(elementType = UriDatatype))
            assert(collectionType.getClassIri.isEmpty)
        }

        "parse Array[Permission]" in {
            val collectionType = ClientCollectionTypeParser.parse(typeStr = "Array[Permission]", ontologyIri = ontologyIri)
            assert(collectionType == ArrayType(elementType = permissionClassRef))
            assert(collectionType.getClassIri.contains(permissionClassIri))
        }

        "parse Map[String, String]" in {
            val collectionType = ClientCollectionTypeParser.parse(typeStr = "Map[String, String]", ontologyIri = ontologyIri)
            assert(collectionType == MapType(keyType = StringDatatype, valueType = StringDatatype))
            assert(collectionType.getClassIri.isEmpty)
        }

        "parse Map[URI, String]" in {
            val collectionType = ClientCollectionTypeParser.parse(typeStr = "Map[URI, String]", ontologyIri = ontologyIri)
            assert(collectionType == MapType(keyType = UriDatatype, valueType = StringDatatype))
            assert(collectionType.getClassIri.isEmpty)
        }

        "parse Map[URI, Permission]" in {
            val collectionType = ClientCollectionTypeParser.parse(typeStr = "Map[URI, Permission]", ontologyIri = ontologyIri)

            assert(collectionType == MapType(
                keyType = UriDatatype,
                valueType = permissionClassRef
            ))

            assert(collectionType.getClassIri.contains(permissionClassIri))
        }

        "parse Map[URI, Array[String]]" in {
            val collectionType = ClientCollectionTypeParser.parse(typeStr = "Map[URI, Array[String]]", ontologyIri = ontologyIri)

            assert(collectionType == MapType(
                keyType = UriDatatype,
                valueType = ArrayType(elementType = StringDatatype)
            ))

            assert(collectionType.getClassIri.isEmpty)
        }

        "parse Map[URI, Array[Permission]]" in {
            val collectionType = ClientCollectionTypeParser.parse(typeStr = "Map[URI, Array[Permission]]", ontologyIri = ontologyIri)

            assert(collectionType == MapType(
                keyType = UriDatatype,
                valueType = ArrayType(elementType = permissionClassRef)
            ))

            assert(collectionType.getClassIri.contains(permissionClassIri))
        }

        "parse Map[URI, Array[Map[URI, Permission]]]" in {
            val collectionType = ClientCollectionTypeParser.parse(typeStr = "Map[URI, Array[Map[URI, Permission]]]", ontologyIri = ontologyIri)

            assert(collectionType == MapType(
                keyType = UriDatatype,
                valueType = ArrayType(
                    elementType = MapType(
                        keyType = UriDatatype,
                        valueType = permissionClassRef
                    )
                )
            ))

            assert(collectionType.getClassIri.contains(permissionClassIri))
        }

        "parse Array[Map[URI, Array[Map[URI, Permission]]]]" in {
            val collectionType = ClientCollectionTypeParser.parse(typeStr = "Array[Map[URI, Array[Map[URI, Permission]]]]", ontologyIri = ontologyIri)

            assert(collectionType == ArrayType(
                elementType = MapType(
                    keyType = UriDatatype,
                    valueType = ArrayType(
                        elementType = MapType(
                            keyType = UriDatatype,
                            valueType = permissionClassRef
                        )
                    )
                )
            ))

            assert(collectionType.getClassIri.contains(permissionClassIri))
        }

        "reject String" in {
            assertThrows[ClientApiGenerationException] {
                ClientCollectionTypeParser.parse(typeStr = "String", ontologyIri = ontologyIri)
            }
        }

        "reject Array[]" in {
            assertThrows[ClientApiGenerationException] {
                ClientCollectionTypeParser.parse(typeStr = "Array[]", ontologyIri = ontologyIri)
            }
        }

        "reject Array[Map[URI, Array[Map[URI, Permission]]]" in {
            assertThrows[ClientApiGenerationException] {
                ClientCollectionTypeParser.parse(typeStr = "Array[Map[URI, Array[Map[URI, Permission]]]", ontologyIri = ontologyIri)
            }
        }

        "reject Map[String, ]" in {
            assertThrows[ClientApiGenerationException] {
                ClientCollectionTypeParser.parse(typeStr = "Map[String, ]", ontologyIri = ontologyIri)
            }
        }

        "reject []" in {
            assertThrows[ClientApiGenerationException] {
                ClientCollectionTypeParser.parse(typeStr = "[]", ontologyIri = ontologyIri)
            }
        }

        "reject Array[String],Array[String]" in {
            assertThrows[ClientApiGenerationException] {
                ClientCollectionTypeParser.parse(typeStr = "Array[String],Array[String]", ontologyIri = ontologyIri)
            }
        }

        "reject ,Array[String]" in {
            assertThrows[ClientApiGenerationException] {
                ClientCollectionTypeParser.parse(typeStr = ",Array[String]", ontologyIri = ontologyIri)
            }
        }
    }
}
