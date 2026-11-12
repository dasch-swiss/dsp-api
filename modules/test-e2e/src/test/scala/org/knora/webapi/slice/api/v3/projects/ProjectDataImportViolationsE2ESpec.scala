/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*

/**
 * Imports invalid knora-api JSON-LD into incunabula. `data.jsonld` holds one wrong resource per case, with the case
 * name as rdfs:label; the import must fail with a SHACL report naming each of them and write nothing. Cases that the
 * transformer may reject before SHACL runs are imported on their own, so that one rejection cannot mask the others.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ProjectDataImportViolationsE2ESpec extends E2EZSpec with ViolationImportSupport {

  // The incunabula ontology with a data graph holding only list nodes, which satisfies the create-only import.
  override def rdfDataObjects: List[RdfDataObject] = List(
    incunabulaRdfOntology,
    RdfDataObject("test_data/project_data/incunabula-lists-only-data.ttl", "http://www.knora.org/data/0803/incunabula"),
  )

  override protected val projectIri: String     = incunabulaProjectIri.value
  override protected val onBehalfOfUser: String = incunabulaMemberUser.username

  private val validControl = "valid_control"

  /** Labels of the wrong resources of `data.jsonld`; the target of the wrong-class link is a valid resource. */
  private val violationCases = List(
    "card_1_missing",
    "max_card_violation",
    "prop_does_not_have_card",
    "value_wrong_type",
    "link_target_wrong_class",
    "link_target_non_existent",
  )

  override val e2eSpec: Spec[env, Any] = suite("Project Data Import violations E2E")(
    test("fail the import with a report naming every wrong resource, and write nothing") {
      for {
        payload    <- readFixture("data.jsonld")
        iriByLabel <- resourceIrisByLabel(payload)
        ended      <- importAndWait(payload)
        notWritten <- resourceNotFound(iriByLabel(validControl))
      } yield {
        val reported = violationCases.map { name =>
          assertTrue(ended.reports(iriByLabel(name))) ?? s"the report does not name '$name' (${iriByLabel(name)})"
        }
        assertTrue(ended.accepted, ended.failed, notWritten) && reported.reduce(_ && _)
      }
    },
    // The transformer may reject the unknown class when it resolves the default permissions of the class.
    singleResourceTest("cls_not_in_onto", "nonExistingClass"),
    // The transformer may reject the built-in class when it resolves the default permissions of the class.
    singleResourceTest("builtin_representation", "knora-base#Representation"),
    // The transformer may reject the built-in class when it resolves the default permissions of the class.
    singleResourceTest("builtin_resource", "knora-base#Resource"),
  ) @@ TestAspect.sequential

  /** The import fails, names the resource or its class, and writes nothing, whichever stage rejects it. */
  private def singleResourceTest(name: String, classMarker: String) =
    test(s"fail the import of $name and write nothing") {
      for {
        payload    <- readFixture(s"$name.jsonld")
        iri        <- resourceIrisByLabel(payload).map(_(name))
        ended      <- importAndWait(payload)
        notWritten <- resourceNotFound(iri)
      } yield assertTrue(
        ended.accepted,
        ended.failed,
        ended.reports(iri) || ended.reports(classMarker),
        notWritten,
      )
    }
}
