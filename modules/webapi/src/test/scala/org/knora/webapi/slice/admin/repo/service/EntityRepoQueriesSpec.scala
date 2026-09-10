/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.apache.jena.query.QueryFactory
import org.apache.jena.update.UpdateFactory
import org.junit.runner.RunWith
import zio.Chunk
import zio.NonEmptyChunk
import zio.ZIO
import zio.test.*

import org.knora.sparqlbuilder.*
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.DefaultObjectAccessPermissionPart
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.ForWhat
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

/**
 * Pins the SPARQL rendered by [[AbstractEntityRepo]] and the five admin entity repositories against the
 * output of the RDF4J SparqlBuilder implementation these queries were migrated from. Every `legacy` string
 * is the verbatim `getQueryString` output of that implementation; equality is asserted on the parsed,
 * prefix-stripped form, so only the query semantics — not its layout — are compared.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class EntityRepoQueriesSpec extends ZIOSpecDefault {

  private def canonicalQuery(sparql: String): String = {
    val query = QueryFactory.create(sparql)
    query.getPrefixMapping.clearNsPrefixMap()
    query.toString
  }

  private def canonicalUpdate(sparql: String): String = {
    val update = UpdateFactory.create(sparql)
    update.getPrefixMapping.clearNsPrefixMap()
    update.toString
  }

  private val s = Variable("s")

  private val prjIri    = ProjectIri.unsafeFrom("http://rdfh.ch/projects/1234")
  private val prjIri2   = ProjectIri.unsafeFrom("http://rdfh.ch/projects/5678")
  private val grpIri    = GroupIri.unsafeFrom("http://rdfh.ch/groups/0001/1234")
  private val grpIri2   = GroupIri.unsafeFrom("http://rdfh.ch/groups/0001/5678")
  private val prjIriRdf = Iri.unsafeFrom(prjIri.value)
  private val grpIriRdf = Iri.unsafeFrom(grpIri.value)

  private val fullGroup = KnoraGroup(
    grpIri,
    GroupName.unsafeFrom("group-name"),
    GroupDescriptions.unsafeFrom(
      Seq(StringLiteralV2.from("A group", LanguageCode.EN), StringLiteralV2.from("Eine Gruppe", LanguageCode.DE)),
    ),
    GroupStatus.active,
    Some(prjIri),
    GroupSelfJoin.disabled,
  )

  /** A group without the optional `belongsToProject` and with a plain (untagged) description. */
  private val minimalGroup = KnoraGroup(
    grpIri,
    GroupName.unsafeFrom("group-name"),
    GroupDescriptions.unsafeFrom(Seq(StringLiteralV2.from("A group"))),
    GroupStatus.active,
    None,
    GroupSelfJoin.disabled,
  )

  private val fullProject = KnoraProject(
    prjIri,
    Shortname.unsafeFrom("project1"),
    Shortcode.unsafeFrom("1234"),
    Some(Longname.unsafeFrom("Project 1")),
    NonEmptyChunk(Description.unsafeFrom("A project", Some("en")), Description.unsafeFrom("Ein Projekt", Some("de"))),
    List(Keyword.unsafeFrom("kw1"), Keyword.unsafeFrom("kw2")),
    Some(Logo.unsafeFrom("logo.png")),
    SelfJoin.CannotJoin,
    RestrictedView.Size.unsafeFrom("!128,128"),
    List("foo", "bar").map(CopyrightHolder.unsafeFrom).toSet,
    Set(LicenseIri.CC_BY_4_0),
    Some(LicenseIri.CC_BY_4_0),
    Some(CopyrightHolder.unsafeFrom("University of Basel")),
    List("Hilma af Klint", "Lotte Reiniger").map(Authorship.unsafeFrom),
  )

  /** A project with every optional property absent and a watermark instead of a restricted view size. */
  private val minimalProject = KnoraProject(
    prjIri,
    Shortname.unsafeFrom("project1"),
    Shortcode.unsafeFrom("1234"),
    None,
    NonEmptyChunk(Description.unsafeFrom("A project", None)),
    List.empty,
    None,
    SelfJoin.CanJoin,
    RestrictedView.Watermark.On,
    Set.empty,
    Set.empty,
    None,
    None,
    List.empty,
  )

  private val fullUser = KnoraUser(
    UserIri.unsafeFrom("http://rdfh.ch/users/1234"),
    Username.unsafeFrom("username"),
    Email.unsafeFrom("user@example.com"),
    FamilyName.unsafeFrom("Family"),
    GivenName.unsafeFrom("Given"),
    PasswordHash.unsafeFrom("hash"),
    LanguageCode.EN,
    UserStatus.Active,
    Chunk(prjIri, prjIri2),
    Chunk(grpIri, grpIri2),
    SystemAdmin.IsNotSystemAdmin,
    Chunk(prjIri, prjIri2),
  )

  /** A user without any project, group or project-admin membership. */
  private val minimalUser =
    fullUser.copy(isInProject = Chunk.empty, isInGroup = Chunk.empty, isInProjectAdminGroup = Chunk.empty)

  private val permIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/0001/1234")
  private val ap      = AdministrativePermission(
    permIri,
    grpIri,
    prjIri,
    Chunk(AdministrativePermissionPart.Simple.unsafeFrom(Permission.Administrative.ProjectResourceCreateAll)),
  )

  private val rcIri                  = InternalIri("https://example.com/rc")
  private val propIri                = InternalIri("https://example.com/p")
  private def doap(forWhat: ForWhat) = DefaultObjectAccessPermission(
    permIri,
    prjIri,
    forWhat,
    Chunk(DefaultObjectAccessPermissionPart(Permission.ObjectAccess.View, NonEmptyChunk(grpIri))),
  )

  private val groupSuite = suite("group")(
    test("group.findAll") {
      val legacy =
        """CONSTRUCT { ?s ?p ?o . }
WHERE { GRAPH <http://www.knora.org/data/admin> { ?s a <http://www.knora.org/ontology/knora-admin#UserGroup> ;
    ?p ?o . } }"""
      ZIO.serviceWith[KnoraGroupRepoLive](repo =>
        assertTrue(canonicalQuery(repo.findAllQuery.sparql) == canonicalQuery(legacy)),
      )
    },
    test("group.findById") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { <http://rdfh.ch/groups/0001/1234> a knora-admin:UserGroup ;
    knora-admin:groupName ?n0 ;
    knora-admin:groupDescriptions ?n1 ;
    knora-admin:status ?n2 ;
    knora-admin:hasSelfJoinEnabled ?n3 ;
    knora-admin:belongsToProject ?n4 . }
WHERE { GRAPH <http://www.knora.org/data/admin> { <http://rdfh.ch/groups/0001/1234> a knora-admin:UserGroup ;
    knora-admin:groupName ?n0 ;
    knora-admin:groupDescriptions ?n1 ;
    knora-admin:status ?n2 ;
    knora-admin:hasSelfJoinEnabled ?n3 .
OPTIONAL { <http://rdfh.ch/groups/0001/1234> knora-admin:belongsToProject ?n4 . } } }"""
      ZIO.serviceWith[KnoraGroupRepoLive](repo =>
        assertTrue(canonicalQuery(repo.findByIdQuery(grpIri).sparql) == canonicalQuery(legacy)),
      )
    },
    test("group.findByName") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:UserGroup ;
    knora-admin:groupName ?n0 ;
    knora-admin:groupDescriptions ?n1 ;
    knora-admin:status ?n2 ;
    knora-admin:hasSelfJoinEnabled ?n3 ;
    knora-admin:belongsToProject ?n4 . }
WHERE { GRAPH <http://www.knora.org/data/admin> { ?s knora-admin:groupName "group-name" .
?s a knora-admin:UserGroup ;
    knora-admin:groupName ?n0 ;
    knora-admin:groupDescriptions ?n1 ;
    knora-admin:status ?n2 ;
    knora-admin:hasSelfJoinEnabled ?n3 .
OPTIONAL { ?s knora-admin:belongsToProject ?n4 . } } }"""
      ZIO.serviceWith[KnoraGroupRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo.findByPatternQuery(sparql"$s knora-admin:groupName ${Literal.string("group-name")} .").sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("group.findByProjectIri") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:UserGroup ;
    knora-admin:groupName ?n0 ;
    knora-admin:groupDescriptions ?n1 ;
    knora-admin:status ?n2 ;
    knora-admin:hasSelfJoinEnabled ?n3 ;
    knora-admin:belongsToProject ?n4 . }
WHERE { GRAPH <http://www.knora.org/data/admin> { ?s knora-admin:belongsToProject <http://rdfh.ch/projects/1234> .
?s a knora-admin:UserGroup ;
    knora-admin:groupName ?n0 ;
    knora-admin:groupDescriptions ?n1 ;
    knora-admin:status ?n2 ;
    knora-admin:hasSelfJoinEnabled ?n3 .
OPTIONAL { ?s knora-admin:belongsToProject ?n4 . } } }"""
      ZIO.serviceWith[KnoraGroupRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo.findByPatternQuery(sparql"$s knora-admin:belongsToProject $prjIriRdf .").sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("group.save.full") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/admin>
DELETE { <http://rdfh.ch/groups/0001/1234> a knora-admin:UserGroup ;
    knora-admin:groupName ?n0 ;
    knora-admin:groupDescriptions ?n1 ;
    knora-admin:status ?n2 ;
    knora-admin:hasSelfJoinEnabled ?n3 ;
    knora-admin:belongsToProject ?n4 . }
INSERT { <http://rdfh.ch/groups/0001/1234> rdf:type knora-admin:UserGroup ;
    knora-admin:groupName "group-name" ;
    knora-admin:groupDescriptions "A group"@en, "Eine Gruppe"@de ;
    knora-admin:status true ;
    knora-admin:belongsToProject <http://rdfh.ch/projects/1234> ;
    knora-admin:hasSelfJoinEnabled false . }
WHERE { OPTIONAL { <http://rdfh.ch/groups/0001/1234> a knora-admin:UserGroup .
OPTIONAL { <http://rdfh.ch/groups/0001/1234> knora-admin:groupName ?n0 . }
OPTIONAL { <http://rdfh.ch/groups/0001/1234> knora-admin:groupDescriptions ?n1 . }
OPTIONAL { <http://rdfh.ch/groups/0001/1234> knora-admin:status ?n2 . }
OPTIONAL { <http://rdfh.ch/groups/0001/1234> knora-admin:hasSelfJoinEnabled ?n3 . }
OPTIONAL { <http://rdfh.ch/groups/0001/1234> knora-admin:belongsToProject ?n4 . } } }"""
      ZIO.serviceWith[KnoraGroupRepoLive](repo =>
        assertTrue(canonicalUpdate(repo.saveQuery(fullGroup).sparql) == canonicalUpdate(legacy)),
      )
    },
    test("group.save.minimal") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/admin>
DELETE { <http://rdfh.ch/groups/0001/1234> a knora-admin:UserGroup ;
    knora-admin:groupName ?n0 ;
    knora-admin:groupDescriptions ?n1 ;
    knora-admin:status ?n2 ;
    knora-admin:hasSelfJoinEnabled ?n3 ;
    knora-admin:belongsToProject ?n4 . }
INSERT { <http://rdfh.ch/groups/0001/1234> rdf:type knora-admin:UserGroup ;
    knora-admin:groupName "group-name" ;
    knora-admin:groupDescriptions "A group" ;
    knora-admin:status true ;
     ;
    knora-admin:hasSelfJoinEnabled false . }
WHERE { OPTIONAL { <http://rdfh.ch/groups/0001/1234> a knora-admin:UserGroup .
OPTIONAL { <http://rdfh.ch/groups/0001/1234> knora-admin:groupName ?n0 . }
OPTIONAL { <http://rdfh.ch/groups/0001/1234> knora-admin:groupDescriptions ?n1 . }
OPTIONAL { <http://rdfh.ch/groups/0001/1234> knora-admin:status ?n2 . }
OPTIONAL { <http://rdfh.ch/groups/0001/1234> knora-admin:hasSelfJoinEnabled ?n3 . }
OPTIONAL { <http://rdfh.ch/groups/0001/1234> knora-admin:belongsToProject ?n4 . } } }"""
      ZIO.serviceWith[KnoraGroupRepoLive](repo =>
        assertTrue(canonicalUpdate(repo.saveQuery(minimalGroup).sparql) == canonicalUpdate(legacy)),
      )
    },
    test("group.erase") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/admin>
DELETE { <http://rdfh.ch/groups/0001/1234> a knora-admin:UserGroup ;
    ?p ?o . }
WHERE { <http://rdfh.ch/groups/0001/1234> a knora-admin:UserGroup ;
    ?p ?o . }"""
      ZIO.serviceWith[KnoraGroupRepoLive](repo =>
        assertTrue(canonicalUpdate(repo.eraseQuery(fullGroup).sparql) == canonicalUpdate(legacy)),
      )
    },
  )

  private val projectSuite = suite("project")(
    test("project.findAll") {
      val legacy =
        """CONSTRUCT { ?s ?p ?o . }
WHERE { GRAPH <http://www.knora.org/data/admin> { ?s a <http://www.knora.org/ontology/knora-admin#knoraProject> ;
    ?p ?o . } }"""
      ZIO.serviceWith[KnoraProjectRepoLive](repo =>
        assertTrue(canonicalQuery(repo.findAllQuery.sparql) == canonicalQuery(legacy)),
      )
    },
    test("project.findById") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { <http://rdfh.ch/projects/1234> a knora-admin:knoraProject ;
    knora-admin:hasSelfJoinEnabled ?n0 ;
    knora-admin:projectDescription ?n1 ;
    knora-admin:projectShortcode ?n2 ;
    knora-admin:projectShortname ?n3 ;
    knora-admin:projectKeyword ?n4 ;
    knora-admin:projectLogo ?n5 ;
    knora-admin:projectLongname ?n6 ;
    knora-admin:projectRestrictedViewSize ?n7 ;
    knora-admin:projectRestrictedViewWatermark ?n8 ;
    knora-admin:hasAllowedCopyrightHolder ?n9 ;
    knora-admin:hasEnabledLicense ?n10 ;
    knora-admin:hasDataLicense ?n11 ;
    knora-admin:hasDataCopyrightHolder ?n12 ;
    knora-admin:hasDefaultDataAuthorship ?n13 . }
WHERE { GRAPH <http://www.knora.org/data/admin> { <http://rdfh.ch/projects/1234> a knora-admin:knoraProject ;
    knora-admin:hasSelfJoinEnabled ?n0 ;
    knora-admin:projectDescription ?n1 ;
    knora-admin:projectShortcode ?n2 ;
    knora-admin:projectShortname ?n3 .
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectKeyword ?n4 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectLogo ?n5 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectLongname ?n6 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectRestrictedViewSize ?n7 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectRestrictedViewWatermark ?n8 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasAllowedCopyrightHolder ?n9 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasEnabledLicense ?n10 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasDataLicense ?n11 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasDataCopyrightHolder ?n12 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasDefaultDataAuthorship ?n13 . } } }"""
      ZIO.serviceWith[KnoraProjectRepoLive](repo =>
        assertTrue(canonicalQuery(repo.findByIdQuery(prjIri).sparql) == canonicalQuery(legacy)),
      )
    },
    test("project.findByShortcode") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:knoraProject ;
    knora-admin:hasSelfJoinEnabled ?n0 ;
    knora-admin:projectDescription ?n1 ;
    knora-admin:projectShortcode ?n2 ;
    knora-admin:projectShortname ?n3 ;
    knora-admin:projectKeyword ?n4 ;
    knora-admin:projectLogo ?n5 ;
    knora-admin:projectLongname ?n6 ;
    knora-admin:projectRestrictedViewSize ?n7 ;
    knora-admin:projectRestrictedViewWatermark ?n8 ;
    knora-admin:hasAllowedCopyrightHolder ?n9 ;
    knora-admin:hasEnabledLicense ?n10 ;
    knora-admin:hasDataLicense ?n11 ;
    knora-admin:hasDataCopyrightHolder ?n12 ;
    knora-admin:hasDefaultDataAuthorship ?n13 . }
WHERE { GRAPH <http://www.knora.org/data/admin> { ?s knora-admin:projectShortcode "1234" .
?s a knora-admin:knoraProject ;
    knora-admin:hasSelfJoinEnabled ?n0 ;
    knora-admin:projectDescription ?n1 ;
    knora-admin:projectShortcode ?n2 ;
    knora-admin:projectShortname ?n3 .
OPTIONAL { ?s knora-admin:projectKeyword ?n4 . }
OPTIONAL { ?s knora-admin:projectLogo ?n5 . }
OPTIONAL { ?s knora-admin:projectLongname ?n6 . }
OPTIONAL { ?s knora-admin:projectRestrictedViewSize ?n7 . }
OPTIONAL { ?s knora-admin:projectRestrictedViewWatermark ?n8 . }
OPTIONAL { ?s knora-admin:hasAllowedCopyrightHolder ?n9 . }
OPTIONAL { ?s knora-admin:hasEnabledLicense ?n10 . }
OPTIONAL { ?s knora-admin:hasDataLicense ?n11 . }
OPTIONAL { ?s knora-admin:hasDataCopyrightHolder ?n12 . }
OPTIONAL { ?s knora-admin:hasDefaultDataAuthorship ?n13 . } } }"""
      ZIO.serviceWith[KnoraProjectRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo.findByPatternQuery(sparql"$s knora-admin:projectShortcode ${Literal.string("1234")} .").sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("project.findByShortname") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:knoraProject ;
    knora-admin:hasSelfJoinEnabled ?n0 ;
    knora-admin:projectDescription ?n1 ;
    knora-admin:projectShortcode ?n2 ;
    knora-admin:projectShortname ?n3 ;
    knora-admin:projectKeyword ?n4 ;
    knora-admin:projectLogo ?n5 ;
    knora-admin:projectLongname ?n6 ;
    knora-admin:projectRestrictedViewSize ?n7 ;
    knora-admin:projectRestrictedViewWatermark ?n8 ;
    knora-admin:hasAllowedCopyrightHolder ?n9 ;
    knora-admin:hasEnabledLicense ?n10 ;
    knora-admin:hasDataLicense ?n11 ;
    knora-admin:hasDataCopyrightHolder ?n12 ;
    knora-admin:hasDefaultDataAuthorship ?n13 . }
WHERE { GRAPH <http://www.knora.org/data/admin> { ?s knora-admin:projectShortname "project1" .
?s a knora-admin:knoraProject ;
    knora-admin:hasSelfJoinEnabled ?n0 ;
    knora-admin:projectDescription ?n1 ;
    knora-admin:projectShortcode ?n2 ;
    knora-admin:projectShortname ?n3 .
OPTIONAL { ?s knora-admin:projectKeyword ?n4 . }
OPTIONAL { ?s knora-admin:projectLogo ?n5 . }
OPTIONAL { ?s knora-admin:projectLongname ?n6 . }
OPTIONAL { ?s knora-admin:projectRestrictedViewSize ?n7 . }
OPTIONAL { ?s knora-admin:projectRestrictedViewWatermark ?n8 . }
OPTIONAL { ?s knora-admin:hasAllowedCopyrightHolder ?n9 . }
OPTIONAL { ?s knora-admin:hasEnabledLicense ?n10 . }
OPTIONAL { ?s knora-admin:hasDataLicense ?n11 . }
OPTIONAL { ?s knora-admin:hasDataCopyrightHolder ?n12 . }
OPTIONAL { ?s knora-admin:hasDefaultDataAuthorship ?n13 . } } }"""
      ZIO.serviceWith[KnoraProjectRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo.findByPatternQuery(sparql"$s knora-admin:projectShortname ${Literal.string("project1")} .").sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("project.save.full") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/admin>
DELETE { <http://rdfh.ch/projects/1234> a knora-admin:knoraProject ;
    knora-admin:hasSelfJoinEnabled ?n0 ;
    knora-admin:projectDescription ?n1 ;
    knora-admin:projectShortcode ?n2 ;
    knora-admin:projectShortname ?n3 ;
    knora-admin:projectKeyword ?n4 ;
    knora-admin:projectLogo ?n5 ;
    knora-admin:projectLongname ?n6 ;
    knora-admin:projectRestrictedViewSize ?n7 ;
    knora-admin:projectRestrictedViewWatermark ?n8 ;
    knora-admin:hasAllowedCopyrightHolder ?n9 ;
    knora-admin:hasEnabledLicense ?n10 ;
    knora-admin:hasDataLicense ?n11 ;
    knora-admin:hasDataCopyrightHolder ?n12 ;
    knora-admin:hasDefaultDataAuthorship ?n13 . }
INSERT { <http://rdfh.ch/projects/1234> rdf:type knora-admin:knoraProject ;
    knora-admin:projectShortname "project1" ;
    knora-admin:projectShortcode "1234" ;
    knora-admin:hasSelfJoinEnabled false ;
    knora-admin:projectLongname "Project 1" ;
    knora-admin:projectDescription "A project"@en ;
    knora-admin:projectDescription "Ein Projekt"@de ;
    knora-admin:projectKeyword "kw1" ;
    knora-admin:projectKeyword "kw2" ;
    knora-admin:projectLogo "logo.png" ;
    knora-admin:projectRestrictedViewSize "!128,128" ;
    knora-admin:hasAllowedCopyrightHolder "foo" ;
    knora-admin:hasAllowedCopyrightHolder "bar" ;
    knora-admin:hasEnabledLicense <http://rdfh.ch/licenses/cc-by-4.0> ;
    knora-admin:hasDataLicense <http://rdfh.ch/licenses/cc-by-4.0> ;
    knora-admin:hasDataCopyrightHolder "University of Basel" ;
    knora-admin:hasDefaultDataAuthorship "Hilma af Klint" ;
    knora-admin:hasDefaultDataAuthorship "Lotte Reiniger" . }
WHERE { OPTIONAL { <http://rdfh.ch/projects/1234> a knora-admin:knoraProject .
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasSelfJoinEnabled ?n0 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectDescription ?n1 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectShortcode ?n2 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectShortname ?n3 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectKeyword ?n4 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectLogo ?n5 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectLongname ?n6 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectRestrictedViewSize ?n7 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectRestrictedViewWatermark ?n8 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasAllowedCopyrightHolder ?n9 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasEnabledLicense ?n10 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasDataLicense ?n11 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasDataCopyrightHolder ?n12 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasDefaultDataAuthorship ?n13 . } } }"""
      ZIO.serviceWith[KnoraProjectRepoLive](repo =>
        assertTrue(canonicalUpdate(repo.saveQuery(fullProject).sparql) == canonicalUpdate(legacy)),
      )
    },
    test("project.save.minimal") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/admin>
DELETE { <http://rdfh.ch/projects/1234> a knora-admin:knoraProject ;
    knora-admin:hasSelfJoinEnabled ?n0 ;
    knora-admin:projectDescription ?n1 ;
    knora-admin:projectShortcode ?n2 ;
    knora-admin:projectShortname ?n3 ;
    knora-admin:projectKeyword ?n4 ;
    knora-admin:projectLogo ?n5 ;
    knora-admin:projectLongname ?n6 ;
    knora-admin:projectRestrictedViewSize ?n7 ;
    knora-admin:projectRestrictedViewWatermark ?n8 ;
    knora-admin:hasAllowedCopyrightHolder ?n9 ;
    knora-admin:hasEnabledLicense ?n10 ;
    knora-admin:hasDataLicense ?n11 ;
    knora-admin:hasDataCopyrightHolder ?n12 ;
    knora-admin:hasDefaultDataAuthorship ?n13 . }
INSERT { <http://rdfh.ch/projects/1234> rdf:type knora-admin:knoraProject ;
    knora-admin:projectShortname "project1" ;
    knora-admin:projectShortcode "1234" ;
    knora-admin:hasSelfJoinEnabled true ;
    knora-admin:projectDescription "A project" ;
    knora-admin:projectRestrictedViewWatermark true . }
WHERE { OPTIONAL { <http://rdfh.ch/projects/1234> a knora-admin:knoraProject .
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasSelfJoinEnabled ?n0 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectDescription ?n1 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectShortcode ?n2 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectShortname ?n3 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectKeyword ?n4 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectLogo ?n5 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectLongname ?n6 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectRestrictedViewSize ?n7 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:projectRestrictedViewWatermark ?n8 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasAllowedCopyrightHolder ?n9 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasEnabledLicense ?n10 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasDataLicense ?n11 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasDataCopyrightHolder ?n12 . }
OPTIONAL { <http://rdfh.ch/projects/1234> knora-admin:hasDefaultDataAuthorship ?n13 . } } }"""
      ZIO.serviceWith[KnoraProjectRepoLive](repo =>
        assertTrue(canonicalUpdate(repo.saveQuery(minimalProject).sparql) == canonicalUpdate(legacy)),
      )
    },
    test("project.erase") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/admin>
DELETE { <http://rdfh.ch/projects/1234> a knora-admin:knoraProject ;
    ?p ?o . }
WHERE { <http://rdfh.ch/projects/1234> a knora-admin:knoraProject ;
    ?p ?o . }"""
      ZIO.serviceWith[KnoraProjectRepoLive](repo =>
        assertTrue(canonicalUpdate(repo.eraseQuery(fullProject).sparql) == canonicalUpdate(legacy)),
      )
    },
  )

  private val userSuite = suite("user")(
    test("user.findAll") {
      val legacy =
        """CONSTRUCT { ?s ?p ?o . }
WHERE { GRAPH <http://www.knora.org/data/admin> { ?s a <http://www.knora.org/ontology/knora-admin#User> ;
    ?p ?o . } }"""
      ZIO.serviceWith[KnoraUserRepoLive](repo =>
        assertTrue(canonicalQuery(repo.findAllQuery.sparql) == canonicalQuery(legacy)),
      )
    },
    test("user.findById") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { <http://rdfh.ch/users/1234> a knora-admin:User ;
    knora-admin:username ?n0 ;
    knora-admin:email ?n1 ;
    knora-admin:givenName ?n2 ;
    knora-admin:familyName ?n3 ;
    knora-admin:status ?n4 ;
    knora-admin:preferredLanguage ?n5 ;
    knora-admin:password ?n6 ;
    knora-admin:isInSystemAdminGroup ?n7 ;
    knora-admin:isInProject ?n8 ;
    knora-admin:isInGroup ?n9 ;
    knora-admin:isInProjectAdminGroup ?n10 . }
WHERE { GRAPH <http://www.knora.org/data/admin> { <http://rdfh.ch/users/1234> a knora-admin:User ;
    knora-admin:username ?n0 ;
    knora-admin:email ?n1 ;
    knora-admin:givenName ?n2 ;
    knora-admin:familyName ?n3 ;
    knora-admin:status ?n4 ;
    knora-admin:preferredLanguage ?n5 ;
    knora-admin:password ?n6 .
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:isInSystemAdminGroup ?n7 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:isInProject ?n8 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:isInGroup ?n9 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:isInProjectAdminGroup ?n10 . } } }"""
      ZIO.serviceWith[KnoraUserRepoLive](repo =>
        assertTrue(canonicalQuery(repo.findByIdQuery(fullUser.id).sparql) == canonicalQuery(legacy)),
      )
    },
    test("user.findByEmail") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:User ;
    knora-admin:username ?n0 ;
    knora-admin:email ?n1 ;
    knora-admin:givenName ?n2 ;
    knora-admin:familyName ?n3 ;
    knora-admin:status ?n4 ;
    knora-admin:preferredLanguage ?n5 ;
    knora-admin:password ?n6 ;
    knora-admin:isInSystemAdminGroup ?n7 ;
    knora-admin:isInProject ?n8 ;
    knora-admin:isInGroup ?n9 ;
    knora-admin:isInProjectAdminGroup ?n10 . }
WHERE { GRAPH <http://www.knora.org/data/admin> { ?s knora-admin:email "user@example.com" .
?s a knora-admin:User ;
    knora-admin:username ?n0 ;
    knora-admin:email ?n1 ;
    knora-admin:givenName ?n2 ;
    knora-admin:familyName ?n3 ;
    knora-admin:status ?n4 ;
    knora-admin:preferredLanguage ?n5 ;
    knora-admin:password ?n6 .
OPTIONAL { ?s knora-admin:isInSystemAdminGroup ?n7 . }
OPTIONAL { ?s knora-admin:isInProject ?n8 . }
OPTIONAL { ?s knora-admin:isInGroup ?n9 . }
OPTIONAL { ?s knora-admin:isInProjectAdminGroup ?n10 . } } }"""
      ZIO.serviceWith[KnoraUserRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo.findByPatternQuery(sparql"$s knora-admin:email ${Literal.string("user@example.com")} .").sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("user.findByUsername") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:User ;
    knora-admin:username ?n0 ;
    knora-admin:email ?n1 ;
    knora-admin:givenName ?n2 ;
    knora-admin:familyName ?n3 ;
    knora-admin:status ?n4 ;
    knora-admin:preferredLanguage ?n5 ;
    knora-admin:password ?n6 ;
    knora-admin:isInSystemAdminGroup ?n7 ;
    knora-admin:isInProject ?n8 ;
    knora-admin:isInGroup ?n9 ;
    knora-admin:isInProjectAdminGroup ?n10 . }
WHERE { GRAPH <http://www.knora.org/data/admin> { ?s knora-admin:username "username" .
?s a knora-admin:User ;
    knora-admin:username ?n0 ;
    knora-admin:email ?n1 ;
    knora-admin:givenName ?n2 ;
    knora-admin:familyName ?n3 ;
    knora-admin:status ?n4 ;
    knora-admin:preferredLanguage ?n5 ;
    knora-admin:password ?n6 .
OPTIONAL { ?s knora-admin:isInSystemAdminGroup ?n7 . }
OPTIONAL { ?s knora-admin:isInProject ?n8 . }
OPTIONAL { ?s knora-admin:isInGroup ?n9 . }
OPTIONAL { ?s knora-admin:isInProjectAdminGroup ?n10 . } } }"""
      ZIO.serviceWith[KnoraUserRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo.findByPatternQuery(sparql"$s knora-admin:username ${Literal.string("username")} .").sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("user.findByProjectMembership") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:User ;
    knora-admin:username ?n0 ;
    knora-admin:email ?n1 ;
    knora-admin:givenName ?n2 ;
    knora-admin:familyName ?n3 ;
    knora-admin:status ?n4 ;
    knora-admin:preferredLanguage ?n5 ;
    knora-admin:password ?n6 ;
    knora-admin:isInSystemAdminGroup ?n7 ;
    knora-admin:isInProject ?n8 ;
    knora-admin:isInGroup ?n9 ;
    knora-admin:isInProjectAdminGroup ?n10 . }
WHERE { GRAPH <http://www.knora.org/data/admin> { ?s knora-admin:isInProject <http://rdfh.ch/projects/1234> .
?s a knora-admin:User ;
    knora-admin:username ?n0 ;
    knora-admin:email ?n1 ;
    knora-admin:givenName ?n2 ;
    knora-admin:familyName ?n3 ;
    knora-admin:status ?n4 ;
    knora-admin:preferredLanguage ?n5 ;
    knora-admin:password ?n6 .
OPTIONAL { ?s knora-admin:isInSystemAdminGroup ?n7 . }
OPTIONAL { ?s knora-admin:isInProject ?n8 . }
OPTIONAL { ?s knora-admin:isInGroup ?n9 . }
OPTIONAL { ?s knora-admin:isInProjectAdminGroup ?n10 . } } }"""
      ZIO.serviceWith[KnoraUserRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo.findByPatternQuery(sparql"$s knora-admin:isInProject $prjIriRdf .").sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("user.findByProjectAdminMembership") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:User ;
    knora-admin:username ?n0 ;
    knora-admin:email ?n1 ;
    knora-admin:givenName ?n2 ;
    knora-admin:familyName ?n3 ;
    knora-admin:status ?n4 ;
    knora-admin:preferredLanguage ?n5 ;
    knora-admin:password ?n6 ;
    knora-admin:isInSystemAdminGroup ?n7 ;
    knora-admin:isInProject ?n8 ;
    knora-admin:isInGroup ?n9 ;
    knora-admin:isInProjectAdminGroup ?n10 . }
WHERE { GRAPH <http://www.knora.org/data/admin> { ?s knora-admin:isInProjectAdminGroup <http://rdfh.ch/projects/1234> .
?s a knora-admin:User ;
    knora-admin:username ?n0 ;
    knora-admin:email ?n1 ;
    knora-admin:givenName ?n2 ;
    knora-admin:familyName ?n3 ;
    knora-admin:status ?n4 ;
    knora-admin:preferredLanguage ?n5 ;
    knora-admin:password ?n6 .
OPTIONAL { ?s knora-admin:isInSystemAdminGroup ?n7 . }
OPTIONAL { ?s knora-admin:isInProject ?n8 . }
OPTIONAL { ?s knora-admin:isInGroup ?n9 . }
OPTIONAL { ?s knora-admin:isInProjectAdminGroup ?n10 . } } }"""
      ZIO.serviceWith[KnoraUserRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo.findByPatternQuery(sparql"$s knora-admin:isInProjectAdminGroup $prjIriRdf .").sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("user.findByGroupMembership") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:User ;
    knora-admin:username ?n0 ;
    knora-admin:email ?n1 ;
    knora-admin:givenName ?n2 ;
    knora-admin:familyName ?n3 ;
    knora-admin:status ?n4 ;
    knora-admin:preferredLanguage ?n5 ;
    knora-admin:password ?n6 ;
    knora-admin:isInSystemAdminGroup ?n7 ;
    knora-admin:isInProject ?n8 ;
    knora-admin:isInGroup ?n9 ;
    knora-admin:isInProjectAdminGroup ?n10 . }
WHERE { GRAPH <http://www.knora.org/data/admin> { ?s knora-admin:isInGroup <http://rdfh.ch/groups/0001/1234> .
?s a knora-admin:User ;
    knora-admin:username ?n0 ;
    knora-admin:email ?n1 ;
    knora-admin:givenName ?n2 ;
    knora-admin:familyName ?n3 ;
    knora-admin:status ?n4 ;
    knora-admin:preferredLanguage ?n5 ;
    knora-admin:password ?n6 .
OPTIONAL { ?s knora-admin:isInSystemAdminGroup ?n7 . }
OPTIONAL { ?s knora-admin:isInProject ?n8 . }
OPTIONAL { ?s knora-admin:isInGroup ?n9 . }
OPTIONAL { ?s knora-admin:isInProjectAdminGroup ?n10 . } } }"""
      ZIO.serviceWith[KnoraUserRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo.findByPatternQuery(sparql"$s knora-admin:isInGroup $grpIriRdf .").sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("user.save.full") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/admin>
DELETE { <http://rdfh.ch/users/1234> a knora-admin:User ;
    knora-admin:username ?n0 ;
    knora-admin:email ?n1 ;
    knora-admin:givenName ?n2 ;
    knora-admin:familyName ?n3 ;
    knora-admin:status ?n4 ;
    knora-admin:preferredLanguage ?n5 ;
    knora-admin:password ?n6 ;
    knora-admin:isInSystemAdminGroup ?n7 ;
    knora-admin:isInProject ?n8 ;
    knora-admin:isInGroup ?n9 ;
    knora-admin:isInProjectAdminGroup ?n10 . }
INSERT { <http://rdfh.ch/users/1234> a knora-admin:User ;
    knora-admin:username "username" ;
    knora-admin:email "user@example.com" ;
    knora-admin:givenName "Given" ;
    knora-admin:familyName "Family" ;
    knora-admin:preferredLanguage "en" ;
    knora-admin:status true ;
    knora-admin:password "hash" ;
    knora-admin:isInSystemAdminGroup false ;
    knora-admin:isInProject <http://rdfh.ch/projects/1234>, <http://rdfh.ch/projects/5678> ;
    knora-admin:isInGroup <http://rdfh.ch/groups/0001/1234>, <http://rdfh.ch/groups/0001/5678> ;
    knora-admin:isInProjectAdminGroup <http://rdfh.ch/projects/1234>, <http://rdfh.ch/projects/5678> . }
WHERE { OPTIONAL { <http://rdfh.ch/users/1234> a knora-admin:User .
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:username ?n0 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:email ?n1 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:givenName ?n2 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:familyName ?n3 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:status ?n4 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:preferredLanguage ?n5 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:password ?n6 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:isInSystemAdminGroup ?n7 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:isInProject ?n8 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:isInGroup ?n9 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:isInProjectAdminGroup ?n10 . } } }"""
      ZIO.serviceWith[KnoraUserRepoLive](repo =>
        assertTrue(canonicalUpdate(repo.saveQuery(fullUser).sparql) == canonicalUpdate(legacy)),
      )
    },
    test("user.save.minimal") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/admin>
DELETE { <http://rdfh.ch/users/1234> a knora-admin:User ;
    knora-admin:username ?n0 ;
    knora-admin:email ?n1 ;
    knora-admin:givenName ?n2 ;
    knora-admin:familyName ?n3 ;
    knora-admin:status ?n4 ;
    knora-admin:preferredLanguage ?n5 ;
    knora-admin:password ?n6 ;
    knora-admin:isInSystemAdminGroup ?n7 ;
    knora-admin:isInProject ?n8 ;
    knora-admin:isInGroup ?n9 ;
    knora-admin:isInProjectAdminGroup ?n10 . }
INSERT { <http://rdfh.ch/users/1234> a knora-admin:User ;
    knora-admin:username "username" ;
    knora-admin:email "user@example.com" ;
    knora-admin:givenName "Given" ;
    knora-admin:familyName "Family" ;
    knora-admin:preferredLanguage "en" ;
    knora-admin:status true ;
    knora-admin:password "hash" ;
    knora-admin:isInSystemAdminGroup false ;
     ;
     ;
     . }
WHERE { OPTIONAL { <http://rdfh.ch/users/1234> a knora-admin:User .
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:username ?n0 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:email ?n1 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:givenName ?n2 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:familyName ?n3 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:status ?n4 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:preferredLanguage ?n5 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:password ?n6 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:isInSystemAdminGroup ?n7 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:isInProject ?n8 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:isInGroup ?n9 . }
OPTIONAL { <http://rdfh.ch/users/1234> knora-admin:isInProjectAdminGroup ?n10 . } } }"""
      ZIO.serviceWith[KnoraUserRepoLive](repo =>
        assertTrue(canonicalUpdate(repo.saveQuery(minimalUser).sparql) == canonicalUpdate(legacy)),
      )
    },
    test("user.erase") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/admin>
DELETE { <http://rdfh.ch/users/1234> a knora-admin:User ;
    ?p ?o . }
WHERE { <http://rdfh.ch/users/1234> a knora-admin:User ;
    ?p ?o . }"""
      ZIO.serviceWith[KnoraUserRepoLive](repo =>
        assertTrue(canonicalUpdate(repo.eraseQuery(fullUser).sparql) == canonicalUpdate(legacy)),
      )
    },
  )

  private val apSuite = suite("ap")(
    test("ap.findAll") {
      val legacy =
        """CONSTRUCT { ?s ?p ?o . }
WHERE { GRAPH <http://www.knora.org/data/permissions> { ?s a <http://www.knora.org/ontology/knora-admin#AdministrativePermission> ;
    ?p ?o . } }"""
      ZIO.serviceWith[AdministrativePermissionRepoLive](repo =>
        assertTrue(canonicalQuery(repo.findAllQuery.sparql) == canonicalQuery(legacy)),
      )
    },
    test("ap.findById") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { <http://rdfh.ch/permissions/0001/1234> a knora-admin:AdministrativePermission ;
    knora-base:hasPermissions ?n0 ;
    knora-admin:forGroup ?n1 ;
    knora-admin:forProject ?n2 . }
WHERE { GRAPH <http://www.knora.org/data/permissions> { <http://rdfh.ch/permissions/0001/1234> a knora-admin:AdministrativePermission ;
    knora-base:hasPermissions ?n0 ;
    knora-admin:forGroup ?n1 ;
    knora-admin:forProject ?n2 . } }"""
      ZIO.serviceWith[AdministrativePermissionRepoLive](repo =>
        assertTrue(canonicalQuery(repo.findByIdQuery(permIri).sparql) == canonicalQuery(legacy)),
      )
    },
    test("ap.findByGroupAndProject") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:AdministrativePermission ;
    knora-base:hasPermissions ?n0 ;
    knora-admin:forGroup ?n1 ;
    knora-admin:forProject ?n2 . }
WHERE { GRAPH <http://www.knora.org/data/permissions> { ?s knora-admin:forGroup <http://rdfh.ch/groups/0001/1234> ;
    knora-admin:forProject <http://rdfh.ch/projects/1234> .
?s a knora-admin:AdministrativePermission ;
    knora-base:hasPermissions ?n0 ;
    knora-admin:forGroup ?n1 ;
    knora-admin:forProject ?n2 . } }"""
      ZIO.serviceWith[AdministrativePermissionRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo
              .findByPatternQuery(sparql"$s knora-admin:forGroup $grpIriRdf ; knora-admin:forProject $prjIriRdf .")
              .sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("ap.findByProject") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:AdministrativePermission ;
    knora-base:hasPermissions ?n0 ;
    knora-admin:forGroup ?n1 ;
    knora-admin:forProject ?n2 . }
WHERE { GRAPH <http://www.knora.org/data/permissions> { ?s knora-admin:forProject <http://rdfh.ch/projects/1234> .
?s a knora-admin:AdministrativePermission ;
    knora-base:hasPermissions ?n0 ;
    knora-admin:forGroup ?n1 ;
    knora-admin:forProject ?n2 . } }"""
      ZIO.serviceWith[AdministrativePermissionRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo.findByPatternQuery(sparql"$s knora-admin:forProject $prjIriRdf .").sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("ap.save") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/permissions>
DELETE { <http://rdfh.ch/permissions/0001/1234> a knora-admin:AdministrativePermission ;
    <http://www.knora.org/ontology/knora-base#hasPermissions> ?n0 ;
    knora-admin:forGroup ?n1 ;
    knora-admin:forProject ?n2 . }
INSERT { <http://rdfh.ch/permissions/0001/1234> a knora-admin:AdministrativePermission ;
    knora-admin:forGroup <http://rdfh.ch/groups/0001/1234> ;
    knora-admin:forProject <http://rdfh.ch/projects/1234> ;
    <http://www.knora.org/ontology/knora-base#hasPermissions> "ProjectResourceCreateAllPermission" . }
WHERE { OPTIONAL { <http://rdfh.ch/permissions/0001/1234> a knora-admin:AdministrativePermission .
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> <http://www.knora.org/ontology/knora-base#hasPermissions> ?n0 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forGroup ?n1 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forProject ?n2 . } } }"""
      ZIO.serviceWith[AdministrativePermissionRepoLive](repo =>
        assertTrue(canonicalUpdate(repo.saveQuery(ap).sparql) == canonicalUpdate(legacy)),
      )
    },
    test("ap.erase") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/permissions>
DELETE { <http://rdfh.ch/permissions/0001/1234> a knora-admin:AdministrativePermission ;
    ?p ?o . }
WHERE { <http://rdfh.ch/permissions/0001/1234> a knora-admin:AdministrativePermission ;
    ?p ?o . }"""
      ZIO.serviceWith[AdministrativePermissionRepoLive](repo =>
        assertTrue(canonicalUpdate(repo.eraseQuery(ap).sparql) == canonicalUpdate(legacy)),
      )
    },
  )

  private val doapSuite = suite("doap")(
    test("doap.findAll") {
      val legacy =
        """CONSTRUCT { ?s ?p ?o . }
WHERE { GRAPH <http://www.knora.org/data/permissions> { ?s a <http://www.knora.org/ontology/knora-admin#DefaultObjectAccessPermission> ;
    ?p ?o . } }"""
      ZIO.serviceWith[DefaultObjectAccessPermissionRepoLive](repo =>
        assertTrue(canonicalQuery(repo.findAllQuery.sparql) == canonicalQuery(legacy)),
      )
    },
    test("doap.findById") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    knora-base:hasPermissions ?n1 ;
    knora-admin:forGroup ?n2 ;
    knora-admin:forProperty ?n3 ;
    knora-admin:forResourceClass ?n4 . }
WHERE { GRAPH <http://www.knora.org/data/permissions> { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    knora-base:hasPermissions ?n1 .
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forGroup ?n2 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forProperty ?n3 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forResourceClass ?n4 . } } }"""
      ZIO.serviceWith[DefaultObjectAccessPermissionRepoLive](repo =>
        assertTrue(canonicalQuery(repo.findByIdQuery(permIri).sparql) == canonicalQuery(legacy)),
      )
    },
    test("doap.findByProject") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    knora-base:hasPermissions ?n1 ;
    knora-admin:forGroup ?n2 ;
    knora-admin:forProperty ?n3 ;
    knora-admin:forResourceClass ?n4 . }
WHERE { GRAPH <http://www.knora.org/data/permissions> { ?s knora-admin:forProject <http://rdfh.ch/projects/1234> .
?s a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    knora-base:hasPermissions ?n1 .
OPTIONAL { ?s knora-admin:forGroup ?n2 . }
OPTIONAL { ?s knora-admin:forProperty ?n3 . }
OPTIONAL { ?s knora-admin:forResourceClass ?n4 . } } }"""
      ZIO.serviceWith[DefaultObjectAccessPermissionRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo.findByPatternQuery(sparql"$s knora-admin:forProject $prjIriRdf .").sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("doap.forWhat.group") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    knora-base:hasPermissions ?n1 ;
    knora-admin:forGroup ?n2 ;
    knora-admin:forProperty ?n3 ;
    knora-admin:forResourceClass ?n4 . }
WHERE { GRAPH <http://www.knora.org/data/permissions> { ?s knora-admin:forProject <http://rdfh.ch/projects/1234> ;
    knora-admin:forGroup <http://rdfh.ch/groups/0001/1234> .
?s a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    knora-base:hasPermissions ?n1 .
OPTIONAL { ?s knora-admin:forGroup ?n2 . }
OPTIONAL { ?s knora-admin:forProperty ?n3 . }
OPTIONAL { ?s knora-admin:forResourceClass ?n4 . } } }"""
      ZIO.serviceWith[DefaultObjectAccessPermissionRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo.findByPatternQuery(repo.forWhatPattern(prjIri, ForWhat.Group(grpIri))).sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("doap.forWhat.resourceClass") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    knora-base:hasPermissions ?n1 ;
    knora-admin:forGroup ?n2 ;
    knora-admin:forProperty ?n3 ;
    knora-admin:forResourceClass ?n4 . }
WHERE { GRAPH <http://www.knora.org/data/permissions> { ?s knora-admin:forProject <http://rdfh.ch/projects/1234> ;
    knora-admin:forResourceClass <https://example.com/rc> .
FILTER NOT EXISTS { ?s knora-admin:forProperty ?prop . }
?s a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    knora-base:hasPermissions ?n1 .
OPTIONAL { ?s knora-admin:forGroup ?n2 . }
OPTIONAL { ?s knora-admin:forProperty ?n3 . }
OPTIONAL { ?s knora-admin:forResourceClass ?n4 . } } }"""
      ZIO.serviceWith[DefaultObjectAccessPermissionRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo.findByPatternQuery(repo.forWhatPattern(prjIri, ForWhat.ResourceClass(rcIri))).sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("doap.forWhat.property") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    knora-base:hasPermissions ?n1 ;
    knora-admin:forGroup ?n2 ;
    knora-admin:forProperty ?n3 ;
    knora-admin:forResourceClass ?n4 . }
WHERE { GRAPH <http://www.knora.org/data/permissions> { ?s knora-admin:forProject <http://rdfh.ch/projects/1234> ;
    knora-admin:forProperty <https://example.com/p> .
FILTER NOT EXISTS { ?s knora-admin:forResourceClass ?rc . }
?s a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    knora-base:hasPermissions ?n1 .
OPTIONAL { ?s knora-admin:forGroup ?n2 . }
OPTIONAL { ?s knora-admin:forProperty ?n3 . }
OPTIONAL { ?s knora-admin:forResourceClass ?n4 . } } }"""
      ZIO.serviceWith[DefaultObjectAccessPermissionRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo.findByPatternQuery(repo.forWhatPattern(prjIri, ForWhat.Property(propIri))).sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("doap.forWhat.resourceClassAndProperty") {
      val legacy =
        """PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
CONSTRUCT { ?s a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    knora-base:hasPermissions ?n1 ;
    knora-admin:forGroup ?n2 ;
    knora-admin:forProperty ?n3 ;
    knora-admin:forResourceClass ?n4 . }
WHERE { GRAPH <http://www.knora.org/data/permissions> { ?s knora-admin:forProject <http://rdfh.ch/projects/1234> ;
    knora-admin:forResourceClass <https://example.com/rc> ;
    knora-admin:forProperty <https://example.com/p> .
?s a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    knora-base:hasPermissions ?n1 .
OPTIONAL { ?s knora-admin:forGroup ?n2 . }
OPTIONAL { ?s knora-admin:forProperty ?n3 . }
OPTIONAL { ?s knora-admin:forResourceClass ?n4 . } } }"""
      ZIO.serviceWith[DefaultObjectAccessPermissionRepoLive](repo =>
        assertTrue(
          canonicalQuery(
            repo
              .findByPatternQuery(repo.forWhatPattern(prjIri, ForWhat.ResourceClassAndProperty(rcIri, propIri)))
              .sparql,
          ) == canonicalQuery(legacy),
        ),
      )
    },
    test("doap.save.group") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/permissions>
DELETE { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    <http://www.knora.org/ontology/knora-base#hasPermissions> ?n1 ;
    knora-admin:forGroup ?n2 ;
    knora-admin:forProperty ?n3 ;
    knora-admin:forResourceClass ?n4 . }
INSERT { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject <http://rdfh.ch/projects/1234> ;
    <http://www.knora.org/ontology/knora-base#hasPermissions> "V http://rdfh.ch/groups/0001/1234" ;
    knora-admin:forGroup <http://rdfh.ch/groups/0001/1234> . }
WHERE { OPTIONAL { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission .
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forProject ?n0 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> <http://www.knora.org/ontology/knora-base#hasPermissions> ?n1 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forGroup ?n2 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forProperty ?n3 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forResourceClass ?n4 . } } }"""
      ZIO.serviceWith[DefaultObjectAccessPermissionRepoLive](repo =>
        assertTrue(canonicalUpdate(repo.saveQuery(doap(ForWhat.Group(grpIri))).sparql) == canonicalUpdate(legacy)),
      )
    },
    test("doap.save.resourceClass") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/permissions>
DELETE { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    <http://www.knora.org/ontology/knora-base#hasPermissions> ?n1 ;
    knora-admin:forGroup ?n2 ;
    knora-admin:forProperty ?n3 ;
    knora-admin:forResourceClass ?n4 . }
INSERT { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject <http://rdfh.ch/projects/1234> ;
    <http://www.knora.org/ontology/knora-base#hasPermissions> "V http://rdfh.ch/groups/0001/1234" ;
    knora-admin:forResourceClass <https://example.com/rc> . }
WHERE { OPTIONAL { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission .
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forProject ?n0 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> <http://www.knora.org/ontology/knora-base#hasPermissions> ?n1 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forGroup ?n2 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forProperty ?n3 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forResourceClass ?n4 . } } }"""
      ZIO.serviceWith[DefaultObjectAccessPermissionRepoLive](repo =>
        assertTrue(
          canonicalUpdate(repo.saveQuery(doap(ForWhat.ResourceClass(rcIri))).sparql) == canonicalUpdate(legacy),
        ),
      )
    },
    test("doap.save.property") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/permissions>
DELETE { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    <http://www.knora.org/ontology/knora-base#hasPermissions> ?n1 ;
    knora-admin:forGroup ?n2 ;
    knora-admin:forProperty ?n3 ;
    knora-admin:forResourceClass ?n4 . }
INSERT { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject <http://rdfh.ch/projects/1234> ;
    <http://www.knora.org/ontology/knora-base#hasPermissions> "V http://rdfh.ch/groups/0001/1234" ;
    knora-admin:forProperty <https://example.com/p> . }
WHERE { OPTIONAL { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission .
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forProject ?n0 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> <http://www.knora.org/ontology/knora-base#hasPermissions> ?n1 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forGroup ?n2 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forProperty ?n3 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forResourceClass ?n4 . } } }"""
      ZIO.serviceWith[DefaultObjectAccessPermissionRepoLive](repo =>
        assertTrue(canonicalUpdate(repo.saveQuery(doap(ForWhat.Property(propIri))).sparql) == canonicalUpdate(legacy)),
      )
    },
    test("doap.save.resourceClassAndProperty") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/permissions>
DELETE { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject ?n0 ;
    <http://www.knora.org/ontology/knora-base#hasPermissions> ?n1 ;
    knora-admin:forGroup ?n2 ;
    knora-admin:forProperty ?n3 ;
    knora-admin:forResourceClass ?n4 . }
INSERT { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission ;
    knora-admin:forProject <http://rdfh.ch/projects/1234> ;
    <http://www.knora.org/ontology/knora-base#hasPermissions> "V http://rdfh.ch/groups/0001/1234" ;
    knora-admin:forResourceClass <https://example.com/rc> ;
    knora-admin:forProperty <https://example.com/p> . }
WHERE { OPTIONAL { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission .
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forProject ?n0 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> <http://www.knora.org/ontology/knora-base#hasPermissions> ?n1 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forGroup ?n2 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forProperty ?n3 . }
OPTIONAL { <http://rdfh.ch/permissions/0001/1234> knora-admin:forResourceClass ?n4 . } } }"""
      ZIO.serviceWith[DefaultObjectAccessPermissionRepoLive](repo =>
        assertTrue(
          canonicalUpdate(
            repo.saveQuery(doap(ForWhat.ResourceClassAndProperty(rcIri, propIri))).sparql,
          ) == canonicalUpdate(legacy),
        ),
      )
    },
    test("doap.erase") {
      val legacy =
        """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
WITH <http://www.knora.org/data/permissions>
DELETE { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission ;
    ?p ?o . }
WHERE { <http://rdfh.ch/permissions/0001/1234> a knora-admin:DefaultObjectAccessPermission ;
    ?p ?o . }"""
      ZIO.serviceWith[DefaultObjectAccessPermissionRepoLive](repo =>
        assertTrue(canonicalUpdate(repo.eraseQuery(doap(ForWhat.Group(grpIri))).sparql) == canonicalUpdate(legacy)),
      )
    },
  )

  override def spec: Spec[Any, Any] = suite("EntityRepoQueriesSpec")(
    groupSuite,
    projectSuite,
    userSuite,
    apSuite,
    doapSuite,
  ).provide(
    KnoraGroupRepoLive.layer,
    KnoraProjectRepoLive.layer,
    KnoraUserRepoLive.layer,
    AdministrativePermissionRepoLive.layer,
    DefaultObjectAccessPermissionRepoLive.layer,
    TriplestoreServiceInMemory.emptyLayer,
    StringFormatter.test,
    CacheManager.layer,
  )
}
