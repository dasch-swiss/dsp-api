/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.mainquery

import org.junit.runner.RunWith
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.SparqlSelectResultBody
import org.knora.webapi.messages.util.rdf.SparqlSelectResultHeader
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.util.search.QueryVariable
import org.knora.webapi.slice.common.ResourceIri

/**
 * Tests [[GravsearchMainQueryGenerator]].
 */
@RunWith(classOf[DspZTestJUnitRunner])
class GravsearchMainQueryGeneratorSpec extends ZIOSpecDefault {

  private val mainResourceVar = QueryVariable("mainRes")

  private def prequeryResultBindingMainResTo(values: String*): SparqlSelectResult =
    SparqlSelectResult(
      SparqlSelectResultHeader(Seq(mainResourceVar.variableName)),
      SparqlSelectResultBody(values.map(v => VariableResultsRow(Map(mainResourceVar.variableName -> v)))),
    )

  val spec: Spec[Any, Any] =
    suite("GravsearchMainQueryGenerator.getValueObjectVarsAndIrisPerMainResource")(
      test(
        "skip rows whose main-resource variable is bound to a non-resource IRI (e.g. a LinkValue) rather than throwing (DEV-6604)",
      ) {
        // For variable-predicate incoming-link searches the prequery can bind the main-resource variable to a
        // LinkValue node (`.../values/...`). Before the fix this crashed the whole request with an
        // IllegalArgumentException from `ResourceIri.unsafeFrom`; now the spurious row is dropped.
        val resourceIri = "http://rdfh.ch/0828/eawbn9g4QVKouqfB8n0WiA"
        val valueIri    = "http://rdfh.ch/0828/qMBgsLpPTMSHv9FYfG1Zcg/values/9KeFJfrLTjmPhB3158J65Q"

        val result = GravsearchMainQueryGenerator.getValueObjectVarsAndIrisPerMainResource(
          prequeryResponse = prequeryResultBindingMainResTo(resourceIri, valueIri),
          valueObjectVariablesConcat = Set.empty,
          mainResourceVar = mainResourceVar,
        )

        assertTrue(
          result.valueObjectVariablesAndValueObjectIris.keySet == Set(ResourceIri.unsafeFrom(resourceIri)),
        )
      },
      test("keep all rows when every main-resource binding is a valid resource IRI") {
        val resourceIri1 = "http://rdfh.ch/0828/eawbn9g4QVKouqfB8n0WiA"
        val resourceIri2 = "http://rdfh.ch/0810/0Y_87NRETpiNRp_Ujx-NHw"

        val result = GravsearchMainQueryGenerator.getValueObjectVarsAndIrisPerMainResource(
          prequeryResponse = prequeryResultBindingMainResTo(resourceIri1, resourceIri2),
          valueObjectVariablesConcat = Set.empty,
          mainResourceVar = mainResourceVar,
        )

        assertTrue(
          result.valueObjectVariablesAndValueObjectIris.keySet ==
            Set(ResourceIri.unsafeFrom(resourceIri1), ResourceIri.unsafeFrom(resourceIri2)),
        )
      },
    )
}
