/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.junit.runner.RunWith
import zio.test.*

import java.time.Instant

import dsp.errors.InconsistentRepositoryDataException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.resources.domain.ResourceInfo

@RunWith(classOf[DspZTestJUnitRunner])
class ResourceInfoRepoLiveSpec extends ZIOSpecDefault {

  private val resource = "http://rdfh.ch/0001/aaa"
  private val thing    = InternalIri("http://www.knora.org/ontology/0001/anything#Thing")
  private val created  = Instant.parse("2026-01-01T00:00:00Z")

  private val validRow = Map(
    "resource"      -> resource,
    "resourceClass" -> thing.value,
    "creationDate"  -> created.toString,
    "isDeleted"     -> "false",
  )

  private def map(row: Map[String, String], requestedClass: Option[InternalIri] = None) =
    ResourceInfoRepoLive.toResourceInfo(requestedClass)(VariableResultsRow(row))

  private def invalid(reason: String) = Left(InconsistentRepositoryDataException(s"Invalid resource info for $reason"))

  override def spec: Spec[TestEnvironment, Any] = suite("ResourceInfoRepoLive.toResourceInfo")(
    test("maps a valid row") {
      assertTrue(map(validRow) == Right(ResourceInfo(ResourceIri.unsafeFrom(resource), thing, created, None)))
    },
    test("takes the requested class when the row binds none") {
      assertTrue(map(validRow - "resourceClass", Some(thing)).map(_.resourceClass) == Right(thing))
    },
    test("fails with InconsistentRepositoryDataException on a malformed date") {
      assertTrue(
        map(validRow + ("lastModificationDate" -> "yesterday")) ==
          invalid(s"$resource: 'lastModificationDate' is not a timestamp: yesterday"),
      )
    },
    test("fails with InconsistentRepositoryDataException when no class is bound or requested") {
      assertTrue(map(validRow - "resourceClass") == invalid(s"$resource: no class bound"))
    },
    test("fails with InconsistentRepositoryDataException when a required variable is missing") {
      assertTrue(map(validRow - "isDeleted") == invalid(s"$resource: 'isDeleted' is not bound"))
    },
  )
}
