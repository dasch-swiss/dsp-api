/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

import akka.util.Timeout
import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.sharedtestdata.SharedTestDataADM

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Tests [[StandoffTagUtilV2]].
  */
class StandoffTagUtilV2Spec extends CoreSpec {
  private implicit val timeout: Timeout = 10.seconds
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

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
      standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag".toSmartIri
    ),
    StandoffTagV2(
      endParentIndex = None,
      originalXMLID = None,
      uuid = UUID.fromString("1d250370-9692-497f-a28b-bd20ebafe171"),
      endPosition = 4,
      startParentIndex = Some(0),
      attributes = Vector(
        StandoffTagStringAttributeV2(
          standoffPropertyIri =
            "http://www.knora.org/ontology/0113/lumieres-lausanne#standoffEditionTagHasFix".toSmartIri,
          value = "correction"
        ),
        StandoffTagStringAttributeV2(
          standoffPropertyIri =
            "http://www.knora.org/ontology/0113/lumieres-lausanne#standoffEditionTagHasTitle".toSmartIri,
          value = "titre"
        )
      ),
      startIndex = 1,
      endIndex = None,
      dataType = None,
      startPosition = 0,
      standoffTagClassIri = "http://www.knora.org/ontology/0113/lumieres-lausanne#StandoffEditionTag".toSmartIri
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
      standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag".toSmartIri
    ),
    StandoffTagV2(
      endParentIndex = None,
      originalXMLID = None,
      uuid = UUID.fromString("1d250370-9692-497f-a28b-bd20ebafe171"),
      endPosition = 4,
      startParentIndex = Some(0),
      attributes = Vector(
        StandoffTagStringAttributeV2(
          standoffPropertyIri =
            "http://www.knora.org/ontology/0113/lumieres-lausanne#standoffEditionTagHasTitle".toSmartIri,
          value = "titre"
        ),
        StandoffTagStringAttributeV2(
          standoffPropertyIri =
            "http://www.knora.org/ontology/0113/lumieres-lausanne#standoffEditionTagHasFix".toSmartIri,
          value = "correction"
        )
      ),
      startIndex = 1,
      endIndex = None,
      dataType = None,
      startPosition = 0,
      standoffTagClassIri = "http://www.knora.org/ontology/0113/lumieres-lausanne#StandoffEditionTag".toSmartIri
    )
  )

  val sparqlResultsV1 = Map(
    "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/3" -> Map(
      "http://www.knora.org/ontology/knora-base#standoffTagHasStartParent" -> "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/2",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStartIndex" -> "3",
      "http://www.knora.org/ontology/knora-base#standoffTagHasEnd" -> "247",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStart" -> "235",
      "http://www.knora.org/ontology/knora-base#standoffTagHasUUID" -> "JPyErhf2RVmq0QYTIAKwYw",
      "http://www.knora.org/ontology/knora-base#standoffTagHasInternalReference" -> "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/1",
      "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" -> "http://www.knora.org/ontology/knora-base#StandoffInternalReferenceTag"
    ),
    "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/0" -> Map(
      "http://www.knora.org/ontology/knora-base#standoffTagHasStartIndex" -> "0",
      "http://www.knora.org/ontology/knora-base#standoffTagHasEnd" -> "297",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStart" -> "0",
      "http://www.knora.org/ontology/knora-base#standoffTagHasUUID" -> "PQ2Xgu4mTSywsqHM8um1Tg",
      "http://www.knora.org/ontology/standoff#standoffRootTagHasDocumentType" -> "letter",
      "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" -> "http://www.knora.org/ontology/standoff#StandoffRootTag"
    ),
    "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/2" -> Map(
      "http://www.knora.org/ontology/knora-base#standoffTagHasStartParent" -> "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/0",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStartIndex" -> "2",
      "http://www.knora.org/ontology/knora-base#standoffTagHasEnd" -> "295",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStart" -> "75",
      "http://www.knora.org/ontology/knora-base#standoffTagHasUUID" -> "ysmmZTLdQEGP6dWtGOmZlg",
      "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" -> "http://www.knora.org/ontology/standoff#StandoffParagraphTag"
    ),
    "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/1" -> Map(
      "http://www.knora.org/ontology/knora-base#standoffTagHasOriginalXMLID" -> "first",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStartParent" -> "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/0",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStartIndex" -> "1",
      "http://www.knora.org/ontology/knora-base#standoffTagHasEnd" -> "69",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStart" -> "5",
      "http://www.knora.org/ontology/knora-base#standoffTagHasUUID" -> "98NJtln8QtaS5u7ahxeUfg",
      "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" -> "http://www.knora.org/ontology/standoff#StandoffParagraphTag"
    )
  )

  val sparqlResultsV2 = Map(
    "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/2" -> Map(
      "http://www.knora.org/ontology/knora-base#standoffTagHasStartParent" -> "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/0",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStartIndex" -> "2",
      "http://www.knora.org/ontology/knora-base#standoffTagHasEnd" -> "295",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStart" -> "75",
      "http://www.knora.org/ontology/knora-base#standoffTagHasUUID" -> "ysmmZTLdQEGP6dWtGOmZlg",
      "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" -> "http://www.knora.org/ontology/standoff#StandoffParagraphTag"
    ),
    "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/1" -> Map(
      "http://www.knora.org/ontology/knora-base#standoffTagHasOriginalXMLID" -> "first",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStartParent" -> "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/0",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStartIndex" -> "1",
      "http://www.knora.org/ontology/knora-base#standoffTagHasEnd" -> "69",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStart" -> "5",
      "http://www.knora.org/ontology/knora-base#standoffTagHasUUID" -> "98NJtln8QtaS5u7ahxeUfg",
      "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" -> "http://www.knora.org/ontology/standoff#StandoffParagraphTag"
    ),
    "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/0" -> Map(
      "http://www.knora.org/ontology/knora-base#standoffTagHasStartIndex" -> "0",
      "http://www.knora.org/ontology/knora-base#standoffTagHasEnd" -> "297",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStart" -> "0",
      "http://www.knora.org/ontology/knora-base#standoffTagHasUUID" -> "PQ2Xgu4mTSywsqHM8um1Tg",
      "http://www.knora.org/ontology/standoff#standoffRootTagHasDocumentType" -> "letter",
      "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" -> "http://www.knora.org/ontology/standoff#StandoffRootTag"
    ),
    "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/3" -> Map(
      "http://www.knora.org/ontology/knora-base#standoffTagHasStartParent" -> "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/2",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStartIndex" -> "3",
      "http://www.knora.org/ontology/knora-base#standoffTagHasEnd" -> "247",
      "http://www.knora.org/ontology/knora-base#targetHasOriginalXMLID" -> "first",
      "http://www.knora.org/ontology/knora-base#standoffTagHasStart" -> "235",
      "http://www.knora.org/ontology/knora-base#standoffTagHasUUID" -> "JPyErhf2RVmq0QYTIAKwYw",
      "http://www.knora.org/ontology/knora-base#standoffTagHasInternalReference" -> "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA/values/QJ0z7WgkTziowCAi-aGyWg/standoff/1",
      "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" -> "http://www.knora.org/ontology/knora-base#StandoffInternalReferenceTag"
    )
  )

  val expectedStandoffTagsV1: Vector[StandoffTagV2] = Vector(
    StandoffTagV2(
      endParentIndex = None,
      originalXMLID = None,
      uuid = stringFormatter.decodeUuid("PQ2Xgu4mTSywsqHM8um1Tg"),
      endPosition = 297,
      startParentIndex = None,
      attributes = Vector(
        StandoffTagStringAttributeV2(
          standoffPropertyIri = "http://www.knora.org/ontology/standoff#standoffRootTagHasDocumentType".toSmartIri,
          value = "letter"
        )),
      startIndex = 0,
      endIndex = None,
      dataType = None,
      startPosition = 0,
      standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag".toSmartIri
    ),
    StandoffTagV2(
      endParentIndex = None,
      originalXMLID = Some("first"),
      uuid = stringFormatter.decodeUuid("98NJtln8QtaS5u7ahxeUfg"),
      endPosition = 69,
      startParentIndex = Some(0),
      attributes = Nil,
      startIndex = 1,
      endIndex = None,
      dataType = None,
      startPosition = 5,
      standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffParagraphTag".toSmartIri
    ),
    StandoffTagV2(
      endParentIndex = None,
      originalXMLID = None,
      uuid = stringFormatter.decodeUuid("ysmmZTLdQEGP6dWtGOmZlg"),
      endPosition = 295,
      startParentIndex = Some(0),
      attributes = Nil,
      startIndex = 2,
      endIndex = None,
      dataType = None,
      startPosition = 75,
      standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffParagraphTag".toSmartIri
    ),
    StandoffTagV2(
      endParentIndex = None,
      originalXMLID = None,
      uuid = stringFormatter.decodeUuid("JPyErhf2RVmq0QYTIAKwYw"),
      endPosition = 247,
      startParentIndex = Some(2),
      attributes = Vector(
        StandoffTagInternalReferenceAttributeV2(
          standoffPropertyIri = "http://www.knora.org/ontology/knora-base#standoffTagHasInternalReference".toSmartIri,
          value = "first"
        )),
      startIndex = 3,
      endIndex = None,
      dataType = Some(StandoffDataTypeClasses.StandoffInternalReferenceTag),
      startPosition = 235,
      standoffTagClassIri = "http://www.knora.org/ontology/knora-base#StandoffInternalReferenceTag".toSmartIri
    )
  )

  val expectedStandoffTagsV2 = Vector(
    StandoffTagV2(
      endParentIndex = None,
      originalXMLID = None,
      uuid = stringFormatter.decodeUuid("PQ2Xgu4mTSywsqHM8um1Tg"),
      endPosition = 297,
      startParentIndex = None,
      attributes = Vector(
        StandoffTagStringAttributeV2(
          standoffPropertyIri = "http://www.knora.org/ontology/standoff#standoffRootTagHasDocumentType".toSmartIri,
          value = "letter"
        )),
      startIndex = 0,
      endIndex = None,
      dataType = None,
      startPosition = 0,
      standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffRootTag".toSmartIri
    ),
    StandoffTagV2(
      endParentIndex = None,
      originalXMLID = Some("first"),
      uuid = stringFormatter.decodeUuid("98NJtln8QtaS5u7ahxeUfg"),
      endPosition = 69,
      startParentIndex = Some(0),
      attributes = Nil,
      startIndex = 1,
      endIndex = None,
      dataType = None,
      startPosition = 5,
      standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffParagraphTag".toSmartIri
    ),
    StandoffTagV2(
      endParentIndex = None,
      originalXMLID = None,
      uuid = stringFormatter.decodeUuid("ysmmZTLdQEGP6dWtGOmZlg"),
      endPosition = 295,
      startParentIndex = Some(0),
      attributes = Nil,
      startIndex = 2,
      endIndex = None,
      dataType = None,
      startPosition = 75,
      standoffTagClassIri = "http://www.knora.org/ontology/standoff#StandoffParagraphTag".toSmartIri
    ),
    StandoffTagV2(
      endParentIndex = None,
      originalXMLID = None,
      uuid = stringFormatter.decodeUuid("JPyErhf2RVmq0QYTIAKwYw"),
      endPosition = 247,
      startParentIndex = Some(2),
      attributes = Vector(
        StandoffTagInternalReferenceAttributeV2(
          standoffPropertyIri = "http://www.knora.org/ontology/knora-base#standoffTagHasInternalReference".toSmartIri,
          value = "first"
        )
      ),
      startIndex = 3,
      endIndex = None,
      dataType = Some(StandoffDataTypeClasses.StandoffInternalReferenceTag),
      startPosition = 235,
      standoffTagClassIri = "http://www.knora.org/ontology/knora-base#StandoffInternalReferenceTag".toSmartIri
    )
  )

  "StandoffTagUtilV2" should {

    "compare standoff when the order of attributes is different" in {
      val comparableStandoff1 = StandoffTagUtilV2.makeComparableStandoffCollection(standoff1)
      val comparableStandoff2 = StandoffTagUtilV2.makeComparableStandoffCollection(standoff2)
      assert(comparableStandoff1 == comparableStandoff2)
    }

    "should create the correct StandoffTagV2 from SPARQL query results" in {

      val anythingUserProfile = SharedTestDataADM.anythingUser2

      val standoffTagsV1: Vector[StandoffTagV2] = Await.result(
        StandoffTagUtilV2.createStandoffTagsV2FromSelectResults(sparqlResultsV1, responderManager, anythingUserProfile),
        10.seconds)

      val standoffTagsV2: Vector[StandoffTagV2] = Await.result(
        StandoffTagUtilV2.createStandoffTagsV2FromSelectResults(sparqlResultsV2, responderManager, anythingUserProfile),
        10.seconds)

      assert(standoffTagsV1 == expectedStandoffTagsV1)
      assert(standoffTagsV2 == expectedStandoffTagsV2)

    }
  }
}
