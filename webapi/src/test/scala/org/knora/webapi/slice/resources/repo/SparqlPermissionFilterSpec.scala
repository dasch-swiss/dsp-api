/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder
import zio.test.*

import scala.util.matching.Regex
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
    suite("built-in Groups")(
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
      test("Should build SPARQL FILTER regex for View and custom group") {
        val filter = SparqlPermissionFilter.buildSparqlRegex(SparqlBuilder.`var`("perms"), View, testGroup)
        assertTrue(
          filter.getQueryString == """REGEX( ?perms, ".*(?:^|\\|)V (?:.*,)?http://rdfh\\.ch/groups/0001/SeC9hQQFRJChD7ZyExcwCg(?:,.*)?(?:\\||$).*" )""",
        )
      },
    ),
  )
}
