/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.repo

import zio.test.*

import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.ResourceIri

object GetIncomingImageLinksGravsearchQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing")

  override val spec: Spec[Any, Nothing] = suite("GetIncomingImageLinksGravsearchQuery")(
    test("build should produce the expected Gravsearch CONSTRUCT query") {
      val actual = GetIncomingImageLinksGravsearchQuery.build(resourceIri)
      assertTrue(
        actual ==
          """|PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
             |
             |CONSTRUCT {
             |  ?resource knora-api:isMainResource true .
             |
             |  ?representation knora-api:isPartOf ?resource ;
             |      knora-api:hasStillImageFileValue ?fileValue .
             |} WHERE {
             |  BIND(<http://rdfh.ch/0001/a-thing> AS ?resource)
             |
             |  ?resource a knora-api:Resource .
             |
             |  ?representation knora-api:isPartOf ?resource ;
             |    a knora-api:StillImageRepresentation ;
             |    knora-api:hasStillImageFileValue ?fileValue .
             |}
             |""".stripMargin,
      )
    },
  )
}
