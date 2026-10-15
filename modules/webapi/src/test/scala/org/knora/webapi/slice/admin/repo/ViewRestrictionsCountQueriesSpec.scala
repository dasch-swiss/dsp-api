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
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ResourceCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ValueCountRow
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.store.triplestore.TestDatasetBuilder.datasetLayerFromTurtle
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

/** Runs the class-scoped count queries of [[ViewRestrictionsRepo]] against an in-memory triplestore. */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsCountQueriesSpec extends ZIOSpecDefault {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val thingClass = "http://www.knora.org/ontology/0001/anything#Thing"
  private val kb         = "http://www.knora.org/ontology/knora-base#"
  private val hasText    = "http://www.knora.org/ontology/0001/anything#hasText"
  private val hasLink    = "http://www.knora.org/ontology/0001/anything#hasThingValue"
  private val hasThing   = "http://www.knora.org/ontology/0001/anything#hasThing"

  private val turtle: String =
    s"""
       |@prefix kb: <$kb> .
       |
       |<http://rdfh.ch/0001/a>
       |  a <$thingClass> ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  <$hasText> <http://rdfh.ch/0001/a/values/t1>, <http://rdfh.ch/0001/a/values/t2>, <http://rdfh.ch/0001/a/values/t3> ;
       |  <$hasLink> <http://rdfh.ch/0001/a/values/l1> ;
       |  <$hasThing> <http://rdfh.ch/0001/b> .
       |
       |<http://rdfh.ch/0001/b>
       |  a <$thingClass> ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/c>
       |  a <$thingClass> ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/deleted>
       |  a <$thingClass> ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted true .
       |
       |<http://rdfh.ch/0001/a/values/t1>
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  kb:valueHasComment "a comment" .
       |
       |<http://rdfh.ch/0001/a/values/t2>
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/a/values/t3>
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted true .
       |
       |<http://rdfh.ch/0001/a/values/l1>
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false .
       |""".stripMargin

  private val repo = ZIO.serviceWithZIO[ViewRestrictionsRepo]

  private val layers = ZLayer.makeSome[Ref[Dataset], ViewRestrictionsRepo](
    ViewRestrictionsRepo.layer,
    CacheManager.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.layer,
  )

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("ViewRestrictionsRepo count queries")(
    test("resourceCounts groups by class and literal and skips deleted resources") {
      for {
        rows <- repo(_.resourceCounts(projectIri))
      } yield assertTrue(
        rows.toSet == Set(
          ResourceCountRow(thingClass, "V knora-admin:UnknownUser", 2),
          ResourceCountRow(thingClass, "M knora-admin:ProjectMember", 1),
        ),
      )
    }.provide(layers, datasetLayerFromTurtle(turtle)),
    test("valueCounts returns deleted values, comments, link values and link targets per property") {
      for {
        rows <- repo(_.valueCounts(projectIri, thingClass))
      } yield assertTrue(
        rows.toSet == Set(
          ValueCountRow(thingClass, hasText, "V knora-admin:UnknownUser", deleted = false, count = 2, commented = 1),
          ValueCountRow(thingClass, hasText, "M knora-admin:ProjectMember", deleted = true, count = 1, commented = 0),
          ValueCountRow(thingClass, hasLink, "V knora-admin:UnknownUser", deleted = false, count = 1, commented = 0),
          ValueCountRow(thingClass, hasThing, "V knora-admin:UnknownUser", deleted = false, count = 1, commented = 0),
        ),
      )
    }.provide(layers, datasetLayerFromTurtle(turtle)),
  )
}
