/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.apache.jena.update.UpdateFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.OntologyIri

@RunWith(classOf[DspZTestJUnitRunner])
class DeleteOntologyQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private def canonical(query: String): String = UpdateFactory.create(query).toString

  private val testOntologyIri: OntologyIri =
    OntologyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything".toSmartIri)

  override def spec: Spec[TestEnvironment, Any] = suite("DeleteOntologyQuerySpec")(
    suite("build")(
      test("should produce the correct query for deleting an ontology") {
        val actual = DeleteOntologyQuery.build(testOntologyIri).sparql
        assertTrue(
          canonical(actual) == canonical(
            """DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { ?s ?p ?o . } }
              |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { ?s ?p ?o . } }""".stripMargin,
          ),
        )
      },
    ),
  )
}
