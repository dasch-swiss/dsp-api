package org.knora.webapi.slice.common.service

import zio._

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.IRI
import org.knora.webapi.messages.store.triplestoremessages.LiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

final case class PredicateObjectMapper(private val iriConverter: IriConverter) {

  def getListOption[A <: LiteralV2](key: IRI, propertiesMap: ConstructPredicateObjects): Task[Option[List[A]]] =
    for {
      smartIri <- iriConverter.asInternalSmartIri(key)
      props     = propertiesMap.get(smartIri)
      values   <- ZIO.foreach(props)(ZIO.foreach(_)(cast[A]).map(_.toList))
    } yield values

  private def cast[A <: LiteralV2](prop: LiteralV2): Task[A] =
    ZIO.attempt(prop.asInstanceOf[A]).logError(s"Could not cast $prop.")

  def getList[A <: LiteralV2](key: IRI, propertiesMap: ConstructPredicateObjects): Task[List[A]] =
    getListOption[A](key, propertiesMap).map(_.getOrElse(List.empty[A]))

  def getListOrFail[A <: LiteralV2](key: IRI, propertiesMap: ConstructPredicateObjects): Task[List[A]] =
    getListOption[A](key, propertiesMap)
      .flatMap(ZIO.fromOption(_))
      .orElseFail(InconsistentRepositoryDataException(s"PropertiesMap has no $key defined."))

  def getSingleOption[A <: LiteralV2](key: IRI, propertiesMap: ConstructPredicateObjects): Task[Option[A]] =
    getListOption[A](key, propertiesMap).map(_.flatMap(_.headOption))

  def getSingleOrFail[A <: LiteralV2](key: IRI, propertiesMap: ConstructPredicateObjects): Task[A] =
    getSingleOption[A](key, propertiesMap)
      .flatMap(ZIO.fromOption(_))
      .orElseFail(InconsistentRepositoryDataException(s"PropertiesMap has no value for $key defined."))
}

object PredicateObjectMapper {
  val layer: URLayer[IriConverter, PredicateObjectMapper] = ZLayer.fromFunction(PredicateObjectMapper.apply _)
}
