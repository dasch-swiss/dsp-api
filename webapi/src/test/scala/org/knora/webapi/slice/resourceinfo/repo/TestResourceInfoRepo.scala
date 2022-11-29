/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.repo

import zio.Ref
import zio.UIO
import zio.ULayer
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.slice.resourceinfo.domain
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfo
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfoRepo

final case class TestResourceInfoRepo(entitiesRef: Ref[Map[(InternalIri, InternalIri), List[ResourceInfo]]])
    extends ResourceInfoRepo {

  override def findByProjectAndResourceClass(
    projectIri: InternalIri,
    resourceClass: InternalIri
  ): UIO[List[ResourceInfo]] =
    entitiesRef.get.map(_.getOrElse((projectIri, resourceClass), List.empty))

  def add(entity: ResourceInfo, projectIRI: InternalIri, resourceClass: InternalIri): UIO[Unit] = {
    val key = (projectIRI, resourceClass)
    entitiesRef.getAndUpdate(entities => entities + (key -> (entity :: entities.getOrElse(key, Nil)))).unit
  }

  def addAll(entities: List[ResourceInfo], projectIri: InternalIri, resourceClass: InternalIri): UIO[Unit] =
    entities.map(add(_, projectIri, resourceClass)).reduce(_ *> _)

  def removeAll(): UIO[Unit] =
    entitiesRef.set(Map.empty[(InternalIri, InternalIri), List[ResourceInfo]])
}

object TestResourceInfoRepo {

  val knownProjectIRI    = domain.InternalIri("http://some-project-iri")
  val knownResourceClass = domain.InternalIri("http://some-resource-class")

  def findByProjectAndResourceClass(
    projectIri: InternalIri,
    resourceClass: InternalIri
  ): ZIO[ResourceInfoRepo, Throwable, List[ResourceInfo]] = {
    ZIO.debug(s"query for $projectIri, $resourceClass")
    ResourceInfoRepo.findByProjectAndResourceClass(projectIri, resourceClass)
  }

  def addAll(
    items: List[ResourceInfo],
    projectIri: InternalIri,
    resourceClass: InternalIri
  ): ZIO[TestResourceInfoRepo, Nothing, Unit] =
    ZIO.service[TestResourceInfoRepo].flatMap(_.addAll(items, projectIri, resourceClass))

  def add(
    entity: ResourceInfo,
    projectIri: InternalIri,
    resourceClass: InternalIri
  ): ZIO[TestResourceInfoRepo, Nothing, Unit] =
    ZIO.service[TestResourceInfoRepo].flatMap(_.add(entity, projectIri, resourceClass))

  def removeAll(): ZIO[TestResourceInfoRepo, Nothing, Unit] =
    ZIO.service[TestResourceInfoRepo].flatMap(_.removeAll())

  val layer: ULayer[TestResourceInfoRepo] =
    ZLayer.fromZIO(Ref.make(Map.empty[(InternalIri, InternalIri), List[ResourceInfo]]).map(TestResourceInfoRepo(_)))
}
