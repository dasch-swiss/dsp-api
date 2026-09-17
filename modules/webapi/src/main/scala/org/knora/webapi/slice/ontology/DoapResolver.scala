/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology

import zio.*

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.responders.admin.PermissionsResponder
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User

/**
 * Supplies the object-access permission string for an imported resource or value, matching the single-resource create
 * path. The import transformer needs two things that both reach the triplestore in production: the default DOAP for a
 * resource class or property, and the reformatting of a payload-supplied permission string. Both go through this one
 * narrow trait so [[OntologyTransformerSpec]] can substitute an in-memory double and stay triplestore-free. Injecting
 * the concrete [[PermissionUtilADM]] instead would pull `GroupService` and its triplestore graph into that spec.
 */
trait DoapResolver {

  /** The default object-access permission string for a resource of the given class. */
  def resourceDoap(project: KnoraProject, user: User, resourceClassIri: String): Task[String]

  /** The default object-access permission string for a value of the given property on the given resource class. */
  def valueDoap(project: KnoraProject, user: User, resourceClassIri: String, propertyIri: String): Task[String]

  /** Reformats a payload-supplied permission literal byte-identically to the create path. */
  def validate(permissionLiteral: String): Task[String]
}

final class DoapResolverLive(
  permissionsResponder: PermissionsResponder,
  permissionUtil: PermissionUtilADM,
  sf: StringFormatter,
) extends DoapResolver {

  override def resourceDoap(project: KnoraProject, user: User, resourceClassIri: String): Task[String] =
    permissionsResponder
      .newResourceDefaultObjectAccessPermissions(project.id, sf.toSmartIri(resourceClassIri), user)
      .map(_.permissionLiteral)

  override def valueDoap(
    project: KnoraProject,
    user: User,
    resourceClassIri: String,
    propertyIri: String,
  ): Task[String] =
    permissionsResponder
      .newValueDefaultObjectAccessPermissions(
        project.id,
        sf.toSmartIri(resourceClassIri),
        sf.toSmartIri(propertyIri),
        user,
      )
      .map(_.permissionLiteral)

  override def validate(permissionLiteral: String): Task[String] =
    permissionUtil.validatePermissions(permissionLiteral)
}

object DoapResolverLive {
  val layer: URLayer[PermissionsResponder & PermissionUtilADM & StringFormatter, DoapResolver] =
    ZLayer.derive[DoapResolverLive]
}
