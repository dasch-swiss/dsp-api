/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.handler

import zio._
import zio.prelude._
import zio.test.Assertion._
import zio.test._

import dsp.errors.DuplicateValueException
import dsp.errors.NotFoundException
import dsp.project.domain.Project
import dsp.project.repo.impl.ProjectRepoMock
import dsp.valueobjects.Project._
import dsp.valueobjects.ProjectId
import dsp.valueobjects.V2

object ProjectHandlerSpec extends ZIOSpecDefault {

  private def initializeHandler(projects: Project*): URIO[ProjectHandler with ProjectRepoMock, ProjectHandler] =
    for {
      repo    <- ZIO.service[ProjectRepoMock]
      _       <- repo.initializeRepo(projects: _*)
      handler <- ZIO.service[ProjectHandler]
    } yield handler

  private def getValidated[NonEmptyChunk[E], A](validation: Validation[Throwable, A]): A =
    validation.fold(e => throw e.head, v => v)

  private val shortCode  = getValidated(ShortCode.make("0000"))
  private val shortCode2 = getValidated(ShortCode.make("0001"))
  private val id         = getValidated(ProjectId.make(shortCode))
  private val id2        = getValidated(ProjectId.make(shortCode2))
  private val name       = getValidated(Name.make("projectName"))
  private val name2      = getValidated(Name.make("projectName 2"))
  private val description = getValidated(
    ProjectDescription.make(Seq(V2.StringLiteralV2("project description", Some("en"))))
  )
  private val description2 = getValidated(
    ProjectDescription.make(Seq(V2.StringLiteralV2("different project description", Some("en"))))
  )
  private val project  = getValidated(Project.make(id, name, description))
  private val project2 = getValidated(Project.make(id2, name2, description2))
  // private val project = getValidated(Project.make(id, name, description))
  def spec = suite("ProjectHandlerSpec")(
    getProjectsTests,
    getProjectTests,
    createProjectTests,
    deleteProjectTests,
    updateProjectTests
  ).provide(ProjectRepoMock.layer, ProjectHandler.layer)

  private val getProjectsTests = suite("get all projects")(
    test("return an empty list when requesting all users when there are none") {
      for {
        handler           <- initializeHandler()
        retrievedProjects <- handler.getProjects()
      } yield assertTrue(retrievedProjects == List.empty)
    },
    test("return all projects when several are stored") {
      for {
        handler           <- initializeHandler(project, project2)
        retrievedProjects <- handler.getProjects()
        shortCodeSet       = retrievedProjects.map(_.id.shortCode).toSet
      } yield assertTrue(retrievedProjects.length == 2) &&
        assertTrue(shortCodeSet == Set(shortCode, shortCode2))
    }
  )

  private val getProjectTests = suite("get a single projects")(
    suite("get a project by ID")(
      test("return an error if a project is requested that does not exist") {
        for {
          handler <- initializeHandler()
          res     <- handler.getProjectById(id).exit
        } yield assert(res)(failsWithA[NotFoundException])
      },
      test("return a project that exists") {
        for {
          handler <- initializeHandler(project)
          res     <- handler.getProjectById(id)
        } yield assertTrue(res.id == id)
      }
    ),
    suite("get a project by shortCode")(
      test("return an error if a project is requested that does not exist") {
        for {
          handler <- initializeHandler()
          res     <- handler.getProjectByShortCode(shortCode).exit
        } yield assert(res)(failsWithA[NotFoundException])
      },
      test("return a project that exists") {
        for {
          handler <- initializeHandler(project)
          res     <- handler.getProjectByShortCode(shortCode)
        } yield assertTrue(res.id == id)
      }
    )
  )

  private val createProjectTests = suite("create a project")(
    test("successfully create a project") {
      for {
        handler <- ZIO.service[ProjectHandler]
        id      <- handler.createProject(project)
      } yield assertTrue(id.shortCode == shortCode)
    },
    test("fail to create a project with an occupied shortCode") {
      for {
        handler                      <- ZIO.service[ProjectHandler]
        _                            <- handler.createProject(project)
        idWithOccupiedShortCode      <- ProjectId.make(shortCode).toZIO
        projectWithOccupiedShortCode <- Project.make(idWithOccupiedShortCode, name2, description2).toZIO
        res                          <- handler.createProject(projectWithOccupiedShortCode).exit
      } yield assert(res)(fails(isSubtype[DuplicateValueException](anything)))
    }
  )

  private val deleteProjectTests = suite("delete a project")(
    test("successfully delete a project") {
      for {
        handler            <- initializeHandler(project)
        deletedId          <- handler.deleteProject(id)
        retieveAfterDelete <- handler.getProjectById(id).exit
      } yield assertTrue(deletedId == id) &&
        assert(retieveAfterDelete)(fails(isSubtype[NotFoundException](anything)))
    },
    test("fail to delete a project that does not exist") {
      for {
        handler <- initializeHandler()
        delete  <- handler.deleteProject(id).exit
      } yield assert(delete)(fails(isSubtype[NotFoundException](anything)))
    }
  )

  private val updateProjectTests = suite("update a project")(
    suite("update project name")(
      test("successfully update a project name with a valid name") {
        for {
          handler <- initializeHandler(project)
          res     <- handler.updateProjectName(id, name2).exit
        } yield assert(res)(succeeds(equalTo(id)))
      },
      test("not update a project name of a non-existing project")(
        for {
          handler <- initializeHandler(project)
          res     <- handler.updateProjectName(id2, name2).exit
        } yield assert(res)(fails(isSubtype[NotFoundException](anything)))
      )
    ),
    suite("update project description")(
      test("successfully update a project description with a valid description") {
        for {
          handler <- initializeHandler(project)
          res     <- handler.updateProjectDescription(id, description2).exit
        } yield assert(res)(succeeds(equalTo(id)))
      },
      test("not update a project description of a non-existing project")(
        for {
          handler <- initializeHandler(project)
          res     <- handler.updateProjectDescription(id2, description2).exit
        } yield assert(res)(fails(isSubtype[NotFoundException](anything)))
      )
    )
  )
}
