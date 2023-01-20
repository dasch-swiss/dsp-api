/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service

import zio.Task
import zio.ZIO
import zio.macros.accessible

import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.slice.common.service.Repository
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

@accessible
trait OntologyRepo extends Repository[ReadOntologyV2, InternalIri] {

  override def findById(id: InternalIri): Task[Option[ReadOntologyV2]]

  override def findAll(): Task[List[ReadOntologyV2]]

  def findClassBy(classIri: InternalIri): Task[Option[ReadClassInfoV2]]

  def findClassesBy(classIris: List[InternalIri]): Task[List[ReadClassInfoV2]] =
    ZIO.foreach(classIris)(findClassBy).map(_.flatten)

  def findSuperClassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]] =
    findClassBy(classIri)
      .map(_.toList.flatMap(_.allBaseClasses.map(_.toInternalIri)))
      .flatMap(findClassesBy)

  def findSubclassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]]
}
