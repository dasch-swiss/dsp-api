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
    smartIriMapCache(ontologyIri)(findById)

  private def smartIriMapCache[A](iri: InternalIri)(mapper: (SmartIri, OntologyCacheData) => A): Task[A] =
    toSmartIri(iri).flatMap(smartIri => getCache.map(mapper.apply(smartIri, _)))

  private def toSmartIri(iri: InternalIri) = converter.asInternalSmartIri(iri)

  private def getCache = ontologyCache.get

  private def findById(ontologyIri: SmartIri, cache: OntologyCacheData): Option[ReadOntologyV2] =
    cache.ontologies.get(ontologyIri)

  private def findByClassIri(classIri: SmartIri, cache: OntologyCacheData): Option[ReadOntologyV2] =
    findById(classIri.getOntologyFromEntity, cache)

  override def findAll(): Task[List[ReadOntologyV2]] = getCache.map(_.ontologies.values.toList)

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
    cache: OntologyCacheData
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
    cache         <- ontologyCache.get
  } yield findDirectSuperClassesBy(classSmartIri, cache)

  private def findDirectSuperClassesBy(classIri: SmartIri, cache: OntologyCacheData): List[ReadClassInfoV2] =
    findClassBy(classIri, cache).toList.flatMap(_.allBaseClasses).flatMap(findClassBy(_, cache))

  private def findDirectSuperClassesBy(classIris: List[SmartIri], cache: OntologyCacheData): List[ReadClassInfoV2] =
    classIris.flatMap(findDirectSuperClassesBy(_, cache))

  override def findAllSuperClassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] =
    smartIriMapCache(classIri)(findAllSuperClassesBy)

  private def findAllSuperClassesBy(classIri: SmartIri, cache: OntologyCacheData): List[ReadClassInfoV2] =
    findAllSuperClassesBy(List(classIri), List.empty, cache)

  @tailrec
  private def findAllSuperClassesBy(
    classIris: List[SmartIri],
    acc: List[ReadClassInfoV2],
    cache: OntologyCacheData
  ): List[ReadClassInfoV2] = {
    val superClassesWithSelf = findDirectSuperClassesBy(classIris, cache)
    val superClasses         = superClassesWithSelf.filter(it => !classIris.contains(it.entityInfoContent.classIri))
    superClasses match {
      case Nil     => acc
      case classes => findAllSuperClassesBy(toClassIris(classes), acc ::: classes, cache)
    }
  }
}

object OntologyRepoLive {
  val layer: ZLayer[IriConverter with OntologyCache, Nothing, OntologyRepoLive] =
    ZLayer.fromFunction(OntologyRepoLive.apply _)
}
