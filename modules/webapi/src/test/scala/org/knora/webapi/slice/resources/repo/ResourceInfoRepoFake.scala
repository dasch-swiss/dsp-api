/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.Ref
import zio.UIO
import zio.ULayer
import zio.URIO
import zio.ZIO
import zio.ZLayer

import java.time.Instant

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.resources.domain.ResourceInfo
import org.knora.webapi.slice.resources.domain.ResourceInfoRepo

final case class ResourceInfoRepoFake(entitiesRef: Ref[Map[ProjectIri, List[ResourceInfo]]]) extends ResourceInfoRepo {

  override def find(
    projectIri: ProjectIri,
    resourceClass: Option[InternalIri],
    cutoff: Option[Instant],
  ): UIO[List[ResourceInfo]] =
    entitiesRef.get.map(
      _.getOrElse(projectIri, List.empty)
        .filter(info => resourceClass.forall(_ == info.resourceClass))
        .filter(info => cutoff.forall(c => !info.lastModificationDate.getOrElse(info.creationDate).isBefore(c))),
    )

  def add(entity: ResourceInfo, projectIri: ProjectIri): UIO[Unit] =
    entitiesRef.update(current => current.updated(projectIri, current.getOrElse(projectIri, List.empty) :+ entity))

  def addAll(entities: List[ResourceInfo], projectIri: ProjectIri): UIO[Unit] =
    ZIO.foreachDiscard(entities)(add(_, projectIri))
}

object ResourceInfoRepoFake {

  val knownProjectIRI    = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  val unknownProjectIRI  = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0002")
  val knownResourceClass = InternalIri("http://some-resource-class")

  def add(entity: ResourceInfo, projectIri: ProjectIri): URIO[ResourceInfoRepoFake, Unit] =
    ZIO.serviceWithZIO[ResourceInfoRepoFake](_.add(entity, projectIri))

  def addAll(entities: List[ResourceInfo], projectIri: ProjectIri): URIO[ResourceInfoRepoFake, Unit] =
    ZIO.serviceWithZIO[ResourceInfoRepoFake](_.addAll(entities, projectIri))

  val layer: ULayer[ResourceInfoRepoFake] =
    ZLayer.fromZIO(Ref.make(Map.empty[ProjectIri, List[ResourceInfo]]).map(ResourceInfoRepoFake(_)))
}
