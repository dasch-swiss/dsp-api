/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.apache.jena.query.Dataset
import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologyMetadataV2
import org.knora.webapi.messages.v2.responder.ontologymessages.PropertyInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsByPropertyRepo
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ResourceCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ValueCountRow
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ValueItemType
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.ontology.repo.model.OntologyCacheData
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.store.triplestore.TestDatasetBuilder.datasetLayerFromTurtle
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

/**
 * Tests for [[ViewRestrictionsByPropertyService]].
 *
 * The counts classify a bare literal through [[ViewRestrictionsAudience]] while the drill-down resolves
 * each value with its real creator; the equivalence of the two is pinned in [[ViewRestrictionsAudienceSpec]].
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsByPropertyServiceSpec extends ZIOSpecDefault {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")

  // ----- dataset-backed: the fold and the drill-down paging -----

  private val kb       = "http://www.knora.org/ontology/knora-base#"
  private val anything = "http://www.knora.org/ontology/0001/anything#"
  private val hasText  = s"${anything}hasText"
  private val thing    = s"${anything}Thing"

  private val byProperty = ZIO.serviceWithZIO[ViewRestrictionsByPropertyService]

  private val secondThing    = s"${anything}SecondThing"
  private val subThing       = s"${anything}SubThing"
  private val hasOtherThingV = s"${anything}hasOtherThingValue"
  private val hasUnknown     = s"${anything}hasUnknown"
  private val standoffLinkV  = s"${kb}hasStandoffLinkToValue"

  /** The ontology cache the counts classify with: the classes and properties the Turtle fixtures use. */
  private def ontologyCacheData(sf: StringFormatter): OntologyCacheData = {
    def iri(s: String): SmartIri = sf.toSmartIri(s, requireInternal = true)

    def resourceClass(classIri: String): (SmartIri, ReadClassInfoV2) =
      iri(classIri) -> ReadClassInfoV2(
        ClassInfoContentV2(iri(classIri), ontologySchema = InternalSchema),
        allBaseClasses = Seq.empty,
        isResourceClass = true,
      )

    def property(propertyIri: String, isLinkValueProp: Boolean): (SmartIri, ReadPropertyInfoV2) =
      iri(propertyIri) -> ReadPropertyInfoV2(
        PropertyInfoContentV2(iri(propertyIri), ontologySchema = InternalSchema),
        isResourceProp = true,
        isLinkValueProp = isLinkValueProp,
      )

    def ontology(
      ontologyIri: String,
      classes: Seq[(SmartIri, ReadClassInfoV2)],
      properties: Seq[(SmartIri, ReadPropertyInfoV2)],
    ): (SmartIri, ReadOntologyV2) =
      iri(ontologyIri) -> ReadOntologyV2(
        OntologyMetadataV2(iri(ontologyIri)),
        classes = classes.toMap,
        properties = properties.toMap,
      )

    OntologyCacheFake.emptyData.copy(
      ontologies = Map(
        ontology(
          "http://www.knora.org/ontology/0001/anything",
          classes = Seq(thing, secondThing, subThing).map(resourceClass),
          properties = Seq(property(hasText, false), property(hasOtherThingV, true)),
        ),
        ontology(
          "http://www.knora.org/ontology/knora-base",
          classes = Seq.empty,
          properties = Seq(property(standoffLinkV, true)),
        ),
      ),
    )
  }

  private val ontologyCacheLayer: ZLayer[StringFormatter, Nothing, OntologyCacheFake] =
    ZLayer.fromZIO(
      ZIO.serviceWithZIO[StringFormatter](sf => Ref.make(ontologyCacheData(sf)).map(OntologyCacheFake(_))),
    )

  /** The service over a given row cache and triplestore. */
  private val serviceLayers =
    ZLayer.makeSome[ViewRestrictionsRowCache & TriplestoreService & StringFormatter, ViewRestrictionsByPropertyService](
      ViewRestrictionsByPropertyService.layer,
      ViewRestrictionsByPropertyRepo.layer,
      ontologyCacheLayer,
      OntologyRepoLive.layer,
      IriConverter.layer,
    )

  private val commonLayers = ZLayer.makeSome[Ref[Dataset], ViewRestrictionsByPropertyService](
    serviceLayers,
    ViewRestrictionsRowCache.layer,
    ViewRestrictionsRepo.layer,
    CacheManager.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.layer,
  )

  private def restrictedValue(iri: String) =
    s"""
       |<$iri> rdf:type kb:TextValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |""".stripMargin

  /**
   * Three resources, each carrying **two** restricted values of one property, plus one world-readable
   * value on the first.
   *
   * Two values per resource is the whole point: it is the shape that makes "a page of value rows" and
   * "a total of distinct resources" disagree. With one value each the two units coincide and the paging
   * bug this fixture exists to catch is invisible.
   */
  private val pagingTurtle: String =
    s"""
       |@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:   <$kb> .
       |
       |<$thing> rdfs:subClassOf kb:Resource .
       |<$hasText> rdfs:subPropertyOf kb:hasValue .
       |
       |<http://rdfh.ch/0001/a> rdf:type <$thing> ;
       |  rdfs:label "A" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  <$hasText> <http://rdfh.ch/0001/a/v1>, <http://rdfh.ch/0001/a/v2>, <http://rdfh.ch/0001/a/open> .
       |
       |<http://rdfh.ch/0001/b> rdf:type <$thing> ;
       |  rdfs:label "B" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  <$hasText> <http://rdfh.ch/0001/b/v1>, <http://rdfh.ch/0001/b/v2> .
       |
       |<http://rdfh.ch/0001/c> rdf:type <$thing> ;
       |  rdfs:label "C" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  <$hasText> <http://rdfh.ch/0001/c/v1>, <http://rdfh.ch/0001/c/v2> .
       |
       |${restrictedValue("http://rdfh.ch/0001/a/v1")}
       |${restrictedValue("http://rdfh.ch/0001/a/v2")}
       |${restrictedValue("http://rdfh.ch/0001/b/v1")}
       |${restrictedValue("http://rdfh.ch/0001/b/v2")}
       |${restrictedValue("http://rdfh.ch/0001/c/v1")}
       |${restrictedValue("http://rdfh.ch/0001/c/v2")}
       |
       |<http://rdfh.ch/0001/a/open> rdf:type kb:TextValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false .
       |""".stripMargin

  /** One resource asserted as two classes in one hierarchy, carrying a single restricted value. */
  private val multiTypedTurtle: String =
    s"""
       |@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:   <$kb> .
       |
       |<$thing> rdfs:subClassOf kb:Resource .
       |<${anything}SubThing> rdfs:subClassOf <$thing> .
       |<$hasText> rdfs:subPropertyOf kb:hasValue .
       |
       |<http://rdfh.ch/0001/multi> rdf:type <$thing>, <${anything}SubThing> ;
       |  rdfs:label "Multi" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  <$hasText> <http://rdfh.ch/0001/multi/v1> .
       |
       |${restrictedValue("http://rdfh.ch/0001/multi/v1")}
       |""".stripMargin

  private def linkValueTurtle(valueType: String, property: String): String =
    s"""
       |@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:   <$kb> .
       |
       |<$thing> rdfs:subClassOf kb:Resource .
       |
       |<http://rdfh.ch/0001/host> rdf:type <$thing> ;
       |  rdfs:label "Host" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  <$property> <http://rdfh.ch/0001/host/v1> .
       |
       |<http://rdfh.ch/0001/host/v1> rdf:type $valueType ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |""".stripMargin

  /** Two classes that both use `hasText`: three restricted values on `Thing`, two on `SecondThing`. */
  private val twoClassTurtle: String =
    def host(iri: String, clazz: String, values: Seq[String]) =
      s"""
         |<$iri> rdf:type <$clazz> ;
         |  rdfs:label "$iri" ;
         |  kb:attachedToProject <${projectIri.value}> ;
         |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
         |  kb:hasPermissions "V knora-admin:UnknownUser" ;
         |  kb:isDeleted false ;
         |  <$hasText> ${values.map(v => s"<$v>").mkString(", ")} .
         |${values.map(restrictedValue).mkString("\n")}
         |""".stripMargin
    s"""
       |@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:   <$kb> .
       |
       |<$thing> rdfs:subClassOf kb:Resource .
       |<$secondThing> rdfs:subClassOf kb:Resource .
       |
       |${host("http://rdfh.ch/0001/t1", thing, Seq("http://rdfh.ch/0001/t1/v1", "http://rdfh.ch/0001/t1/v2"))}
       |${host("http://rdfh.ch/0001/t2", thing, Seq("http://rdfh.ch/0001/t2/v1"))}
       |${host("http://rdfh.ch/0001/s1", secondThing, Seq("http://rdfh.ch/0001/s1/v1", "http://rdfh.ch/0001/s1/v2"))}
       |""".stripMargin

  /** One resource with a restricted text value and a restricted user link value: two properties, one class. */
  private val twoPropertyTurtle: String =
    s"""
       |@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:   <$kb> .
       |
       |<http://rdfh.ch/0001/both> rdf:type <$thing> ;
       |  rdfs:label "Both" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  <$hasText> <http://rdfh.ch/0001/both/text> ;
       |  <$hasOtherThingV> <http://rdfh.ch/0001/both/link> .
       |
       |${restrictedValue("http://rdfh.ch/0001/both/text")}
       |<http://rdfh.ch/0001/both/link> rdf:type kb:LinkValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |""".stripMargin

  /** A cache whose loaders count how often rows are read. */
  private def countingCache(loads: Ref[Int]): ULayer[ViewRestrictionsRowCache] =
    ZLayer.fromZIO {
      for {
        resources <- ViewRestrictionsRowCache.makeCache[ProjectIri, Seq[ResourceCountRow]](10, 1.minute)(_ =>
                       loads.update(_ + 1).as(Seq(ResourceCountRow(thing, "V knora-admin:UnknownUser", 1))),
                     )
        values <-
          ViewRestrictionsRowCache.makeCache[ViewRestrictionsRowCache.ValueKey, Seq[ValueCountRow]](10, 1.minute)(_ =>
            loads.update(_ + 1).as(Seq.empty),
          )
      } yield ViewRestrictionsRowCache(resources, values)
    }

  private def page(n: Int, size: Int) =
    byProperty(_.propertyItems(projectIri, hasText, ValueItemType.All, PageAndSize(n, size)))

  private val pagingSuite = suite("propertyItems paging")(
    test("a page is a page of resources, in the same unit the total counts") {
      // THE regression pin. Before the two-step window, the page query applied LIMIT/OFFSET to value rows
      // while the total counted distinct resources. With 3 resources x 2 restricted values, `totalItems`
      // was 3 and `totalPages` 2 at size 2, but the two pages consumed rows 1-4 — i.e. resources A and B
      // only. Resource C existed, was counted in the total, and was reachable by no page number at all.
      for {
        p1  <- page(1, 2)
        p2  <- page(2, 2)
        seen = (p1.data ++ p2.data).map(_.resourceIri)
      } yield assertTrue(
        p1.pagination.totalItems == 3,
        p1.pagination.totalPages == 2,
        // a page advertising size 2 returns 2 resources, not 2 rows' worth of them
        p1.data.size == 2,
        p2.data.size == 1,
        // every resource is reachable, and none is served twice
        seen.distinct.size == 3,
        seen.size == 3,
        seen.contains("http://rdfh.ch/0001/c"),
      )
    }.provide(commonLayers, datasetLayerFromTurtle(pagingTurtle)),
    test("a resource arrives whole: all of its restricted values on its own page, and none of the open ones") {
      // The other half of windowing by resource — a resource's values can no longer straddle a boundary
      // and come back twice, partial on each side.
      for {
        p1 <- page(1, 2)
        a   = p1.data.find(_.resourceIri == "http://rdfh.ch/0001/a")
      } yield assertTrue(
        a.map(_.values.size).contains(2),
        a.exists(_.values.map(_.valueIri).toSet == Set("http://rdfh.ch/0001/a/v1", "http://rdfh.ch/0001/a/v2")),
        // the world-readable value is not a restriction, so the drill-down omits it
        a.exists(!_.values.map(_.valueIri).contains("http://rdfh.ch/0001/a/open")),
      )
    }.provide(commonLayers, datasetLayerFromTurtle(pagingTurtle)),
    test("ordering is stable across pages, so paging cannot skip or repeat") {
      for {
        p1 <- page(1, 1)
        p2 <- page(2, 1)
        p3 <- page(3, 1)
      } yield assertTrue(
        p1.data.map(_.label) == Seq("A"),
        p2.data.map(_.label) == Seq("B"),
        p3.data.map(_.label) == Seq("C"),
      )
    }.provide(commonLayers, datasetLayerFromTurtle(pagingTurtle)),
    test("a page past the end is empty rather than an error") {
      for {
        p <- page(9, 25)
      } yield assertTrue(p.data.isEmpty, p.pagination.totalItems == 3)
    }.provide(commonLayers, datasetLayerFromTurtle(pagingTurtle)),
  )

  private val foldSuite = suite("propertyValues")(
    test("counts values and derives the population from the same rows") {
      for {
        result <- byProperty(_.propertyValues(projectIri, hasText, ValueItemType.All))
      } yield assertTrue(
        result.property == hasText,
        // 6 restricted + 1 world-readable: the population includes literals that are in no restriction
        // bucket, which is what retires a separate population query.
        result.totalValues == 7,
        result.counts.anonymous.hidden == 6,
        result.counts.authenticated.hidden == 6,
        result.counts.projectMember.hidden == 0,
        result.counts.anonymous.restrictedView == 0,
        // and the denominator is in the same unit as the numerator
        result.counts.anonymous.total <= result.totalValues,
      )
    }.provide(commonLayers, datasetLayerFromTurtle(pagingTurtle)),
    test("sums the rows of every class that uses the property") {
      for {
        result <- byProperty(_.propertyValues(projectIri, hasText, ValueItemType.All))
      } yield assertTrue(
        result.totalValues == 5,
        result.counts.anonymous.hidden == 5,
        result.counts.authenticated.hidden == 5,
        result.counts.projectMember.hidden == 0,
      )
    }.provide(commonLayers, datasetLayerFromTurtle(twoClassTurtle)),
    test("counts only the values of the requested property") {
      for {
        text <- byProperty(_.propertyValues(projectIri, hasText, ValueItemType.All))
        link <- byProperty(_.propertyValues(projectIri, hasOtherThingV, ValueItemType.All))
      } yield assertTrue(text.totalValues == 1, link.totalValues == 1)
    }.provide(commonLayers, datasetLayerFromTurtle(twoPropertyTurtle)),
    test("counts a restricted user link value") {
      for {
        result <- byProperty(_.propertyValues(projectIri, hasOtherThingV, ValueItemType.All))
      } yield assertTrue(result.totalValues == 1, result.counts.anonymous.hidden == 1)
    }.provide(commonLayers, datasetLayerFromTurtle(linkValueTurtle("kb:LinkValue", hasOtherThingV))),
    test("gives zero counts for the standoff link value property") {
      for {
        result <- byProperty(_.propertyValues(projectIri, standoffLinkV, ValueItemType.All))
      } yield assertTrue(
        result.totalValues == 0,
        result.counts.anonymous.hidden == 0,
        result.counts.authenticated.hidden == 0,
      )
    }.provide(commonLayers, datasetLayerFromTurtle(linkValueTurtle("kb:LinkValue", standoffLinkV))),
    test("gives zero counts for a property that is not in the ontology, without loading rows") {
      for {
        loads  <- Ref.make(0)
        result <- byProperty(_.propertyValues(projectIri, hasUnknown, ValueItemType.All))
                    .provideSome[Ref[Dataset]](
                      serviceLayers,
                      countingCache(loads),
                      StringFormatter.test,
                      TriplestoreServiceInMemory.layer,
                    )
        count <- loads.get
      } yield assertTrue(result.totalValues == 0, result.counts.anonymous.hidden == 0, count == 0)
    }.provide(datasetLayerFromTurtle(twoClassTurtle)),
  )

  private val multiTypedSuite = suite("a resource asserted as several classes")(
    test("is one row carrying its value once, not one row per type") {
      // This report joins `?resource a ?resClass` without the most-specific-class filter the class report
      // needs, so a multi-typed resource comes back once per type. Left unhandled that repeats every value
      // under `values` and reports an arbitrary class.
      for {
        p <- page(1, 25)
      } yield assertTrue(
        p.pagination.totalItems == 1,
        p.data.size == 1,
        p.data.head.values.size == 1,
        p.data.head.values.head.valueIri == "http://rdfh.ch/0001/multi/v1",
        // deterministic rather than whichever binding the store returned first
        p.data.head.resourceClassIri == s"${anything}SubThing",
      )
    }.provide(commonLayers, datasetLayerFromTurtle(multiTypedTurtle)),
  )

  def spec = suite("ViewRestrictionsByPropertyService")(
    pagingSuite,
    foldSuite,
    multiTypedSuite,
  )
}
