/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.ZIO
import zio.test.*
import zio.test.Assertion.*

import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.admin.domain.model.KnoraProject.CopyrightAttribution
import org.knora.webapi.slice.admin.domain.model.KnoraProject.LicenseText
import org.knora.webapi.slice.admin.domain.model.KnoraProject.LicenseUri
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoInMemory
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object KnoraProjectServiceSpec extends ZIOSpecDefault {

  private val projectService = ZIO.serviceWithZIO[KnoraProjectService]
  private val repo           = ZIO.serviceWithZIO[KnoraProjectRepoInMemory]

  private val updateSuite = suite("updateProject")(
    test("should update the license and copyright attribution") {
      val project = TestDataFactory.someProject
      for {
        _ <- repo(_.save(project))
        updateRequest = ProjectUpdateRequest(
                          copyrightAttribution = Some(CopyrightAttribution.unsafeFrom("Foo")),
                          licenseText = Some(LicenseText.unsafeFrom("CC BY 4.0")),
                          licenseUri = Some(LicenseUri.unsafeFrom("https://creativecommons.org/licenses/by/4.0/")),
                        )
        actual <- projectService(_.updateProject(project, updateRequest))
      } yield assertTrue(
        actual == project.copy(
          copyrightAttribution = Some(CopyrightAttribution.unsafeFrom("Foo")),
          licenseText = Some(LicenseText.unsafeFrom("CC BY 4.0")),
          licenseUri = Some(LicenseUri.unsafeFrom("https://creativecommons.org/licenses/by/4.0/")),
        ),
      )
    },
  )
  val spec = suite("KnoraProjectService")(updateSuite)
    .provide(
      KnoraProjectService.layer,
      KnoraProjectRepoInMemory.layer,
      OntologyRepoInMemory.emptyLayer,
      IriConverter.layer,
      StringFormatter.test,
    )
}
