package org.knora.webapi.slice.admin.domain.service

import zio.Task

import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierType
import org.knora.webapi.slice.admin.domain.model.ActiveUser
import org.knora.webapi.slice.admin.domain.model.InactiveUser
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.repo.service.Repository
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

trait UserRepo extends Repository[User, InternalIri] {

  def findByUserIdentifier(id: UserIdentifierADM): Task[Option[User]] =
    id.hasType match {
      case UserIdentifierType.Iri      => findById(InternalIri(id.toIri))
      case UserIdentifierType.Email    => findByEmail(id.toEmail)
      case UserIdentifierType.Username => findByUsername(id.toUsername)
    }
  def findActiveUserIdentifier(id: UserIdentifierADM): Task[Option[ActiveUser]] =
    findByUserIdentifier(id).map(_.collect { case activeUser: ActiveUser => activeUser })
  def findInactiveUserIdentifier(id: UserIdentifierADM): Task[Option[InactiveUser]] =
    findByUserIdentifier(id).map(_.collect { case inactiveUser: InactiveUser => inactiveUser })

  def findByEmail(email: String): Task[Option[User]]
  def findActiveByEmail(email: String): Task[Option[ActiveUser]] =
    findByEmail(email).map(_.collect { case activeUser: ActiveUser => activeUser })
  def findInactiveByEmail(email: String): Task[Option[InactiveUser]] =
    findByEmail(email).map(_.collect { case inactiveUser: InactiveUser => inactiveUser })

  def findByUsername(username: String): Task[Option[User]]
  def findActiveByUsername(username: String): Task[Option[ActiveUser]] =
    findByUsername(username).map(_.collect { case activeUser: ActiveUser => activeUser })
  def findInactiveByUsername(username: String): Task[Option[InactiveUser]] =
    findByUsername(username).map(_.collect { case inactiveUser: InactiveUser => inactiveUser })

  def findActiveById(id: InternalIri): Task[Option[ActiveUser]] =
    findById(id).map(_.collect { case activeUser: ActiveUser => activeUser })
  def findInactiveById(id: InternalIri): Task[Option[InactiveUser]] =
    findById(id).map(_.collect { case inactiveUser: InactiveUser => inactiveUser })
}
