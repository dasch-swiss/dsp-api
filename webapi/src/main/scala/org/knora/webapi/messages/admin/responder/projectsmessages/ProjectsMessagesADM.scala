/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.admin.responder.projectsmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.{KnoraRequestADM, KnoraResponseADM}
import org.knora.webapi.responders.admin.ProjectsResponderADM
import org.knora.webapi.{BadRequestException, IRI}
import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing a request message that can be sent to [[ProjectsResponderADM]].
  */
sealed trait ProjectsResponderRequestADM extends KnoraRequestADM

// Requests
/**
  * Get all information about all projects in form of [[ProjectsResponseADMADM]]. The ProjectsGetRequestV1 returns either
  * something or a NotFound exception if there are no projects found. Administration permission checking is performed.
  *
  * @param user the profile of the user making the request.
  */
case class ProjectsGetRequestADMADM(user: Option[UserADM]) extends ProjectsResponderRequestADM

/**
  * Get all information about all projects in form of a sequence of [[ProjectADM]]. Returns an empty sequence if
  * no projects are found. Administration permission checking is skipped.
  *
  * @param user the profile of the user making the request.
  */
case class ProjectsGetADM(user: Option[UserADM]) extends ProjectsResponderRequestADM

/**
  * Get info about a single project identified either through its IRI, shortname or shortcode. The response is in form
  * of [[ProjectResponseADMADM]]. External use.
  *
  * @param maybeIri           the IRI of the project.
  * @param maybeShortname the project's short name.
  * @param maybeShortcode the project's shortcode.
  * @param user the profile of the user making the request (optional).
  */
case class ProjectGetRequestADMADM(maybeIri: Option[IRI] = None,
                                   maybeShortname: Option[String] = None,
                                   maybeShortcode: Option[String] = None,
                                   user: Option[UserADM]) extends ProjectsResponderRequestADM {
    val parametersCount = List(
        maybeIri,
        maybeShortname,
        maybeShortcode
    ).flatten.size

    // only one is allowed
    if (parametersCount == 0 || parametersCount > 1) throw BadRequestException("Need to provide either project IRI, shortname, or shortcode.")
}

/**
  * Get info about a single project identified either through its IRI, shortname or shortcode. The response is in form
  * of [[ProjectADM]]. Internal use only.
  *
  * @param maybeIri           the IRI of the project.
  * @param maybeShortname the project's short name.
  * @param maybeShortcode the project's shortcode.
  * @param user the profile of the user making the request (optional).
  */
case class ProjectGetADM(maybeIri: Option[IRI],
                         maybeShortname: Option[String],
                         maybeShortcode: String,
                         user: Option[UserADM]) extends ProjectsResponderRequestADM {

    val parametersCount = List(
        maybeIri,
        maybeShortname,
        maybeShortcode
    ).flatten.size

    // Only one is allowed
    if (parametersCount == 0 || parametersCount > 1) throw BadRequestException("Need to provide either project IRI, shortname, or shortcode.")
}

/**
  * Get all the existing ontologies from all projects as a sequence of [[org.knora.webapi.messages.admin.responder.ontologiesmessages.OntologyInfoADM]].
  *
  * @param maybeProjectIri the profile of the user making the request.
  */
case class ProjectsOntologiesGetADM(maybeProjectIri: IRI) extends ProjectsAdminJsonProtocol

// Responses
/**
  * Represents the Knora API v1 JSON response to a request for information about all projects.
  *
  * @param projects information about all existing projects.
  */
case class ProjectsResponseADMADM(projects: Seq[ProjectADM]) extends KnoraResponseADM with ProjectsAdminJsonProtocol {
    def toJsValue = projectsResponseADMFormat.write(this)
}

/**
  * Represents the Knora API v1 JSON response to a request for information about a single project.
  *
  * @param project all information about the project.
  */
case class ProjectResponseADMADM(project: ProjectADM) extends KnoraResponseADM with ProjectsAdminJsonProtocol {
    def toJsValue = projectInfoResponseADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages


/**
  * Represents basic information about a project.
  *
  * @param id                 The project's IRI.
  * @param shortname          The project's shortname. Needs to be system wide unique.
  * @param longname           The project's long name.
  * @param description        The project's description.
  * @param keywords           The project's keywords.
  * @param logo               The project's logo.
  * @param institution        The project's institution.
  * @param ontologies         The project's ontologies.
  * @param status             The project's status.
  * @param selfjoin           The project's self-join status.
  */
case class ProjectADM(id: IRI,
                      shortname: String,
                      shortcode: Option[String],
                      longname: Option[String],
                      description: Option[String],
                      keywords: Option[String],
                      logo: Option[String],
                      institution: Option[IRI],
                      ontologies: Seq[IRI],
                      status: Boolean,
                      selfjoin: Boolean)


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formating

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about projects.
  */
trait ProjectsAdminJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {

    implicit val projectADMFormat: JsonFormat[ProjectADM] = jsonFormat11(ProjectADM)
    implicit val projectsResponseADMFormat: RootJsonFormat[ProjectsResponseADMADM] = rootFormat(lazyFormat(jsonFormat(ProjectsResponseADMADM, "projects")))
    implicit val projectInfoResponseADMFormat: RootJsonFormat[ProjectResponseADMADM] = rootFormat(lazyFormat(jsonFormat(ProjectResponseADMADM, "project")))
}