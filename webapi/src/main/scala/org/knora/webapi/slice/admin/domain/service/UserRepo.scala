package org.knora.webapi.slice.admin.domain.service

import zio.Task

import org.knora.webapi.slice.admin.domain.model.ActiveUser
import org.knora.webapi.slice.admin.domain.model.InactiveUser
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.repo.service.Repository
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

trait UserRepo extends Repository[User, InternalIri] {
  def findActiveUserById(id: InternalIri): Task[Option[ActiveUser]] =
    findById(id).map(_.collect { case activeUser: ActiveUser => activeUser })
  def findInactiveUserById(id: InternalIri): Task[Option[InactiveUser]] =
    findById(id).map(_.collect { case inactiveUser: InactiveUser => inactiveUser })
}
