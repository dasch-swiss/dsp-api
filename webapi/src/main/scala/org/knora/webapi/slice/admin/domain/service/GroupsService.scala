package org.knora.webapi.slice.admin.domain.service

import zio.ZLayer

final case class GroupsService(
                                private val groupRepo: KnoraGroupRepo,
) {}

object GroupsService {
  def layer = ZLayer.derive[GroupsService]
}
