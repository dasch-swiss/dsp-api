/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.apache.jena.query.Dataset
import org.junit.runner.RunWith
import zio.*
import zio.test.*

import dsp.errors.NotFoundException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.*
import org.knora.webapi.store.triplestore.TestDatasetBuilder.datasetLayerFromTriG
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

  /**
   * Five resource classes, each with one restricted value carried by the SAME property.
   *
   * Five classes exceeds `ViewRestrictionsRepo.ClassChunkSize` (3), so the grouped counts really are issued
   * as more than one per-chunk query and merged. In property mode all five classes contribute to the one
   * `hasText` group, so that group's count spans every chunk — the case where the merge must add the
   * per-chunk counts rather than pick one of them. With a single chunk the merge is a no-op and a broken
   * merge would go unnoticed.
   */
  private val manyClassesTurtle: String = {
    val classes = (1 to 5).map(i => s"http://www.knora.org/ontology/0001/anything#Klass$i")
    val decls   = classes.map(c => s"<$c> rdfs:subClassOf kb:Resource .").mkString("\n")
    val bodies  = classes.zipWithIndex.map { case (cls, i) =>
      s"""
         |<http://rdfh.ch/0001/many$i>
         |  a <$cls> ;
         |  rdfs:label "Many $i" ;
         |  kb:attachedToProject <${projectIri.value}> ;
         |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
         |  kb:hasPermissions "V knora-admin:UnknownUser" ;
         |  kb:isDeleted false ;
         |  <$hasText> <http://rdfh.ch/0001/many$i/values/text> .
         |
         |<http://rdfh.ch/0001/many$i/values/text>
         |  a kb:TextValue ;
         |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
         |  kb:hasPermissions "M knora-admin:ProjectMember" ;
         |  kb:isDeleted false .
         |""".stripMargin
    }.mkString

    s"""
       |@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:    <$kb> .
       |
       |$decls
       |<$hasText> rdfs:subPropertyOf kb:hasValue .
       |$bodies
       |""".stripMargin
  }

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

  /**
   * ONE resource, itself fully visible, carrying THREE hidden values.
   *
   * LOAD-BEARING: this is the shape that exposed the unit-mixing bug. When resource counts and value counts
   * were summed into a single number, this fixture reported `hidden = 3` against `totalResources = 1` — a
   * row reading "3 of 1", i.e. more restrictions than the class has resources. Every other fixture hides the
   * bug by accident: they either restrict resources only, or carry exactly one restricted value per resource,
   * so the two units coincide numerically.
   *
   * With the units split, the correct answer is `resources = 0` (the resource itself is open) and
   * `items = 3`, with `totalResources = 1`.
   */
  private val manyValuesTurtle: String =
    s"""
       |@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:    <$kb> .
       |
       |<$thingClass> rdfs:subClassOf kb:Resource .
       |<$hasText>    rdfs:subPropertyOf kb:hasValue .
       |
       |<http://rdfh.ch/0001/manyValues>
       |  a <$thingClass> ;
       |  rdfs:label "Many values" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  <$hasText> <http://rdfh.ch/0001/manyValues/values/v1> ,
       |             <http://rdfh.ch/0001/manyValues/values/v2> ,
       |             <http://rdfh.ch/0001/manyValues/values/v3> .
       |
       |<http://rdfh.ch/0001/manyValues/values/v1>
       |  a kb:TextValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/manyValues/values/v2>
       |  a kb:TextValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/manyValues/values/v3>
       |  a kb:TextValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |""".stripMargin

  /**
   * A resource that is BOTH itself restricted AND carries a restricted value — so both units are non-zero
   * on the same row at the same time. Ensures neither unit leaks into the other.
   */
  private val bothUnitsTurtle: String =
    s"""
       |@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:    <$kb> .
       |
       |<$thingClass> rdfs:subClassOf kb:Resource .
       |<$hasText>    rdfs:subPropertyOf kb:hasValue .
       |
       |<http://rdfh.ch/0001/bothUnits>
       |  a <$thingClass> ;
       |  rdfs:label "Both units" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false ;
       |  <$hasText> <http://rdfh.ch/0001/bothUnits/values/v1> ,
       |             <http://rdfh.ch/0001/bothUnits/values/v2> .
       |
       |<http://rdfh.ch/0001/bothUnits/values/v1>
       |  a kb:TextValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/bothUnits/values/v2>
       |  a kb:TextValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "RV knora-admin:UnknownUser|V knora-admin:KnownUser" ;
       |  kb:isDeleted false .
       |""".stripMargin

  private val manyValueClass = "http://www.knora.org/ontology/0001/anything#ManyValueThing"

  /**
   * Two classes to pin the row ordering across units: `Thing` has ONE hidden resource, `ManyValueThing` has
   * THREE hidden values but no hidden resource. Whole-resource loss is the more serious finding, so `Thing`
   * must lead despite the smaller raw number — which only works if the ordering key looks at the units
   * separately rather than at a summed total (3 > 1 would invert it).
   */
  private val orderingTurtle: String =
    s"""
       |@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:    <$kb> .
       |
       |<$thingClass>     rdfs:subClassOf kb:Resource .
       |<$manyValueClass> rdfs:subClassOf kb:Resource .
       |<$hasText>        rdfs:subPropertyOf kb:hasValue .
       |
       |<http://rdfh.ch/0001/hiddenRes>
       |  a <$thingClass> ;
       |  rdfs:label "Hidden resource" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/valueHost>
       |  a <$manyValueClass> ;
       |  rdfs:label "Value host" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  <$hasText> <http://rdfh.ch/0001/valueHost/values/v1> ,
       |             <http://rdfh.ch/0001/valueHost/values/v2> ,
       |             <http://rdfh.ch/0001/valueHost/values/v3> .
       |
       |<http://rdfh.ch/0001/valueHost/values/v1>
       |  a kb:TextValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/valueHost/values/v2>
       |  a kb:TextValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/valueHost/values/v3>
       |  a kb:TextValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |""".stripMargin

  private val openClass = "http://www.knora.org/ontology/0001/anything#OpenThing"

  /**
   * Two classes: `Thing` with a restricted resource, and `OpenThing` whose two resources are fully visible
   * to everyone. The report must still list `OpenThing` with `totalResources = 2` — the resource count is a
   * property of the class, not of the restrictions, so a class with nothing restricted is not "no data".
   *
   * This is the case that was previously dropped: rows were built from restriction hits only, so an
   * unrestricted class contributed no row and its population vanished from the report and its total.
   */
  private val unrestrictedClassTurtle: String =
    s"""
       |@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:    <$kb> .
       |
       |<$thingClass> rdfs:subClassOf kb:Resource .
       |<$openClass>  rdfs:subClassOf kb:Resource .
       |
       |<http://rdfh.ch/0001/restrictedThing>
       |  a <$thingClass> ;
       |  rdfs:label "Restricted thing" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/open1>
       |  a <$openClass> ;
       |  rdfs:label "Open one" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false .
       |
       |<http://rdfh.ch/0001/open2>
       |  a <$openClass> ;
       |  rdfs:label "Open two" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/creator> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
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
    KnoraProjectRepoInMemory.layer,
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

  // ----- summary over seeded data -----

  /** `h`idden / `rv` restricted-view, for compact expectations. */
  private def rc(hidden: Int, restrictedView: Int = 0): RestrictionCounts =
    RestrictionCounts(hidden, restrictedView)

  /** A unit-split expectation: restricted whole resources, and restricted values. */
  private def uc(resources: RestrictionCounts = rc(0), items: RestrictionCounts = rc(0)): UnitCounts =
    UnitCounts(resources, items)

  /** Only whole resources are restricted — the common case in the resource-only fixtures. */
  private def res(hidden: Int, restrictedView: Int = 0): UnitCounts = uc(resources = rc(hidden, restrictedView))

  /** Only values are restricted — the common case in the value fixtures. */
  private def items(hidden: Int, restrictedView: Int = 0): UnitCounts = uc(items = rc(hidden, restrictedView))

  /** Expected per-audience counts; each argument is a unit-split pair via [[uc]] / [[res]] / [[items]]. */
  private def ac(
    anonymous: UnitCounts,
    authenticated: UnitCounts,
    projectMember: UnitCounts,
  ): AudienceCounts = AudienceCounts(anonymous, authenticated, projectMember)

  private val summarySuite = suite("summary")(
    test("empty project yields no groups and zero totals") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield assertTrue(
        result.groups.isEmpty,
        result.totals == AudienceCounts.zero,
      )
    }.provide(commonLayers, emptyDataset),
    test("counts hidden and restricted-view resources separately, per audience, excluding deleted") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield {
        val thing = result.groups.find(_.id == thingClass)
        assertTrue(
          // one group (the Thing class); the deleted resource is excluded
          result.groups.size == 1,
          thing.isDefined,
          // anon: memberOnly + loggedIn are hidden (2); rvThing is restricted view (1) — reported in its own
          // bucket rather than folded into `hidden`. authenticated: memberOnly is hidden (1).
          // openThing is fully open (filtered out entirely).
          thing.get.counts == ac(anonymous = res(2, 1), authenticated = res(1), projectMember = res(0)),
          // AC2 — access widens across audiences, so the restricted count cannot grow. Checked on the
          // resources unit, which is the only one this fixture populates.
          thing.get.counts.anonymous.resources.total >= thing.get.counts.authenticated.resources.total,
          thing.get.counts.authenticated.resources.total >= thing.get.counts.projectMember.resources.total,
          // totals equal the single group's counts
          result.totals == ac(anonymous = res(2, 1), authenticated = res(1), projectMember = res(0)),
          // ontology label derived from the class IRI
          thing.get.ontology.contains("anything"),
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(turtle)),
    // The class total is the denominator the restriction counts are read against, so it counts the whole
    // class population and NOT just the restricted part: openThing is fully visible and contributes 0 to
    // every count, yet it is one of the class's resources. The deleted resource is still excluded, so the
    // total is counted over the same universe as the counts themselves.
    test("reports the class's whole resource population as totalResources, not just the restricted part") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield {
        val thing = result.groups.find(_.id == thingClass)
        assertTrue(
          // openThing + memberOnly + loggedIn + rv = 4; deletedThing excluded
          thing.get.totalResources.contains(4),
          // The population is a real denominator for the RESOURCES unit: 3 of the 4 are restricted for anon
          // (2 hidden + 1 restricted view), and the open one is not.
          thing.get.counts.anonymous.resources.total == 3,
          thing.get.totalResources.exists(_ > thing.get.counts.anonymous.resources.total),
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(turtle)),
    // The total must stay independent of the itemType filter: filtering to file values narrows which
    // *restrictions* are reported, not how many resources the class has.
    test("totalResources is the class population regardless of the itemType filter") {
      for {
        all   <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
        files <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.File))
      } yield assertTrue(
        all.groups.find(_.id == thingClass).get.totalResources.contains(1),
        files.groups.find(_.id == thingClass).get.totalResources.contains(1),
      )
    }.provide(commonLayers, datasetLayerFromTurtle(valuesTurtle)),
    // A multi-typed resource must be counted once, under its most specific class only — the same pinning
    // the restriction counts rely on. Without it the population total double-counts it across the hierarchy.
    test("counts a multi-typed resource once, under its most specific class") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield assertTrue(
        result.groups.map(_.id) == Seq(subThingClass),
        result.groups.head.totalResources.contains(1),
      )
    }.provide(commonLayers, datasetLayerFromTurtle(multiTypedTurtle)),
    // The point of the resources count: it is a property of the class, independent of the restrictions. A
    // class with nothing restricted still has resources, so it is still reported — with zero counts and a
    // non-zero population.
    test("reports a class with no restrictions at all, with its population intact") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield {
        val open = result.groups.find(_.id == openClass)
        assertTrue(
          // both classes are listed, not just the restricted one
          result.groups.map(_.id).toSet == Set(thingClass, openClass),
          open.isDefined,
          open.get.totalResources.contains(2),
          // …and it is reported as unrestricted rather than omitted
          open.get.counts == AudienceCounts.zero,
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(unrestrictedClassTurtle)),
    // Summing the per-class populations must give the project's whole resource count — that only holds if
    // unrestricted classes are included in the row set.
    test("the class populations sum to the project's whole resource count") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield assertTrue(
        // 1 restricted Thing + 2 open OpenThings
        result.groups.flatMap(_.totalResources).sum == 3,
      )
    }.provide(commonLayers, datasetLayerFromTurtle(unrestrictedClassTurtle)),
    // Restricted classes stay at the top; the unrestricted remainder follows. Otherwise listing every class
    // would bury the findings an admin opened the page for.
    test("orders restricted classes before unrestricted ones") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield assertTrue(result.groups.map(_.id) == Seq(thingClass, openClass))
    }.provide(commonLayers, datasetLayerFromTurtle(unrestrictedClassTurtle)),
    // A class with hidden RESOURCES outranks one with only hidden values: whole-resource loss is the more
    // serious finding, so it leads even when the other class has a larger raw item count.
    test("orders hidden resources above hidden values") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield {
        val ids = result.groups.map(_.id)
        assertTrue(
          // Thing has 1 hidden resource; ManyValueThing has 3 hidden values but no hidden resource
          ids.headOption.contains(thingClass),
          ids == Seq(thingClass, manyValueClass),
          result.groups.head.counts.anonymous.resources.hidden == 1,
          result.groups(1).counts.anonymous.items.hidden == 3,
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(orderingTurtle)),
    // AC2 per unit: access widens across audiences, so neither unit's total can grow as the audience gains
    // access. Asserted per unit because a mixed number could satisfy the invariant while a unit violated it.
    test("both units are monotonic across the widening audiences") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield assertTrue(
        result.groups.nonEmpty,
        result.groups.forall { g =>
          val (a, k, m) = (g.counts.anonymous, g.counts.authenticated, g.counts.projectMember)
          a.resources.total >= k.resources.total && k.resources.total >= m.resources.total &&
          a.items.total >= k.items.total && k.items.total >= m.items.total
        },
      )
    }.provide(commonLayers, datasetLayerFromTurtle(bothUnitsTurtle)),
    // THE REGRESSION TEST. Before the units were split this returned hidden = 3 against totalResources = 1
    // — "3 of 1 resources restricted", which is not a fact about anything. The resource is fully visible, so
    // the resources unit must be empty and all three restrictions must land in the items unit.
    test("does not mix value counts into the resource count (one resource, three hidden values)") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield {
        val g = result.groups.find(_.id == thingClass).get
        assertTrue(
          // the resource itself is open to everyone, so nothing is counted in the resources unit…
          g.counts.anonymous.resources == rc(0, 0),
          // …and its three hidden values are all counted as items
          g.counts.anonymous.items == rc(3, 0),
          // the population is one resource, and the resources unit never exceeds it
          g.totalResources.contains(1),
          g.counts.anonymous.resources.total <= g.totalResources.get,
          // the mixed number that used to be reported (3) is NOT what the resource unit says
          g.counts.anonymous.resources.hidden != 3,
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(manyValuesTurtle)),
    // The same invariant stated generally: for every group and every audience, the resources unit is bounded
    // by the class population. This is the assertion that would have failed loudly on the old shape.
    test("the resources unit never exceeds the class population, for any audience") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield assertTrue(
        result.groups.nonEmpty,
        result.groups.forall { g =>
          val pop = g.totalResources.getOrElse(0)
          g.counts.anonymous.resources.total <= pop &&
          g.counts.authenticated.resources.total <= pop &&
          g.counts.projectMember.resources.total <= pop
        },
      )
    }.provide(commonLayers, datasetLayerFromTurtle(manyValuesTurtle)),
    // Both units non-zero on the same row: a restricted resource that also carries restricted values. Neither
    // unit may absorb the other's count.
    test("reports both units independently when a resource and its values are both restricted") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield {
        val g = result.groups.find(_.id == thingClass).get
        assertTrue(
          // the resource itself: hidden from anon and logged-in (M ProjectMember)
          g.counts.anonymous.resources == rc(1, 0),
          // its values: v1 hidden from anon, v2 restricted-view for anon
          g.counts.anonymous.items == rc(1, 1),
          // project members see everything here
          g.counts.projectMember == uc(),
          g.totalResources.contains(1),
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(bothUnitsTurtle)),
    // itemType=Resource asks only about whole resources, so the items unit must be empty — the filter must
    // not leak value counts into a resource-only view.
    test("itemType=Resource reports the resources unit only") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.Resource))
      } yield {
        val g = result.groups.find(_.id == thingClass).get
        assertTrue(
          g.counts.anonymous.resources == rc(1, 0),
          g.counts.anonymous.items == rc(0, 0),
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(bothUnitsTurtle)),
    // …and the converse: a value-only filter must not report whole resources, even though this resource is
    // itself restricted.
    test("itemType=Value reports the items unit only") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.Value))
      } yield {
        val g = result.groups.find(_.id == thingClass).get
        assertTrue(
          g.counts.anonymous.resources == rc(0, 0),
          g.counts.anonymous.items == rc(1, 1),
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(bothUnitsTurtle)),
    // The totals row must also keep the units apart, not just the group rows.
    test("totals keep the two units separate") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield assertTrue(
        result.totals.anonymous.resources == rc(1, 0),
        result.totals.anonymous.items == rc(1, 1),
      )
    }.provide(commonLayers, datasetLayerFromTurtle(bothUnitsTurtle)),
    // Property mode has no resource population to report: a property groups values across classes, so any
    // number here would be arbitrary. Absent, not zero. Unrestricted properties are omitted entirely, since
    // such a row would be empty in every column.
    // The dsp-app summary footer shows `totals` above a column of per-row `counts` and cross-checks
    // neither against the other, so a `totals` that did not reconcile with the rows would render as a
    // column that visibly fails to add up. Pinned in both grouping modes because they build `groups`
    // differently — class mode from the class population (unrestricted classes included), property mode
    // from the restriction rows only — and because the counts behind them are now assembled from several
    // per-chunk queries and merged (see ViewRestrictionsRepo.runCountByGroup), so an error in that merge
    // would surface here.
    test("totals reconcile with the sum of the per-group counts, grouping by class") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield {
        val summed = result.groups.map(_.counts).foldLeft(AudienceCounts.zero)(addCounts)
        assertTrue(
          result.groups.size > 1, // more than one group, or the sum is trivially the single row
          result.totals == summed,
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(unrestrictedClassTurtle)),
    test("totals reconcile with the sum of the per-group counts, grouping by property") {
      for {
        result <- service(_.summary(projectIri, GroupBy.Property, ItemType.All))
      } yield {
        val summed = result.groups.map(_.counts).foldLeft(AudienceCounts.zero)(addCounts)
        assertTrue(
          result.groups.nonEmpty,
          result.totals == summed,
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(valuesTurtle)),
    // Five classes -> more than one chunk, so these two exercise the per-chunk merge rather than a no-op.
    // In property mode the single `hasText` group is populated from every chunk, so its count is only right
    // if the merge sums the per-chunk contributions.
    test("a group whose rows span several class chunks gets the summed count") {
      for {
        result <- service(_.summary(projectIri, GroupBy.Property, ItemType.All))
      } yield {
        val text = result.groups.find(_.id == hasText)
        assertTrue(
          result.groups.size == 1,
          // one hidden value per class, all five under the same property
          text.map(_.counts.anonymous.items) == Some(rc(5, 0)),
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(manyClassesTurtle)),
    test("every class chunk is counted, so no class is dropped from the class-mode row set") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield {
        val summed = result.groups.map(_.counts).foldLeft(AudienceCounts.zero)(addCounts)
        assertTrue(
          result.groups.size == 5,
          result.groups.flatMap(_.totalResources).sum == 5,
          result.totals == summed,
          result.totals.anonymous.items == rc(5, 0),
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(manyClassesTurtle)),
    test("omits totalResources entirely when grouping by property") {
      for {
        result <- service(_.summary(projectIri, GroupBy.Property, ItemType.All))
      } yield assertTrue(
        result.groups.nonEmpty,
        result.groups.forall(_.totalResources.isEmpty),
      )
    }.provide(commonLayers, datasetLayerFromTurtle(valuesTurtle)),
  )

  /**
   * Adds two [[AudienceCounts]] per audience and unit. Deliberately spelled out here rather than reusing the
   * service's private `plus`, so the reconciliation tests below check the service's arithmetic against an
   * independent one instead of against itself.
   */
  private def addCounts(a: AudienceCounts, b: AudienceCounts): AudienceCounts =
    AudienceCounts(
      anonymous = addUnits(a.anonymous, b.anonymous),
      authenticated = addUnits(a.authenticated, b.authenticated),
      projectMember = addUnits(a.projectMember, b.projectMember),
    )

  private def addUnits(a: UnitCounts, b: UnitCounts): UnitCounts =
    UnitCounts(
      resources = RestrictionCounts(
        a.resources.hidden + b.resources.hidden,
        a.resources.restrictedView + b.resources.restrictedView,
      ),
      items = RestrictionCounts(a.items.hidden + b.items.hidden, a.items.restrictedView + b.items.restrictedView),
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
    // The picture is RV for anon and V above. Under the old hidden-only count this group vanished; now the
    // restricted-view state is reported in its own bucket, so the group survives and the RV item is visible
    // to the admin instead of being silently dropped.
    test("itemType=File reports the file value's restricted view rather than dropping it") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.File))
      } yield {
        val thing = result.groups.find(_.id == thingClass)
        assertTrue(
          thing.isDefined,
          thing.get.counts == ac(
            anonymous = items(hidden = 0, restrictedView = 1),
            authenticated = items(0),
            projectMember = items(0),
          ),
          // nothing is fully hidden for any audience
          thing.get.counts.anonymous.items.hidden == 0,
        )
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
          thing.get.counts == ac(anonymous = items(1), authenticated = items(1), projectMember = items(0)),
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(valuesTurtle)),
    test("itemType=Comment counts the hidden value that carries a comment") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.Comment))
      } yield {
        val thing = result.groups.find(_.id == thingClass)
        // the commented value shares its parent value's permissions (hidden from anon + logged-in)
        assertTrue(
          thing.isDefined,
          thing.get.counts == ac(anonymous = items(1), authenticated = items(1), projectMember = items(0)),
        )
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
          result.groups
            .find(_.id == hasText)
            .exists(_.counts == ac(anonymous = items(1), authenticated = items(1), projectMember = items(0))),
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
    test("summary counts for a property equal the drill-down tally, in BOTH states (AC12)") {
      for {
        summary <- service(_.summary(projectIri, GroupBy.Property, ItemType.All))
        page    <- service(_.items(projectIri, GroupBy.Property, hasText, ItemType.All, PageAndSize.Default))
      } yield {
        val summaryAnon   = summary.groups.find(_.id == hasText).map(_.counts.anonymous)
        val nonComment    = page.data.flatMap(_.items).filter(_.`type` != ItemType.Comment)
        val itemsHidden   = nonComment.count(_.visibility.anonymous == Visibility.Hidden)
        val itemsRestrict = nonComment.count(_.visibility.anonymous == Visibility.RestrictedView)
        // and the comment is nonetheless present as a nested item in the drill-down
        val hasCommentItem = page.data.flatMap(_.items).exists(_.`type` == ItemType.Comment)
        assertTrue(
          summaryAnon.contains(items(hidden = 1, restrictedView = 0)),
          // The drill-down lists VALUES, so it is the `items` unit that must match it — comparing against a
          // resource+value sum would be comparing different things.
          summaryAnon.map(_.items.hidden).contains(itemsHidden),
          summaryAnon.map(_.items.restrictedView).contains(itemsRestrict),
          // property mode never counts whole resources, so that unit stays empty
          summaryAnon.map(_.resources).contains(rc(0, 0)),
          hasCommentItem,
        )
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
    test("summary counts the non-image file as restricted view, not hidden, for anon") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.File))
      } yield {
        val thing = result.groups.find(_.id == thingClass)
        assertTrue(
          thing.isDefined,
          // the document is code 1 for anon: restricted view, and explicitly NOT hidden
          thing.get.counts == ac(
            anonymous = items(hidden = 0, restrictedView = 1),
            authenticated = items(0),
            projectMember = items(0),
          ),
          result.totals == ac(
            anonymous = items(hidden = 0, restrictedView = 1),
            authenticated = items(0),
            projectMember = items(0),
          ),
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(nonImageFileTurtle)),
  )

  // ----- the aggregated summary and the row-based drill-down must agree -----

  private val creatorOnlySuite = suite("CR knora-admin:Creator permissions")(
    test("a creator-only resource is hidden from all three audiences in the summary") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield {
        val thing = result.groups.find(_.id == thingClass)
        assertTrue(
          thing.isDefined,
          // none of the three audiences is the creator, so CR grants them nothing
          thing.get.counts == ac(anonymous = res(1), authenticated = res(1), projectMember = res(1)),
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(creatorOnlyTurtle)),
    // The summary classifies the bare literal with a placeholder creator; the drill-down resolves the real
    // creator from the row. If those two ever disagreed, the matrix and the detail view would contradict
    // each other — this asserts they don't, on the one literal where the creator is load-bearing.
    test("the drill-down agrees with the summary for the same resource") {
      for {
        page <- service(_.items(projectIri, GroupBy.ResourceClass, thingClass, ItemType.All, PageAndSize.Default))
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
    test("the summary counts the resource once, under its most specific class") {
      for {
        result <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
      } yield assertTrue(
        // one group only — not one per class in the hierarchy
        result.groups.size == 1,
        result.groups.head.id == subThingClass,
        result.groups.head.counts == ac(anonymous = res(1), authenticated = res(1), projectMember = res(0)),
        // and the totals are not inflated by the superclass binding
        result.totals == ac(anonymous = res(1), authenticated = res(1), projectMember = res(0)),
      )
    }.provide(commonLayers, datasetLayerFromTurtle(multiTypedTurtle)),
    test("the drill-down lists the resource once for its most specific class") {
      for {
        page <- service(_.items(projectIri, GroupBy.ResourceClass, subThingClass, ItemType.All, PageAndSize.Default))
      } yield assertTrue(
        page.pagination.totalItems == 1,
        page.data.size == 1,
        page.data.head.resourceIri == "http://rdfh.ch/0001/multiTyped",
      )
    }.provide(commonLayers, datasetLayerFromTurtle(multiTypedTurtle)),
  )

  // ----- stepped report (DEV-6778) -----

  /**
   * The graph `TestDataFactory.someProject` resolves to — shortcode `0001`, shortname `shortname`, so
   * `ProjectService.projectDataNamedGraphV2` yields this.
   *
   * The stepped queries scope with `GRAPH <…>` instead of filtering on `attachedToProject`, so the fixture
   * has to put its triples in exactly this graph. A Turtle fixture would land them in
   * `http://www.example.org/graph` and every count would come back zero — passing vacuously, which is why
   * these use TriG.
   */
  private val projectDataGraph = "http://www.knora.org/data/0001/shortname"

  /**
   * Two `Thing`s in the project's data graph, one member-only (hidden from anon and logged-in) and one
   * world-readable, plus one member-only value on the first.
   *
   * Populations matter here as much as restrictions: `totalResources` is derived by summing a class's rows
   * over *every* literal, the fully visible one included, which is what retires the separate population
   * query.
   */
  private val steppedTriG: String =
    s"""
       |@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:   <$kb> .
       |
       |<$projectDataGraph> {
       |  <$thingClass> rdfs:subClassOf kb:Resource .
       |
       |  <http://rdfh.ch/0001/memberOnly> rdf:type <$thingClass> ;
       |    kb:attachedToProject <${projectIri.value}> ;
       |    kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |    kb:hasPermissions "M knora-admin:ProjectMember" ;
       |    kb:isDeleted false ;
       |    <http://www.knora.org/ontology/0001/anything#hasText> <http://rdfh.ch/0001/memberOnly/values/v1> .
       |
       |  <http://rdfh.ch/0001/memberOnly/values/v1> rdf:type kb:TextValue ;
       |    kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |    kb:hasPermissions "M knora-admin:ProjectMember" ;
       |    kb:isDeleted false .
       |
       |  <http://www.knora.org/ontology/0001/anything#hasText> rdfs:subPropertyOf kb:hasValue .
       |
       |  <http://rdfh.ch/0001/openThing> rdf:type <$thingClass> ;
       |    kb:attachedToProject <${projectIri.value}> ;
       |    kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |    kb:hasPermissions "V knora-admin:UnknownUser" ;
       |    kb:isDeleted false .
       |}
       |""".stripMargin

  /**
   * The project must exist for its data graph to be resolvable, so every stepped test seeds it first —
   * except the 404 test below, which deliberately does not.
   */
  private val seedProject =
    ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(TestDataFactory.someProject))

  /**
   * As [[commonLayers]], but also *exposes* the in-memory project repo.
   *
   * `commonLayers` outputs only the service, so the repo it is built from is unreachable from a test body —
   * and these tests have to seed the project whose shortcode and shortname the data graph is derived from.
   */
  private val steppedLayers =
    ZLayer.makeSome[Ref[Dataset], ViewRestrictionsService & KnoraProjectRepoInMemory](
      ViewRestrictionsService.layer,
      ViewRestrictionsRepo.layer,
      KnoraProjectRepoInMemory.layer,
      StringFormatter.test,
      TriplestoreServiceInMemory.layer,
    )

  private val steppedSuite = suite("stepped report")(
    test("classSummaries derives totalResources by summing every literal, visible ones included") {
      for {
        _      <- seedProject
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
    }.provide(steppedLayers, datasetLayerFromTriG(steppedTriG)),
    test("classSummaries reports every asserted class, including one with nothing restricted") {
      for {
        _      <- seedProject
        result <- service(_.classSummaries(projectIri))
      } yield assertTrue(
        result.classes.nonEmpty,
        result.projectIri == projectIri.value,
        // A class is a row whether or not it carries a restriction — the frontend needs the denominator.
        result.classes.forall(_.totalResources >= 0),
      )
    }.provide(steppedLayers, datasetLayerFromTriG(steppedTriG)),
    test("valueCounts counts values, in a unit separate from the resource counts") {
      for {
        _      <- seedProject
        result <- service(_.valueCounts(projectIri, thingClass, ValueItemType.All))
      } yield assertTrue(
        result.resourceClass == thingClass,
        // One member-only value: hidden from anon and logged-in, visible to a member. This is 1 *value*,
        // not to be added to the 1 restricted *resource* above.
        result.counts.anonymous.hidden == 1,
        result.counts.authenticated.hidden == 1,
        result.counts.projectMember.hidden == 0,
      )
    }.provide(steppedLayers, datasetLayerFromTriG(steppedTriG)),
    test("a project that does not exist is a failure, not an empty report") {
      // Deliberately does NOT seed the project. The authorization gate lets a system admin through without
      // the project existing, so this path is reachable and must not be a 500 or a silently empty table.
      for {
        exit <- service(_.classSummaries(projectIri)).exit
      } yield assertTrue(
        exit.isFailure,
        // a recoverable failure in the error channel (404), not a defect
        exit.causeOption.exists(c => c.failureOption.exists(_.isInstanceOf[NotFoundException])),
      )
    }.provide(steppedLayers, datasetLayerFromTriG(steppedTriG)),
  )

  // ----- count units, at the repo boundary where the tagging happens -----

  private val repo = ZIO.serviceWithZIO[ViewRestrictionsRepo]

  /** The permission literal that means "project members only" — hidden from anon and logged-in. */
  private val memberOnly = Set("M knora-admin:ProjectMember")

  private val repoLayers = ZLayer.makeSome[Ref[Dataset], ViewRestrictionsRepo](
    ViewRestrictionsRepo.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.layer,
  )

  private val countUnitSuite = suite("count units")(
    // The repo must label each count with what it counted. This is the single point where the resource and
    // value queries could be conflated, so it is pinned directly rather than only through the service.
    test("tags resource counts as Resources and value counts as Items") {
      for {
        classes <- repo(_.projectClasses(projectIri))
        rows    <- repo(_.countByGroup(projectIri, GroupBy.ResourceClass, ItemType.All, memberOnly, classes))
      } yield {
        val byUnit = rows.groupBy(_.unit).view.mapValues(_.map(_.count).sum).toMap
        assertTrue(
          // the resource itself is restricted (1 resource) and both its values are (2 values, one of which
          // is member-only; the RV one is not in this literal set)
          byUnit.get(ViewRestrictionsRepo.CountUnit.Resources).contains(1),
          byUnit.get(ViewRestrictionsRepo.CountUnit.Items).contains(1),
          // and the two are separate rows, never pre-summed
          rows.map(_.unit).distinct.size == 2,
        )
      }
    }.provide(repoLayers, datasetLayerFromTurtle(bothUnitsTurtle)),
    // The population query counts resources, so it must be tagged as such — it is compared against the
    // resources unit downstream.
    test("tags the class population as Resources") {
      for {
        classes <- repo(_.projectClasses(projectIri))
        rows    <- repo(_.totalResourcesByClass(projectIri, classes))
      } yield assertTrue(
        rows.nonEmpty,
        rows.forall(_.unit == ViewRestrictionsRepo.CountUnit.Resources),
      )
    }.provide(repoLayers, datasetLayerFromTurtle(manyValuesTurtle)),
    // itemType=Resource must not even run the value query, so no Items rows can appear.
    test("emits no Items rows under itemType=Resource") {
      for {
        classes <- repo(_.projectClasses(projectIri))
        rows    <- repo(_.countByGroup(projectIri, GroupBy.ResourceClass, ItemType.Resource, memberOnly, classes))
      } yield assertTrue(
        rows.nonEmpty,
        rows.forall(_.unit == ViewRestrictionsRepo.CountUnit.Resources),
      )
    }.provide(repoLayers, datasetLayerFromTurtle(bothUnitsTurtle)),
    // …and property mode never counts whole resources, so no Resources rows can appear.
    test("emits no Resources rows in property mode") {
      for {
        classes <- repo(_.projectClasses(projectIri))
        rows    <- repo(_.countByGroup(projectIri, GroupBy.Property, ItemType.All, memberOnly, classes))
      } yield assertTrue(
        rows.nonEmpty,
        rows.forall(_.unit == ViewRestrictionsRepo.CountUnit.Items),
      )
    }.provide(repoLayers, datasetLayerFromTurtle(bothUnitsTurtle)),
  )

  // ----- the summary's resources unit agrees with the drill-down's resource list -----

  private val unitConsistencySuite = suite("summary/drill-down consistency per unit")(
    // The drill-down's resource-level visibility is the ground truth for the resources unit: counting the
    // resources it reports as hidden must reproduce the summary's number exactly. Under the old mixed count
    // this could not hold, because the summary number included values.
    test("the resources unit equals the drill-down's hidden-resource tally") {
      for {
        summary <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
        page    <- service(_.items(projectIri, GroupBy.ResourceClass, thingClass, ItemType.All, PageAndSize.Default))
      } yield {
        val counts      = summary.groups.find(_.id == thingClass).get.counts.anonymous
        val resHidden   = page.data.count(_.resourceVisibility.anonymous == Visibility.Hidden)
        val resRestrict = page.data.count(_.resourceVisibility.anonymous == Visibility.RestrictedView)
        assertTrue(
          counts.resources.hidden == resHidden,
          counts.resources.restrictedView == resRestrict,
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(turtle)),
    // And the items unit equals the drill-down's nested-item tally, comments excluded (a comment shares its
    // parent value's permissions, so the summary counts the value once).
    test("the items unit equals the drill-down's restricted-item tally") {
      for {
        summary <- service(_.summary(projectIri, GroupBy.ResourceClass, ItemType.All))
        page    <- service(_.items(projectIri, GroupBy.ResourceClass, thingClass, ItemType.All, PageAndSize.Default))
      } yield {
        val counts     = summary.groups.find(_.id == thingClass).get.counts.anonymous
        val nonComment = page.data.flatMap(_.items).filter(_.`type` != ItemType.Comment)
        assertTrue(
          counts.items.hidden == nonComment.count(_.visibility.anonymous == Visibility.Hidden),
          counts.items.restrictedView == nonComment.count(_.visibility.anonymous == Visibility.RestrictedView),
        )
      }
    }.provide(commonLayers, datasetLayerFromTurtle(manyValuesTurtle)),
  )

  def spec = suite("ViewRestrictionsService")(
    pureSuite,
    summarySuite,
    itemsSuite,
    valuesSuite,
    nonImageSuite,
    creatorOnlySuite,
    multiTypedSuite,
    countUnitSuite,
    unitConsistencySuite,
    steppedSuite,
  )
}
