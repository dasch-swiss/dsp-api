/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.domain

import dsp.valueobjects.Project._
import zio._
import zio.test._

import java.util.UUID
import dsp.valueobjects.Iri
import dsp.valueobjects.ProjectId

/**
 * This spec is used to test [[dsp.project.domain.ProjectDomain]].
 */
object ProjectDomainSpec extends ZIOSpecDefault {

  private val shortcode = ShortCode.make("0001").fold(e => throw e.head, v => v)
  private val uuid      = UUID.randomUUID()
  private val iri = Iri.ProjectIri
    .make(s"http://rdfh.ch/projects/${UUID.randomUUID()}")
    .fold(e => throw e.head, v => v)
  private val id          = ProjectId.make(shortcode).fold(e => throw e.head, v => v)
  private val name        = "proj"
  private val description = "A Project"
  // TODO-BL: these should be langString/valueobjects

  override def spec = suite("Project")(projectCreateTests + projectCompareTests + projectUpdateTests)

  val projectCreateTests = suite("create project")(
    test("create a project from valid input") {
      (for {
        project <- Project.make(id, name, description)
      } yield (
        assertTrue(project.id == id) &&
          assertTrue(project.name == name) &&
          assertTrue(project.description == description)
      )).toZIO
    }
  )

  val projectCompareTests = suite("compare projects")(
    test("compare projects by IRI") {
      val uuidList   = List(UUID.randomUUID(), UUID.randomUUID()).sorted
      val iri1String = s"http://rdfh.ch/projects/${uuidList.head}"
      val iri2String = s"http://rdfh.ch/projects/${uuidList.reverse.head}"
      (for {
        iri1             <- Iri.ProjectIri.make(iri1String)
        iri2             <- Iri.ProjectIri.make(iri2String)
        id1              <- ProjectId.fromIri(iri1, shortcode)
        id2              <- ProjectId.fromIri(iri2, shortcode)
        project1         <- Project.make(id1, name, description)
        project2         <- Project.make(id2, name, description)
        listInitial       = List(project1, project2)
        listSorted        = listInitial.sorted
        listSortedInverse = listInitial.sortWith(_ > _)
      } yield (
        assertTrue(listInitial == listSorted) &&
          assertTrue(listInitial != listSortedInverse) &&
          assertTrue(listInitial == listSortedInverse.reverse)
      )).toZIO
    }
  )

  val projectUpdateTests = suite("update project")(
    test("update a project name when provided a valid new name") {
      val newName = "new project name"
      (for {
        project        <- Project.make(id, name, description)
        updatedProject <- project.updateProjectName(newName)
      } yield (
        assertTrue(project.id == updatedProject.id) &&
          assertTrue(project.name != updatedProject.name) &&
          assertTrue(project.description == updatedProject.description) &&
          assertTrue(project.name == name) &&
          assertTrue(updatedProject.name == newName)
      )).toZIO
    },
    test("update a project description when provided a valid new description") {
      val newDescription = "new project name"
      (for {
        project        <- Project.make(id, name, description)
        updatedProject <- project.updateProjectDescription(newDescription)
      } yield (
        assertTrue(project.id == updatedProject.id) &&
          assertTrue(project.name == updatedProject.name) &&
          assertTrue(project.description != updatedProject.description) &&
          assertTrue(project.description == description) &&
          assertTrue(updatedProject.description == newDescription)
      )).toZIO
    }
  )
  // TODO-BL: add tests for unhappy path, as soon as there are invalid values for these things

}
