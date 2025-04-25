package org.knora.webapi.slice.admin.api.model
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.User

final case class UserDto(
  id: String,
  username: String,
  email: String,
  givenName: String,
  familyName: String,
  status: Boolean,
  lang: String,
  groups: Seq[Group],
  projects: Seq[Project],
  permissions: PermissionsDataADM,
)
object UserDto {

  given JsonCodec[UserDto] = DeriveJsonCodec.gen[UserDto]

  def from(user: User): UserDto =
    UserDto(
      user.id,
      user.username,
      user.email,
      user.givenName,
      user.familyName,
      user.status,
      user.lang,
      user.groups,
      user.projects,
      user.permissions,
    )
}
