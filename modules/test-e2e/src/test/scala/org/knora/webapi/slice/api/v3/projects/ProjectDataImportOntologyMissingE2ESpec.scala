/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import org.junit.runner.RunWith
import sttp.client4.*
import sttp.model.*
import zio.json.ast.Json
import zio.test.*

import java.nio.charset.StandardCharsets

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.api.admin.model.ProjectOperationResponseADM
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestAdminApiClient
import org.knora.webapi.testservices.TestApiClient

@RunWith(classOf[DspZTestJUnitRunner])
class ProjectDataImportOntologyMissingE2ESpec extends E2EZSpec {

  // No ontology data is loaded. The test creates a fresh project instead of reusing a shared one: a shared project's
  // ontology leaks across specs via the in-memory ontology cache in the shared test JVM, so it never reads as
  // ontology-free. A freshly created project has a unique IRI that no spec ever loads, so its ontology set is always
  // empty.
  override def rdfDataObjects: List[RdfDataObject] = List.empty

  private val jsonLdType = MediaType.unsafeApply("application", "ld+json")
  // The precondition fails before the body is read, so the payload content is irrelevant.
  private val payload = "[]".getBytes(StandardCharsets.UTF_8)

  private val createProjectReq = ProjectCreateRequest(
    shortname = Shortname.unsafeFrom("ontomissing"),
    shortcode = Shortcode.unsafeFrom("4123"),
    description = List(Description.unsafeFrom(StringLiteralV2.from("A project with no ontologies"))),
  )

  override val e2eSpec: Spec[env, Any] = suite("Project Data Import — ontology-missing precondition E2E")(
    test("reject an import into a project with no ontologies, synchronously (REQ-9.1)") {
      for {
        project <- TestApiClient
                     .postJson[ProjectOperationResponseADM, ProjectCreateRequest](
                       uri"/admin/projects",
                       createProjectReq,
                       rootUser,
                     )
                     .flatMap(_.assert200)
                     .map(_.project)
        projectIri = project.id
        // Add an eligible non-sysadmin member. A freshly created project already grants its ProjectMember group
        // project-wide create rights (default administrative permission), so membership alone makes the user eligible.
        // Without this the on-behalf-of user fails the eligibility check (G1 order) before reaching the ontology check.
        _        <- TestAdminApiClient.addUserToProject(normalUser.userIri, projectIri, rootUser).flatMap(_.assert200)
        response <- TestApiClient.postBinary[Json](
                      uri"/v3/projects/${projectIri.value}/data-imports?onBehalfOfUser=${normalUser.username}",
                      payload,
                      jsonLdType,
                      rootUser,
                    )
      } yield assertTrue(
        response.code == StatusCode.Conflict,
        response.body.exists(_.toString.contains("project_ontologies_missing")),
      )
    },
  )
}
