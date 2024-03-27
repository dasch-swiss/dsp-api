/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM

sealed trait ObjectAccessPermission extends Ordered[ObjectAccessPermission] {
  self =>
  def name: String
  def token: String
  def code: Int
  final def toPermissionADM(groupIri: IRI): PermissionADM       = PermissionADM.from(self, groupIri)
  final override def compare(that: ObjectAccessPermission): Int = self.code - that.code
  final override def toString: String                           = token
}

object ObjectAccessPermission {
  case object RestrictedView extends ObjectAccessPermission {
    override val name: String  = "restricted view permission"
    override val token: String = "RV"
    override val code: Int     = 1
  }

  case object View extends ObjectAccessPermission {
    override val name: String  = "view permission"
    override val token: String = "V"
    override val code: Int     = 2
  }

  case object Modify extends ObjectAccessPermission {
    override val name: String  = "modify permission"
    override val token: String = "M"
    override val code: Int     = 6
  }

  case object Delete extends ObjectAccessPermission {
    override val name: String  = "delete permission"
    override val token: String = "D"
    override val code: Int     = 7
  }

  case object ChangeRights extends ObjectAccessPermission {
    override val name: String  = "change rights permission"
    override val token: String = "CR"
    override val code: Int     = 8
  }

  val maxPermission: ObjectAccessPermission           = ChangeRights
  def from(code: Int): Option[ObjectAccessPermission] = ObjectAccessPermissions.all.find(_.code == code)
  def fromToken(token: String): Option[ObjectAccessPermission] =
    ObjectAccessPermissions.all.find(_.token == token)
}

object ObjectAccessPermissions {
  val all: Set[ObjectAccessPermission] = Set(
    ObjectAccessPermission.ChangeRights,
    ObjectAccessPermission.Delete,
    ObjectAccessPermission.Modify,
    ObjectAccessPermission.RestrictedView,
    ObjectAccessPermission.View,
  )
  val allCodes: Set[Int]            = all.map(_.code)
  val allTokens: Set[String]        = all.map(_.token)
  val codeByToken: Map[String, Int] = all.map(p => p.token -> p.code).toMap
}
