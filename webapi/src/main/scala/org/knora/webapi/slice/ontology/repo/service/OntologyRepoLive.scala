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

  override def findById(iri: InternalIri): Task[Option[ReadOntologyV2]] =
    for {
      ontologyIri <- converter.asInternalSmartIri(iri)
      cache       <- ontologyCache.get
    } yield findByIri(ontologyIri, cache)

  private def findByIri(ontologyIri: SmartIri, cache: OntologyCacheData): Option[ReadOntologyV2] =
    cache.ontologies.get(ontologyIri)

  private def findByClassIri(classIri: InternalIri): Task[Option[ReadOntologyV2]] =
    for {
      classIri <- converter.asInternalSmartIri(classIri)
      cache    <- ontologyCache.get
    } yield _findByClassIri(classIri, cache)

  private def _findByClassIri(classIri: SmartIri, cache: OntologyCacheData): Option[ReadOntologyV2] =
    findByIri(classIri.getOntologyFromEntity, cache)

  private def getOntologiesMap: Task[Map[SmartIri, ReadOntologyV2]] = ontologyCache.get.map(_.ontologies)

  override def findAll(): Task[List[ReadOntologyV2]] = getOntologiesMap.map(_.values.toList)

  override def findClassBy(classIri: InternalIri): Task[Option[ReadClassInfoV2]] = for {
    classSmartIri <- converter.asInternalSmartIri(classIri)
    cache         <- ontologyCache.get
  } yield findClassBy(classSmartIri, cache)

  private def findClassBy(classIri: SmartIri, cache: OntologyCacheData): Option[ReadClassInfoV2] =
    _findByClassIri(classIri, cache).flatMap(_.classes.get(classIri))

  override def findSubclassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] =
    for {
      classIri <- converter.asInternalSmartIri(classIri)
      cache    <- ontologyCache.get
    } yield _findSubclassesBy(classIri, cache)

  private def _findSubclassesBy(classIri: SmartIri, cache: OntologyCacheData): List[ReadClassInfoV2] =
    cache.ontologies.flatMap { case (_, o) => o.classes.values.filter(_.allBaseClasses.contains(classIri)) }.toList

  override def findDirectSuperClassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] = for {
    classSmartIri <- converter.asInternalSmartIri(classIri)
    cache         <- ontologyCache.get
  } yield _findDirectSuperClassesBy(classSmartIri, cache)

  private def _findDirectSuperClassesBy(classIri: SmartIri, cache: OntologyCacheData): List[ReadClassInfoV2] =
    findClassBy(classIri, cache).toList.flatMap(_.allBaseClasses).flatMap(findClassBy(_, cache))

  private def _findDirectSuperClassesBy(classIris: List[SmartIri], cache: OntologyCacheData): List[ReadClassInfoV2] =
    classIris.flatMap(_findDirectSuperClassesBy(_, cache))

  override def findAllSuperClassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] =
    for {
      classSmartIri <- converter.asInternalSmartIri(classIri)
      cache         <- ontologyCache.get
    } yield _findAllSuperClassesBy(List(classSmartIri), List.empty, cache).distinct

  @tailrec
  private def _findAllSuperClassesBy(
    classIris: List[SmartIri],
    acc: List[ReadClassInfoV2],
    cache: OntologyCacheData
  ): List[ReadClassInfoV2] = {
    val directSuperClasses = _findDirectSuperClassesBy(classIris, cache)
    directSuperClasses match {
      case Nil => acc
      case classes =>
        val newAcc               = acc ::: classes
        val next: List[SmartIri] = classes.map(_.entityInfoContent.classIri)
        _findAllSuperClassesBy(next, newAcc, cache)
    }
  }
}

object OntologyRepoLive {
  val layer: ZLayer[IriConverter with OntologyCache, Nothing, OntologyRepoLive] =
    ZLayer.fromFunction(OntologyRepoLive.apply _)
}
