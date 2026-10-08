/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.apache.jena.rdf.model.ModelFactory
import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.io.StringReader
import java.time.Instant
import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.store.triplestore.api.TestTripleStore
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

/**
 * Guards that, in the resource read queries, a standoff tag only receives the original XML id of the tag it refers to.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class StandoffInternalReferenceQuerySpec extends ZIOSpecDefault {

  private val kb              = "http://www.knora.org/ontology/knora-base#"
  private val targetXmlIdProp = s"${kb}targetHasOriginalXMLID"
  private val startIndexProp  = s"${kb}standoffTagHasStartIndex"
  private val tagPrefix       = "http://rdfh.ch/0001/tag-"
  private val resource1       = "http://rdfh.ch/0001/resource1"

  private def tag(id: String, xmlId: Option[String], refersTo: Option[String]): String =
    s"""<$tagPrefix$id> a ex:Tag ;
       |  knora-base:standoffTagHasStartIndex 0 ;
       |  knora-base:standoffTagHasEndIndex 1
       |  ${xmlId.fold("")(x => s""" ; knora-base:standoffTagHasOriginalXMLID "$x" """)}
       |  ${refersTo.fold("")(r => s" ; knora-base:standoffTagHasInternalReference <$tagPrefix$r> ")} .
       |""".stripMargin

  private def textValue(id: String, tagIds: List[String]): String =
    s"""<http://rdfh.ch/0001/value-$id> a knora-base:TextValue ;
       |  knora-base:hasPermissions "CR knora-admin:Creator" ;
       |  knora-base:valueHasUUID "uuid-$id" ;
       |  knora-base:valueCreationDate "2020-01-01T00:00:00Z"^^xsd:dateTime ;
       |  knora-base:valueHasString "text" ;
       |  knora-base:valueHasStandoff ${tagIds.map(t => s"<$tagPrefix$t>").mkString(", ")} .
       |""".stripMargin

  private def resource(id: String, valueIds: List[String]): String =
    s"""<http://rdfh.ch/0001/$id> a ex:Thing ;
       |  knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
       |  knora-base:attachedToUser <http://rdfh.ch/users/user> ;
       |  knora-base:hasPermissions "CR knora-admin:Creator" ;
       |  knora-base:creationDate "2020-01-01T00:00:00Z"^^xsd:dateTime ;
       |  rdfs:label "$id" ;
       |  knora-base:isDeleted false ;
       |  ex:hasText ${valueIds.map(v => s"<http://rdfh.ch/0001/value-$v>").mkString(", ")} .
       |""".stripMargin

  private val fixture =
    s"""@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
       |@prefix knora-base: <$kb> .
       |@prefix ex: <http://example.org/ontology#> .
       |
       |<http://example.org/graph> {
       |  ex:Thing rdfs:subClassOf knora-base:Resource .
       |  ex:hasText rdfs:subPropertyOf knora-base:hasValue .
       |  ${resource("resource1", List("A", "B"))}
       |  ${resource("resource2", List("C"))}
       |  ${textValue("A", List("A1", "A2", "A3"))}
       |  ${textValue("B", List("B1", "B2"))}
       |  ${textValue("C", List("C1", "C2"))}
       |  ${tag("A1", Some("link_id"), None)}
       |  ${tag("A2", None, Some("A1"))}
       |  ${tag("A3", None, None)}
       |  ${tag("B1", Some("_ref-note1"), Some("B2"))}
       |  ${tag("B2", Some("_note1"), Some("B1"))}
       |  ${tag("C1", Some("other_target"), None)} # decoys: an unbound reference lookup leaks these into resource1
       |  ${tag("C2", None, Some("C1"))}
       |}
       |""".stripMargin

  /** Maps every standoff tag the query returns to the `targetHasOriginalXMLID` values it carries. */
  private def targetXmlIdsByReturnedTag(
    construct: Construct,
  ): ZIO[TestTripleStore, Throwable, Map[String, Set[String]]] =
    for {
      _      <- TestTripleStore.setDatasetFromTriG(fixture)
      turtle <- ZIO.serviceWithZIO[TestTripleStore](_.queryRdf(construct))
      model   = ModelFactory.createDefaultModel().read(new StringReader(turtle), null, "TURTLE")
      prop    = model.createProperty(targetXmlIdProp)
    } yield model
      .listSubjectsWithProperty(model.createProperty(startIndexProp))
      .asScala
      .map(tag =>
        tag.getURI
          .stripPrefix(tagPrefix) -> model.listObjectsOfProperty(tag, prop).asScala.map(_.asLiteral().getString).toSet,
      )
      .toMap

  private val expectedTargetXmlIds = Map(
    "A1" -> Set.empty[String],
    "A2" -> Set("link_id"),
    "A3" -> Set.empty[String],
    "B1" -> Set("_note1"),
    "B2" -> Set("_ref-note1"),
  )

  private def assertOwnTargetsOnly(construct: Construct) =
    targetXmlIdsByReturnedTag(construct).map(actual => assertTrue(actual == expectedTargetXmlIds))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("StandoffInternalReferenceQuerySpec")(
      test("current values: each referring tag carries only its own target's XML id") {
        assertOwnTargetsOnly(
          GetResourcePropertiesAndValuesQuery
            .build(Seq(resource1), preview = false, withDeleted = false, queryStandoff = true),
        )
      },
      test("versioned values: each referring tag carries only its own target's XML id") {
        assertOwnTargetsOnly(
          GetResourcePropertiesAndValuesQuery.build(
            Seq(resource1),
            preview = false,
            withDeleted = false,
            queryStandoff = true,
            maybeVersionDate = Some(Instant.parse("2030-01-01T00:00:00Z")),
          ),
        )
      },
      test("search results: each referring tag carries only its own target's XML id") {
        assertOwnTargetsOnly(SearchResultResourcesQuery.build(Seq(resource1), queryStandoff = true))
      },
    ).provide(StringFormatter.test, TriplestoreServiceInMemory.emptyDatasetRefLayer, TriplestoreServiceInMemory.layer)
}
