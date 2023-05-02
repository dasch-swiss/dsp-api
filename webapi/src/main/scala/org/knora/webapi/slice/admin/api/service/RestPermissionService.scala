package org.knora.webapi.slice.admin.api.service

import zio._
import zio.macros.accessible

import dsp.errors.ForbiddenException
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.slice.admin.api.service.RestPermissionService.isActive
import org.knora.webapi.slice.admin.api.service.RestPermissionService.isSystemAdmin
import org.knora.webapi.slice.admin.api.service.RestPermissionService.isSystemOrProjectAdmin
import org.knora.webapi.slice.admin.domain.model.KnoraProject

@accessible
trait RestPermissionService {
  def ensureSystemAdmin(user: UserADM): IO[ForbiddenException, Unit]
  def ensureSystemOrProjectAdmin(user: UserADM, project: KnoraProject): IO[ForbiddenException, Unit]
}

object RestPermissionService {
  def isActive(userADM: UserADM): Boolean                           = userADM.status
  def isSystemAdmin(user: UserADM): Boolean                         = user.permissions.isSystemAdmin
  def isProjectAdmin(user: UserADM, project: KnoraProject): Boolean = user.permissions.isProjectAdmin(project.id)
  def isSystemOrProjectAdmin(userADM: UserADM, project: KnoraProject): Boolean =
    isSystemAdmin(userADM) || isProjectAdmin(userADM, project)
}

final case class RestPermissionServiceLive() extends RestPermissionService {
  override def ensureSystemAdmin(user: UserADM): IO[ForbiddenException, Unit] =
    ensureIsActive(user) *>
      failUnless(
        isSystemAdmin(user),
        s"You are logged in with username '${user.username}', but only a system administrator has permissions for this operation."
      )

  private def failUnless(condition: Boolean, errorMsg: String): ZIO[Any, ForbiddenException, Unit] =
    ZIO.fail(ForbiddenException(errorMsg)).unless(condition).unit

  private def ensureIsActive(userADM: UserADM) =
    failUnless(
      isActive(userADM),
      s"The account with username '${userADM.username}' is not active."
    )
  override def ensureSystemOrProjectAdmin(user: UserADM, project: KnoraProject): IO[ForbiddenException, Unit] =
    ensureIsActive(user) *>
      failUnless(
        isSystemOrProjectAdmin(user, project),
        s"You are logged in with username '${user.username}', but only a system administrator or project administrator has permissions for this operation."
      )
}

object RestPermissionServiceLive {
  val layer: ULayer[RestPermissionServiceLive] = zio.ZLayer.fromFunction(RestPermissionServiceLive.apply _)
}
