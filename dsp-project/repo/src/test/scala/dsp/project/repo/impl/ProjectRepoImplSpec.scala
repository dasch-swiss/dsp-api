/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.repo.impl

import zio.prelude.Validation
import zio.test.Assertion._
import zio.test._

import dsp.project.api.ProjectRepo
import dsp.project.domain.Project
import dsp.valueobjects.Project._
import dsp.valueobjects._

/**
 * This spec is used to test all [[dsp.user.repo.ProjectRepo]] implementations.
 */
object ProjectRepoImplSpec extends ZIOSpecDefault {

  private def getValidated[NonEmptyChunk[E], A](validation: Validation[Throwable, A]): A =
    validation.fold(e => throw e.head, v => v)

  private val shortCode = getValidated(ShortCode.make("0000"))
  private val id        = getValidated(ProjectId.make(shortCode))
  private val name      = getValidated(Name.make("projectName"))
  private val description = getValidated(
    ProjectDescription.make(Seq(V2.StringLiteralV2("project description", Some("en"))))
  )
  private val project = getValidated(Project.make(id, name, description))

  def spec = suite("ProjectRepoImplSpec")(
    projectRepoMockTest,
    projectRepoLiveTest
  )

  val getProjectTest = suite("retrieve a single project")(
    test("store a project and retrieve it by ID") {
      for {
        _             <- ProjectRepo.storeProject(project)
        storedProject <- ProjectRepo.getProjectById(id)
      } yield (
        assertTrue(project == storedProject)
      )
    },
    test("store a project and retrieve it by shortCode") {
      for {
        _             <- ProjectRepo.storeProject(project)
        storedProject <- ProjectRepo.getProjectByShortCode(shortCode)
      } yield (
        assertTrue(project == storedProject)
      )
    },
    test("not retrieve a project by ID that is not in the repo") {
      for {
        _             <- ProjectRepo.storeProject(project)
        shortCode2    <- ShortCode.make("0001").toZIO
        id2           <- ProjectId.make(shortCode2).toZIO
        storedProject <- ProjectRepo.getProjectById(id2).exit
      } yield assert(storedProject)(fails(equalTo(None)))
    },
    test("not retrieve a project by shortcode that is not in the repo") {
      for {
        _             <- ProjectRepo.storeProject(project)
        shortCode2    <- ShortCode.make("0001").toZIO
        storedProject <- ProjectRepo.getProjectByShortCode(shortCode2).exit
      } yield assert(storedProject)(fails(equalTo(None)))
    }
  )

  val getProjectsTest = suiteAll("retrieve all projects") {
    val project2 = (for {
      shortCode   <- ShortCode.make("0001")
      id          <- ProjectId.make(shortCode)
      name        <- Name.make("projectName")
      description <- ProjectDescription.make(Seq(V2.StringLiteralV2("project description", Some("en"))))
      project     <- Project.make(id, name, description)
    } yield project).toZIO.orDie

    test("get no project from an empty repository") {
      for {
        res <- ProjectRepo.getProjects()
      } yield (
        assert(res)(isEmpty)
      )
    }

    test("get one project from a repository with one project") {
      for {
        _   <- ProjectRepo.storeProject(project)
        res <- ProjectRepo.getProjects()
      } yield (
        assertTrue(res.size == 1) &&
          assert(res)(hasAt(0)(equalTo(project)))
      )
    }

    test("get multiple projects from a repository with multiple project") {
      for {
        _          <- ProjectRepo.storeProject(project)
        project2   <- project2
        _          <- ProjectRepo.storeProject(project2)
        res        <- ProjectRepo.getProjects()
        resSet      = res.toSet
        expectedSet = Set(project2, project)
      } yield (
        assertTrue(res.size == 2) &&
          assertTrue(resSet == expectedSet)
      )
    }
  }

  val storeProjectTest = test("store a project") {
    for {
      storedId <- ProjectRepo.storeProject(project)
    } yield (
      assertTrue(id == storedId)
    )
  }

  val checkShortCodeExists = suite("check if shortCode already exists")(
    test("not return that a shortCode exists if it does not exist") {
      for {
        res <- ProjectRepo.checkShortCodeExists(shortCode)
      } yield (
        assert(res)(isUnit)
      )
    },
    test("return that a shortCode exists if it does indeed exist") {
      for {
        _   <- ProjectRepo.storeProject(project)
        res <- ProjectRepo.checkShortCodeExists(shortCode).exit
      } yield (
        assert(res)(fails(equalTo(None)))
      )
    }
  )

  val deleteProject = suite("delete project")(
    test("do not delete a project that is not in the repository") {
      for {
        shortCode <- ShortCode.make("0000").toZIO
        id        <- ProjectId.make(shortCode).toZIO
        res       <- ProjectRepo.deleteProject(id).exit
      } yield (
        assert(res)(fails(equalTo(None)))
      )
    },
    test("delete a project if it exists in the repository") {
      for {
        _   <- ProjectRepo.storeProject(project)
        res <- ProjectRepo.deleteProject(id)
      } yield (
        assertTrue(res == id)
      )
    }
  )

  val projectTests = suite("ProjectRepo")(
    storeProjectTest,
    getProjectTest,
    getProjectsTest,
    checkShortCodeExists,
    deleteProject
  )

  val projectRepoMockTest = suite("ProjectRepo - Mock")(
    projectTests
  ).provide(ProjectRepoMock.layer)

  val projectRepoLiveTest = suite("ProjectRepo - Live")(
    projectTests
  ).provide(ProjectRepoLive.layer)

}
