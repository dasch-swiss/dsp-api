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

/**
 * This spec is used to test [[dsp.project.domain.ProjectDomain]].
 */
object ProjectDomainSpec extends ZIOSpecDefault {

  private val shortcode = Shortcode.make("0001").fold(e => throw e.head, v => v)
  private val uuid      = UUID.randomUUID()
  private val iri = Iri.ProjectIri
    .make(s"http://rdfh.ch/projects/${UUID.randomUUID()}")
    .fold(e => throw e.head, v => v)

  override def spec = suite("ProjectDomainSpec")(projectIdTests, projectTests)

  val projectIdTests = suite("ProjectId")(
    test("should create an ID from only a shortcode") {
      val projectId = ProjectId.make(shortcode)
      assertTrue(projectId.shortcode == shortcode) &&
      assertTrue(!projectId.iri.value.isEmpty()) &&
      assertTrue(!projectId.uuid.toString().isEmpty())
    },
    test("should create an ID from a shortcode and a UUID") {
      val projectId = ProjectId.fromUuid(uuid, shortcode)
      assertTrue(projectId.shortcode == shortcode) &&
      assertTrue(!projectId.iri.value.isEmpty()) &&
      assertTrue(projectId.uuid == uuid)
    },
    test("should create an ID from a shortcode and an IRI") {
      val projectId = ProjectId.fromIri(iri, shortcode)
      assertTrue(projectId.shortcode == shortcode) &&
      assertTrue(projectId.iri == iri) &&
      assertTrue(!projectId.uuid.toString().isEmpty())
    }
  )

  private val id          = ProjectId.make(shortcode)
  private val name        = "proj"
  private val description = "A Project"
  // TODO: these should be langString

  val projectTests = suite("Project")(
    test("should create a project") {
      val project = Project.make(id, name, description)
      assertTrue(project.id == id) &&
      assertTrue(project.name == name) &&
      assertTrue(project.description == description)
    }
  )

}
