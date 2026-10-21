/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.search.ports.ProjectDataGraphs
import org.knora.webapi.slice.search.ports.ProjectDataGraphsInMemory
import org.knora.webapi.slice.search.repo.FulltextProjectRestriction

@RunWith(classOf[DspZTestJUnitRunner])
class SearchResponderV2LiveSpec extends ZIOSpecDefault {

  private val known     = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val unknown   = ProjectIri.unsafeFrom("http://rdfh.ch/projects/9999")
  private val dataGraph = InternalIri("http://www.knora.org/data/0001/anything")
  private val port      = ProjectDataGraphsInMemory(Map(known -> dataGraph))

  private val failingPort = new ProjectDataGraphs {
    override def dataGraphOf(project: ProjectIri): Task[Option[InternalIri]] =
      ZIO.fail(new RuntimeException("triplestore down"))
  }

  val spec = suite("SearchResponderV2Live.resolveRestriction")(
    test("resolves a known project to Scoped with its data graph") {
      for {
        restriction <- SearchResponderV2Live.resolveRestriction(port, Some(known))
      } yield assertTrue(restriction.contains(FulltextProjectRestriction.Scoped(known, dataGraph)))
    },
    test("resolves an unknown project to Unresolved") {
      for {
        restriction <- SearchResponderV2Live.resolveRestriction(port, Some(unknown))
      } yield assertTrue(restriction.contains(FulltextProjectRestriction.Unresolved(unknown)))
    },
    test("fails when the lookup fails, instead of falling back to an unscoped search") {
      for {
        exit <- SearchResponderV2Live.resolveRestriction(failingPort, Some(known)).exit
      } yield assertTrue(exit.isFailure)
    },
    test("returns None without a project restriction, without asking the port") {
      for {
        restriction <- SearchResponderV2Live.resolveRestriction(failingPort, None)
      } yield assertTrue(restriction.isEmpty)
    },
  )
}
