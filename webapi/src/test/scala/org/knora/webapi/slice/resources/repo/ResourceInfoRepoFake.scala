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

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.resources.domain.ResourceInfo
import org.knora.webapi.slice.resources.domain.ResourceInfoRepo

final case class ResourceInfoRepoFake(entitiesRef: Ref[Map[(ProjectIri, InternalIri), List[ResourceInfo]]])
    extends ResourceInfoRepo {

  override def findByProjectAndResourceClass(
    projectIri: ProjectIri,
    resourceClass: InternalIri,
  ): UIO[List[ResourceInfo]] =
    entitiesRef.get.map(_.getOrElse((projectIri, resourceClass), List.empty))

  def add(entity: ResourceInfo, projectIRI: ProjectIri, resourceClass: InternalIri): UIO[Unit] = {
    val key = (projectIRI, resourceClass)
    for {
      current <- entitiesRef.get
      updated  = current.get(key) match {
                  case Some(existing) => current + (key -> (existing :+ entity))
                  case None           => current + (key -> List(entity))
                }
      _ <- entitiesRef.set(updated)
    } yield ()
  }

  def addAll(entities: List[ResourceInfo], projectIRI: ProjectIri, resourceClass: InternalIri): UIO[Unit] =
    ZIO.foreachDiscard(entities)(add(_, projectIRI, resourceClass))
}

object ResourceInfoRepoFake {

  val knownProjectIRI    = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  val unknownProjectIRI  = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0002")
  val knownResourceClass = InternalIri("http://some-resource-class")

  def findByProjectAndResourceClass(
    projectIri: ProjectIri,
    resourceClass: InternalIri,
  ): URIO[ResourceInfoRepoFake, List[ResourceInfo]] =
    ZIO.serviceWithZIO[ResourceInfoRepoFake](_.findByProjectAndResourceClass(projectIri, resourceClass))

  def add(entity: ResourceInfo, projectIri: ProjectIri, resourceClass: InternalIri): URIO[ResourceInfoRepoFake, Unit] =
    ZIO.serviceWithZIO[ResourceInfoRepoFake](_.add(entity, projectIri, resourceClass))

  def addAll(
    entities: List[ResourceInfo],
    projectIri: ProjectIri,
    resourceClass: InternalIri,
  ): URIO[ResourceInfoRepoFake, Unit] =
    ZIO.serviceWithZIO[ResourceInfoRepoFake](_.addAll(entities, projectIri, resourceClass))

  val layer: ULayer[ResourceInfoRepoFake] = ZLayer.fromZIO(
    Ref.make(Map.empty[(ProjectIri, InternalIri), List[ResourceInfo]]).map(ResourceInfoRepoFake(_)),
  )
}
