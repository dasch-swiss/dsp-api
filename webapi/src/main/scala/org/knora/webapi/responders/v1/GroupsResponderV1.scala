/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.responders.v1

import akka.actor.Status
import akka.pattern._
import org.apache.jena.sparql.function.library.leviathan.log
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectsResponderRequestV1
import org.knora.webapi.messages.v1.responder.groupmessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.SparqlUtil
import org.knora.webapi.{IRI, NotFoundException, OntologyConstants, UnexpectedMessageException}

import scala.concurrent.Future


/**
  * Returns information about Knora projects.
  */
class GroupsResponderV1 extends ResponderV1 {

    /**
      * Receives a message extending [[ProjectsResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case GroupsGetRequestV1(infoType, userProfile) => future2Message(sender(), getGroupsResponseV1(infoType, userProfile), log)
        case GroupInfoByIRIGetRequest(iri, infoType, userProfile) => future2Message(sender(), getGroupInfoByIRIGetRequest(iri, infoType, userProfile), log)
        case GroupInfoByNameGetRequest(projectIri, groupName, infoType, userProfile) => future2Message(sender(), getGroupInfoByNameGetRequest(projectIri, groupName, infoType, userProfile), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Gets all the groups and returns them as a [[GroupsResponseV1]].
      *
      * @param userProfile the profile of the user that is making the request.
      * @return all the groups as a [[GroupsResponseV1]].
      */
    private def getGroupsResponseV1(infoType: GroupInfoType.Value, userProfile: Option[UserProfileV1]): Future[GroupsResponseV1] = {

        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getProjects(
                triplestore = settings.triplestoreType
            ).toString())
            groupsResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            groupsResponseRows: Seq[VariableResultsRow] = groupsResponse.results.bindings

            groupsWithProperties: Map[String, Map[String, String]] = groupsResponseRows.groupBy(_.rowMap("s")).map {
                case (projIri: String, rows: Seq[VariableResultsRow]) => (projIri, rows.map {
                    case row => (row.rowMap("p"), row.rowMap("o"))
                }.toMap)
            }

            groups = groupsWithProperties.map {
                case (projIri: String, propsMap: Map[String, String]) =>

                    GroupInfoV1(
                        id = projIri,
                        name = propsMap.getOrElse(OntologyConstants.Foaf.Name, ""),
                        description = propsMap.get(OntologyConstants.KnoraBase.Description)
                    )
            }.toVector
        } yield GroupsResponseV1(
            groups = groups,
            userdata = userProfile match {
                case Some(profile) => Some(profile.userData)
                case None => None
            }
        )
    }

    /**
      * Gets the group with the given group Iri and returns the information as a [[GroupInfoResponseV1]].
      *
      * @param groupIri the Iri of the group requested.
      * @param infoType type request: either short or full.
      * @param userProfile the profile of user that is making the request.
      * @return information about the group as a [[GroupInfoResponseV1]].
      */
    private def getGroupInfoByIRIGetRequest(groupIri: IRI, infoType: GroupInfoType.Value, userProfile: Option[UserProfileV1] = None): Future[GroupInfoResponseV1] = {
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getGroupByIri(
                triplestore = settings.triplestoreType,
                groupIri = groupIri
            ).toString())
            groupResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            // check group response
            _ = if (groupResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"For the given group iri '$groupIri' no information was found")
            }

            // get group info
            groupInfo = createGroupInfoV1FromGroupResponse(groupResponse = groupResponse.results.bindings, groupIri = groupIri, infoType = infoType, userProfile)

        } yield GroupInfoResponseV1(
            group_info = groupInfo,
            userdata = userProfile match {
                case Some(profile) => Some(profile.userData)
                case None => None
            }
        )
    }

    /**
      * Gets the group with the given name and returns the information as a [[GroupInfoResponseV1]].
      *
      * @param name the name of the project requested.
      * @param infoType type request: either short or full.
      * @param userProfile the profile of user that is making the request.
      * @return information about the project as a [[GroupInfoResponseV1]].
      */
    private def getGroupInfoByNameGetRequest(projectIri: IRI, name: String, infoType: GroupInfoType.Value, userProfile: Option[UserProfileV1]): Future[GroupInfoResponseV1] = {
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getGroupByName(
                triplestore = settings.triplestoreType,
                name = SparqlUtil.any2SparqlLiteral(name)
            ).toString())
            _ = log.debug(s"sparql query: $sparqlQuery")
            groupResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            _ = log.debug(s"group response: ${groupResponse.toString}")

            // check group response and get group Iri
            groupIri: IRI = if (groupResponse.results.bindings.nonEmpty) {
                groupResponse.results.bindings.head.rowMap("s")
            } else {
                throw NotFoundException(s"For the given group name '$name' no information was found")
            }

            // get group info
            groupInfo = createGroupInfoV1FromGroupResponse(groupResponse = groupResponse.results.bindings, groupIri = groupIri, infoType = infoType, userProfile)

        } yield GroupInfoResponseV1(
            group_info = groupInfo,
            userdata = userProfile match {
                case Some(profile) => Some(profile.userData)
                case None => None
            }
        )
    }


    /**
      * Helper method that turns SPARQL result rows into a [[GroupInfoV1]].
      *
      * @param groupResponse results from the SPARQL query representing information about the group.
      * @param groupIri the Iri of the group the querid information belong to.
      * @param infoType type request: either short or full.
      * @param userProfile the profile of the user that is making the request.
      * @return a [[GroupInfoV1]] representing information about the group.
      */
    private def createGroupInfoV1FromGroupResponse(groupResponse: Seq[VariableResultsRow], groupIri: IRI, infoType: GroupInfoType.Value, userProfile: Option[UserProfileV1]): GroupInfoV1 = {

        val groupProperties = groupResponse.foldLeft(Map.empty[IRI, String]) {
            case (acc, row: VariableResultsRow) =>
                acc + (row.rowMap("p") -> row.rowMap("o"))
        }

        log.debug(s"group properties: ${groupProperties.toString}")

        infoType match {
            //TODO: For now both cases return the same information. This should change in the future.
            case GroupInfoType.FULL =>
                GroupInfoV1(
                    id = groupIri,
                    name = groupProperties.get(OntologyConstants.KnoraBase.GroupName).get,
                    description = groupProperties.get(OntologyConstants.KnoraBase.GroupDescription),
                    belongsToProject = groupProperties.get(OntologyConstants.KnoraBase.BelongsToProject),
                    isActiveGroup = groupProperties.get(OntologyConstants.KnoraBase.IsActiveGroup).map(_.toBoolean),
                    hasSelfJoinEnabled = groupProperties.get(OntologyConstants.KnoraBase.HasSelfJoinEnabled).map(_.toBoolean),
                    hasPermissions = Vector.empty[GroupPermissionV1]

                )
            case GroupInfoType.SHORT | _ =>
                GroupInfoV1(
                    id = groupIri,
                    name = groupProperties.get(OntologyConstants.KnoraBase.GroupName).get,
                    description = groupProperties.get(OntologyConstants.KnoraBase.GroupDescription)
                )
        }
    }


}
