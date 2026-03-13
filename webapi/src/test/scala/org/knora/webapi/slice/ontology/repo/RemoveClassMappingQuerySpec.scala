/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.*
import zio.test.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.OntologyIri

object RemoveClassMappingQuerySpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val ontologyIri = OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
  private val classIri    = ontologyIri.makeClass("Thing").smartIri
  private val externalIri = "http://schema.org/Thing".toSmartIri

  override def spec: Spec[TestEnvironment, Any] = suite("RemoveClassMappingQuerySpec")(
    test("query contains the subClassOf triple in the DELETE clause") {
      for {
        update <- RemoveClassMappingQuery.build(ontologyIri, classIri, externalIri)
      } yield assertTrue(
        update.sparql.contains("rdfs:subClassOf"),
        update.sparql.contains("http://schema.org/Thing"),
      )
    },
    test("query rotates lastModificationDate (present in DELETE and INSERT)") {
      val knownInstant = java.time.Instant.parse("2026-01-01T00:00:00Z")
      for {
        _      <- TestClock.setTime(knownInstant)
        update <- RemoveClassMappingQuery.build(ontologyIri, classIri, externalIri)
      } yield assertTrue(
        update.sparql.contains("knora-base:lastModificationDate"),
        update.sparql.contains(knownInstant.toString),
        // appears in both DELETE and INSERT
        update.sparql.indexOf("lastModificationDate") != update.sparql.lastIndexOf("lastModificationDate"),
      )
    },
    test("query uses OPTIONAL in WHERE clause (idempotent on missing lastModificationDate)") {
      for {
        update <- RemoveClassMappingQuery.build(ontologyIri, classIri, externalIri)
      } yield assertTrue(update.sparql.contains("OPTIONAL"))
    },
    test("removed triple is fully specified in DELETE (no variables for the subClassOf triple)") {
      for {
        update <- RemoveClassMappingQuery.build(ontologyIri, classIri, externalIri)
      } yield {
        val sparql = update.sparql
        // The subClassOf triple in DELETE must be fully specified — no ?-variables on it
        val deleteSection = sparql.substring(sparql.indexOf("DELETE"), sparql.indexOf("INSERT"))
        assertTrue(
          deleteSection.contains("rdfs:subClassOf"),
          deleteSection.contains("http://schema.org/Thing"),
          // No unbound variable for the removed triple
          !deleteSection.contains("?externalIri"),
        )
      }
    },
    test("removed triple does NOT appear in INSERT clause") {
      for {
        update <- RemoveClassMappingQuery.build(ontologyIri, classIri, externalIri)
      } yield {
        val sparql        = update.sparql
        val insertSection = sparql.substring(sparql.indexOf("INSERT"))
        assertTrue(!insertSection.contains("rdfs:subClassOf"))
      }
    },
    test("query targets the correct ontology graph") {
      for {
        update <- RemoveClassMappingQuery.build(ontologyIri, classIri, externalIri)
      } yield assertTrue(
        update.sparql.contains("http://www.knora.org/ontology/0001/anything"),
      )
    },
  )
}
