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
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.*
import org.knora.webapi.store.triplestore.TestDatasetBuilder.datasetLayerFromTurtle
import org.knora.webapi.store.triplestore.TestDatasetBuilder.emptyDataset
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsServiceSpec extends ZIOSpecDefault {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val thingClass = "http://www.knora.org/ontology/0001/anything#Thing"

  private val kb      = "http://www.knora.org/ontology/knora-base#"
  private val service = ZIO.serviceWithZIO[ViewRestrictionsService]

  /**
   * Turtle seeding four `anything:Thing` resources of project 0001 with different permission literals,
   * plus a deleted one. `Thing` is declared a subclass of `knora-base:Resource` so the query's
   * `rdfs:subClassOf*` closure matches. Permission literals use the `knora-admin:` prefix that
   * `PermissionUtilADM.parsePermissions` expands.
   *
   *   - openThing        : V for UnknownUser  → visible to everyone           (hidden count 0/0/0)
   *   - memberOnlyThing  : M for ProjectMember → hidden from anon + logged-in (hidden count 1/1/0)
   *   - loggedInThing    : V for KnownUser     → hidden from anon only        (hidden count 1/0/0)
   *   - rvThing          : RV for UnknownUser, V for KnownUser → reported as RestrictedView for anon,
   *                        which the summary does not count as hidden (hidden count 0/0/0)
   *   - deletedThing     : hidden from all but knora-base:isDeleted true → excluded entirely
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

  private val commonLayers = ZLayer.makeSome[Ref[Dataset], ViewRestrictionsService](
    ViewRestrictionsService.layer,
    ViewRestrictionsRepo.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.layer,
  )

  // ----- pure logic (no triplestore) -----

  private val pureSuite = suite("visibilityOf")(
    test("maps permission code 0 (no access) to Hidden") {
      for {
        vis <- service(s => ZIO.succeed(s.visibilityOf(None)))
      } yield assertTrue(vis == Visibility.Hidden)
    },
    test("maps RestrictedView (code 1) to RestrictedView for every item type") {
      for {
        vis <- service(s => ZIO.succeed(s.visibilityOf(Some(Permission.ObjectAccess.RestrictedView))))
      } yield assertTrue(vis == Visibility.RestrictedView)
    },
    test("maps View and higher (code >= 2) to Visible") {
      for {
        v <- service(s => ZIO.succeed(s.visibilityOf(Some(Permission.ObjectAccess.View))))
        m <- service(s => ZIO.succeed(s.visibilityOf(Some(Permission.ObjectAccess.Modify))))
        c <- service(s => ZIO.succeed(s.visibilityOf(Some(Permission.ObjectAccess.ChangeRights))))
      } yield assertTrue(v == Visibility.Visible, m == Visibility.Visible, c == Visibility.Visible)
    },
    test("the anonymous audience user is not an admin (would otherwise short-circuit to max permission)") {
      for {
        user <- service(s => ZIO.succeed(s.audienceUser(Audience.Anonymous, projectIri)))
      } yield assertTrue(user.isAnonymousUser, !user.isSystemAdmin, !user.isProjectAdmin(projectIri))
    },
    test("the project-member audience user is a member but not an admin of the project") {
      for {
        user <- service(s => ZIO.succeed(s.audienceUser(Audience.ProjectMember, projectIri)))
      } yield assertTrue(user.isProjectMember(projectIri), !user.isProjectAdmin(projectIri), !user.isSystemAdmin)
    },
  ).provide(commonLayers, emptyDataset)

  // ----- summary over seeded data -----

  private val summarySuite = suite("summary")(
    test("empty project yields no groups and zero totals") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield assertTrue(
        result.groups.isEmpty,
        result.totals == AudienceCounts(0, 0, 0),
      )
    }.provide(commonLayers, emptyDataset),
    test("counts hidden resources per audience, cumulatively, excluding deleted") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield {
        val thing = result.groups.find(_.id == thingClass)
        assertTrue(
          // one group (the Thing class); the deleted resource is excluded
          result.groups.size == 1,
          thing.isDefined,
          // hidden-for-anon: memberOnly (1) + loggedIn (1) = 2; rvThing is RestrictedView for anon, which
          // the summary does not count as hidden. hidden-for-authenticated: memberOnly (1) = 1.
          // openThing is fully open (filtered out).
          thing.get.counts == AudienceCounts(anonymous = 2, authenticated = 1, projectMember = 0),
          // AC2 — cumulative: anonymous >= authenticated >= projectMember
          thing.get.counts.anonymous >= thing.get.counts.authenticated,
          thing.get.counts.authenticated >= thing.get.counts.projectMember,
          // totals equal the single group's counts
          result.totals == AudienceCounts(2, 1, 0),
          // ontology label derived from the class IRI
          thing.get.ontology.contains("anything"),
          // small dataset well under the scan cap → exact, not approximate
          !result.approximate,
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(turtle)),
  )

  // ----- drill-down over seeded data -----

  private val itemsSuite = suite("items")(
    test("returns each affected resource with its per-audience visibility and correct paging total") {
      for {
        page <- service(_.items(projectIri, GroupBy.ResourceClass, thingClass, ItemType.All, PageAndSize.Default))
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
        page <- service(_.items(projectIri, GroupBy.ResourceClass, thingClass, ItemType.All, PageAndSize(2, 25)))
      } yield assertTrue(page.data.isEmpty, page.pagination.totalItems == 3)
    }.provide(commonLayers, datasetLayerFromTurtle(turtle)),
  )

  // ----- values / file values / comments + property grouping over the values fixture -----

  private val valuesSuite = suite("values, files and comments")(
    test("itemType=File counts only the restricted file value, per audience") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.File))
      } yield {
        val thing = result.groups.find(_.id == thingClass)
        // the picture is RV for anon (not hidden) and V above → hidden count 0/0/0, so no group survives
        assertTrue(result.groups.isEmpty || thing.forall(_.counts == AudienceCounts(0, 0, 0)))
      }
    }.provide(commonLayers, datasetLayerFromTurtle(valuesTurtle)),
    test("itemType=Value counts the hidden ordinary value") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.Value))
      } yield {
        val thing = result.groups.find(_.id == thingClass)
        assertTrue(
          thing.isDefined,
          // text value is M-for-member → hidden from anon + logged-in
          thing.get.counts == AudienceCounts(anonymous = 1, authenticated = 1, projectMember = 0),
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(valuesTurtle)),
    test("itemType=Comment counts the hidden value that carries a comment") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.Comment))
      } yield {
        val thing = result.groups.find(_.id == thingClass)
        // the commented value shares its parent value's permissions (hidden from anon + logged-in)
        assertTrue(thing.isDefined, thing.get.counts == AudienceCounts(1, 1, 0))
      }
    }.provide(commonLayers, datasetLayerFromTurtle(valuesTurtle)),
    test("drill-down nests the file/value/comment items under their resource with correct visibility") {
      for {
        page <- service(_.items(projectIri, GroupBy.ResourceClass, thingClass, ItemType.All, PageAndSize.Default))
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
    test("groupBy=Property groups values under the carrying property and omits whole-resource rows") {
      for {
        result <- service(_.summary(projectIri, GroupBy.Property, ItemType.All))
      } yield {
        val ids = result.groups.map(_.id).toSet
        assertTrue(
          // grouped by property IRI, not class IRI
          ids.contains(hasText),
          !ids.contains(thingClass),
          // the hidden text value shows up under hasText
          result.groups.find(_.id == hasText).exists(_.counts == AudienceCounts(1, 1, 0)),
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(valuesTurtle)),
    // AC7 (W1): in property mode, itemType=Resource must behave like itemType=All — property mode has no
    // whole-resource rows to group, so it surfaces the value/file/comment rows instead of an empty report.
    test("groupBy=Property + itemType=Resource behaves like itemType=All (AC7)") {
      for {
        asResource <- service(_.summary(projectIri, GroupBy.Property, ItemType.Resource))
        asAll      <- service(_.summary(projectIri, GroupBy.Property, ItemType.All))
      } yield assertTrue(
        asResource.groups.nonEmpty,
        // identical grouping/counts to the All view
        asResource.groups.map(g => g.id -> g.counts).toMap == asAll.groups.map(g => g.id -> g.counts).toMap,
      )
    }.provide(commonLayers, datasetLayerFromTurtle(valuesTurtle)),
    // AC12: the summary count and the drill-down must agree for the same property/filter. The summary
    // folds a comment into its parent value (a comment shares the value's permissions, so it is not counted
    // separately), so the consistency check excludes Comment facets from the drill-down tally too — the
    // drill-down still *lists* the comment as its own nested item, it just is not double-counted.
    test("summary hidden-count for a property equals the number of hidden non-comment items (AC12)") {
      for {
        summary <- service(_.summary(projectIri, GroupBy.Property, ItemType.All))
        page    <- service(_.items(projectIri, GroupBy.Property, hasText, ItemType.All, PageAndSize.Default))
      } yield {
        val summaryAnon = summary.groups.find(_.id == hasText).map(_.counts.anonymous).getOrElse(0)
        val itemsAnon   = page.data
          .flatMap(_.items)
          .filter(_.`type` != ItemType.Comment)
          .count(_.visibility.anonymous == Visibility.Hidden)
        // and the comment is nonetheless present as a nested item in the drill-down
        val hasCommentItem = page.data.flatMap(_.items).exists(_.`type` == ItemType.Comment)
        assertTrue(summaryAnon == 1, itemsAnon == summaryAnon, hasCommentItem, !page.pagination.approximate)
      }
    }.provide(commonLayers, datasetLayerFromTurtle(valuesTurtle)),
  )

  // ----- restricted view is reported for non-image file values too -----

  private val nonImageSuite = suite("non-image file values")(
    test("a non-image file value with RV for anon is reported as RestrictedView") {
      for {
        page <- service(_.items(projectIri, GroupBy.ResourceClass, thingClass, ItemType.File, PageAndSize.Default))
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
    test("summary does not count the RestrictedView file as hidden for anon") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.File))
      } yield {
        val thing = result.groups.find(_.id == thingClass)
        assertTrue(thing.exists(_.counts.anonymous == 0))
      }
    }.provide(commonLayers, datasetLayerFromTurtle(nonImageFileTurtle)),
  )

  def spec = suite("ViewRestrictionsService")(pureSuite, summarySuite, itemsSuite, valuesSuite, nonImageSuite)
}
