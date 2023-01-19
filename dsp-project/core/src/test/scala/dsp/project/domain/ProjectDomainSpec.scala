/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.domain

import zio.test._

import dsp.valueobjects.Iri
import dsp.valueobjects.Project._
import dsp.valueobjects.ProjectId
import dsp.valueobjects.V2

/**
 * This spec is used to test [[dsp.project.domain.ProjectDomain]].
 */
object ProjectDomainSpec extends ZIOSpecDefault {

  private val shortCode = ShortCode.make("0001").fold(e => throw e.head, v => v)
  private val id        = ProjectId.make(shortCode).fold(e => throw e.head, v => v)
  private val name      = Name.make("proj").fold(e => throw e.head, v => v)
  private val description = ProjectDescription
    .make(Seq(V2.StringLiteralV2("A Project", Some("en"))))
    .fold(e => throw e.head, v => v)

  override def spec =
    suite("ProjectDomainSpec")(
      projectCreateTests,
      projectCompareTests,
      projectUpdateTests
    )

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
      val iri1String = s"http://rdfh.ch/projects/d78c6cc8-0a18-4131-af72-4bb6cb688bed"
      val iri2String = s"http://rdfh.ch/projects/f4184d7a-caf7-4ab9-991e-d5da9eb7ec17"
      (for {
        iri1             <- Iri.ProjectIri.make(iri1String)
        iri2             <- Iri.ProjectIri.make(iri2String)
        id1              <- ProjectId.fromIri(iri1, shortCode)
        id2              <- ProjectId.fromIri(iri2, shortCode)
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
      (for {
        newName        <- Name.make("new project name")
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
      (for {
        newDescription <- ProjectDescription.make(Seq(V2.StringLiteralV2("new project name", Some("en"))))
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
}
