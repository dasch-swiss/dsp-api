/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.repo

import zio.test.*

import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri

object GetResourceWithSpecifiedPropertiesGravsearchQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val resourceIri  = ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing".toSmartIri)
  private val propertyIris = Seq(
    PropertyIri.unsafeFrom("http://www.knora.org/ontology/knora-base#hasStillImageFileValue".toSmartIri),
    PropertyIri.unsafeFrom("http://www.knora.org/ontology/knora-base#hasStandoffLinkTo".toSmartIri),
  )

  override val spec: Spec[Any, Nothing] = suite("GetResourceWithSpecifiedPropertiesGravsearchQuery")(
    test("build should produce the expected Gravsearch CONSTRUCT query with multiple properties") {
      val actual = GetResourceWithSpecifiedPropertiesGravsearchQuery.build(resourceIri, propertyIris)
      assertTrue(
        actual ==
          """|PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
             |
             |CONSTRUCT {
             |  ?resource knora-api:isMainResource true .
             |  ?resource <http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue> ?propertyObj0 .
             |  ?resource <http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo> ?propertyObj1 .
             |} WHERE {
             |  BIND(<http://rdfh.ch/0001/a-thing> AS ?resource)
             |
             |  ?resource a knora-api:Resource .
             |
             |  OPTIONAL {
             |    ?resource <http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue> ?propertyObj0 .
             |  }
             |  OPTIONAL {
             |    ?resource <http://api.knora.org/ontology/knora-api/v2#hasStandoffLinkTo> ?propertyObj1 .
             |  }
             |}
             |""".stripMargin,
      )
    },
    test("build should produce the expected Gravsearch CONSTRUCT query with a single property") {
      val singlePropertyIri = Seq(
        PropertyIri.unsafeFrom("http://www.knora.org/ontology/knora-base#hasStillImageFileValue".toSmartIri),
      )
      val actual = GetResourceWithSpecifiedPropertiesGravsearchQuery.build(resourceIri, singlePropertyIri)
      assertTrue(
        actual ==
          """|PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
             |
             |CONSTRUCT {
             |  ?resource knora-api:isMainResource true .
             |  ?resource <http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue> ?propertyObj0 .
             |} WHERE {
             |  BIND(<http://rdfh.ch/0001/a-thing> AS ?resource)
             |
             |  ?resource a knora-api:Resource .
             |
             |  OPTIONAL {
             |    ?resource <http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue> ?propertyObj0 .
             |  }
             |}
             |""".stripMargin,
      )
    },
  )
}
