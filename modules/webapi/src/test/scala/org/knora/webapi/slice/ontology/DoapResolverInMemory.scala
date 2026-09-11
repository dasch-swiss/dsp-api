/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology

import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User

/**
 * Hermetic [[DoapResolver]] test double. Returns fixed strings so [[OntologyTransformerSpec]] stays triplestore-free:
 * `resourceDoap` and `valueDoap` supply the default the transformer applies to an entity with no payload permission,
 * and `validate` supplies the string the transformer applies to an entity that carries one. The three are distinct so
 * a test can prove which branch ran.
 */
final class DoapResolverInMemory(resourceStr: String, valueStr: String, validatedStr: String) extends DoapResolver {

  override def resourceDoap(project: KnoraProject, user: User, resourceClassIri: String): Task[String] =
    ZIO.succeed(resourceStr)

  override def valueDoap(
    project: KnoraProject,
    user: User,
    resourceClassIri: String,
    propertyIri: String,
  ): Task[String] =
    ZIO.succeed(valueStr)

  override def validate(permissionLiteral: String): Task[String] =
    ZIO.succeed(validatedStr)
}

object DoapResolverInMemory {
  def layer(resourceStr: String, valueStr: String, validatedStr: String): ULayer[DoapResolver] =
    ZLayer.succeed(new DoapResolverInMemory(resourceStr, valueStr, validatedStr))
}
