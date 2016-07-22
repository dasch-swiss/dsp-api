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
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.permissionmessages.{AdministrativePermissionV1, _}
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{KnoraIriUtil, MessageUtil}
import shapeless.get

import scala.concurrent.Future


/**
  * Provides information about Knora users to other responders.
  */
class PermissionsResponderV1 extends ResponderV1 {

    // Creates IRIs for new Knora user objects.
    val knoraIriUtil = new KnoraIriUtil

    /**
      * Receives a message extending [[org.knora.webapi.messages.v1.responder.permissionmessages.PermissionsResponderRequestV1]].
      * If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case GetProjectAdministrativePermissionsV1(projectIri, userProfileV1) => future2Message(sender(), getProjectAdministrativePermissionsV1(projectIri, userProfileV1), log)
        case GetAdministrativePermissionV1(administrativePermissionIri) => future2Message(sender(), getAdministrativePermissionV1(administrativePermissionIri), log)
        case GetProjectDefaultObjectAccessPermissionsV1(projectIri, userProfileV1) => future2Message(sender(), getProjectDefaultObjectAccessPermissionsV1(projectIri, userProfileV1), log)
        case GetDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri) => future2Message(sender(), getDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    private def getProjectAdministrativePermissionsV1(forProject: IRI, userProfileV1: UserProfileV1): Future[List[IRI]] = {
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectAdministrativePermissions(
                triplestore = settings.triplestoreType,
                projectIri = forProject
            ).toString())
            _ = log.debug(s"getProjectAdministrativePermissionsV1 - query: $sparqlQueryString")

            permissionsQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            _ = log.debug(s"getProjectAdministrativePermissionsV1 - result: ${MessageUtil.toSource(permissionsQueryResponse)}")

            administrativePermissionIris = List("some iri")
        } yield administrativePermissionIris
    }

    /**
      * Gets the [[AdministrativePermissionV1]] object for a group inside a project.
      *
      * @param administrativePermissionIri the IRI of the administrative permission.
      *
      * @return a single [[AdministrativePermissionV1]] object.
      */
    private def getAdministrativePermissionV1(administrativePermissionIri: IRI): Future[AdministrativePermissionV1] = {

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getAdministrativePermission(
                triplestore = settings.triplestoreType,
                administrativePermissionIri = administrativePermissionIri
            ).toString())
            _ = log.debug(s"getGroupAdministrativePermissionV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            _ = log.debug(s"getGroupAdministrativePermissionV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            _ = log.debug(s"getGroupAdministrativePermissionV1 - groupedResult: ${MessageUtil.toSource(groupedPermissionsQueryResponse)}")

            // TODO: Handle restricted permissions correctly (or at all)
            administrativePermission = AdministrativePermissionV1 (
                forProject = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForProject).get.head,
                forGroup = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForGroup).get.head,
                resourceCreationPermissionValues = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasResourceCreationPermission).map(_.toList),
                resourceCreationPermissionProperties = None,
                projectAdministrationPermissionValues = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasProjectAdministrationPermission).map(_.toList),
                projectAdministrationPermissionProperties = None,
                ontologyAdministrationPermissionValues = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasOntologyAdministrationPermission).map(_.toList),
                ontologyAdministrationPermissionProperties = None
            )

        } yield administrativePermission
    }

    /**
      * Gets all IRI's of all default object access permissions defined inside a project.
      *
      * @param forProject the IRI of the project.
      * @param userProfileV1 the [[UserProfileV1]] of the requesting user.
      * @return a list of [[DefaultObjectAccessPermissionV1]] objects.
      */
    private def getProjectDefaultObjectAccessPermissionsV1(forProject: IRI, userProfileV1: UserProfileV1): Future[Option[List[DefaultObjectAccessPermissionV1]]] = {

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getProjectDefaultObjectAccessPermissions(
                triplestore = settings.triplestoreType,
                projectIri = forProject
            ).toString())
            _ = log.debug(s"getGroupAdministrativePermissionV1 - query: $sparqlQueryString")

            permissionsQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            _ = log.debug(s"getGroupAdministrativePermissionV1 - result: ${MessageUtil.toSource(permissionsQueryResponse)}")


            defaultObjectAccessPermissions = Some(List( DefaultObjectAccessPermissionV1()))

        } yield defaultObjectAccessPermissions

    }

    private def getDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri: IRI): Future[DefaultObjectAccessPermissionV1] = {
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getDefaultObjectAccessPermission(
                triplestore = settings.triplestoreType,
                defaultObjectAccessPermissionIri = defaultObjectAccessPermissionIri
            ).toString())
            _ = log.debug(s"getDefaultObjectAccessPermissionV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            _ = log.debug(s"getDefaultObjectAccessPermissionV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")


            defaultObjectAccessPermission = DefaultObjectAccessPermissionV1()

        } yield defaultObjectAccessPermission
    }
}
