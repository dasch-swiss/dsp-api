/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries

import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraAdmin
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object CreateAdministrativePermissionQuery extends QueryBuilderHelper {
  def build(
    permissionIri: PermissionIri,
    forProjectIri: ProjectIri,
    forGroupIri: GroupIri,
    permissions: Set[Permission.Administrative],
  ): ModifyQuery =
    Queries
      .INSERT(
        toRdfIri(permissionIri)
          .isA(KnoraAdmin.AdministrativePermission)
          .andHas(KnoraAdmin.forProject, toRdfIri(forProjectIri))
          .andHas(KnoraAdmin.forGroup, toRdfIri(forGroupIri))
          .andHas(KnoraBase.hasPermissions, PermissionUtilADM.formatAdministrativePermissions(permissions)),
      )
      .prefix(KnoraAdmin.NS, KnoraBase.NS)
      .into(toRdfIri(AdminConstants.permissionsDataNamedGraph))
}
