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
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.{BadRequestException, IRI}
import spray.json.{DefaultJsonProtocol, JsonFormat, NullOptions, RootJsonFormat}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to create a new project.
  *
  * @param shortname   the shortname of the project to be created (unique).
  * @param shortcode   the shortcode of the project to be creates (unique, optional)
  * @param longname    the longname of the project to be created.
  * @param description the description of the project to be created.
  * @param keywords    the keywords of the project to be created.
  * @param logo        the logo of the project to be created.
  * @param status      the status of the project to be created (active = true, inactive = false).
  * @param selfjoin    the status of self-join of the project to be created.
  */
case class CreateProjectApiRequestV1(shortname: String,
                                     shortcode: Option[String],
                                     longname: Option[String],
                                     description: Option[String],
                                     keywords: Option[String],
                                     logo: Option[String],
                                     status: Boolean,
                                     selfjoin: Boolean) extends ProjectV1JsonProtocol {
    def toJsValue = createProjectApiRequestV1Format.write(this)
}

/**
  * Represents an API request payload that asks the Knora API server to update an existing project.
  *
  * @param shortname     the new project's shortname.
  * @param longname      the new project's longname.
  * @param description   the new project's description.
  * @param keywords      the new project's keywords.
  * @param logo          the new project's logo.
  * @param institution   the new project's institution.
  * @param status        the new project's status.
  * @param selfjoin      the new project's self-join status.
  */
case class ChangeProjectApiRequestV1(shortname: Option[String] = None,
                                     longname: Option[String] = None,
                                     description: Option[String] = None,
                                     keywords: Option[String] = None,
                                     logo: Option[String] = None,
                                     institution: Option[IRI] = None,
                                     status: Option[Boolean] = None,
                                     selfjoin: Option[Boolean] = None) extends ProjectV1JsonProtocol {

    val parametersCount = List(
        shortname,
        longname,
        description,
        keywords,
        logo,
        institution,
        status,
        selfjoin
    ).flatten.size

    // something needs to be sent, i.e. everything 'None' is not allowed
    if (parametersCount == 0) throw BadRequestException("No data sent in API request.")

    // change basic project information case
    if (parametersCount > 8) throw BadRequestException("To many parameters sent for changing basic project information.")

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
  * Requests adding an ontology to the project. This is an internal message, which should
  * only be sent by the ontology responder who is responsible for actually creating the
  * ontology.
  *
  * @param projectIri the IRI of the project to be updated.
  * @param ontologyIri the IRI of the ontology to be added.
  * @param apiRequestID the ID of the API request.
  */
case class ProjectOntologyAddV1(projectIri: IRI,
                                ontologyIri: IRI,
                                apiRequestID: UUID) extends ProjectsResponderRequestV1


/**
  * Requests removing an ontology from the project. This is an internal message, which should
  * only be sent by the ontology responder who is responsible for actually removing the
  * ontology.
  *
  * @param projectIri the IRI of the project to be updated.
  * @param ontologyIri the IRI of the ontology to be removed.
  * @param apiRequestID the ID of the API request.
  */
case class ProjectOntologyRemoveV1(projectIri: IRI,
                                   ontologyIri: IRI,
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
  * Represents the Knora API v1 JSON response to a request for a list of admin members inside a single project.
  *
  * @param members    a list of admin members.
  * @param userDataV1 information about the user that made the request.
  */
case class ProjectAdminMembersGetResponseV1(members: Seq[UserDataV1],
                                            userDataV1: UserDataV1) extends KnoraResponseV1 with ProjectV1JsonProtocol {

    def toJsValue = projectAdminMembersGetRequestV1Format.write(this)
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
case class ProjectInfoV1(id: IRI,
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


/**
  * Payload used for updating of an existing project.
  *
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
case class ProjectUpdatePayloadV1(shortname: Option[String] = None,
                                  longname: Option[String] = None,
                                  description: Option[String] = None,
                                  keywords: Option[String] = None,
                                  logo: Option[String] = None,
                                  institution: Option[IRI] = None,
                                  ontologies: Option[Seq[IRI]] = None,
                                  status: Option[Boolean] = None,
                                  selfjoin: Option[Boolean] = None)

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
    implicit val createProjectApiRequestV1Format: RootJsonFormat[CreateProjectApiRequestV1] = rootFormat(lazyFormat(jsonFormat(CreateProjectApiRequestV1, "shortname", "shortcode", "longname", "description", "keywords", "logo", "status", "selfjoin")))
    implicit val changeProjectApiRequestV1Format: RootJsonFormat[ChangeProjectApiRequestV1] = rootFormat(lazyFormat(jsonFormat(ChangeProjectApiRequestV1, "shortname", "longname", "description", "keywords", "logo", "institution", "status", "selfjoin")))
    implicit val projectOperationResponseV1Format: RootJsonFormat[ProjectOperationResponseV1] = rootFormat(lazyFormat(jsonFormat(ProjectOperationResponseV1, "project_info")))

}