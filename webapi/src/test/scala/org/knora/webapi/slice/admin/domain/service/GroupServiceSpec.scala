/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Scope
import zio.ZIO
import zio.test.*
import zio.test.Spec
import zio.test.TestEnvironment
import zio.test.ZIOSpecDefault

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.GroupDescriptions
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.GroupSelfJoin
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoInMemory
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheLive
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object GroupServiceSpec extends ZIOSpecDefault {
  private val exampleGroup = new Group(
    id = "http://rdfh.ch/groups/0007/james-bond-group",
    name = "James Bond Group",
    descriptions = Seq(StringLiteralV2.from("James Bond Group Description", Some("en"))),
    project = None,
    status = true,
    selfjoin = false,
  )

  private val exampleKnoraGroup = new KnoraGroup(
    id = GroupIri.unsafeFrom("http://rdfh.ch/groups/0007/james-bond-group"),
    groupName = GroupName.unsafeFrom("James Bond Group"),
    groupDescriptions =
      GroupDescriptions.unsafeFrom(Seq(StringLiteralV2.from("James Bond Group Description", Some("en")))),
    status = GroupStatus.active,
    belongsToProject = None,
    hasSelfJoinEnabled = GroupSelfJoin.disabled,
  )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("GroupServiceSpec")(
      test("KnoraGroup should be transformed to Group") {
        for {
          group <- ZIO.serviceWithZIO[GroupService](_.toGroup(exampleKnoraGroup))
        } yield assertTrue(group == exampleGroup)
      },
      test("Group should be transformed to KnoraGroup") {
        for {
          knoraGroup <- ZIO.serviceWith[GroupService](_.toKnoraGroup(exampleGroup))
        } yield assertTrue(knoraGroup == exampleKnoraGroup)
      },
    ).provide(
      AppConfig.layer,
      CacheManager.layer,
      GroupService.layer,
      IriConverter.layer,
      IriService.layer,
      KnoraGroupRepoInMemory.layer,
      KnoraGroupService.layer,
      KnoraProjectRepoInMemory.layer,
      KnoraProjectService.layer,
      KnoraUserRepoLive.layer,
      KnoraUserService.layer,
      OntologyCacheLive.layer,
      OntologyRepoLive.layer,
      PasswordService.layer,
      ProjectService.layer,
      StringFormatter.test,
      TriplestoreServiceInMemory.emptyLayer,
    )
}
