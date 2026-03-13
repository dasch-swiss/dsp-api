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

object AddPropertyMappingQuerySpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val ontologyIri  = OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
  private val propertyIri  = ontologyIri.makeProperty("hasName").smartIri
  private val externalIri1 = "http://purl.org/dc/terms/title".toSmartIri
  private val externalIri2 = "http://schema.org/name".toSmartIri

  override def spec: Spec[TestEnvironment, Any] = suite("AddPropertyMappingQuerySpec")(
    test("query uses rdfs:subPropertyOf (not subClassOf)") {
      for {
        update <- AddPropertyMappingQuery.build(ontologyIri, propertyIri, List(externalIri1))
      } yield assertTrue(
        update.sparql.contains("rdfs:subPropertyOf"),
        !update.sparql.contains("rdfs:subClassOf"),
      )
    },
    test("query contains the external IRI in the INSERT clause") {
      for {
        instant <- Clock.instant
        update  <- AddPropertyMappingQuery.build(ontologyIri, propertyIri, List(externalIri1))
      } yield assertTrue(
        update.sparql.contains("http://purl.org/dc/terms/title"),
        update.sparql.contains(instant.toString),
        update.sparql.contains("knora-base:lastModificationDate"),
        update.sparql.indexOf("lastModificationDate") != update.sparql.lastIndexOf("lastModificationDate"),
      )
    },
    test("query contains all external IRIs when multiple mappings are given") {
      for {
        update <- AddPropertyMappingQuery.build(ontologyIri, propertyIri, List(externalIri1, externalIri2))
      } yield assertTrue(
        update.sparql.contains("http://purl.org/dc/terms/title"),
        update.sparql.contains("http://schema.org/name"),
      )
    },
    test("query uses OPTIONAL for the WHERE clause") {
      for {
        update <- AddPropertyMappingQuery.build(ontologyIri, propertyIri, List(externalIri1))
      } yield assertTrue(update.sparql.contains("OPTIONAL"))
    },
    test("query does not decode percent-encoded injection characters in IRI positions") {
      // %3E = '>' — valid percent-encoding in IRIs; RDF4J must not decode it
      val encodedIri = "http://example.org/prop%3Einjection".toSmartIri
      for {
        update <- AddPropertyMappingQuery.build(ontologyIri, propertyIri, List(encodedIri))
      } yield assertTrue(
        !update.sparql.contains(">injection"),
      )
    },
    test("query targets the correct ontology graph") {
      for {
        update <- AddPropertyMappingQuery.build(ontologyIri, propertyIri, List(externalIri1))
      } yield assertTrue(
        update.sparql.contains("http://www.knora.org/ontology/0001/anything"),
      )
    },
  )
}
