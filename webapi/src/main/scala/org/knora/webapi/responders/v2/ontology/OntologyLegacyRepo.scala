/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import zio.ZIO

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.routing.UnsafeZioRun

object OntologyLegacyRepo {

  def getCache(implicit runtime: zio.Runtime[Cache]): Future[Cache.OntologyCacheData] =
    UnsafeZioRun.runToFuture(ZIO.serviceWithZIO[Cache](_.getCacheData))

  private def ensureInternal(iri: SmartIri) = iri.toOntologySchema(InternalSchema)

  def findOntologyBy(
    ontologyIri: SmartIri
  )(implicit ec: ExecutionContext, runtime: zio.Runtime[Cache]): Future[Option[ReadOntologyV2]] =
    getCache.map(_.ontologies.get(ensureInternal(ontologyIri)))

  def findClassBy(
    classIri: SmartIri
  )(implicit ec: ExecutionContext, runtime: zio.Runtime[Cache]): Future[Option[ReadClassInfoV2]] =
    findClassBy(ensureInternal(classIri), ensureInternal(classIri).getOntologyFromEntity)

  def findClassBy(classIri: SmartIri, ontologyIri: SmartIri)(implicit
    ec: ExecutionContext,
    runtime: zio.Runtime[Cache]
  ): Future[Option[ReadClassInfoV2]] =
    findOntologyBy(ensureInternal(ontologyIri)).map(_.flatMap(_.classes.get(ensureInternal(classIri))))
}
