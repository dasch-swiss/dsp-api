/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.domain.model

import zio.Task

import org.knora.webapi.messages.admin.responder.listsmessages.ListsGetResponseADM
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.v3.projects.domain.model.DomainTypes.*

trait ProjectsRepo {

  def findProjectByIri(id: ProjectIri): Task[Option[KnoraProject]]

  def findProjectByShortcode(shortcode: Shortcode): Task[Option[KnoraProject]]

  def findOntologiesByProject(projectId: ProjectIri): Task[List[ReadOntologyV2]]

  def findListsByProject(projectId: ProjectIri): Task[ListsGetResponseADM]

  def countInstancesByClasses(
    shortcode: Shortcode,
    shortname: Shortname,
    classIris: List[String],
  ): Task[Map[String, Int]]

  def getClassesFromOntology(ontologyIri: OntologyIri): Task[List[(String, Map[String, String])]]
}
