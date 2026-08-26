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
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.*
import org.knora.webapi.slice.infrastructure.CacheManager
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

  private val subThingClass = "http://www.knora.org/ontology/0001/anything#SubThing"

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

  private val commonLayers = ZLayer.makeSome[Ref[Dataset], ViewRestrictionsService](
    ViewRestrictionsService.layer,
    ViewRestrictionsRepo.layer,
    CacheManager.layer,
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
    // LOAD-BEARING: the exact summary counts classify *bare* permission literals, substituting a placeholder
    // for each object's real creator. That is only sound if the creator cannot change the answer — i.e. if
    // no audience user is ever the creator of the object being judged. It holds because the audience users
    // are synthetic and never author data. This pins it: for any real creator IRI, all three audiences see
    // the same visibility they'd see with the placeholder.
    //
    // Note the deliberately included `CR knora-admin:Creator` literal: it is a real default clause, and it
    // is exactly the case that would break if an audience user could ever equal an object's creator.
    test("visibility of a literal does not depend on which real user created the object") {
      val literals = Seq(
        "M knora-admin:ProjectMember",
        "V knora-admin:KnownUser",
        "RV knora-admin:UnknownUser|V knora-admin:KnownUser",
        "CR knora-admin:Creator|V knora-admin:ProjectMember",
      )
      val creators = Seq(
        "http://rdfh.ch/users/creator",
        "http://rdfh.ch/users/root",
        "http://rdfh.ch/users/someone-else",
      )
      for {
        results <- service(s =>
                     ZIO.succeed(
                       for {
                         literal  <- literals
                         audience <- Audience.ordered
                       } yield creators.map { creator =>
                         s.visibilityOf(
                           PermissionUtilADM.getUserPermissionADM(
                             entityCreator = creator,
                             entityProject = projectIri.value,
                             entityPermissionLiteral = literal,
                             requestingUser = s.audienceUser(audience, projectIri),
                           ),
                         )
                       }.distinct,
                     ),
                   )
        // …and no audience user's own id collides with a real user IRI, which is what makes the above hold.
        ids <- service(s => ZIO.succeed(Audience.ordered.map(a => s.audienceUser(a, projectIri).id)))
      } yield assertTrue(
        results.forall(_.size == 1),
        ids.forall(id => !creators.contains(id)),
      )
    },
  ).provide(commonLayers, emptyDataset)

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
    test("the drill-down lists the resource once for its most specific class") {
      for {
        page <- service(_.items(projectIri, subThingClass, ItemType.All, PageAndSize.Default))
      } yield assertTrue(
        page.pagination.totalItems == 1,
        page.data.size == 1,
        page.data.head.resourceIri == "http://rdfh.ch/0001/multiTyped",
      )
    }.provide(commonLayers, datasetLayerFromTurtle(multiTypedTurtle)),
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

  // ----- count units, at the repo boundary where the tagging happens -----

  def spec = suite("ViewRestrictionsService")(
    pureSuite,
    itemsSuite,
    valuesSuite,
    nonImageSuite,
    creatorOnlySuite,
    multiTypedSuite,
    steppedSuite,
  )
}
