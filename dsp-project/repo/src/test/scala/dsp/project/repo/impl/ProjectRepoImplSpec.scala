/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.repo.impl

import dsp.project.api.ProjectRepo
import dsp.project.domain.Project
import dsp.valueobjects
import dsp.valueobjects.Project._
import dsp.valueobjects.ProjectId
import zio._
import zio.prelude.Validation
import zio.test.Assertion._
import zio.test._

/**
 * This spec is used to test all [[dsp.user.repo.ProjectRepo]] implementations.
 */
object ProjectRepoImplSpec extends ZIOSpecDefault {

  def spec = (projectRepoMockTest + projectRepoLiveTest)

  val getProjectTest = suite("retrieve a single project")(
    test("store a project and retrieve it by ID") {
      for {
        shortCode     <- ShortCode.make("0000").toZIO
        id            <- ProjectId.make(shortCode).toZIO
        project       <- Project.make(id, "projectName", "project description").toZIO
        _             <- ProjectRepo.storeProject(project)
        storedProject <- ProjectRepo.getProjectById(id)
      } yield (
        assertTrue(project == storedProject)
      )
    },
    test("store a project and retrieve it by shortCode") {
      for {
        shortCode     <- ShortCode.make("0000").toZIO
        id            <- ProjectId.make(shortCode).toZIO
        project       <- Project.make(id, "projectName", "project description").toZIO
        _             <- ProjectRepo.storeProject(project)
        storedProject <- ProjectRepo.getProjectByShortCode(shortCode.value)
      } yield (
        assertTrue(project == storedProject)
      )
    },
    test("not retrieve a project by ID that is not in the repo") {
      for {
        shortCode1    <- ShortCode.make("0000").toZIO
        id1           <- ProjectId.make(shortCode1).toZIO
        project1      <- Project.make(id1, "projectName", "project description").toZIO
        _             <- ProjectRepo.storeProject(project1)
        shortCode2    <- ShortCode.make("0001").toZIO
        id2           <- ProjectId.make(shortCode2).toZIO
        storedProject <- ProjectRepo.getProjectById(id2).exit
      } yield assert(storedProject)(fails(equalTo(None)))
    },
    test("not retrieve a project by shortcode that is not in the repo") {
      for {
        shortCode1    <- ShortCode.make("0000").toZIO
        id1           <- ProjectId.make(shortCode1).toZIO
        project1      <- Project.make(id1, "projectName", "project description").toZIO
        _             <- ProjectRepo.storeProject(project1)
        shortCode2    <- ShortCode.make("0001").toZIO
        storedProject <- ProjectRepo.getProjectByShortCode(shortCode2.value).exit
      } yield assert(storedProject)(fails(equalTo(None)))
    }
  )

  val getAllProjectsTest = suiteAll("retrieve all projects") {
    val project1 = (for {
      shortCode <- ShortCode.make("0000")
      id        <- ProjectId.make(shortCode)
      project   <- Project.make(id, "project1", "project description")
    } yield project).toZIO.orDie
    val project2 = (for {
      shortCode <- ShortCode.make("0001")
      id        <- ProjectId.make(shortCode)
      project   <- Project.make(id, "project2", "project description")
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
        project1 <- project1
        _        <- ProjectRepo.storeProject(project1)
        res      <- ProjectRepo.getProjects()
      } yield (
        assertTrue(res.size == 1) &&
          assert(res)(hasAt(0)(equalTo(project1)))
      )
    }

    test("get multiple projects from a repository with multiple project") {
      for {
        project1   <- project1
        _          <- ProjectRepo.storeProject(project1)
        project2   <- project2
        _          <- ProjectRepo.storeProject(project2)
        res        <- ProjectRepo.getProjects()
        resSet      = res.toSet
        expectedSet = Set(project2, project1)
      } yield (
        assertTrue(res.size == 2) &&
          assertTrue(resSet == expectedSet)
      )
    }
  }

  val storeProjectTest = test("store a project") {
    for {
      shortCode <- ShortCode.make("0000").toZIO
      id        <- ProjectId.make(shortCode).toZIO
      project   <- Project.make(id, "projectName", "project description").toZIO
      storedId  <- ProjectRepo.storeProject(project)
    } yield (
      assertTrue(id == storedId)
    )
  }

  val checkShortCodeExists = suite("check if shortCode already exists")(
    test("not return that a shortCode exists if it does not exist") {
      val shortCodeString = "0000"
      for {
        res <- ProjectRepo.checkShortCodeExists(shortCodeString)
      } yield (
        assert(res)(isUnit)
      )
    },
    test("return that a shortCode exists if it does indeed exist") {
      val shortCodeString = "0000"
      for {
        shortCode <- ShortCode.make(shortCodeString).toZIO
        id        <- ProjectId.make(shortCode).toZIO
        project   <- Project.make(id, "projectName", "project description").toZIO
        storedId  <- ProjectRepo.storeProject(project)
        res       <- ProjectRepo.checkShortCodeExists(shortCodeString).exit
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
        shortCode <- ShortCode.make("0000").toZIO
        id        <- ProjectId.make(shortCode).toZIO
        project   <- Project.make(id, "projectName", "project description").toZIO
        storedId  <- ProjectRepo.storeProject(project)
        res       <- ProjectRepo.deleteProject(id)
      } yield (
        assertTrue(res == id)
      )
    }
  )

  val projectTests = suite("ProjectRepo")(
    storeProjectTest,
    getProjectTest,
    getAllProjectsTest,
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
