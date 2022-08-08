/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.handler

import dsp.valueobjects.Project._
import zio.ZLayer
import zio._
import zio.test._
import zio.test.Assertion._
import dsp.project.domain.Project
import dsp.valueobjects.ProjectId
import dsp.valueobjects
import dsp.project.repo.impl.ProjectRepoMock
import dsp.errors.NotFoundException
import dsp.errors.DuplicateValueException

object ProjectHandlerSpec extends ZIOSpecDefault {
  def spec = (getAllProjectsTests + getProjectTests + createProjectTests + deleteProjectTests)

  private val getAllProjectsTests = suite("get all projects")(
    test("return an empty list when requesting all users when there are none") {
      for {
        projectHandler    <- ZIO.service[ProjectHandler]
        retrievedProjects <- projectHandler.getProjects()
      } yield assertTrue(retrievedProjects == List.empty)
    },
    test("return all projects when several are stored") {
      for {
        handler <- ZIO.service[ProjectHandler]

        shortCode1 <- ShortCode.make("0000").toZIO
        _          <- handler.createProject(shortCode1, "project1", "project description")

        shortCode2 <- ShortCode.make("0001").toZIO
        _          <- handler.createProject(shortCode2, "project2", "project description")

        retrievedProjects <- handler.getProjects()
        shortCodeSet       = retrievedProjects.map(_.id.shortCode).toSet
      } yield assertTrue(retrievedProjects.length == 2) &&
        assertTrue(shortCodeSet == Set(shortCode1, shortCode2))
    }
  ).provide(ProjectRepoMock.layer, ProjectHandler.layer)

  private val getProjectTests = suite("get a single projects")(
    suite("get a project by ID")(
      test("return an error if a project is requested that does not exist") {
        for {
          handler <- ZIO.service[ProjectHandler]

          shortCode <- ShortCode.make("0000").toZIO
          id        <- ProjectId.make(shortCode).toZIO

          res <- handler.getProjectById(id).exit
        } yield assert(res)(failsWithA[NotFoundException])
      },
      test("return a project that exists") {
        for {
          handler <- ZIO.service[ProjectHandler]

          shortCode <- ShortCode.make("0000").toZIO
          id        <- handler.createProject(shortCode, "project1", "a project")

          res <- handler.getProjectById(id)
        } yield assertTrue(res.id == id)
      }
    ),
    suite("get a project by shortCode")(
      test("return an error if a project is requested that does not exist") {
        for {
          handler <- ZIO.service[ProjectHandler]

          shortCode <- ShortCode.make("0000").toZIO

          res <- handler.getProjectByShortCode(shortCode).exit
        } yield assert(res)(failsWithA[NotFoundException])
      },
      test("return a project that exists") {
        for {
          handler <- ZIO.service[ProjectHandler]

          shortCode <- ShortCode.make("0000").toZIO
          id        <- handler.createProject(shortCode, "project1", "a project")

          res <- handler.getProjectByShortCode(shortCode)
        } yield assertTrue(res.id == id)
      }
    )
  ).provide(ProjectRepoMock.layer, ProjectHandler.layer)

  // TODO-BL: do we test private methods
//   private val checkIfShortCodeTakenTests = suite("check if a shortcode is already taken")(
//     test("return nothing if the repo is empty") {
//       for {
//         handler <- ZIO.service[ProjectHandler]
//         res     <- handler.
//       } yield assertTrue(retrievedProjects == List.empty)
//     }
//   ).provide(ProjectRepoMock.layer, ProjectHandler.layer)

  private val createProjectTests = suite("create a project")(
    test("successfully create a project") {
      for {
        handler   <- ZIO.service[ProjectHandler]
        shortCode <- ShortCode.make("0000").toZIO
        id        <- handler.createProject(shortCode, "project1", "project description")
      } yield assertTrue(id.shortCode == shortCode)
    },
    test("fail to create a project with an occupied shortCode") {
      for {
        handler   <- ZIO.service[ProjectHandler]
        shortCode <- ShortCode.make("0000").toZIO
        _         <- handler.createProject(shortCode, "project1", "project description")
        res       <- handler.createProject(shortCode, "project2", "different project description").exit
      } yield assert(res)(fails(isSubtype[DuplicateValueException](anything)))
    } // TODO-BL: as soon as we have more illegal states, maybe they can be tested here?
  ).provide(ProjectRepoMock.layer, ProjectHandler.layer)

  private val deleteProjectTests = suite("delete a project")(
    test("successfully delete a project") {
      for {
        handler            <- ZIO.service[ProjectHandler]
        shortCode          <- ShortCode.make("0000").toZIO
        id                 <- handler.createProject(shortCode, "project1", "project description")
        deletedId          <- handler.deleteProject(id)
        retieveAfterDelete <- handler.getProjectById(id).exit
      } yield assertTrue(deletedId == id) &&
        assert(retieveAfterDelete)(fails(isSubtype[NotFoundException](anything)))
    },
    test("fail to delete a project that does not exist") {
      for {
        handler   <- ZIO.service[ProjectHandler]
        shortCode <- ShortCode.make("0000").toZIO
        id        <- ProjectId.make(shortCode).toZIO
        delete    <- handler.deleteProject(id).exit
      } yield assert(delete)(fails(isSubtype[NotFoundException](anything)))
    }
  ).provide(ProjectRepoMock.layer, ProjectHandler.layer)

  // TODO-BL: test updating project properties once implemented
}
