/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

@RunWith(classOf[DspZTestJUnitRunner])
class StandoffMappingIriSpec extends ZIOSpecDefault {

  private val projectIri        = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val projectMappingIri = "http://rdfh.ch/projects/0001/mappings/MyMapping"
  private val standardMapping   = "http://rdfh.ch/standoff/mappings/StandardMapping"
  private val teiMapping        = "http://rdfh.ch/standoff/mappings/TEIMapping"

  val spec = suite("StandoffMappingIri")(
    suite("from(String)")(
      test("should return a StandoffMappingIri for a project-scoped IRI") {
        val actual = StandoffMappingIri.from(projectMappingIri)
        assertTrue(
          actual.isRight,
          actual.toOption.get.value == projectMappingIri,
          actual.toOption.get.projectIri.contains(projectIri),
          actual.toOption.get.mappingName == "MyMapping",
          !actual.toOption.get.isBuiltIn,
        )
      },
      test("should return a StandoffMappingIri for the built-in StandardMapping") {
        val actual = StandoffMappingIri.from(standardMapping)
        assertTrue(
          actual.isRight,
          actual.toOption.get.value == standardMapping,
          actual.toOption.get.projectIri.isEmpty,
          actual.toOption.get.mappingName == "StandardMapping",
          actual.toOption.get.isBuiltIn,
        )
      },
      test("should return a StandoffMappingIri for the built-in TEIMapping") {
        val actual = StandoffMappingIri.from(teiMapping)
        assertTrue(
          actual.isRight,
          actual.toOption.get.mappingName == "TEIMapping",
          actual.toOption.get.isBuiltIn,
        )
      },
      test("should fail for an invalid mapping IRI") {
        val invalidIris = Seq(
          "",
          "not-an-iri",
          "http://example.com/ontology#Foo",
          "http://rdfh.ch/0001/thing-with-history/values/xZisRC3jPkcplt1hQQdb-A",
          "http://rdfh.ch/standoff/mappings/",
          "http://rdfh.ch/standoff/mappings/Has Space",
          "http://rdfh.ch/projects/0001/mappings/",
          "http://rdfh.ch/projects/0001/mappings/Bad Name",
          "http://rdfh.ch/projects/abc/mappings/MyMapping",
        )
        check(Gen.fromIterable(invalidIris)) { iri =>
          val actual = StandoffMappingIri.from(iri)
          assertTrue(actual == Left(s"<$iri> is not a standoff mapping IRI"))
        }
      },
    ),
    suite("from(ProjectIri, String)")(
      test("should assemble a project-scoped mapping IRI") {
        val actual = StandoffMappingIri.from(projectIri, "MyMapping")
        assertTrue(
          actual.isRight,
          actual.toOption.get.value == projectMappingIri,
          actual.toOption.get.projectIri.contains(projectIri),
          actual.toOption.get.mappingName == "MyMapping",
        )
      },
      test("should fail for an invalid mapping name") {
        val invalidNames = Seq("", "Has Space", "with/slash", "with#hash")
        check(Gen.fromIterable(invalidNames)) { name =>
          val actual = StandoffMappingIri.from(projectIri, name)
          assertTrue(actual == Left(s"<$name> is not a valid mapping name"))
        }
      },
    ),
  )
}
