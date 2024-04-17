package org.knora.webapi.slice.admin.domain.service

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.model.{
  Group,
  GroupDescriptions,
  GroupIri,
  GroupName,
  GroupSelfJoin,
  GroupStatus,
  KnoraGroup,
}
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.admin.repo.service.{KnoraGroupRepoInMemory, KnoraGroupRepoLive}
import org.knora.webapi.slice.ontology.repo.service.{OntologyCacheLive, OntologyRepoLive}
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.cache.CacheService
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory
import zio.{Scope, ZIO}
import zio.test._
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault}

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
      CacheService.layer,
      GroupService.layer,
      IriConverter.layer,
      IriService.layer,
      KnoraGroupRepoInMemory.layer,
      KnoraGroupService.KnoraGroupService.layer,
      KnoraProjectRepoInMemory.layer,
      KnoraProjectService.layer,
      OntologyCacheLive.layer,
      OntologyRepoLive.layer,
      ProjectService.layer,
      StringFormatter.test,
      TriplestoreServiceInMemory.emptyLayer,
    )
}
