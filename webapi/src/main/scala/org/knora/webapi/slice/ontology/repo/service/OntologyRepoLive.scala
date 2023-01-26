/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.service

import zio.Task
import zio.ZLayer

import scala.annotation.tailrec

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.responders.v2.ontology.Cache.OntologyCacheData
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

final case class OntologyRepoLive(private val converter: IriConverter, private val ontologyCache: OntologyCache)
    extends OntologyRepo {

  override def findById(ontologyIri: InternalIri): Task[Option[ReadOntologyV2]] =
    _smartIriMapCache(ontologyIri)(_findByIri)

  private def _smartIriMapCache[A](iri: InternalIri)(mapper: (SmartIri, OntologyCacheData) => A): Task[A] =
    toSmartIri(iri).flatMap(smartIri => getCache.map(mapper.apply(smartIri, _)))

  private def toSmartIri(iri: InternalIri) = converter.asInternalSmartIri(iri)

  private def getCache = ontologyCache.get

  private def _findByIri(ontologyIri: SmartIri, cache: OntologyCacheData): Option[ReadOntologyV2] =
    cache.ontologies.get(ontologyIri)

  private def _findByClassIri(classIri: SmartIri, cache: OntologyCacheData): Option[ReadOntologyV2] =
    _findByIri(classIri.getOntologyFromEntity, cache)

  override def findAll(): Task[List[ReadOntologyV2]] = getCache.map(_.ontologies.values.toList)

  override def findClassBy(classIri: InternalIri): Task[Option[ReadClassInfoV2]] =
    _smartIriMapCache(classIri)(_findClassBy)

  private def _findClassBy(classIri: SmartIri, cache: OntologyCacheData): Option[ReadClassInfoV2] =
    _findByClassIri(classIri, cache).flatMap(_.classes.get(classIri))

  override def findDirectSubclassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] =
    _smartIriMapCache(classIri)(_findDirectSubclassesBy)

  private def _findDirectSubclassesBy(classIri: SmartIri, cache: OntologyCacheData): List[ReadClassInfoV2] =
    cache.ontologies.values.flatMap(_.classes.values.filter(_.allBaseClasses.contains(classIri))).toList

  private def _findDirectSubclassesBy(classIris: List[SmartIri], cache: OntologyCacheData): List[ReadClassInfoV2] =
    classIris.flatMap(_findDirectSubclassesBy(_, cache))

  override def findSubclassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] =
    _smartIriMapCache(classIri)(_findAllSubclassesBy)

  private def _findAllSubclassesBy(classIris: SmartIri, cache: OntologyCacheData): List[ReadClassInfoV2] =
    _findAllSubclassesBy(List(classIris), List.empty, cache)

  @tailrec
  private def _findAllSubclassesBy(
    classIris: List[SmartIri],
    acc: List[ReadClassInfoV2],
    cache: OntologyCacheData
  ): List[ReadClassInfoV2] =
    _findDirectSubclassesBy(classIris, cache) match {
      case Nil     => acc
      case classes => _findAllSubclassesBy(_toClassIris(classes), acc ::: classes, cache)

    }

  private def _toClassIris(classes: List[ReadClassInfoV2]): List[SmartIri] = classes.map(_.entityInfoContent.classIri)

  override def findDirectSuperClassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] = for {
    classSmartIri <- toSmartIri(classIri)
    cache         <- ontologyCache.get
  } yield _findDirectSuperClassesBy(classSmartIri, cache)

  private def _findDirectSuperClassesBy(classIri: SmartIri, cache: OntologyCacheData): List[ReadClassInfoV2] =
    _findClassBy(classIri, cache).toList.flatMap(_.allBaseClasses).flatMap(_findClassBy(_, cache))

  private def _findDirectSuperClassesBy(classIris: List[SmartIri], cache: OntologyCacheData): List[ReadClassInfoV2] =
    classIris.flatMap(_findDirectSuperClassesBy(_, cache))

  override def findAllSuperClassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] =
    _smartIriMapCache(classIri)(_findAllSuperClassesBy)

  private def _findAllSuperClassesBy(classIri: SmartIri, cache: OntologyCacheData): List[ReadClassInfoV2] =
    _findAllSuperClassesBy(List(classIri), List.empty, cache)

  @tailrec
  private def _findAllSuperClassesBy(
    classIris: List[SmartIri],
    acc: List[ReadClassInfoV2],
    cache: OntologyCacheData
  ): List[ReadClassInfoV2] =
    _findDirectSuperClassesBy(classIris, cache) match {
      case Nil     => acc
      case classes => _findAllSuperClassesBy(_toClassIris(classes), acc ::: classes, cache)
    }
}

object OntologyRepoLive {
  val layer: ZLayer[IriConverter with OntologyCache, Nothing, OntologyRepoLive] =
    ZLayer.fromFunction(OntologyRepoLive.apply _)
}
