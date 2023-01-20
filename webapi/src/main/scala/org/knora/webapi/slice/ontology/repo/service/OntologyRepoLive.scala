/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.service

import zio.Task
import zio.ZLayer

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

final case class OntologyRepoLive(private val converter: IriConverter, private val ontologyCache: OntologyCache)
    extends OntologyRepo {

  override def findById(iri: InternalIri): Task[Option[ReadOntologyV2]] =
    converter.asInternalSmartIri(iri).flatMap(findBySmartIri)

  private def findByClassIri(classIri: InternalIri): Task[Option[ReadOntologyV2]] =
    converter.getOntologyIriFromClassIri(classIri).flatMap(findById)

  private def findBySmartIri(ontologyIri: SmartIri): Task[Option[ReadOntologyV2]] =
    getOntologiesMap.map(_.get(ontologyIri))

  private def getOntologiesMap: Task[Map[SmartIri, ReadOntologyV2]] = ontologyCache.get.map(_.ontologies)

  override def findAll(): Task[List[ReadOntologyV2]] = getOntologiesMap.map(_.values.toList)

  override def findClassBy(classIri: InternalIri): Task[Option[ReadClassInfoV2]] = for {
    ontologyMaybe <- findByClassIri(classIri)
    classIri      <- converter.asInternalSmartIri(classIri)
  } yield ontologyMaybe.flatMap(_.classes.get(classIri))

  override def findSubclassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] =
    for {
      ontologyMaybe <- findByClassIri(classIri)
    } yield ontologyMaybe
      .map(_.classes.values.toList.filter(_.allBaseClasses.map(_.toInternalIri).contains(classIri)))
      .getOrElse(List.empty)
}

object OntologyRepoLive {
  val layer: ZLayer[IriConverter with OntologyCache, Nothing, OntologyRepoLive] =
    ZLayer.fromFunction(OntologyRepoLive.apply _)
}
