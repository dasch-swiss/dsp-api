/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.v1.responder.projectmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, NullOptions, RootJsonFormat}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

// use 'admin' route for writing.

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing a request message that can be sent to [[org.knora.webapi.responders.v1.ProjectsResponderV1]].
  */
sealed trait ProjectsResponderRequestV1 extends KnoraRequestV1

// Requests
/**
  * Get all information about all projects in form of [[ProjectsResponseV1]]. The ProjectsGetRequestV1 returns either
  * something or a NotFound exception if there are no projects found. Administration permission checking is performed.
  *
  * @param userProfile the profile of the user making the request.
  */
case class ProjectsGetRequestV1(userProfile: Option[UserProfileV1]) extends ProjectsResponderRequestV1

/**
  * Get all information about all projects in form of a sequence of [[ProjectInfoV1]]. Returns an empty sequence if
  * no projects are found. Administration permission checking is skipped.
  *
  * @param userProfile the profile of the user making the request.
  */
case class ProjectsGetV1(userProfile: Option[UserProfileV1]) extends ProjectsResponderRequestV1

/**
  * Get info about a single project identified through its IRI. A successful response will be a [[ProjectInfoResponseV1]].
  *
  * @param iri           the IRI of the project.
  * @param userProfileV1 the profile of the user making the request (optional).
  */
case class ProjectInfoByIRIGetRequestV1(iri: IRI, userProfileV1: Option[UserProfileV1]) extends ProjectsResponderRequestV1

/**
  * Get info about a single project identified through its IRI. A successful response will be an [[Option[ProjectInfoV1] ]].
  *
  * @param iri           the IRI of the project.
  * @param userProfileV1 the profile of the user making the request (optional).
  */
case class ProjectInfoByIRIGetV1(iri: IRI, userProfileV1: Option[UserProfileV1]) extends ProjectsResponderRequestV1

/**
  * Find everything about a single project identified through its shortname.
  *
  * @param shortname     of the project.
  * @param userProfileV1 the profile of the user making the request.
  */
case class ProjectInfoByShortnameGetRequestV1(shortname: String, userProfileV1: Option[UserProfileV1]) extends ProjectsResponderRequestV1

/**
  * Returns all users belonging to a project.
  *
  * @param iri           the IRI of the project.
  * @param userProfileV1 the profile of the user making the request.
  */
case class ProjectMembersByIRIGetRequestV1(iri: IRI, userProfileV1: UserProfileV1) extends ProjectsResponderRequestV1

/**
  * Returns all users belonging to a project.
  *
  * @param shortname     of the project
  * @param userProfileV1 the profile of the user making the request.
  */
case class ProjectMembersByShortnameGetRequestV1(shortname: String, userProfileV1: UserProfileV1) extends ProjectsResponderRequestV1

/**
  * Returns all admin users of a project.
  *
  * @param iri           the IRI of the project.
  * @param userProfileV1 the profile of the user making the request.
  */
case class ProjectAdminMembersByIRIGetRequestV1(iri: IRI, userProfileV1: UserProfileV1) extends ProjectsResponderRequestV1

/**
  * Returns all admin users of a project.
  *
  * @param shortname     of the project
  * @param userProfileV1 the profile of the user making the request.
  */
case class ProjectAdminMembersByShortnameGetRequestV1(shortname: String, userProfileV1: UserProfileV1) extends ProjectsResponderRequestV1

// Responses
/**
  * Represents the Knora API v1 JSON response to a request for information about all projects.
  *
  * @param projects information about all existing projects.
  */
case class ProjectsResponseV1(projects: Seq[ProjectInfoV1]) extends KnoraResponseV1 with ProjectV1JsonProtocol {
    def toJsValue: JsValue = projectsResponseV1Format.write(this)
}

/**
  * Represents the Knora API v1 JSON response to a request for information about a single project.
  *
  * @param project_info all information about the project.
  */
case class ProjectInfoResponseV1(project_info: ProjectInfoV1) extends KnoraResponseV1 with ProjectV1JsonProtocol {
    def toJsValue: JsValue = projectInfoResponseV1Format.write(this)
}

/**
  * Represents the Knora API v1 JSON response to a request for a list of members inside a single project.
  *
  * @param members    a list of members.
  * @param userDataV1 information about the user that made the request.
  */
case class ProjectMembersGetResponseV1(members: Seq[UserDataV1],
                                       userDataV1: UserDataV1) extends KnoraResponseV1 with ProjectV1JsonProtocol {

    def toJsValue: JsValue = projectMembersGetRequestV1Format.write(this)
}

/**
  * Represents the Knora API v1 JSON response to a request for a list of admin members inside a single project.
  *
  * @param members    a list of admin members.
  * @param userDataV1 information about the user that made the request.
  */
case class ProjectAdminMembersGetResponseV1(members: Seq[UserDataV1],
                                            userDataV1: UserDataV1) extends KnoraResponseV1 with ProjectV1JsonProtocol {

    def toJsValue: JsValue = projectAdminMembersGetRequestV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents basic information about a project.
  *
  * @param id                 The project's IRI.
  * @param shortname          The project's shortname. Needs to be system wide unique.
  * @param longname           The project's long name. Needs to be system wide unique.
  * @param description        The project's description.
  * @param keywords           The project's keywords.
  * @param logo               The project's logo.
  * @param institution        The project's institution.
  * @param ontologies         The project's ontologies.
  * @param status             The project's status.
  * @param selfjoin           The project's self-join status.
  */
case class ProjectInfoV1(id: IRI,
                         shortname: String,
                         shortcode: String,
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
trait ProjectV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    import org.knora.webapi.messages.v1.responder.usermessages.UserV1JsonProtocol._

    // Some of these formatters have to use lazyFormat because there is a recursive dependency between this
    // protocol and UserV1JsonProtocol. See ttps://github.com/spray/spray-json#jsonformats-for-recursive-types.
    // rootFormat makes it return the expected type again.
    // https://github.com/spray/spray-json#jsonformats-for-recursive-types
    implicit val projectAdminMembersGetRequestV1Format: RootJsonFormat[ProjectAdminMembersGetResponseV1] = rootFormat(lazyFormat(jsonFormat(ProjectAdminMembersGetResponseV1, "members", "userdata")))
    implicit val projectMembersGetRequestV1Format: RootJsonFormat[ProjectMembersGetResponseV1] = rootFormat(lazyFormat(jsonFormat(ProjectMembersGetResponseV1, "members", "userdata")))
    implicit val projectInfoV1Format: JsonFormat[ProjectInfoV1] = jsonFormat11(ProjectInfoV1)
    implicit val projectsResponseV1Format: RootJsonFormat[ProjectsResponseV1] = rootFormat(lazyFormat(jsonFormat(ProjectsResponseV1, "projects")))
    implicit val projectInfoResponseV1Format: RootJsonFormat[ProjectInfoResponseV1] = rootFormat(lazyFormat(jsonFormat(ProjectInfoResponseV1, "project_info")))
}