/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.NonEmptyChunk
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants

object ProjectServiceSpec extends ZIOSpecDefault {

  val spec: Spec[Any, Nothing] =
    suite("projectDataNamedGraphV2 should return the data named graph of a project with shortcode for")(
      test("a ProjectADM") {
        val shortname = Shortname.unsafeFrom("someProject")
        val shortcode = Shortcode.unsafeFrom("0001")
        val p = Project(
          id = IriTestConstants.Project.TestProject,
          shortname = shortname,
          shortcode = shortcode,
          longname = None,
          description = List(StringLiteralV2.from("description not used in test", None)),
          keywords = List.empty,
          logo = None,
          ontologies = List.empty,
          status = Status.Active,
          selfjoin = SelfJoin.CanJoin,
          enabledLicenses = Set.empty,
        )
        assertTrue(
          ProjectService.projectDataNamedGraphV2(p).value == s"http://www.knora.org/data/$shortcode/$shortname",
        )
      },
      test("a KnoraProject") {
        val shortcode = "0002"
        val shortname = "someOtherProject"
        val p: KnoraProject = KnoraProject(
          id = IriTestConstants.Project.TestProject,
          shortname = Shortname.unsafeFrom(shortname),
          shortcode = Shortcode.unsafeFrom(shortcode),
          longname = None,
          description =
            NonEmptyChunk(Description.unsafeFrom(StringLiteralV2.from("description not used in test", None))),
          keywords = List.empty,
          logo = None,
          status = Status.Active,
          selfjoin = SelfJoin.CanJoin,
          restrictedView = RestrictedView.default,
          allowedCopyrightHolders = Set.empty,
          enabledLicenses = Set.empty,
        )
        assertTrue(
          ProjectService
            .projectDataNamedGraphV2(p)
            .value == s"http://www.knora.org/data/$shortcode/$shortname",
        )
      },
    )
}
