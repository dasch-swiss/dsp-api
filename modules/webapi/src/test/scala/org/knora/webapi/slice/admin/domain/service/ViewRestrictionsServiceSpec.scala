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
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsTestFixtures.*
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.*
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.store.triplestore.TestDatasetBuilder.datasetLayerFromTriG
import org.knora.webapi.store.triplestore.TestDatasetBuilder.datasetLayerFromTurtle
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsServiceSpec extends ZIOSpecDefault {

  private val projectIri    = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val thingClass    = "http://www.knora.org/ontology/0001/anything#Thing"
  private val subThingClass = "http://www.knora.org/ontology/0001/anything#SubThing"

  private val kb      = "http://www.knora.org/ontology/knora-base#"
  private val service = ZIO.serviceWithZIO[ViewRestrictionsService]

  /**
   * Turtle seeding four `anything:Thing` resources of project 0001 with different permission literals,
   * plus a deleted one. `Thing` is declared a subclass of `knora-base:Resource` so the query's
   * `rdfs:subClassOf*` closure matches. Permission literals use the `knora-admin:` prefix that
   * `PermissionUtilADM.parsePermissions` expands.
   *
   * Counts below are (hidden, restrictedView) per audience — anon / authenticated / projectMember.
   *
   *   - openThing        : V for UnknownUser   → visible to everyone, filtered out of the report entirely
   *   - memberOnlyThing  : M for ProjectMember → hidden from anon + logged-in   (1,0 / 1,0 / 0,0)
   *   - loggedInThing    : V for KnownUser     → hidden from anon only          (1,0 / 0,0 / 0,0)
   *   - rvThing          : RV for UnknownUser, V for KnownUser → restricted view for anon, counted in its
   *                        own bucket rather than as hidden    (0,1 / 0,0 / 0,0)
   *   - deletedThing     : restricted, but knora-base:isDeleted true → excluded entirely
   */
  private val turtle: String =
    s"""
       |@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:    <$kb> .
       |
       |<$thingClass> rdfs:subClassOf kb:Resource .
       |
       |<http://rdfh.ch/0001/openThing>
       |  a <$thingClass> ;
       |  rdfs:label "Open thing" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/memberOnlyThing>
       |  a <$thingClass> ;
       |  rdfs:label "Member only thing" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/loggedInThing>
       |  a <$thingClass> ;
       |  rdfs:label "Logged in thing" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "V knora-admin:KnownUser|M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/rvThing>
       |  a <$thingClass> ;
       |  rdfs:label "Restricted view thing" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "RV knora-admin:UnknownUser|V knora-admin:KnownUser" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/deletedThing>
       |  a <$thingClass> ;
       |  rdfs:label "Deleted thing" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted true .
       |""".stripMargin

  private val hasPicture = "http://www.knora.org/ontology/0001/anything#hasPicture"
  private val hasText    = "http://www.knora.org/ontology/0001/anything#hasText"

  /**
   * A single visible resource carrying two restricted values:
   *   - a hidden ordinary text value (hidden from anon + logged-in) that also has a valueHasComment,
   *   - a still-image file value with RV for anon (restricted view), V for logged-in.
   * Exercises the value / file / comment paths and property-first grouping.
   */
  private val valuesTurtle: String =
    s"""
       |@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:    <$kb> .
       |
       |<$thingClass> rdfs:subClassOf kb:Resource .
       |<$hasText>    rdfs:subPropertyOf kb:hasValue .
       |<$hasPicture> rdfs:subPropertyOf kb:hasStillImageFileValue .
       |kb:hasStillImageFileValue rdfs:subPropertyOf kb:hasFileValue .
       |kb:hasFileValue rdfs:subPropertyOf kb:hasValue .
       |kb:StillImageFileValue rdfs:subClassOf kb:FileValue .
       |
       |<http://rdfh.ch/0001/host>
       |  a <$thingClass> ;
       |  rdfs:label "Host resource" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  <$hasText> <http://rdfh.ch/0001/host/values/text> ;
       |  <$hasPicture> <http://rdfh.ch/0001/host/values/pic> .
       |
       |<http://rdfh.ch/0001/host/values/text>
       |  a kb:TextValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false ;
       |  kb:valueHasComment "an editorial note" .
       |
       |<http://rdfh.ch/0001/host/values/pic>
       |  a kb:StillImageFileValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "RV knora-admin:UnknownUser|V knora-admin:KnownUser" ;
       |  kb:isDeleted false .
       |""".stripMargin

  /**
   * A resource whose permissions grant only `CR knora-admin:Creator` — hidden from all three audiences,
   * since none of them created it. This is the fixture that catches a divergence between the aggregated
   * summary (which classifies the bare literal with a placeholder creator) and the drill-down (which
   * resolves the same object with its real creator).
   */
  private val creatorOnlyTurtle: String =
    s"""
       |@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:    <$kb> .
       |
       |<$thingClass> rdfs:subClassOf kb:Resource .
       |
       |<http://rdfh.ch/0001/creatorOnly>
       |  a <$thingClass> ;
       |  rdfs:label "Creator only" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "CR knora-admin:Creator" ;
       |  kb:isDeleted false .
       |""".stripMargin

  /**
   * A resource asserted as BOTH its own class and that class's superclass — what a triplestore with RDFS
   * inference (or data that states both types) looks like. The report must still see it as ONE resource of
   * ONE class: the aggregated summary counts per class, so an unpinned `?resClass` would bind once per class
   * in the hierarchy and count the same resource twice, inflating the totals.
   */
  private val multiTypedTurtle: String =
    s"""
       |@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:    <$kb> .
       |
       |<$thingClass>    rdfs:subClassOf kb:Resource .
       |<$subThingClass> rdfs:subClassOf <$thingClass> .
       |
       |<http://rdfh.ch/0001/multiTyped>
       |  a <$subThingClass>, <$thingClass> ;
       |  rdfs:label "Multi typed" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |""".stripMargin

  private val hasDocument = "http://www.knora.org/ontology/0001/anything#hasDocument"

  /**
   * A visible resource with a **non-image** file value (a document) carrying RV for anon. A document is a
   * `kb:FileValue` but not a `kb:StillImageFileValue`; the summary is a reporting view of the stored
   * permissions, so code 1 is reported as RestrictedView here just as in the still-image case in
   * [[valuesTurtle]].
   */
  private val nonImageFileTurtle: String =
    s"""
       |@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:    <$kb> .
       |
       |<$thingClass> rdfs:subClassOf kb:Resource .
       |<$hasDocument> rdfs:subPropertyOf kb:hasValue .
       |kb:DocumentFileValue rdfs:subClassOf kb:FileValue .
       |
       |<http://rdfh.ch/0001/docHost>
       |  a <$thingClass> ;
       |  rdfs:label "Doc host" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  <$hasDocument> <http://rdfh.ch/0001/docHost/values/doc> .
       |
       |<http://rdfh.ch/0001/docHost/values/doc>
       |  a kb:DocumentFileValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "RV knora-admin:UnknownUser|V knora-admin:KnownUser" ;
       |  kb:isDeleted false .
       |""".stripMargin

  private val anythingOntology  = "http://www.knora.org/ontology/0001/anything"
  private val secondOntology    = "http://www.knora.org/ontology/0001/second"
  private val hasOtherThing     = s"$anythingOntology#hasOtherThing"
  private val hasOtherThingVal  = s"$anythingOntology#hasOtherThingValue"
  private val hasSecondText     = s"$secondOntology#hasSecondText"
  private val standoffLinkValue = s"${kb}hasStandoffLinkToValue"
  private val deletedOnlyClass  = s"$anythingOntology#DeletedOnlyThing"

  private val ontologyCacheLayer = ViewRestrictionsTestFixtures.ontologyCacheLayer(
    Seq(
      ontology(
        anythingOntology,
        classes = Seq(thingClass, subThingClass, deletedOnlyClass).map(resourceClass),
        properties = Seq(
          valueProp(hasText),
          fileProp(hasPicture),
          fileProp(hasDocument),
          linkProp(hasOtherThing),
          linkValueProp(hasOtherThingVal),
        ),
      ),
      ontology(secondOntology, classes = Seq.empty, properties = Seq(valueProp(hasSecondText))),
      ontology(
        "http://www.knora.org/ontology/knora-base",
        classes = Seq.empty,
        properties = Seq(linkValueProp(standoffLinkValue)),
      ),
    ),
  )

  private val commonLayers = ZLayer.makeSome[Ref[Dataset], ViewRestrictionsService](
    ViewRestrictionsService.layer,
    ViewRestrictionsRowCache.layer,
    ViewRestrictionsRepo.layer,
    IriConverter.layer,
    ontologyCacheLayer,
    OntologyRepoLive.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.layer,
  )

  private def countedLayers(resourceLoads: Ref[Int], valueLoads: Ref[Int]) =
    ZLayer.makeSome[Ref[Dataset], ViewRestrictionsService](
      ViewRestrictionsService.layer,
      countingCache(resourceLoads, valueLoads, thingClass),
      ViewRestrictionsRepo.layer,
      IriConverter.layer,
      ontologyCacheLayer,
      OntologyRepoLive.layer,
      StringFormatter.test,
      TriplestoreServiceInMemory.layer,
    )

  /** The number of (resource-row, value-row) loads the drill-down of `itemType` triggered. */
  private def loadsOf(itemType: ItemType) =
    for {
      resourceLoads <- Ref.make(0)
      valueLoads    <- Ref.make(0)
      _             <- service(_.items(projectIri, thingClass, itemType, PageAndSize.Default))
             .provideSome[Ref[Dataset]](countedLayers(resourceLoads, valueLoads))
      resources <- resourceLoads.get
      values    <- valueLoads.get
    } yield (resources, values)

  private val loadsSuite = suite("items reads only the cached rows the item type needs")(
    test("Resource loads resource rows only") {
      loadsOf(ItemType.Resource).map(loads => assertTrue(loads == (1, 0)))
    }.provide(datasetLayerFromTurtle(turtle)),
    test("Value loads value rows only") {
      loadsOf(ItemType.Value).map(loads => assertTrue(loads == (0, 1)))
    }.provide(datasetLayerFromTurtle(turtle)),
    test("File loads value rows only") {
      loadsOf(ItemType.File).map(loads => assertTrue(loads == (0, 1)))
    }.provide(datasetLayerFromTurtle(turtle)),
    test("Comment loads value rows only") {
      loadsOf(ItemType.Comment).map(loads => assertTrue(loads == (0, 1)))
    }.provide(datasetLayerFromTurtle(turtle)),
    test("All loads both") {
      loadsOf(ItemType.All).map(loads => assertTrue(loads == (1, 1)))
    }.provide(datasetLayerFromTurtle(turtle)),
  )

  private val itemsSuite = suite("items")(
    test("returns each affected resource with its per-audience visibility and correct paging total") {
      for {
        page <- service(_.items(projectIri, thingClass, ItemType.All, PageAndSize.Default))
      } yield {
        val byIri = page.data.map(r => r.resourceIri -> r.resourceVisibility).toMap
        assertTrue(
          // three RESTRICTED resources of the class: memberOnly, loggedIn, rv.
          // openThing (V for UnknownUser → fully open) is filtered out server-side, not reported.
          page.pagination.totalItems == 3,
          page.data.size == 3,
          !byIri.contains("http://rdfh.ch/0001/openThing"),
          // memberOnlyThing: hidden from anon + logged-in, visible to member
          byIri("http://rdfh.ch/0001/memberOnlyThing") ==
            ItemVisibility(Visibility.Hidden, Visibility.Hidden, Visibility.Visible),
          // loggedInThing: hidden from anon only
          byIri("http://rdfh.ch/0001/loggedInThing") ==
            ItemVisibility(Visibility.Hidden, Visibility.Visible, Visibility.Visible),
          // rvThing: RV permission on a RESOURCE → reported as RestrictedView for anon, since the
          // dashboard reports the stored permission rather than making a rendering decision.
          byIri("http://rdfh.ch/0001/rvThing") ==
            ItemVisibility(Visibility.RestrictedView, Visibility.Visible, Visibility.Visible),
          // AC14: the deleted resource is absent
          !byIri.contains("http://rdfh.ch/0001/deletedThing"),
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(turtle)),
    test("second page is empty when page size exceeds the result set") {
      for {
        page <- service(_.items(projectIri, thingClass, ItemType.All, PageAndSize(2, 25)))
      } yield assertTrue(page.data.isEmpty, page.pagination.totalItems == 3)
    }.provide(commonLayers, datasetLayerFromTurtle(turtle)),
  )

  // ----- values / file values / comments + property grouping over the values fixture -----

  private val valuesSuite = suite("values, files and comments")(
    test("drill-down nests the file/value/comment items under their resource with correct visibility") {
      for {
        page <- service(_.items(projectIri, thingClass, ItemType.All, PageAndSize.Default))
      } yield {
        val host  = page.data.find(_.resourceIri == "http://rdfh.ch/0001/host")
        val items = host.map(_.items).getOrElse(Nil)
        val types = items.map(_.`type`).toSet
        val file  = items.find(_.`type` == ItemType.File)
        val value = items.find(_.`type` == ItemType.Value)
        assertTrue(
          host.isDefined,
          // host resource itself is visible to all
          host.get.resourceVisibility == ItemVisibility(Visibility.Visible, Visibility.Visible, Visibility.Visible),
          // it nests a File, a Value and a Comment (the comment derives from the text value)
          types == Set(ItemType.File, ItemType.Value, ItemType.Comment),
          // file value: restricted view for anon, visible above
          file.exists(
            _.visibility == ItemVisibility(Visibility.RestrictedView, Visibility.Visible, Visibility.Visible),
          ),
          // ordinary value: hidden from anon + logged-in
          value.exists(_.visibility == ItemVisibility(Visibility.Hidden, Visibility.Hidden, Visibility.Visible)),
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(valuesTurtle)),
  )

  // ----- restricted view is reported for non-image file values too -----

  private val nonImageSuite = suite("non-image file values")(
    test("a non-image file value with RV for anon is reported as RestrictedView") {
      for {
        page <- service(_.items(projectIri, thingClass, ItemType.File, PageAndSize.Default))
      } yield {
        val doc = page.data.flatMap(_.items).find(_.`type` == ItemType.File)
        assertTrue(
          doc.isDefined,
          // Reporting view: code 1 is reported as stored, regardless of whether the file renders in RV.
          doc.get.visibility.anonymous == Visibility.RestrictedView,
          doc.get.visibility.authenticated == Visibility.Visible,
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(nonImageFileTurtle)),
  )

  // ----- the aggregated summary and the row-based drill-down must agree -----

  private val creatorOnlySuite = suite("CR knora-admin:Creator permissions")(
    // The summary classifies the bare literal with a placeholder creator; the drill-down resolves the real
    // creator from the row. If those two ever disagreed, the matrix and the detail view would contradict
    // each other — this asserts they don't, on the one literal where the creator is load-bearing.
    test("the drill-down agrees with the summary for the same resource") {
      for {
        page <- service(_.items(projectIri, thingClass, ItemType.All, PageAndSize.Default))
      } yield {
        val res = page.data.find(_.resourceIri == "http://rdfh.ch/0001/creatorOnly")
        assertTrue(
          res.isDefined,
          res.get.resourceVisibility ==
            ItemVisibility(Visibility.Hidden, Visibility.Hidden, Visibility.Hidden),
          page.pagination.totalItems == 1,
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(creatorOnlyTurtle)),
  )

  // ----- a resource typed as several classes in one hierarchy must not be counted per class -----

  private val multiTypedSuite = suite("resources typed as several classes in one hierarchy")(
    test("the drill-down lists the resource under each of its classes") {
      for {
        sub   <- service(_.items(projectIri, subThingClass, ItemType.All, PageAndSize.Default))
        thing <- service(_.items(projectIri, thingClass, ItemType.All, PageAndSize.Default))
      } yield assertTrue(
        sub.pagination.totalItems == 1,
        sub.data.size == 1,
        sub.data.head.resourceIri == "http://rdfh.ch/0001/multiTyped",
        sub.data.head.resourceClassIri == subThingClass,
        thing.pagination.totalItems == 1,
        thing.data.map(_.resourceIri) == Seq("http://rdfh.ch/0001/multiTyped"),
        thing.data.head.resourceClassIri == thingClass,
      )
    }.provide(commonLayers, datasetLayerFromTurtle(multiTypedTurtle)),
  )

  private val userCreator = "<http://rdfh.ch/users/creator>"

  /** One project resource in Turtle; `extra` holds further `predicate object` pairs of the resource. */
  private def resourceTurtle(
    iri: String,
    cls: String,
    label: String,
    permissions: String,
    deleted: Boolean = false,
    extra: Seq[String] = Seq.empty,
  ): String = {
    val predicates = Seq(
      s"a <$cls>",
      s"rdfs:label \"$label\"",
      s"kb:attachedToProject <${projectIri.value}>",
      s"kb:attachedToUser $userCreator",
      s"kb:hasPermissions \"$permissions\"",
      s"kb:isDeleted $deleted",
    ) ++ extra
    s"<$iri>\n  ${predicates.mkString(" ;\n  ")} .\n"
  }

  /** One value in Turtle, attached to a user and carrying a permission literal. */
  private def valueTurtle(iri: String, cls: String, permissions: String): String =
    s"""<$iri>
       |  a $cls ;
       |  kb:attachedToUser $userCreator ;
       |  kb:hasPermissions "$permissions" ;
       |  kb:isDeleted false .
       |""".stripMargin

  private val turtlePrefixes: String =
    s"""@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:    <$kb> .
       |""".stripMargin

  private val linkHost         = "http://rdfh.ch/0001/linkHost"
  private val linkValueIri     = s"$linkHost/values/link"
  private val standoffValueIri = s"$linkHost/values/standoff"
  private val standoffValue    = valueTurtle(standoffValueIri, "kb:LinkValue", "M knora-admin:ProjectMember")

  private val linkPropertiesTurtle: String =
    s"""<$hasOtherThingVal> rdfs:subPropertyOf kb:hasValue .
       |<$standoffLinkValue> rdfs:subPropertyOf kb:hasValue .
       |""".stripMargin

  private val hasOtherThingTurtle: String =
    turtlePrefixes + linkPropertiesTurtle +
      resourceTurtle(
        linkHost,
        thingClass,
        "Link host",
        "V knora-admin:UnknownUser",
        extra = Seq(
          s"<$hasOtherThing> <http://rdfh.ch/0001/linkTarget>",
          s"<$hasOtherThingVal> <$linkValueIri>",
          s"<$standoffLinkValue> <$standoffValueIri>",
        ),
      ) +
      resourceTurtle("http://rdfh.ch/0001/linkTarget", thingClass, "Link target", "V knora-admin:UnknownUser") +
      valueTurtle(linkValueIri, "kb:LinkValue", "M knora-admin:ProjectMember") +
      standoffValue

  /** The same host without the user link value: only the standoff link value is restricted. */
  private val standoffOnlyTurtle: String =
    turtlePrefixes + linkPropertiesTurtle +
      resourceTurtle(
        linkHost,
        thingClass,
        "Link host",
        "V knora-admin:UnknownUser",
        extra = Seq(s"<$standoffLinkValue> <$standoffValueIri>"),
      ) +
      standoffValue

  private val secondOntologyTurtle: String =
    turtlePrefixes +
      resourceTurtle(
        "http://rdfh.ch/0001/secondHost",
        thingClass,
        "Second host",
        "V knora-admin:UnknownUser",
        extra = Seq(s"<$hasSecondText> <http://rdfh.ch/0001/secondHost/values/text>"),
      ) +
      valueTurtle("http://rdfh.ch/0001/secondHost/values/text", "kb:TextValue", "M knora-admin:ProjectMember")

  private val deletedOnlyTurtle: String =
    turtlePrefixes +
      resourceTurtle("http://rdfh.ch/0001/liveThing", thingClass, "Live thing", "M knora-admin:ProjectMember") +
      resourceTurtle(
        "http://rdfh.ch/0001/goneThing",
        deletedOnlyClass,
        "Gone thing",
        "M knora-admin:ProjectMember",
        deleted = true,
      )

  private val valueClassificationSuite = suite("value classification by the ontology cache")(
    test("a restricted user link value is counted, as All and as Value") {
      for {
        all   <- service(_.valueCounts(projectIri, thingClass, ValueItemType.All))
        value <- service(_.valueCounts(projectIri, thingClass, ValueItemType.Value))
      } yield assertTrue(
        all.counts.anonymous.hidden == 1,
        all.counts.projectMember.hidden == 0,
        value.counts.anonymous.hidden == 1,
      )
    }.provide(commonLayers, datasetLayerFromTurtle(hasOtherThingTurtle)),
    test("a restricted standoff link value is not counted") {
      for {
        result <- service(_.valueCounts(projectIri, thingClass, ValueItemType.All))
      } yield assertTrue(result.counts == AudienceRestrictionCounts.zero)
    }.provide(commonLayers, datasetLayerFromTurtle(standoffOnlyTurtle)),
    test("a value of a property from another ontology is counted") {
      for {
        result <- service(_.valueCounts(projectIri, thingClass, ValueItemType.All))
      } yield assertTrue(result.counts.anonymous.hidden == 1)
    }.provide(commonLayers, datasetLayerFromTurtle(secondOntologyTurtle)),
    test("a class whose resources are all deleted is not reported") {
      for {
        result <- service(_.classSummaries(projectIri))
      } yield assertTrue(result.classes.map(_.id) == Seq(thingClass))
    }.provide(commonLayers, datasetLayerFromTurtle(deletedOnlyTurtle)),
    test("a resource typed with two classes is counted once for each class") {
      for {
        result <- service(_.classSummaries(projectIri))
      } yield assertTrue(
        result.classes.map(c => c.id -> c.totalResources).toMap == Map(thingClass -> 1, subThingClass -> 1),
      )
    }.provide(commonLayers, datasetLayerFromTurtle(multiTypedTurtle)),
  )

  private val anonymousOpenButRestrictedForKnownUsers = "V knora-admin:UnknownUser|RV knora-admin:KnownUser"

  private val mixedClauseTurtle: String =
    turtlePrefixes +
      resourceTurtle(
        "http://rdfh.ch/0001/mixedClause",
        thingClass,
        "Mixed clause",
        anonymousOpenButRestrictedForKnownUsers,
      )

  private val linkDrillDownSuite = suite("the class drill-down")(
    test("lists a restricted user link value as a Value item") {
      for {
        page <- service(_.items(projectIri, thingClass, ItemType.All, PageAndSize.Default))
        items = page.data.flatMap(_.items)
      } yield assertTrue(
        page.data.map(_.resourceIri) == Seq(linkHost),
        items.map(i => i.`type` -> i.valueIri) == Seq(ItemType.Value -> Some(linkValueIri)),
        page.pagination.totalItems == 1,
      )
    }.provide(commonLayers, datasetLayerFromTurtle(hasOtherThingTurtle)),
    test("does not list a restricted standoff link value") {
      for {
        page <- service(_.items(projectIri, thingClass, ItemType.All, PageAndSize.Default))
      } yield assertTrue(page.data.isEmpty, page.pagination.totalItems == 0)
    }.provide(commonLayers, datasetLayerFromTurtle(standoffOnlyTurtle)),
    test("lists a resource whose literal is open to anonymous users but restricted for authenticated users") {
      for {
        page <- service(_.items(projectIri, thingClass, ItemType.All, PageAndSize.Default))
      } yield assertTrue(
        page.data.map(_.resourceIri) == Seq("http://rdfh.ch/0001/mixedClause"),
        page.data.head.resourceVisibility ==
          ItemVisibility(Visibility.Visible, Visibility.RestrictedView, Visibility.RestrictedView),
      )
    }.provide(commonLayers, datasetLayerFromTurtle(mixedClauseTurtle)),
    test("sets the resource class on every listed resource") {
      for {
        page <- service(_.items(projectIri, thingClass, ItemType.All, PageAndSize.Default))
      } yield assertTrue(page.data.nonEmpty, page.data.forall(_.resourceClassIri == thingClass))
    }.provide(commonLayers, datasetLayerFromTurtle(turtle)),
    test("sets the resource class on every resource listed through a value") {
      for {
        page <- service(_.items(projectIri, thingClass, ItemType.All, PageAndSize.Default))
      } yield assertTrue(page.data.nonEmpty, page.data.forall(_.resourceClassIri == thingClass))
    }.provide(commonLayers, datasetLayerFromTurtle(valuesTurtle)),
    test("the number of listed Value items equals the anonymous hidden plus restricted-view value count") {
      // Both sides count values: every restricted value of the fixture is hidden for anonymous users.
      for {
        page   <- service(_.items(projectIri, thingClass, ItemType.Value, PageAndSize.Default))
        counts <- service(_.valueCounts(projectIri, thingClass, ValueItemType.Value))
        listed  = page.data.flatMap(_.items).count(_.`type` == ItemType.Value)
        anon    = counts.counts.anonymous
      } yield assertTrue(listed == 1, listed == anon.hidden + anon.restrictedView)
    }.provide(commonLayers, datasetLayerFromTurtle(hasOtherThingTurtle)),
  )

  // ----- stepped report (DEV-6778) -----

  /**
   * Two `Thing`s, one member-only (hidden from anon and logged-in) and one world-readable, plus one
   * member-only value on the first.
   *
   * Populations matter here as much as restrictions: `totalResources` is derived by summing a class's rows
   * over *every* literal, the fully visible one included, which is what retires the separate population
   * query.
   */
  private val steppedTurtle: String =
    s"""
       |@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:   <$kb> .
       |
       |<$thingClass> rdfs:subClassOf kb:Resource .
       |<http://www.knora.org/ontology/0001/anything#hasText> rdfs:subPropertyOf kb:hasValue .
       |
       |<http://rdfh.ch/0001/memberOnly> rdf:type <$thingClass> ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false ;
       |  <http://www.knora.org/ontology/0001/anything#hasText> <http://rdfh.ch/0001/memberOnly/values/v1> .
       |
       |<http://rdfh.ch/0001/memberOnly/values/v1> rdf:type kb:TextValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/openThing> rdf:type <$thingClass> ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false .
       |""".stripMargin

  private val steppedSuite = suite("stepped report")(
    test("classSummaries derives totalResources by summing every literal, visible ones included") {
      for {
        result <- service(_.classSummaries(projectIri))
        thing   = result.classes.find(_.id == thingClass)
      } yield assertTrue(
        // Both resources counted, though only one is restricted: the population is not a restriction count.
        thing.map(_.totalResources).contains(2),
        // Hidden from anonymous and from a logged-in non-member, visible to a project member.
        thing.map(_.counts.anonymous.hidden).contains(1),
        thing.map(_.counts.authenticated.hidden).contains(1),
        thing.map(_.counts.projectMember.hidden).contains(0),
        // The open resource is fully visible, so it is in the population but in no restriction bucket.
        thing.map(_.counts.anonymous.restrictedView).contains(0),
      )
    }.provide(commonLayers, datasetLayerFromTurtle(steppedTurtle)),
    test("classSummaries reports the project IRI and every asserted class") {
      for {
        result <- service(_.classSummaries(projectIri))
      } yield assertTrue(
        result.classes.nonEmpty,
        result.projectIri == projectIri.value,
        // A class is a row whether or not it carries a restriction — the frontend needs the denominator.
        result.classes.forall(_.totalResources >= 0),
      )
    }.provide(commonLayers, datasetLayerFromTurtle(steppedTurtle)),
    test("valueCounts counts values, in a unit separate from the resource counts") {
      for {
        result <- service(_.valueCounts(projectIri, thingClass, ValueItemType.All))
      } yield assertTrue(
        result.resourceClass == thingClass,
        // One member-only value: hidden from anon and logged-in, visible to a member. This is 1 *value*,
        // not to be added to the 1 restricted *resource* above.
        result.counts.anonymous.hidden == 1,
        result.counts.authenticated.hidden == 1,
        result.counts.projectMember.hidden == 0,
      )
    }.provide(commonLayers, datasetLayerFromTurtle(steppedTurtle)),
  )

  // ----- graph scoping would undercount: a project's data spans one graph per ontology -----

  /**
   * Three `Thing`s of one project, split across **two** named data graphs.
   *
   * This is the real DSP layout, not a contrived one: a project gets one data graph per ontology, so
   * `projectDataNamedGraphV2` — which derives exactly one — names only a slice of the project. An earlier
   * revision scoped these queries with `GRAPH <thatOneGraph>` for the speed-up, and the report silently
   * lost every resource living in the project's other graphs. Measured on the local `anything` project as
   * 65 resources in one graph and 6 in another; reproduced here at 2 and 1, which is the same defect.
   *
   * A Turtle fixture cannot catch this — [[datasetLayerFromTurtle]] puts everything in one fixed graph, so
   * a graph-scoped query would either match all of it or none of it, and the test would pass either way.
   * Hence TriG.
   */
  private val twoGraphTriG: String =
    s"""
       |@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:   <$kb> .
       |
       |<http://www.knora.org/data/0001/anything> {
       |  <$thingClass> rdfs:subClassOf kb:Resource .
       |
       |  <http://rdfh.ch/0001/inAnything1> rdf:type <$thingClass> ;
       |    kb:attachedToProject <${projectIri.value}> ;
       |    kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |    kb:hasPermissions "M knora-admin:ProjectMember" ;
       |    kb:isDeleted false .
       |
       |  <http://rdfh.ch/0001/inAnything2> rdf:type <$thingClass> ;
       |    kb:attachedToProject <${projectIri.value}> ;
       |    kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |    kb:hasPermissions "V knora-admin:UnknownUser" ;
       |    kb:isDeleted false .
       |}
       |
       |<http://www.knora.org/data/0001/something> {
       |  <http://rdfh.ch/0001/inSomething> rdf:type <$thingClass> ;
       |    kb:attachedToProject <${projectIri.value}> ;
       |    kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |    kb:hasPermissions "M knora-admin:ProjectMember" ;
       |    kb:isDeleted false .
       |}
       |""".stripMargin

  private val graphScopingSuite = suite("a project's data spanning several named graphs")(
    test("every graph is counted, because the queries scope by attachedToProject and not by GRAPH") {
      for {
        result <- service(_.classSummaries(projectIri))
        thing   = result.classes.find(_.id == thingClass)
      } yield assertTrue(
        // 2 in one graph + 1 in another. Under the reverted GRAPH scoping this read 2, with nothing to
        // indicate the third was missing — the failure mode was a plausible-looking smaller number.
        thing.map(_.totalResources).contains(3),
        // and the restriction counts lose the other graph's rows the same way
        thing.map(_.counts.anonymous.hidden).contains(2),
      )
    }.provide(commonLayers, datasetLayerFromTriG(twoGraphTriG)),
  )

  // ----- count units, at the repo boundary where the tagging happens -----

  def spec = suite("ViewRestrictionsService")(
    itemsSuite,
    valuesSuite,
    nonImageSuite,
    creatorOnlySuite,
    multiTypedSuite,
    steppedSuite,
    valueClassificationSuite,
    linkDrillDownSuite,
    loadsSuite,
    graphScopingSuite,
  )
}
