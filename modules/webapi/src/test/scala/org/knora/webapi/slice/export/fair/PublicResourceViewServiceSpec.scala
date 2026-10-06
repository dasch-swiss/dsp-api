/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.time.Instant
import java.util.UUID

import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.ApiV2Schema
import org.knora.webapi.InternalSchema
import org.knora.webapi.Rendering
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.messages.v2.responder.valuemessages.DeletionInfo
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadOtherValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageFileValueContentV2
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v2.VersionDate
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.resources.service.ReadResourcesService

/** A [[ReadResourcesService]] whose every method is unsupported; tests override the one they exercise. */
private trait UnsupportedReadResourcesService extends ReadResourcesService {
  private def unsupported: Task[ReadResourcesSequenceV2] = ZIO.die(new UnsupportedOperationException)

  def readResourcesSequence(
    resourceIris: Seq[ResourceIri],
    propertyIri: Option[SmartIri],
    valueUuid: Option[UUID],
    preview: Boolean,
    targetSchema: ApiV2Schema,
    requestingUser: User,
    withDeleted: Boolean,
    queryStandoff: Boolean,
    skipRetrievalChecks: Boolean,
    standoffTagFilter: Option[SmartIri],
  ): Task[ReadResourcesSequenceV2] = unsupported

  def readResourcesSequencePar(
    resourceIris: Seq[ResourceIri],
    propertyIri: Option[SmartIri],
    valueUuid: Option[UUID],
    preview: Boolean,
    targetSchema: ApiV2Schema,
    requestingUser: User,
    withDeleted: Boolean,
    queryStandoff: Boolean,
    skipRetrievalChecks: Boolean,
    standoffTagFilter: Option[SmartIri],
  ): Task[ReadResourcesSequenceV2] = unsupported

  def getResources(
    resourceIris: Seq[ResourceIri],
    propertyIri: Option[SmartIri],
    targetSchema: ApiV2Schema,
    schemaOptions: Set[Rendering],
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = unsupported

  def getResourcePreviewWithDeletedResource(
    resourceIris: Seq[ResourceIri],
    withDeleted: Boolean,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = unsupported

  def getResourcePreview(
    resourceIris: Seq[ResourceIri],
    withDeleted: Boolean,
    targetSchema: ApiV2Schema,
    requestingUser: User,
  ): Task[ReadResourcesSequenceV2] = unsupported
}

@RunWith(classOf[DspZTestJUnitRunner])
class PublicResourceViewServiceSpec extends ZIOSpecDefault {

  private given sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val iri      = ResourceIri.unsafeFrom("http://rdfh.ch/0001/lklK7rVuVOmpBZYWrF8o-g")
  private val created  = Instant.parse("2020-01-01T00:00:00Z")
  private val classIri = sf.toSmartIri("http://www.knora.org/ontology/0001/anything#Thing")

  private def fileValue(access: ObjectAccess, n: Int = 1): ReadValueV2 =
    ReadOtherValueV2(
      valueIri = ValueIri.unsafeFrom(s"http://rdfh.ch/0001/lklK7rVuVOmpBZYWrF8o-g/values/v$n"),
      attachedToUser = "http://rdfh.ch/users/root",
      permissions = "V knora-admin:UnknownUser",
      userPermission = access,
      valueCreationDate = created,
      valueHasUUID = UUID.randomUUID(),
      valueContent = StillImageFileValueContentV2(InternalSchema, FileValueV2(s"file$n.jp2", "image/jp2"), 10, 10),
      previousValueIri = None,
      deletionInfo = None,
    )

  private def resource(
    access: ObjectAccess,
    files: Seq[ReadValueV2] = Seq.empty,
    classIri: SmartIri = classIri,
    deletionInfo: Option[DeletionInfo] = None,
  ): ReadResourceV2 =
    ReadResourceV2(
      resourceIri = iri,
      label = "label",
      resourceClassIri = classIri,
      attachedToUser = "http://rdfh.ch/users/root",
      projectADM = TestDataFactory.someProjectADM,
      permissions = "V knora-admin:UnknownUser",
      userPermission = access,
      values =
        if (files.isEmpty) Map.empty else Map(sf.toSmartIri(KnoraBase.HasStillImageFileValue) -> files),
      creationDate = created,
      lastModificationDate = None,
      versionDate = None,
      deletionInfo = deletionInfo,
    )

  private def serviceReturning(result: Task[Seq[ReadResourceV2]], seen: Ref[Seq[User]]): PublicResourceViewService =
    PublicResourceViewService(new UnsupportedReadResourcesService {
      override def getResourcesWithDeletedResource(
        resourceIris: Seq[ResourceIri],
        propertyIri: Option[SmartIri],
        valueUuid: Option[UUID],
        versionDate: Option[VersionDate],
        withDeleted: Boolean,
        showDeletedValues: Boolean,
        targetSchema: ApiV2Schema,
        schemaOptions: Set[Rendering],
        requestingUser: User,
      ): Task[ReadResourcesSequenceV2] =
        seen.update(_ :+ requestingUser) *> result.map(rs => ReadResourcesSequenceV2(rs, Set.empty, false))
    })

  private def run(result: Task[Seq[ReadResourceV2]], version: Option[VersionDate] = None) =
    for {
      seen  <- Ref.make(Seq.empty[User])
      res   <- serviceReturning(result, seen).read(ResourceLandingRef(iri, version))
      users <- seen.get
    } yield (res, users)

  private def accessOf(r: PublicResourceResult): Option[AccessLevel] = r match {
    case PublicResourceResult.Public(v) => Some(v.accessLevel)
    case PublicResourceResult.NotPublic => None
  }

  val spec: Spec[Any, Any] = suite("PublicResourceViewService")(
    test("resource and file value viewable: FullOpen with the file value, read as the anonymous user") {
      val file = fileValue(ObjectAccess.View)
      run(ZIO.succeed(Seq(resource(ObjectAccess.View, Seq(file))))).map { case (res, users) =>
        assertTrue(
          accessOf(res).contains(AccessLevel.FullOpen),
          res match {
            case PublicResourceResult.Public(v) =>
              v.fileValue.contains(file) && v.project == TestDataFactory.someProjectADM
            case _ => false
          },
          users == Seq(KnoraSystemInstances.Users.AnonymousUser),
        )
      }
    },
    test("higher than View counts as FullOpen") {
      run(ZIO.succeed(Seq(resource(ObjectAccess.Modify)))).map { case (res, _) =>
        assertTrue(accessOf(res).contains(AccessLevel.FullOpen))
      }
    },
    test("restricted resource is Restricted") {
      run(ZIO.succeed(Seq(resource(ObjectAccess.RestrictedView)))).map { case (res, _) =>
        assertTrue(accessOf(res).contains(AccessLevel.Restricted))
      }
    },
    test("restricted file value makes the view Restricted") {
      run(ZIO.succeed(Seq(resource(ObjectAccess.View, Seq(fileValue(ObjectAccess.RestrictedView)))))).map {
        case (res, _) => assertTrue(accessOf(res).contains(AccessLevel.Restricted))
      }
    },
    test("more than one file value yields no file value") {
      val files = Seq(fileValue(ObjectAccess.View, 1), fileValue(ObjectAccess.View, 2))
      run(ZIO.succeed(Seq(resource(ObjectAccess.View, files)))).map { case (res, _) =>
        assertTrue(res match {
          case PublicResourceResult.Public(v) => v.fileValue.isEmpty
          case _                              => false
        })
      }
    },
    test("ForbiddenException is NotPublic") {
      run(ZIO.fail(ForbiddenException("no"))).map { case (res, _) =>
        assertTrue(res == PublicResourceResult.NotPublic)
      }
    },
    test("NotFoundException is NotPublic") {
      run(ZIO.fail(NotFoundException("missing"))).map { case (res, _) =>
        assertTrue(res == PublicResourceResult.NotPublic)
      }
    },
    test("any other failure propagates") {
      run(ZIO.fail(new RuntimeException("triplestore down"))).exit.map(exit => assertTrue(exit.isFailure))
    },
    test("a deleted resource is NotPublic") {
      val deleted = resource(
        ObjectAccess.View,
        classIri = sf.toSmartIri(KnoraBase.DeletedResource),
        deletionInfo = Some(DeletionInfo(created.plusSeconds(10), None)),
      )
      run(ZIO.succeed(Seq(deleted))).map { case (res, _) => assertTrue(res == PublicResourceResult.NotPublic) }
    },
    test("a resource deleted later is NotPublic even when read at an older version") {
      val deletedLater = resource(ObjectAccess.View, deletionInfo = Some(DeletionInfo(created.plusSeconds(10), None)))
      run(ZIO.succeed(Seq(deletedLater))).map { case (res, _) => assertTrue(res == PublicResourceResult.NotPublic) }
    },
    test("a version date before the creation date is NotPublic") {
      val before = VersionDate.fromInstant(created.minusSeconds(60))
      run(ZIO.succeed(Seq(resource(ObjectAccess.View))), Some(before)).map { case (res, _) =>
        assertTrue(res == PublicResourceResult.NotPublic)
      }
    },
    test("a version date after the creation date is read normally") {
      val after = VersionDate.fromInstant(created.plusSeconds(60))
      run(ZIO.succeed(Seq(resource(ObjectAccess.View))), Some(after)).map { case (res, _) =>
        assertTrue(accessOf(res).contains(AccessLevel.FullOpen))
      }
    },
    test("an empty result is NotPublic") {
      run(ZIO.succeed(Seq.empty)).map { case (res, _) => assertTrue(res == PublicResourceResult.NotPublic) }
    },
  )
}
