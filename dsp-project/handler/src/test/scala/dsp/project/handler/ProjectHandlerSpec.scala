/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.handler

import dsp.valueobjects.Project._
import zio.prelude._
import zio.ZLayer
import zio._
import zio.test._
import zio.test.Assertion._
import dsp.project.domain.Project
import dsp.valueobjects.ProjectId
import dsp.valueobjects.V2
import dsp.project.repo.impl.ProjectRepoMock
import dsp.errors.NotFoundException
import dsp.errors.DuplicateValueException

object ProjectHandlerSpec extends ZIOSpecDefault {

  private def getValidated[NonEmptyChunk[E], A](validation: Validation[Throwable, A]): A =
    validation.fold(e => throw e.head, v => v)

  private val shortCode  = getValidated(ShortCode.make("0000"))
  private val shortCode2 = getValidated(ShortCode.make("0001"))
  private val id         = getValidated(ProjectId.make(shortCode))
  private val name       = getValidated(Name.make("projectName"))
  private val name2      = getValidated(Name.make("projectName 2"))
  private val description = getValidated(
    ProjectDescription.make(Seq(V2.StringLiteralV2("project description", Some("en"))))
  )
  private val description2 = getValidated(
    ProjectDescription.make(Seq(V2.StringLiteralV2("different project description", Some("en"))))
  )
  private val project = getValidated(Project.make(id, name, description))
  def spec            = (getAllProjectsTests + getProjectTests + createProjectTests + deleteProjectTests)

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

        _ <- handler.createProject(shortCode, name, description)
        _ <- handler.createProject(shortCode2, name2, description2)

        retrievedProjects <- handler.getProjects()
        shortCodeSet       = retrievedProjects.map(_.id.shortCode).toSet
      } yield assertTrue(retrievedProjects.length == 2) &&
        assertTrue(shortCodeSet == Set(shortCode, shortCode2))
    }
  ).provide(ProjectRepoMock.layer, ProjectHandler.layer)

  private val getProjectTests = suite("get a single projects")(
    suite("get a project by ID")(
      test("return an error if a project is requested that does not exist") {
        for {
          handler <- ZIO.service[ProjectHandler]
          res     <- handler.getProjectById(id).exit
        } yield assert(res)(failsWithA[NotFoundException])
      },
      test("return a project that exists") {
        for {
          handler <- ZIO.service[ProjectHandler]
          id      <- handler.createProject(shortCode, name, description)
          res     <- handler.getProjectById(id)
        } yield assertTrue(res.id == id)
      }
    ),
    suite("get a project by shortCode")(
      test("return an error if a project is requested that does not exist") {
        for {
          handler <- ZIO.service[ProjectHandler]
          res     <- handler.getProjectByShortCode(shortCode).exit
        } yield assert(res)(failsWithA[NotFoundException])
      },
      test("return a project that exists") {
        for {
          handler <- ZIO.service[ProjectHandler]
          id      <- handler.createProject(shortCode, name, description)
          res     <- handler.getProjectByShortCode(shortCode)
        } yield assertTrue(res.id == id)
      }
    )
  ).provide(ProjectRepoMock.layer, ProjectHandler.layer)

  // TODO-BL: [discuss] (how) do we test private methods
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
        handler <- ZIO.service[ProjectHandler]
        id      <- handler.createProject(shortCode, name, description)
      } yield assertTrue(id.shortCode == shortCode)
    },
    test("fail to create a project with an occupied shortCode") {
      for {
        handler <- ZIO.service[ProjectHandler]
        id      <- handler.createProject(shortCode, name, description)
        res     <- handler.createProject(shortCode, name2, description2).exit
      } yield assert(res)(fails(isSubtype[DuplicateValueException](anything)))
    } // TODO-BL: as soon as we have more illegal states, maybe they can be tested here?
  ).provide(ProjectRepoMock.layer, ProjectHandler.layer)

  private val deleteProjectTests = suite("delete a project")(
    test("successfully delete a project") {
      for {
        handler            <- ZIO.service[ProjectHandler]
        id                 <- handler.createProject(shortCode, name, description)
        deletedId          <- handler.deleteProject(id)
        retieveAfterDelete <- handler.getProjectById(id).exit
      } yield assertTrue(deletedId == id) &&
        assert(retieveAfterDelete)(fails(isSubtype[NotFoundException](anything)))
    },
    test("fail to delete a project that does not exist") {
      for {
        handler <- ZIO.service[ProjectHandler]
        delete  <- handler.deleteProject(id).exit
      } yield assert(delete)(fails(isSubtype[NotFoundException](anything)))
    }
  ).provide(ProjectRepoMock.layer, ProjectHandler.layer)

  // TODO-BL: test updating project properties once implemented
}
