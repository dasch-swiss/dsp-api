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

package org.knora.webapi.util.standoff

import java.util.UUID
import org.knora.webapi.CoreSpec
import org.knora.webapi.twirl.{StandoffTagStringAttributeV2, StandoffTagV2}

/**
  * Tests [[StandoffTagUtilV2]].
  */
class StandoffTagUtilV2Spec extends CoreSpec {
    "StandoffTagUtilV2" should {

        "compare standoff when the order of attributes is different" in {
            val comparableStandoff1 = StandoffTagUtilV2.makeComparableStandoffCollection(StandoffTagUtilV2Spec.standoff1)
            val comparableStandoff2 = StandoffTagUtilV2.makeComparableStandoffCollection(StandoffTagUtilV2Spec.standoff2)
            assert(comparableStandoff1 == comparableStandoff2)
        }
    }
}

object StandoffTagUtilV2Spec {
    val standoff1: Vector[StandoffTagV2] = Vector(
        StandoffTagV2(
            endParentIndex = None,
            originalXMLID = None,
            uuid = UUID.fromString("e8c2c060-a41d-4403-ac7d-d0f84f772378"),
            endPosition = 5,
            startParentIndex = None,
            attributes = Nil,
            startIndex = 0,
            endIndex = None,
            dataType = None,
            startPosition = 0,
            standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag"
        ),
        StandoffTagV2(
            endParentIndex = None,
            originalXMLID = None,
            uuid = UUID.fromString("1d250370-9692-497f-a28b-bd20ebafe171"),
            endPosition = 4,
            startParentIndex = Some(0),
            attributes = Vector(
                StandoffTagStringAttributeV2(
                    standoffPropertyIri = "http://www.knora.org/ontology/0113/lumieres-lausanne#standoffEditionTagHasFix",
                    value = "correction"
                ),
                StandoffTagStringAttributeV2(
                    standoffPropertyIri = "http://www.knora.org/ontology/0113/lumieres-lausanne#standoffEditionTagHasTitle",
                    value = "titre"
                )
            ),
            startIndex = 1,
            endIndex = None,
            dataType = None,
            startPosition = 0,
            standoffTagClassIri = "http://www.knora.org/ontology/0113/lumieres-lausanne#StandoffEditionTag"
        )
    )

    val standoff2: Vector[StandoffTagV2] = Vector(
        StandoffTagV2(
            endParentIndex = None,
            originalXMLID = None,
            uuid = UUID.fromString("e8c2c060-a41d-4403-ac7d-d0f84f772378"),
            endPosition = 5,
            startParentIndex = None,
            attributes = Nil,
            startIndex = 0,
            endIndex = None,
            dataType = None,
            startPosition = 0,
            standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag"
        ),
        StandoffTagV2(
            endParentIndex = None,
            originalXMLID = None,
            uuid = UUID.fromString("1d250370-9692-497f-a28b-bd20ebafe171"),
            endPosition = 4,
            startParentIndex = Some(0),
            attributes = Vector(
                StandoffTagStringAttributeV2(
                    standoffPropertyIri = "http://www.knora.org/ontology/0113/lumieres-lausanne#standoffEditionTagHasTitle",
                    value = "titre"
                ),
                StandoffTagStringAttributeV2(
                    standoffPropertyIri = "http://www.knora.org/ontology/0113/lumieres-lausanne#standoffEditionTagHasFix",
                    value = "correction"
                )
            ),
            startIndex = 1,
            endIndex = None,
            dataType = None,
            startPosition = 0,
            standoffTagClassIri = "http://www.knora.org/ontology/0113/lumieres-lausanne#StandoffEditionTag"
        )
    )
}
