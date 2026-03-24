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

object RemoveMappingQuerySpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val ontologyIri = OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
  private val classIri    = ontologyIri.makeClass("Thing").smartIri
  private val propertyIri = ontologyIri.makeProperty("hasName").smartIri
  private val externalIri = "http://schema.org/Thing".toSmartIri
  private val propExtIri  = "http://purl.org/dc/terms/title".toSmartIri

  override def spec: Spec[TestEnvironment, Any] = suite("RemoveMappingQuerySpec")(
    // -- SUBCLASSOF (class mappings) -------------------------------------------
    suite("rdfs:subClassOf predicate")(
      test("query contains the subClassOf triple in the DELETE clause") {
        for {
          update <- RemoveMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, externalIri)
        } yield assertTrue(
          update.sparql.contains("rdfs:subClassOf"),
          update.sparql.contains("http://schema.org/Thing"),
        )
      },
      test("query rotates lastModificationDate (present in DELETE and INSERT)") {
        val knownInstant = java.time.Instant.parse("2026-01-01T00:00:00Z")
        for {
          _      <- TestClock.setTime(knownInstant)
          update <- RemoveMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, externalIri)
        } yield assertTrue(
          update.sparql.contains("knora-base:lastModificationDate"),
          update.sparql.contains(knownInstant.toString),
          // appears in both DELETE and INSERT
          update.sparql.indexOf("lastModificationDate") != update.sparql.lastIndexOf("lastModificationDate"),
        )
      },
      test("removed subClassOf triple does NOT appear in INSERT clause") {
        for {
          update <- RemoveMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, externalIri)
        } yield {
          val insertSection = update.sparql.substring(update.sparql.indexOf("INSERT"))
          assertTrue(!insertSection.contains("rdfs:subClassOf"))
        }
      },
      test("removed triple is fully specified in DELETE (no variables for the subClassOf triple)") {
        for {
          update <- RemoveMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, externalIri)
        } yield {
          val sparql        = update.sparql
          val deleteSection = sparql.substring(sparql.indexOf("DELETE"), sparql.indexOf("INSERT"))
          assertTrue(
            deleteSection.contains("rdfs:subClassOf"),
            deleteSection.contains("http://schema.org/Thing"),
            !deleteSection.contains("?externalIri"),
          )
        }
      },
    ),
    // -- SUBPROPERTYOF (property mappings) ------------------------------------
    suite("rdfs:subPropertyOf predicate")(
      test("query uses rdfs:subPropertyOf (not subClassOf)") {
        for {
          update <- RemoveMappingQuery.build(ontologyIri, propertyIri, MappingPredicate.SubPropertyOf, propExtIri)
        } yield assertTrue(
          update.sparql.contains("rdfs:subPropertyOf"),
          !update.sparql.contains("rdfs:subClassOf"),
        )
      },
      test("removed subPropertyOf triple does NOT appear in INSERT clause") {
        for {
          update <- RemoveMappingQuery.build(ontologyIri, propertyIri, MappingPredicate.SubPropertyOf, propExtIri)
        } yield {
          val insertSection = update.sparql.substring(update.sparql.indexOf("INSERT"))
          assertTrue(!insertSection.contains("rdfs:subPropertyOf"))
        }
      },
      test("removed triple is fully specified in DELETE (no variables for the subPropertyOf triple)") {
        for {
          update <- RemoveMappingQuery.build(ontologyIri, propertyIri, MappingPredicate.SubPropertyOf, propExtIri)
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
    ),
    // -- Common structural checks ----------------------------------------------
    suite("common structural checks")(
      test("query uses OPTIONAL in WHERE clause (idempotent on missing lastModificationDate)") {
        for {
          update <- RemoveMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, externalIri)
        } yield assertTrue(update.sparql.contains("OPTIONAL"))
      },
      test("query targets the correct ontology graph") {
        for {
          update <- RemoveMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, externalIri)
        } yield assertTrue(update.sparql.contains("http://www.knora.org/ontology/0001/anything"))
      },
    ),
    suite("percent-encoded injection chars produce valid SPARQL output")(
      test("percent-encoded '|' (%7C) in mapping IRI succeeds (SmartIri encodes '|' to '%7C')") {
        val encodedIri = "http://example.org/Thing%7Cinjection".toSmartIri
        for {
          exit <- RemoveMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, encodedIri).exit
        } yield assertTrue(exit.isSuccess)
      },
      test("percent-encoded '}' (%7D) in mapping IRI succeeds (SmartIri encodes '}' to '%7D')") {
        val encodedIri = "http://example.org/Thing%7Dinjection".toSmartIri
        for {
          exit <- RemoveMappingQuery.build(ontologyIri, classIri, MappingPredicate.SubClassOf, encodedIri).exit
        } yield assertTrue(exit.isSuccess)
      },
    ),
  )
}
