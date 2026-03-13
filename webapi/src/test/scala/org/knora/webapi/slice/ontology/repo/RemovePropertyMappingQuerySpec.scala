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

object RemovePropertyMappingQuerySpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val ontologyIri = OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
  private val propertyIri = ontologyIri.makeProperty("hasName").smartIri
  private val externalIri = "http://purl.org/dc/terms/title".toSmartIri

  override def spec: Spec[TestEnvironment, Any] = suite("RemovePropertyMappingQuerySpec")(
    test("query uses rdfs:subPropertyOf (not subClassOf)") {
      for {
        update <- RemovePropertyMappingQuery.build(ontologyIri, propertyIri, externalIri)
      } yield assertTrue(
        update.sparql.contains("rdfs:subPropertyOf"),
        !update.sparql.contains("rdfs:subClassOf"),
      )
    },
    test("query rotates lastModificationDate (present in DELETE and INSERT)") {
      for {
        update  <- RemovePropertyMappingQuery.build(ontologyIri, propertyIri, externalIri)
        instant <- Clock.instant
      } yield assertTrue(
        update.sparql.contains("knora-base:lastModificationDate"),
        update.sparql.contains(instant.toString),
        update.sparql.indexOf("lastModificationDate") != update.sparql.lastIndexOf("lastModificationDate"),
      )
    },
    test("query uses OPTIONAL in WHERE clause") {
      for {
        update <- RemovePropertyMappingQuery.build(ontologyIri, propertyIri, externalIri)
      } yield assertTrue(update.sparql.contains("OPTIONAL"))
    },
    test("removed triple is fully specified in DELETE (no variables for the subPropertyOf triple)") {
      for {
        update <- RemovePropertyMappingQuery.build(ontologyIri, propertyIri, externalIri)
      } yield {
        val sparql        = update.sparql
        val deleteSection = sparql.substring(sparql.indexOf("DELETE"), sparql.indexOf("INSERT"))
        assertTrue(
          deleteSection.contains("rdfs:subPropertyOf"),
          deleteSection.contains("http://purl.org/dc/terms/title"),
          !deleteSection.contains("?externalIri"),
        )
      }
    },
    test("removed triple does NOT appear in INSERT clause") {
      for {
        update <- RemovePropertyMappingQuery.build(ontologyIri, propertyIri, externalIri)
      } yield {
        val sparql        = update.sparql
        val insertSection = sparql.substring(sparql.indexOf("INSERT"))
        assertTrue(!insertSection.contains("rdfs:subPropertyOf"))
      }
    },
    test("query targets the correct ontology graph") {
      for {
        update <- RemovePropertyMappingQuery.build(ontologyIri, propertyIri, externalIri)
      } yield assertTrue(
        update.sparql.contains("http://www.knora.org/ontology/0001/anything"),
      )
    },
  )
}
