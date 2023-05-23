package org.knora.webapi.slice.admin.domain.model

import org.knora.webapi.slice.resourceinfo.domain.InternalIri

sealed trait User {
  def id: InternalIri
  def username: String
  def email: String
  def familyName: String
  def givenName: String
  def password: String
  def preferredLanguage: String
  def isInProject: List[InternalIri]
  def isInGroup: List[InternalIri]
  def isInSystemAdminGroup: InternalIri

  def isActive: Boolean = this match {
    case _: ActiveUser   => true
    case _: InactiveUser => false
  }
}

object User {
  def make(
    id: InternalIri,
    isActive: Boolean,
    username: String,
    email: String,
    familyName: String,
    givenName: String,
    password: String,
    preferredLanguage: String,
    isInProject: List[InternalIri],
    isInGroup: List[InternalIri],
    isInSystemAdminGroup: InternalIri
  ): User =
    if (isActive)
      ActiveUser(
        id,
        username,
        email,
        familyName,
        givenName,
        password,
        preferredLanguage,
        isInProject,
        isInGroup,
        isInSystemAdminGroup
      )
    else
      InactiveUser(
        id,
        username,
        email,
        familyName,
        givenName,
        password,
        preferredLanguage,
        isInProject,
        isInGroup,
        isInSystemAdminGroup
      )
}

final case class ActiveUser private (
  id: InternalIri,
  username: String,
  email: String,
  familyName: String,
  givenName: String,
  password: String,
  preferredLanguage: String,
  isInProject: List[InternalIri],
  isInGroup: List[InternalIri],
  isInSystemAdminGroup: InternalIri
) extends User

final case class InactiveUser private (
  id: InternalIri,
  username: String,
  email: String,
  familyName: String,
  givenName: String,
  password: String,
  preferredLanguage: String,
  isInProject: List[InternalIri],
  isInGroup: List[InternalIri],
  isInSystemAdminGroup: InternalIri
) extends User
