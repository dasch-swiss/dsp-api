/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.test.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.ontology.domain.model.OntologyMappingExternalIri

object AddMappingQuerySpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val ontologyIri                       = OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
  private val classIri                          = ontologyIri.makeClass("Thing").smartIri
  private val propertyIri                       = ontologyIri.makeProperty("hasName").smartIri
  private val externalIri1                      = OntologyMappingExternalIri.unsafeFrom("http://schema.org/Thing")
  private val externalIri2                      = OntologyMappingExternalIri.unsafeFrom("http://purl.org/dc/terms/Agent")
  override def spec: Spec[TestEnvironment, Any] = suite("AddMappingQuerySpec")(
    // -- SUBCLASSOF (class mappings) -------------------------------------------
    suite("rdfs:subClassOf predicate")(
      test("query contains rdfs:subClassOf in the INSERT clause") {
        val knownInstant = java.time.Instant.parse("2026-01-01T00:00:00Z")
        for {
          _      <- TestClock.setTime(knownInstant)
          update <- AddMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, List(externalIri1))
        } yield assertTrue(
          update.sparql.contains("rdfs:subClassOf"),
          !update.sparql.contains("rdfs:subPropertyOf"),
          update.sparql.contains(knownInstant.toString),
          update.sparql.contains("knora-base:lastModificationDate"),
          // lastModificationDate appears in both DELETE and INSERT
          update.sparql.indexOf("lastModificationDate") != update.sparql.lastIndexOf("lastModificationDate"),
        )
      },
      test("query contains all external IRIs when multiple class mappings are given") {
        for {
          update <-
            AddMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, List(externalIri1, externalIri2))
        } yield assertTrue(
          update.sparql.contains("http://schema.org/Thing"),
          update.sparql.contains("http://purl.org/dc/terms/Agent"),
        )
      },
    ),
    // -- SUBPROPERTYOF (property mappings) ------------------------------------
    suite("rdfs:subPropertyOf predicate")(
      test("query uses rdfs:subPropertyOf (not subClassOf)") {
        for {
          update <- AddMappingQuery.build(ontologyIri, propertyIri, MappingPredicate.SubPropertyOf, List(externalIri1))
        } yield assertTrue(
          update.sparql.contains("rdfs:subPropertyOf"),
          !update.sparql.contains("rdfs:subClassOf"),
        )
      },
      test("query contains all external IRIs when multiple property mappings are given") {
        val propIri2 = OntologyMappingExternalIri.unsafeFrom("http://schema.org/name")
        for {
          update <- AddMappingQuery
                      .build(ontologyIri, propertyIri, MappingPredicate.SubPropertyOf, List(externalIri1, propIri2))
        } yield assertTrue(
          update.sparql.contains("http://schema.org/Thing"),
          update.sparql.contains("http://schema.org/name"),
        )
      },
    ),
    // -- Common structural checks ----------------------------------------------
    suite("common structural checks")(
      test("query uses OPTIONAL for the WHERE clause (idempotency)") {
        for {
          update <- AddMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, List(externalIri1))
        } yield assertTrue(update.sparql.contains("OPTIONAL"))
      },
      test("query targets the correct ontology graph") {
        for {
          update <- AddMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, List(externalIri1))
        } yield assertTrue(update.sparql.contains("http://www.knora.org/ontology/0001/anything"))
      },
      test("query output contains no unescaped injection-dangerous characters in IRI positions") {
        for {
          update <- AddMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, List(externalIri1))
        } yield {
          val sparql = update.sparql
          assertTrue(
            !sparql.contains("'>"),
            !sparql.contains("'{"),
            !sparql.contains("'}"),
          )
        }
      },
      test("query does not decode percent-encoded injection characters in IRI positions") {
        val encodedIri = OntologyMappingExternalIri.unsafeFrom("http://example.org/Thing%3Einjection")
        for {
          update <- AddMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, List(encodedIri))
        } yield assertTrue(
          !update.sparql.contains(">injection"),
          !update.sparql.contains("{injection"),
        )
      },
    ),
    suite("percent-encoded injection chars produce valid SPARQL output")(
      test("percent-encoded '|' (%7C) in mapping IRI succeeds") {
        val encodedIri = OntologyMappingExternalIri.unsafeFrom("http://example.org/Thing%7Cinjection")
        for {
          exit <- AddMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, List(encodedIri)).exit
        } yield assertTrue(exit.isSuccess)
      },
      test("percent-encoded '}' (%7D) in mapping IRI succeeds") {
        val encodedIri = OntologyMappingExternalIri.unsafeFrom("http://example.org/Thing%7Dinjection")
        for {
          exit <- AddMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, List(encodedIri)).exit
        } yield assertTrue(exit.isSuccess)
      },
    ),
  )
}
