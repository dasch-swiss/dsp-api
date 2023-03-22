/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service

import zio.Task
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

  def findDirectSuperClassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]]

  def findAllSuperClassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]]

  def findAllSuperClassesBy(classIris: List[InternalIri]): Task[List[ReadClassInfoV2]]

  /**
   * Finds all super-classes of a particular class up to the given class in a hierarchy.
   *
   * @param classIris the classes to find the super-classes for
   * @param upToClass the class to stop the search at for the particular branch in the class hierarchy
   * @return all the super-classes of all other branches in the class hierarchy
   */
  def findAllSuperClassesBy(classIris: List[InternalIri], upToClass: InternalIri): Task[List[ReadClassInfoV2]]

  def findDirectSubclassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]]

  def findAllSubclassesBy(classIri: InternalIri): Task[List[ReadClassInfoV2]]
}
