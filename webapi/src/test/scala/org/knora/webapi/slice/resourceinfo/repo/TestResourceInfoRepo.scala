package org.knora.webapi.slice.resourceinfo.repo

import org.knora.webapi.IRI
import org.knora.webapi.slice.resourceinfo.repo.ResourceInfoRepo.{
  ASC,
  creationDate,
  DESC,
  lastModificationDate,
  Order,
  OrderBy
}
import zio.{Ref, UIO, ULayer, ZIO, ZLayer}

final case class TestResourceInfoRepo(entitiesRef: Ref[Map[(IRI, IRI), List[ResourceInfo]]]) extends ResourceInfoRepo {

  override def findByProjectAndResourceClass(
    projectIri: IRI,
    resourceClass: IRI,
    ordering: (ResourceInfoRepo.OrderBy, ResourceInfoRepo.Order)
  ): UIO[List[ResourceInfo]] = {
    val key = (projectIri, resourceClass)
    val result = entitiesRef.get.map(
      _.getOrElse(key, List.empty).sortBy(info =>
        ordering._1 match {
          case `lastModificationDate` => info.lastModificationDate
          case `creationDate`         => info.creationDate
        }
      )
    )
    ordering._2 match {
      case ASC  => result
      case DESC => result.map(_.reverse)
    }
  }

  def add(entity: ResourceInfo, projectIRI: String, resourceClass: String): UIO[Unit] = {
    val key = (projectIRI, resourceClass)
    entitiesRef.getAndUpdate(entities => entities + (key -> (entity :: entities.getOrElse(key, Nil)))).unit
  }

  def addAll(entities: List[ResourceInfo], projectIri: IRI, resourceClass: IRI): UIO[Unit] =
    entities.map(add(_, projectIri, resourceClass)).reduce(_ *> _)

  def removeAll(): UIO[Unit] =
    entitiesRef.set(Map.empty[(IRI, IRI), List[ResourceInfo]])
}

object TestResourceInfoRepo {

  val knownProjectIRI    = "knownProjectIri"
  val knownResourceClass = "knownResourceClass"

  def findByProjectAndResourceClass(
    projectIri: IRI,
    resourceClass: IRI,
    ordering: (OrderBy, Order)
  ): ZIO[TestResourceInfoRepo, Nothing, List[ResourceInfo]] =
    ZIO.service[TestResourceInfoRepo].flatMap(_.findByProjectAndResourceClass(projectIri, resourceClass, ordering))

  def addAll(items: List[ResourceInfo], projectIri: IRI, resourceClass: IRI): ZIO[TestResourceInfoRepo, Nothing, Unit] =
    ZIO.service[TestResourceInfoRepo].flatMap(_.addAll(items, projectIri, resourceClass))

  def add(entity: ResourceInfo, projectIri: IRI, resourceClass: IRI): ZIO[TestResourceInfoRepo, Nothing, Unit] =
    ZIO.service[TestResourceInfoRepo].flatMap(_.add(entity, projectIri, resourceClass))

  def removeAll(): ZIO[TestResourceInfoRepo, Nothing, Unit] =
    ZIO.service[TestResourceInfoRepo].flatMap(_.removeAll())

  val layer: ULayer[TestResourceInfoRepo] =
    ZLayer.fromZIO(Ref.make(Map.empty[(IRI, IRI), List[ResourceInfo]]).map(TestResourceInfoRepo(_)))
}
