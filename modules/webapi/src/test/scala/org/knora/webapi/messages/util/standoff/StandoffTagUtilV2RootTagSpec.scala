/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.standoff

import org.junit.runner.RunWith
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.standoffmessages.*
import org.knora.webapi.slice.common.StandoffMappingIri

@RunWith(classOf[DspZTestJUnitRunner])
class StandoffTagUtilV2RootTagSpec extends ZIOSpecDefault {

  private val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val rootTagIri = OntologyConstants.Standoff.StandoffRootTag

  // A hermetic mapping that maps only the root element, as the standard mapping does for "text".
  private val mappingResponse: GetMappingResponseV2 = {
    val rootClassIri = sf.toSmartIri(rootTagIri)
    GetMappingResponseV2(
      StandoffMappingIri.StandardMapping,
      MappingXMLtoStandoff(
        namespace = Map(
          "noNamespace" -> Map(
            "text" -> Map(
              "noClass" -> XMLTag(
                "text",
                XMLTagToStandoffClass(rootTagIri, Map.empty, dataType = None),
                separatorRequired = false,
              ),
            ),
          ),
        ),
        defaultXSLTransformation = None,
      ),
      StandoffEntityInfoGetResponseV2(
        standoffClassInfoMap = Map(
          rootClassIri -> ReadClassInfoV2(
            entityInfoContent = ClassInfoContentV2(classIri = rootClassIri, ontologySchema = InternalSchema),
            allBaseClasses = Seq.empty,
            isStandoffClass = true,
          ),
        ),
        standoffPropertyInfoMap = Map.empty,
      ),
    )
  }

  private def tagCount(xml: String): Int =
    StandoffTagUtilV2
      .convertXMLtoStandoffTagV2(xml, mappingResponse, acceptStandoffLinksToClientIDs = false)
      .standoffTagV2
      .size

  override def spec: Spec[Any, Nothing] =
    suite("StandoffTagUtilV2.convertXMLtoStandoffTagV2")(
      test("the root element always yields a standoff tag, even for an empty root") {
        assertTrue(tagCount("""<?xml version="1.0" encoding="UTF-8"?><text></text>""") == 1)
      },
      test("the root element always yields a standoff tag, even for text without markup") {
        assertTrue(tagCount("<text>plain words only</text>") == 1)
      },
    )
}
