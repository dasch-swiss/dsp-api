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

  // Records the data graph the search ran with; an unknown project must not run it at all.
  private def withProjectDataGraph(
    port: ProjectDataGraphs,
    limitToProject: Option[ProjectIri],
  ): Task[(String, List[Option[InternalIri]])] =
    for {
      runs   <- Ref.make(List.empty[Option[InternalIri]])
      result <- SearchResponderV2Live.withProjectDataGraph(port, limitToProject, ifUnknown = "empty") { graph =>
                  runs.update(_ :+ graph).as("searched")
                }
      ran <- runs.get
    } yield (result, ran)

  val spec = suite("SearchResponderV2Live.withProjectDataGraph")(
    test("runs the search with a known project's data graph") {
      for {
        (result, ran) <- withProjectDataGraph(port, Some(known))
      } yield assertTrue(result == "searched", ran == List(Some(dataGraph)))
    },
    test("answers an unknown project with the empty result, without running the search") {
      for {
        (result, ran) <- withProjectDataGraph(port, Some(unknown))
      } yield assertTrue(result == "empty", ran.isEmpty)
    },
    test("fails when the lookup fails, instead of falling back to an unscoped search") {
      for {
        exit <- withProjectDataGraph(failingPort, Some(known)).exit
      } yield assertTrue(exit.isFailure)
    },
    test("runs the search unscoped without a project limit, without asking the port") {
      for {
        (result, ran) <- withProjectDataGraph(failingPort, None)
      } yield assertTrue(result == "searched", ran == List(None))
    },
  )
}
