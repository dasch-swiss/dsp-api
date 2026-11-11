/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.StringFormatter

@RunWith(classOf[DspZTestJUnitRunner])
class StandoffSelectionSpec extends ZIOSpecDefault {

  private val sf        = StringFormatter.getInitializedTestInstance
  private val tagFilter = Some(sf.toSmartIri("http://www.knora.org/ontology/standoff#StandoffFootnoteTag"))

  val spec: Spec[Any, Nothing] = suite("StandoffSelection.from")(
    test("is NoStandoff for the simple schema") {
      assertTrue(StandoffSelection.from(ApiV2Simple, Set.empty) == StandoffSelection.NoStandoff)
    },
    test("is NoStandoff for markup=standoff") {
      assertTrue(
        StandoffSelection.from(ApiV2Complex, Set(MarkupRendering.Standoff)) == StandoffSelection.NoStandoff,
      )
    },
    test("is NoStandoff for markup=standoff even when stored XML is served") {
      assertTrue(
        StandoffSelection.from(ApiV2Complex, Set(MarkupRendering.Standoff, ServeStoredXml)) ==
          StandoffSelection.NoStandoff,
      )
    },
    test("is NoStandoff for markup=standoff even with a standoff tag filter") {
      assertTrue(
        StandoffSelection.from(ApiV2Complex, Set(MarkupRendering.Standoff), tagFilter) == StandoffSelection.NoStandoff,
      )
    },
    test("is AllStandoff for the complex schema without options") {
      assertTrue(StandoffSelection.from(ApiV2Complex, Set.empty) == StandoffSelection.AllStandoff)
    },
    test("is AllStandoff with a standoff tag filter") {
      assertTrue(StandoffSelection.from(ApiV2Complex, Set.empty, tagFilter) == StandoffSelection.AllStandoff)
    },
    test("is OnlyWithoutStoredXml when stored XML is served") {
      assertTrue(StandoffSelection.from(ApiV2Complex, Set(ServeStoredXml)) == StandoffSelection.OnlyWithoutStoredXml)
    },
    test("is AllStandoff when stored XML is served and a standoff tag filter is set") {
      assertTrue(StandoffSelection.from(ApiV2Complex, Set(ServeStoredXml), tagFilter) == StandoffSelection.AllStandoff)
    },
    test("is NoStandoff when stored XML is served for the simple schema") {
      assertTrue(StandoffSelection.from(ApiV2Simple, Set(ServeStoredXml)) == StandoffSelection.NoStandoff)
    },
  )
}
