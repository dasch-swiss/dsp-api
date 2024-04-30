/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.repo

import zio.Ref
import zio.Task
import zio.UIO
import zio.ULayer
import zio.URIO
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.slice.admin.api.model.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.slice.resourceinfo.domain
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfo
import org.knora.webapi.slice.resourceinfo.domain.ResourceInfoRepo

final case class ResourceInfoRepoFake(entitiesRef: Ref[Map[(IriIdentifier, InternalIri), List[ResourceInfo]]])
    extends ResourceInfoRepo {

  override def findByProjectAndResourceClass(
    projectIri: IriIdentifier,
    resourceClass: InternalIri,
  ): Task[List[ResourceInfo]] =
    entitiesRef.get.map(_.getOrElse((projectIri, resourceClass), List.empty))

  def add(entity: ResourceInfo, projectIRI: IriIdentifier, resourceClass: InternalIri): UIO[Unit] = {
    val key = (projectIRI, resourceClass)
    entitiesRef.getAndUpdate(entities => entities + (key -> (entity :: entities.getOrElse(key, Nil)))).unit
  }

  def addAll(entities: List[ResourceInfo], projectIri: IriIdentifier, resourceClass: InternalIri): UIO[Unit] =
    entities.map(add(_, projectIri, resourceClass)).reduce(_ *> _)

  def removeAll(): UIO[Unit] =
    entitiesRef.set(Map.empty[(IriIdentifier, InternalIri), List[ResourceInfo]])
}

object ResourceInfoRepoFake {

  val knownProjectIRI    = IriIdentifier.unsafeFrom("http://rdfh.ch/projects/0001")
  val unknownProjectIRI  = IriIdentifier.unsafeFrom("http://rdfh.ch/projects/0002")
  val knownResourceClass = domain.InternalIri("http://some-resource-class")

  def findByProjectAndResourceClass(
    projectIri: IriIdentifier,
    resourceClass: InternalIri,
  ): ZIO[ResourceInfoRepoFake, Throwable, List[ResourceInfo]] =
    ZIO.service[ResourceInfoRepoFake].flatMap(_.findByProjectAndResourceClass(projectIri, resourceClass))

  def addAll(
    items: List[ResourceInfo],
    projectIri: IriIdentifier,
    resourceClass: InternalIri,
  ): URIO[ResourceInfoRepoFake, Unit] =
    ZIO.service[ResourceInfoRepoFake].flatMap(_.addAll(items, projectIri, resourceClass))

  def add(
    entity: ResourceInfo,
    projectIri: IriIdentifier,
    resourceClass: InternalIri,
  ): URIO[ResourceInfoRepoFake, Unit] =
    ZIO.service[ResourceInfoRepoFake].flatMap(_.add(entity, projectIri, resourceClass))

  def removeAll(): URIO[ResourceInfoRepoFake, Unit] =
    ZIO.service[ResourceInfoRepoFake].flatMap(_.removeAll())

  val layer: ULayer[ResourceInfoRepoFake] =
    ZLayer.fromZIO(Ref.make(Map.empty[(IriIdentifier, InternalIri), List[ResourceInfo]]).map(ResourceInfoRepoFake(_)))
}
