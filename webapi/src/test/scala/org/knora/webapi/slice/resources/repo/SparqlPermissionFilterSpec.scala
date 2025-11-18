/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.GroupDescriptions
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.GroupSelfJoin
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess.*
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo.builtIn.*
import org.knora.webapi.slice.resources.repo.SparqlPermissionFilter
import org.knora.webapi.config.Triplestore
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive
import zio.ZIO
import zio.ZLayer
import zio.test.*

import scala.util.matching.Regex

object SparqlPermissionFilterSpec extends ZIOSpecDefault {

  private val testGroup = KnoraGroup(
    GroupIri.unsafeFrom("http://rdfh.ch/groups/0001/SeC9hQQFRJChD7ZyExcwCg"),
    GroupName.unsafeFrom("TestGroup"),
    GroupDescriptions.unsafeFrom(Seq(StringLiteralV2.from("description"))),
    GroupStatus.active,
    None,
    GroupSelfJoin.disabled,
  )

  override def spec = suite("PermissionFilterSpec")(
    suite("built-in Group")(
      test("Should match permission token and group for built-in groups") {
        val regex = SparqlPermissionFilter.buildRegEx(View, SystemAdmin)
        val testStrings = Seq(
          s"V knora-admin:SystemAdmin",
          s"V knora-admin:SystemAdmin|RV knora-admin:Creator",
          s"RV knora-admin:Creator|V knora-admin:SystemAdmin",
          s"RV knora-admin:Creator|V knora-admin:SystemAdmin|M knora-admin:Creator",
          s"RV knora-admin:Creator|V knora-admin:KnownUser,knora-admin:SystemAdmin",
          s"RV knora-admin:Creator|V knora-admin:KnownUser,knora-admin:SystemAdmin|M knora-admin:Creator",
        )

        checkAll(Gen.fromIterable(testStrings)) { str =>
          assertTrue(regex.matches(str))
        }
      },
      test("Should not match permission token and group for regular groups") {
        val regex = SparqlPermissionFilter.buildRegEx(View, SystemAdmin)
        val testStrings = Seq(
          "V knora-admin:ProjectMember",
          "RV knora-admin:Creator|V knora-admin:KnownUser",
          "RV knora-admin:Creator|V knora-admin:KnownUser,knora-admin:ProjectAdmin",
          s"V knora-admin:ProjectMember|RV knora-admin:SystemAdmin",
          s"V knora-admin:ProjectMember|RV knora-admin:SystemAdmin|M knora-admin:Creator",
          s"RV ${testGroup.id}",
        )
        checkAll(Gen.fromIterable(testStrings)) { str =>
          assertTrue(!regex.matches(str))
        }
      },
      test("Should build SPARQL FILTER regex for View and ProjectAdmin") {
        val filter = SparqlPermissionFilter.buildSparqlRegex(SparqlBuilder.`var`("perms"), View, ProjectAdmin)
        assertTrue(
          filter.getQueryString == """REGEX( ?perms, ".*(?:^|\\|)V (?:.*,)?knora-admin:ProjectAdmin(?:,.*)?(?:\\||$).*" )""",
        )
      },
    ),
    suite("regular Groups")(
      test("Should match permission token and group for regular groups") {
        val regex = SparqlPermissionFilter.buildRegEx(View, testGroup)
        val testStrings = Seq(
          s"V ${testGroup.id}",
          s"V ${testGroup.id}|RV knora-admin:Creator",
          s"RV knora-admin:Creator|V ${testGroup.id}",
          s"RV knora-admin:Creator|V ${testGroup.id}|M knora-admin:Creator",
          s"RV knora-admin:Creator|V knora-admin:KnownUser,${testGroup.id}",
          s"RV knora-admin:Creator|V knora-admin:KnownUser,${testGroup.id}|M knora-admin:Creator",
        )
        checkAll(Gen.fromIterable(testStrings)) { str =>
          assertTrue(regex.matches(str))
        }
      },
      test("Should not match permission token and group for regular groups") {
        val regex = SparqlPermissionFilter.buildRegEx(View, testGroup)
        val testStrings = Seq(
          "V knora-admin:ProjectMember",
          "RV knora-admin:Creator|V knora-admin:KnownUser",
          "RV knora-admin:Creator|V knora-admin:KnownUser,knora-admin:ProjectAdmin",
          s"V knora-admin:ProjectMember|RV ${testGroup.id}",
          s"RV ${testGroup.id}",
        )
        checkAll(Gen.fromIterable(testStrings)) { str =>
          assertTrue(!regex.matches(str))
        }
      },
      test("Should build SPARQL FILTER regex for View and ProjectAdmin") {
        val filter = SparqlPermissionFilter.buildSparqlRegex(SparqlBuilder.`var`("perms"), View, testGroup)
        assertTrue(
          filter.getQueryString == """REGEX( ?perms, ".*(?:^|\\|)V (?:.*,)?http://rdfh\\.ch/groups/0001/SeC9hQQFRJChD7ZyExcwCg(?:,.*)?(?:\\||$).*" )""",
        )
      },
    ),
    test("Should work with the database") {
      for {
        tripleStore <- ZIO.service[TriplestoreService]
        perms        = SparqlBuilder.`var`("perms")
        filter1      = SparqlPermissionFilter.buildSparqlRegex(perms, View, KnownUser)
        filter2      = SparqlPermissionFilter.buildSparqlRegex(perms, View, ProjectMember)
        combined     = List(filter1, filter2).reduce(Expressions.or(_, _))
        filter       = GraphPatterns.and().filter(combined)
        sub          = SparqlBuilder.`var`("sub")
        label        = SparqlBuilder.`var`("label")
        query = Queries
                  .SELECT(sub, label)
                  .where(
                    filter.and(
                      sub
                        .isA(Rdf.iri("http://www.knora.org/ontology/0001/anything#Thing"))
                        .andHas(RDFS.LABEL, label)
                        .andHas(Rdf.iri("http://www.knora.org/ontology/knora-base#hasPermissions"), perms),
                    ),
                  )
        _       = Console.println(s"Generated query:\n${query.getQueryString}")
        result <- tripleStore.select(query).mapError(e => new Exception(e.getMessage))
      } yield assertTrue(result.size == 9)
    }.provide(
      ZLayer.succeed(
        Triplestore(
          dbtype = "fuseki",
          useHttps = false,
          host = "localhost",
          queryTimeout = java.time.Duration.ofSeconds(30),
          gravsearchTimeout = java.time.Duration.ofSeconds(60),
          maintenanceTimeout = java.time.Duration.ofSeconds(120),
          fuseki = org.knora.webapi.config.Fuseki(
            port = 3030,
            username = "admin",
            password = "test",
          ),
          profileQueries = false,
        ),
      ),
      TriplestoreServiceLive.layer,
    ),
  )
}
