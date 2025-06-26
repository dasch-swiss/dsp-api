/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.service

import zio.Chunk
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.prelude.ForEachOps

import scala.annotation.tailrec

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris
import org.knora.webapi.slice.common.KnoraIris.*
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.model.OntologyCacheData
final case class OntologyRepoLive(private val converter: IriConverter, private val ontologyCache: OntologyCache)
    extends OntologyRepo {

  override def findById(ontologyIri: InternalIri): Task[Option[ReadOntologyV2]] =
    smartIriMapCache(ontologyIri)(findById)

  private def smartIriMapCache[A](iri: InternalIri)(mapper: (SmartIri, OntologyCacheData) => A): Task[A] =
    toSmartIri(iri).flatMap(smartIri => getCache.map(mapper.apply(smartIri, _)))

  private def smartIrisMapCache[A](iris: List[InternalIri])(mapper: (List[SmartIri], OntologyCacheData) => A): Task[A] =
    toSmartIris(iris).flatMap(smartIris => getCache.map(mapper.apply(smartIris, _)))

  private def toSmartIri(iri: InternalIri) = converter.asInternalSmartIri(iri)

  private def toSmartIris(iris: List[InternalIri]) = ZIO.foreach(iris)(converter.asInternalSmartIri)

  private def getCache = ontologyCache.getCacheData

  private def findById(ontologyIri: SmartIri, cache: OntologyCacheData): Option[ReadOntologyV2] =
    cache.ontologies.get(ontologyIri)

  private def findByClassIri(classIri: SmartIri, cache: OntologyCacheData): Option[ReadOntologyV2] =
    findById(classIri.getOntologyFromEntity, cache)

  override def findAll(): Task[Chunk[ReadOntologyV2]] = getCache.map(_.ontologies.values.toChunk)

  override def findByProject(projectIri: ProjectIri): Task[List[ReadOntologyV2]] =
    getCache.map(_.ontologies.values.filter(_.projectIri.contains(projectIri)).toList)

  override def findClassBy(classIri: InternalIri): Task[Option[ReadClassInfoV2]] =
    smartIriMapCache(classIri)(findClassBy)

  private def findClassBy(classIri: SmartIri, cache: OntologyCacheData): Option[ReadClassInfoV2] =
    findByClassIri(classIri, cache).flatMap(_.classes.get(classIri))

  override def findDirectSubclassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] =
    smartIriMapCache(classIri)(findDirectSubclassesBy)

  private def findDirectSubclassesBy(classIri: SmartIri, cache: OntologyCacheData): List[ReadClassInfoV2] =
    cache.ontologies.values.flatMap(_.classes.values.filter(_.allBaseClasses.contains(classIri))).toList

  private def findDirectSubclassesBy(classIris: List[SmartIri], cache: OntologyCacheData): List[ReadClassInfoV2] =
    classIris.flatMap(findDirectSubclassesBy(_, cache))

  override def findAllSubclassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] =
    smartIriMapCache(classIri)(findAllSubclassesBy)

  private def findAllSubclassesBy(classIris: SmartIri, cache: OntologyCacheData): List[ReadClassInfoV2] =
    findAllSubclassesBy(List(classIris), List.empty, cache)

  @tailrec
  private def findAllSubclassesBy(
    classIris: List[SmartIri],
    acc: List[ReadClassInfoV2],
    cache: OntologyCacheData,
  ): List[ReadClassInfoV2] = {
    val subclassesWithSelf = findDirectSubclassesBy(classIris, cache)
    val subclasses         = subclassesWithSelf.filter(it => !classIris.contains(it.entityInfoContent.classIri))
    subclasses match {
      case Nil     => acc
      case classes => findAllSubclassesBy(toClassIris(classes), acc ::: classes, cache)
    }
  }

  private def toClassIris(classes: List[ReadClassInfoV2]): List[SmartIri] = classes.map(_.entityInfoContent.classIri)

  override def findDirectSuperClassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] = for {
    classSmartIri <- toSmartIri(classIri)
    cache         <- ontologyCache.getCacheData
  } yield findDirectSuperClassesBy(classSmartIri, cache)

  private def findDirectSuperClassesBy(classIri: SmartIri, cache: OntologyCacheData): List[ReadClassInfoV2] =
    findClassBy(classIri, cache).toList.flatMap(_.allBaseClasses).flatMap(findClassBy(_, cache))

  private def findDirectSuperClassesBy(classIris: List[SmartIri], cache: OntologyCacheData): List[ReadClassInfoV2] =
    classIris.flatMap(findDirectSuperClassesBy(_, cache))

  override def findAllSuperClassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] =
    smartIriMapCache(classIri)(findAllSuperClassesBy)

  private def findAllSuperClassesBy(classIri: SmartIri, cache: OntologyCacheData): List[ReadClassInfoV2] =
    findAllSuperClassesBy(List(classIri), List.empty, cache)

  override def findAllSuperClassesBy(classIris: List[InternalIri]): Task[List[ReadClassInfoV2]] =
    smartIrisMapCache(classIris)((iris, cache) => findAllSuperClassesBy(iris, List.empty, cache))

  override def findAllSuperClassesBy(
    classIris: List[InternalIri],
    upToClass: InternalIri,
  ): Task[List[ReadClassInfoV2]] =
    for {
      upToClassIri <- toSmartIri(upToClass)
      result <- smartIrisMapCache(classIris)((iris, cache) =>
                  findAllSuperClassesBy(iris, List.empty, cache, Some(upToClassIri)),
                )
    } yield result.distinct

  @tailrec
  private def findAllSuperClassesBy(
    classIris: List[SmartIri],
    acc: List[ReadClassInfoV2],
    cache: OntologyCacheData,
    upToClassIri: Option[SmartIri] = None,
  ): List[ReadClassInfoV2] = {
    val superClassesWithSelf = findDirectSuperClassesBy(classIris, cache)
    val superClasses         = superClassesWithSelf.filter(it => !classIris.contains(it.entityInfoContent.classIri))
    val filteredSuperClasses = upToClassIri match {
      case Some(iri) => superClasses.filter(_.entityInfoContent.classIri != iri)
      case None      => superClasses
    }
    filteredSuperClasses match {
      case Nil     => acc
      case classes => findAllSuperClassesBy(toClassIris(classes), acc ::: classes, cache, upToClassIri)
    }
  }

  override def findProperty(propertyIri: PropertyIri): Task[Option[ReadPropertyInfoV2]] =
    getCache.map { c =>
      val iri = propertyIri.toInternalSchema
      for {
        ontology <- c.ontologies.get(iri.getOntologyFromEntity)
        property <- ontology.properties.get(iri)
      } yield property
    }
}

object OntologyRepoLive {
  val layer: ZLayer[IriConverter & OntologyCache, Nothing, OntologyRepoLive] =
    ZLayer.fromFunction(OntologyRepoLive.apply _)
}
