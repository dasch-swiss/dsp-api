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

package org.knora.webapi.messages.v1.responder.projectmessages

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import spray.json.{DefaultJsonProtocol, JsonFormat, NullOptions, RootJsonFormat}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new project.
  *
  * @param shortname          the shortname of the project to be created (unique).
  * @param longname           the longname of the project to be created.
  * @param basepath           the basepath of the project to be created.
  * @param status             the status of the project to be created.
  * @param hasSelfJoinEnabled the status of self-join of the project to be created.
  */
case class CreateProjectApiRequestV1(shortname: String,
                                     longname: Option[String],
                                     description: Option[String],
                                     keywords: Option[String],
                                     logo: Option[String],
                                     basepath: String,
                                     status: Boolean = true,
                                     hasSelfJoinEnabled: Boolean = false) extends ProjectV1JsonProtocol {
    def toJsValue = createProjectApiRequestV1Format.write(this)
}

/**
  * Represents an API request payload that asks the Knora API server to update an existing project.
  *
  * @param shortname          the new project's shortname.
  * @param longname           the new project's longname.
  * @param description        the new project's description.
  * @param keywords           the new project's keywords.
  * @param ontologyGraph      the new project's ontology graph.
  * @param dataGraph          the new project's data graph.
  * @param logo               the new project's logo.
  * @param status             the new project's status.
  * @param hasSelfJoinEnabled the new project's self-join status.
  */
case class ChangeProjectApiRequestV1(shortname: Option[String] = None,
                                     longname: Option[String] = None,
                                     description: Option[String] = None,
                                     keywords: Option[String] = None,
                                     ontologyGraph: Option[String] = None,
                                     dataGraph: Option[String] = None,
                                     logo: Option[String] = None,
                                     status: Option[Boolean] = None,
                                     hasSelfJoinEnabled: Option[Boolean] = None) extends ProjectV1JsonProtocol {
    def toJsValue = changeProjectApiRequestV1Format.write(this)
}


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
  * Get all the existing named graphs from all projects as a vector of [[org.knora.webapi.messages.v1.responder.ontologymessages.NamedGraphV1]].
  *
  * @param userProfile the profile of the user making the request.
  */
case class ProjectsNamedGraphGetV1(userProfile: UserProfileV1) extends ProjectsResponderRequestV1

/**
  * Get info about a single project identified through it's IRI. The response is in form of [[ProjectInfoResponseV1]].
  *
  * @param iri           the IRI of the project.
  * @param userProfileV1 the profile of the user making the request (optional).
  */
case class ProjectInfoByIRIGetRequestV1(iri: IRI, userProfileV1: Option[UserProfileV1]) extends ProjectsResponderRequestV1

/**
  * Get info about a single project identified through it's IRI. The response is in form of [[ProjectInfoV1]].
  *
  * @param iri           the IRI of the project.
  * @param userProfileV1 the profile of the user making the request (optional).
  */
case class ProjectInfoByIRIGetV1(iri: IRI, userProfileV1: Option[UserProfileV1]) extends ProjectsResponderRequestV1

/**
  * Find everything about a single project identified through it's shortname.
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
  * Requests the creation of a new project.
  *
  * @param createRequest the [[CreateProjectApiRequestV1]] information for creation a new project.
  * @param userProfileV1 the user profile of the user creating the new project.
  * @param apiRequestID  the ID of the API request.
  */
case class ProjectCreateRequestV1(createRequest: CreateProjectApiRequestV1,
                                  userProfileV1: UserProfileV1,
                                  apiRequestID: UUID) extends ProjectsResponderRequestV1

/**
  * Requests updating an existing project.
  *
  * @param projectIri           the IRI of the project to be updated.
  * @param changeProjectRequest the data which needs to be update.
  * @param userProfileV1        the user profile of the user requesting the update.
  * @param apiRequestID         the ID of the API request.
  */
case class ProjectChangeRequestV1(projectIri: IRI,
                                  changeProjectRequest: ChangeProjectApiRequestV1,
                                  userProfileV1: UserProfileV1,
                                  apiRequestID: UUID) extends ProjectsResponderRequestV1

/**
  * Request adding a user to a project.
  *
  * @param projectIri    the iri of the project the user is to be added to.
  * @param userIri       the iri of the user that is added to the project.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class ProjectAddUserRequestV1(projectIri: IRI,
                                   userIri: IRI,
                                   userProfileV1: UserProfileV1,
                                   apiRequestID: UUID) extends ProjectsResponderRequestV1

/**
  * Request removing a user from a project.
  *
  * @param projectIri    the iri of the project the user is to be removed from.
  * @param userIri       the iri of the user that is to be removed from the project.
  * @param userProfileV1 the user profile of the user requesting the update.
  * @param apiRequestID  the ID of the API request.
  */
case class ProjectRemoveUserRequestV1(projectIri: IRI,
                                      userIri: IRI,
                                      userProfileV1: UserProfileV1,
                                      apiRequestID: UUID) extends ProjectsResponderRequestV1


// Responses
/**
  * Represents the Knora API v1 JSON response to a request for information about all projects.
  *
  * @param projects information about all existing projects.
  */
case class ProjectsResponseV1(projects: Seq[ProjectInfoV1]) extends KnoraResponseV1 with ProjectV1JsonProtocol {
    def toJsValue = projectsResponseV1Format.write(this)
}

/**
  * Represents the Knora API v1 JSON response to a request for information about a single project.
  *
  * @param project_info all information about the project.
  */
case class ProjectInfoResponseV1(project_info: ProjectInfoV1) extends KnoraResponseV1 with ProjectV1JsonProtocol {
    def toJsValue = projectInfoResponseV1Format.write(this)
}

/**
  * Represents the Knora API v1 JSON response to a request for a list of members inside a single project.
  *
  * @param members    a list of members.
  * @param userDataV1 information about the user that made the request.
  */
case class ProjectMembersGetResponseV1(members: Seq[UserDataV1],
                                       userDataV1: UserDataV1) extends KnoraResponseV1 with ProjectV1JsonProtocol {

    def toJsValue = projectMembersGetRequestV1Format.write(this)
}

/**
  * Represents an answer to a project creating/modifying operation.
  *
  * @param project_info the new project info of the created/modified project.
  */
case class ProjectOperationResponseV1(project_info: ProjectInfoV1) extends KnoraResponseV1 with ProjectV1JsonProtocol {
    def toJsValue = projectOperationResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

case class ProjectInfoV1(id: IRI,
                         shortname: String,
                         longname: Option[String],
                         description: Option[String],
                         keywords: Option[String],
                         logo: Option[String],
                         belongsToInstitution: Option[IRI],
                         ontologyNamedGraph: IRI,
                         dataNamedGraph: IRI,
                         status: Boolean,
                         hasSelfJoinEnabled: Boolean)


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
    implicit val projectMembersGetRequestV1Format: RootJsonFormat[ProjectMembersGetResponseV1] = rootFormat(lazyFormat(jsonFormat(ProjectMembersGetResponseV1, "members", "userdata")))
    implicit val projectInfoV1Format: JsonFormat[ProjectInfoV1] = jsonFormat11(ProjectInfoV1)
    implicit val projectsResponseV1Format: RootJsonFormat[ProjectsResponseV1] = rootFormat(lazyFormat(jsonFormat(ProjectsResponseV1, "projects")))
    implicit val projectInfoResponseV1Format: RootJsonFormat[ProjectInfoResponseV1] = rootFormat(lazyFormat(jsonFormat(ProjectInfoResponseV1, "project_info")))
    implicit val createProjectApiRequestV1Format: RootJsonFormat[CreateProjectApiRequestV1] = rootFormat(lazyFormat(jsonFormat8(CreateProjectApiRequestV1)))
    implicit val changeProjectApiRequestV1Format: RootJsonFormat[ChangeProjectApiRequestV1] = rootFormat(lazyFormat(jsonFormat9(ChangeProjectApiRequestV1)))
    implicit val projectOperationResponseV1Format: RootJsonFormat[ProjectOperationResponseV1] = rootFormat(lazyFormat(jsonFormat1(ProjectOperationResponseV1)))

}