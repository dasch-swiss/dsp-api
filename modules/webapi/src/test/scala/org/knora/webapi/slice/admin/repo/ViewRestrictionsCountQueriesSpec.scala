/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.apache.jena.query.Dataset
import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsTestFixtures.anythingProject
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ResourceCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ValueCountRow
import org.knora.webapi.store.triplestore.TestDatasetBuilder.datasetLayerFromTriG
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

/**
 * Runs the count queries of [[ViewRestrictionsRepo]] against an in-memory triplestore.
 *
 * The project's data is in its data graph, and the ontology sits in a graph of its own, as in a deployed store. One
 * resource of the project lies in another named graph, which the value counts do not read. The property hierarchy
 * is in the data, so the value query classifies the properties as it does on a real store.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsCountQueriesSpec extends ZIOSpecDefault {

  private val projectIri    = anythingProject.id
  private val anythingGraph = "http://www.knora.org/data/0001/anything"
  private val otherGraph    = "http://www.knora.org/data/0001/other"
  private val ontologyGraph = "http://www.knora.org/ontology/0001/anything"
  private val anything      = "http://www.knora.org/ontology/0001/anything#"
  private val kb            = "http://www.knora.org/ontology/knora-base#"
  private val thingClass    = s"${anything}Thing"
  private val hasText       = s"${anything}hasText"
  private val hasThingValue = s"${anything}hasThingValue"
  private val hasPicture    = s"${anything}hasPicture"
  private val open          = "V knora-admin:UnknownUser"
  private val member        = "M knora-admin:ProjectMember"

  private def value(iri: String, permissions: String, deleted: Boolean = false, extra: String = ""): String =
    s"""  <$iri> kb:hasPermissions "$permissions" ; kb:isDeleted $deleted $extra .
       |""".stripMargin

  private def resource(iri: String, permissions: String, deleted: Boolean, properties: String): String =
    s"""  <$iri> a <$thingClass> ;
       |    kb:attachedToProject <${projectIri.value}> ;
       |    kb:hasPermissions "$permissions" ;
       |    kb:isDeleted $deleted $properties .
       |""".stripMargin

  private val trig: String =
    s"""
       |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:   <$kb> .
       |
       |<$ontologyGraph> {
       |  <$ontologyGraph> kb:attachedToProject <${projectIri.value}> .
       |  <$hasText> rdfs:subPropertyOf kb:hasValue .
       |  <$hasThingValue> rdfs:subPropertyOf kb:hasLinkToValue .
       |  <${anything}hasThing> rdfs:subPropertyOf kb:hasLinkTo .
       |  <$hasPicture> rdfs:subPropertyOf kb:hasStillImageFileValue .
       |  kb:hasStillImageFileValue rdfs:subPropertyOf kb:hasFileValue .
       |  kb:hasFileValue rdfs:subPropertyOf kb:hasValue .
       |  kb:hasLinkToValue rdfs:subPropertyOf kb:hasValue .
       |  kb:hasStandoffLinkToValue rdfs:subPropertyOf kb:hasLinkToValue .
       |}
       |
       |<$anythingGraph> {
       |${resource(
        "http://rdfh.ch/0001/a",
        open,
        deleted = false,
        s"""; <$hasText> <http://rdfh.ch/0001/a/t1>, <http://rdfh.ch/0001/a/t2>, <http://rdfh.ch/0001/a/t3> ;
           |    <$hasThingValue> <http://rdfh.ch/0001/a/l1> ;
           |    <${anything}hasThing> <http://rdfh.ch/0001/b> ;
           |    kb:hasStandoffLinkToValue <http://rdfh.ch/0001/a/s1> ;
           |    <$hasPicture> <http://rdfh.ch/0001/a/p1>""".stripMargin,
      )}
       |${resource("http://rdfh.ch/0001/b", open, deleted = false, "")}
       |${resource(
        "http://rdfh.ch/0001/deleted",
        member,
        deleted = true,
        s"; <$hasText> <http://rdfh.ch/0001/deleted/t1>",
      )}
       |${value("http://rdfh.ch/0001/a/t1", open, extra = """; kb:valueHasComment "a comment"""")}
       |${value("http://rdfh.ch/0001/a/t2", open)}
       |${value("http://rdfh.ch/0001/a/t3", member, deleted = true, extra = """; kb:valueHasComment "gone"""")}
       |${value("http://rdfh.ch/0001/a/l1", open)}
       |${value("http://rdfh.ch/0001/a/s1", member)}
       |${value("http://rdfh.ch/0001/a/p1", member)}
       |${value("http://rdfh.ch/0001/deleted/t1", member)}
       |}
       |
       |<$otherGraph> {
       |${resource("http://rdfh.ch/0001/c", member, deleted = false, s"; <$hasText> <http://rdfh.ch/0001/c/t1>")}
       |${value("http://rdfh.ch/0001/c/t1", open)}
       |}
       |""".stripMargin

  private val repo = ZIO.serviceWithZIO[ViewRestrictionsRepo]

  private val layers = ZLayer.makeSome[Ref[Dataset], ViewRestrictionsRepo](
    ViewRestrictionsRepo.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.layer,
  )

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("ViewRestrictionsRepo count queries")(
    test("resourceCounts groups by class and literal and skips deleted resources") {
      for {
        rows <- repo(_.resourceCounts(projectIri))
      } yield assertTrue(
        rows.toSet == Set(ResourceCountRow(thingClass, open, 2), ResourceCountRow(thingClass, member, 1)),
      )
    },
    test("valueCounts reads the project's data graph and keeps only the live values of live resources") {
      for {
        rows <- repo(_.valueCounts(anythingProject))
      } yield assertTrue(
        rows.toSet == Set(
          // a/t1 (commented) and a/t2; the deleted a/t3 and c/t1 in the other graph are left out
          ValueCountRow(thingClass, hasText, open, isFile = false, count = 2, commented = 1),
          // a user link value counts; the link target b and the standoff link value s1 do not
          ValueCountRow(thingClass, hasThingValue, open, isFile = false, count = 1, commented = 0),
          ValueCountRow(thingClass, hasPicture, member, isFile = true, count = 1, commented = 0),
        ),
      )
    },
  ).provide(layers, datasetLayerFromTriG(trig))
}
