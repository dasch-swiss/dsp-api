/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.test.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.OntologyIri

object GetOntologyGraphQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testOntologyIri: OntologyIri =
    OntologyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything".toSmartIri)

  override def spec: Spec[TestEnvironment, Any] = suite("GetOntologyGraphQuerySpec")(
    suite("build")(
      test("should produce the correct CONSTRUCT query for getting an ontology graph") {
        val actual = GetOntologyGraphQuery.build(testOntologyIri).getQueryString
        assertTrue(
          actual == """CONSTRUCT { ?s ?p ?o . }
                      |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { ?s ?p ?o . } }
                      |""".stripMargin,
        )
      },
    ),
  )
}
