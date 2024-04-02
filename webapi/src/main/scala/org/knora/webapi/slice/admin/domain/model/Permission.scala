/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

sealed trait Permission {
  def token: String
}

object Permission {
  sealed trait ObjectAccess extends Ordered[ObjectAccess] with Permission {
    self =>
    def code: Int
    final override def compare(that: ObjectAccess): Int = self.code - that.code
    final override def toString: String                 = token
  }

  object ObjectAccess {
    case object RestrictedView extends ObjectAccess {
      override val token: String = "RV"
      override val code: Int     = 1
    }

    case object View extends ObjectAccess {
      override val token: String = "V"
      override val code: Int     = 2
    }

    case object Modify extends ObjectAccess {
      override val token: String = "M"
      override val code: Int     = 6
    }

    case object Delete extends ObjectAccess {
      override val token: String = "D"
      override val code: Int     = 7
    }

    case object ChangeRights extends ObjectAccess {
      override val token: String = "CR"
      override val code: Int     = 8
    }

    val maxPermission: ObjectAccess = ChangeRights

    def from(code: Int): Option[ObjectAccess] = all.find(_.code == code)

    def fromToken(token: String): Option[ObjectAccess] = all.find(_.token == token)

    val all: Set[ObjectAccess] = Set(
      ObjectAccess.ChangeRights,
      ObjectAccess.Delete,
      ObjectAccess.Modify,
      ObjectAccess.RestrictedView,
      ObjectAccess.View,
    )
    val allCodes: Set[Int]            = all.map(_.code)
    val allTokens: Set[String]        = all.map(_.token)
    val codeByToken: Map[String, Int] = all.map(p => p.token -> p.code).toMap
  }

  sealed trait Administrative extends Permission

  object Administrative {
    case object ProjectResourceCreateAll extends Administrative {
      override val token: String = "ProjectResourceCreateAllPermission"
    }

    case object ProjectResourceCreateRestricted extends Administrative {
      override val token: String = "ProjectResourceCreateRestrictedPermission"
    }

    case object ProjectAdminAll extends Administrative {
      override val token: String = "ProjectAdminAllPermission"
    }

    case object ProjectAdminGroupAll extends Administrative {
      override val token: String = "ProjectAdminGroupAllPermission"
    }

    case object ProjectAdminGroupRestricted extends Administrative {
      override val token: String = "ProjectAdminGroupRestrictedPermission"
    }

    case object ProjectAdminRightsAll extends Administrative {
      override val token: String = "ProjectAdminRightsAllPermission"
    }

    def fromToken(token: String): Option[Administrative] = all.find(_.token == token)

    val all: Set[Administrative] = Set(
      Administrative.ProjectResourceCreateAll,
      Administrative.ProjectResourceCreateRestricted,
      Administrative.ProjectAdminAll,
      Administrative.ProjectAdminGroupAll,
      Administrative.ProjectAdminGroupRestricted,
      Administrative.ProjectAdminRightsAll,
    )

    val allTokens: Set[String] = all.map(_.token)
  }
}
