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

/**
 * Imports JSON-LD with wrong list values into the core-validation test project (9999), whose data graph holds only
 * list nodes. `list-data.jsonld` holds one wrong resource per case, with the case name as rdfs:label; the import must
 * fail with a SHACL report naming each of them and write nothing.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ProjectDataImportListViolationsE2ESpec extends E2EZSpec with ViolationImportSupport {

  private val initGraphBase = "test_data/bulk-import-parity/initial-db-graphs/"
  private val projectGraph  = "http://www.knora.org/data/9999/core-validation"

  override def rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(initGraphBase + "admin.ttl", "http://www.knora.org/data/admin"),
    RdfDataObject(initGraphBase + "permissions.ttl", "http://www.knora.org/data/permissions"),
    RdfDataObject(initGraphBase + "onto.ttl", "http://www.knora.org/ontology/9999/onto"),
    RdfDataObject(initGraphBase + "in-built.ttl", "http://www.knora.org/ontology/9999/in-built"),
    RdfDataObject(initGraphBase + "second-onto.ttl", "http://www.knora.org/ontology/9999/second-onto"),
    // The lists of the project: the data graph of the parity fixtures plus a second list.
    RdfDataObject(initGraphBase + "data.ttl", projectGraph),
    RdfDataObject("test_data/bulk-import-violations/other-list.ttl", projectGraph),
  )

  override protected val projectIri: String     = "http://rdfh.ch/projects/Rt2eOA19Q16vMYb_cIkw2g"
  override protected val onBehalfOfUser: String = "testerKnownUser"

  private val validControl = "valid_list_control"

  /** Labels of the wrong resources of `list-data.jsonld`. */
  private val violationCases = List("list_node_non_existent", "list_wrong_list_name")

  override val e2eSpec: Spec[env, Any] = suite("Project Data Import list violations E2E")(
    test("fail the import with a report naming every resource with a wrong list node, and write nothing") {
      for {
        payload    <- readFixture("list-data.jsonld")
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
  ) @@ TestAspect.sequential
}
