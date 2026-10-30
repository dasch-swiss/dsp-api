/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.TestDataFactory
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.common.domain.InternalIri

@RunWith(classOf[DspZTestJUnitRunner])
class ProjectDataGraphsLiveSpec extends ZIOSpecDefault {

  private val project = TestDataFactory.someProject

  // findById and getDataGraphForProject use neither the licence nor the ontology repo.
  private val adapter = ZIO.serviceWithZIO[KnoraProjectRepoInMemory] { repo =>
    repo.save(project).as(ProjectDataGraphsLive(KnoraProjectService(repo, null, null)))
  }

  val spec = suite("ProjectDataGraphsLive")(
    test("resolves a known project to its data graph") {
      for {
        graph <- adapter.flatMap(_.dataGraphOf(project.id))
      } yield assertTrue(graph.contains(InternalIri("http://www.knora.org/data/0001/shortname")))
    },
    test("resolves an unknown project to None") {
      for {
        graph <- adapter.flatMap(_.dataGraphOf(ProjectIri.unsafeFrom("http://rdfh.ch/projects/9999")))
      } yield assertTrue(graph.isEmpty)
    },
  ).provide(KnoraProjectRepoInMemory.layer)
}
