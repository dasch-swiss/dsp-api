/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.repo.impl

import zio._
import zio.test._

import dsp.project.domain.Project
import dsp.valueobjects.Project._
import dsp.valueobjects.ProjectId
import dsp.valueobjects.V2

object ProjectRepoMockSpec extends ZIOSpecDefault {

  def spec =
    suite("ProjectRepoMockSpec")(
      testInitializeRepo
    ).provide(ProjectRepoMock.layer)

  val testInitializeRepo =
    suite("initialize repo with projects")(
      test("initialize repo empty") {
        for {
          repo     <- ZIO.service[ProjectRepoMock]
          _        <- repo.initializeRepo()
          contents <- repo.getProjects()
        } yield (assertTrue(contents == Nil))
      },
      test("initialize repo with a project") {
        for {
          shortCode   <- ShortCode.make("0000").toZIO
          id          <- ProjectId.make(shortCode).toZIO
          name        <- Name.make("projectName").toZIO
          description <- ProjectDescription.make(Seq(V2.StringLiteralV2("project description", Some("en")))).toZIO
          project     <- Project.make(id, name, description).toZIO

          repo     <- ZIO.service[ProjectRepoMock]
          _        <- repo.initializeRepo(project)
          contents <- repo.getProjects()
        } yield (assertTrue(contents == scala.collection.immutable.List(project)))
      }
    )

}
