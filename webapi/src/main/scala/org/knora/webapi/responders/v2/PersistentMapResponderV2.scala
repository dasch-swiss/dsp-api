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

package org.knora.webapi.responders.v2

import java.time.Instant

import akka.pattern._
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, OntologyConstants}
import org.knora.webapi.messages.store.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse, SparqlUpdateRequest, SparqlUpdateResponse}
import org.knora.webapi.messages.v2.responder.persistentmapmessages._
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.KnoraIdUtil
import org.knora.webapi._

import scala.concurrent.Future

/**
  * Manages storage in `knora-base:Map` objects on behalf of other responders. Since this actor does no permission
  * checking, it should not be used directly by routes.
  */
class PersistentMapResponderV2 extends Responder {
    private val knoraIdUtil = new KnoraIdUtil

    def receive: PartialFunction[Any, Unit] = {
        case mapEntryGetRequest: PersistentMapEntryGetRequestV2 => future2Message(sender(), getPersistentMapEntryV2(mapEntryGetRequest), log)
        case mapGetRequest: PersistentMapGetRequestV2 => future2Message(sender(), getPersistentMapV2(mapGetRequest), log)
        case mapEntryPutRequest: PersistentMapEntryPutRequestV2 => future2Message(sender, putPersistentMapEntryV2(mapEntryPutRequest), log)
        case mapEntryDeleteRequest: PersistentMapEntryDeleteRequestV2 => future2Message(sender, deletePersistentMapEntryV2(mapEntryDeleteRequest), log)
        case mapDeleteRequest: PersistentMapDeleteRequestV2 => future2Message(sender, deletePersistentMapV2(mapDeleteRequest), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Returns a single entry from a persistent map.
      *
      * @param request the request message.
      * @return a persistent map entry.
      */
    private def getPersistentMapEntryV2(request: PersistentMapEntryGetRequestV2): Future[PersistentMapEntryV2] = {
        for {
            entryRequestSparql <- Future(queries.sparql.v2.txt.getMapEntry(
                triplestore = settings.triplestoreType,
                mapIri = knoraIdUtil.makeMapIri(request.mapPath),
                mapEntryKey = request.mapEntryKey
            ).toString())

            constructResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(entryRequestSparql)).mapTo[SparqlConstructResponse]
            entries = readConstructResponse(requestedMapPath = request.mapPath, constructResponse = constructResponse).entries

            _ = if (entries.isEmpty) {
                throw NotFoundException(s"No entry found in persistent map ${request.mapPath} with key ${request.mapEntryKey}")
            }

            _ = if (entries.size > 1) {
                throw InconsistentTriplestoreDataException(s"Multiple entries found in persistent map ${request.mapPath} with key ${request.mapEntryKey}")
            }
        } yield entries.head
    }

    /**
      * Returns a persistent map with all its entries.
      *
      * @param request the request message.
      * @return a persistent map.
      */
    private def getPersistentMapV2(request: PersistentMapGetRequestV2): Future[PersistentMapV2] = {
        for {
            mapRequestSparql <- Future(queries.sparql.v2.txt.getMap(
                triplestore = settings.triplestoreType,
                mapIri = knoraIdUtil.makeMapIri(request.mapPath)
            ).toString())

            constructResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(mapRequestSparql)).mapTo[SparqlConstructResponse]
        } yield readConstructResponse(requestedMapPath = request.mapPath, constructResponse = constructResponse)
    }

    /**
      * Sets the value of a persistent map entry.
      *
      * @param request the request mesage.
      * @return a confirmation message.
      */
    private def putPersistentMapEntryV2(request: PersistentMapEntryPutRequestV2): Future[PersistentMapEntryPutResponseV2] = {
        def makeTaskFuture(): Future[PersistentMapEntryPutResponseV2] = {
            // An xsd:dateTimeStamp for the map entry, and for the map if it doesn't exist yet.
            val currentTime = Instant.now.toString

            val updateFuture = for {
            // Create the persistent map if it doesn't exist already.

                createMapSparql <- Future(queries.sparql.v2.txt.createMap(
                    mapNamedGraphIri = OntologyConstants.NamedGraphs.PersistentMapNamedGraph,
                    triplestore = settings.triplestoreType,
                    mapIri = knoraIdUtil.makeMapIri(request.mapPath),
                    currentTime = currentTime
                ).toString())

                createMapSparqlUpdateResponse: SparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(createMapSparql)).mapTo[SparqlUpdateResponse]

                setMapEntryValueSparql = queries.sparql.v2.txt.setMapEntryValue(
                    triplestore = settings.triplestoreType,
                    mapNamedGraphIri = OntologyConstants.NamedGraphs.PersistentMapNamedGraph,
                    mapIri = knoraIdUtil.makeMapIri(request.mapPath),
                    mapEntryIri = knoraIdUtil.makeRandomMapEntryIri,
                    mapEntryKey = request.mapEntryKey,
                    mapEntryValue = request.sparqlEncodedMapEntryValue,
                    currentTime = currentTime
                ).toString()

                setMapEntryValueUpdateResponse: SparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(setMapEntryValueSparql)).mapTo[SparqlUpdateResponse]

                // Check that both the map and the map entry now exist, and that the map entry has the correct value.

                persistentMapEntry: PersistentMapEntryV2 <- getPersistentMapEntryV2(PersistentMapEntryGetRequestV2(mapPath = request.mapPath, mapEntryKey = request.mapEntryKey))

                _ = if (persistentMapEntry.key != request.mapEntryKey || persistentMapEntry.value != request.mapEntryValue) {
                    throw UpdateNotPerformedException(s"The value of persistent map entry ${request.mapEntryKey} in map ${request.mapPath} was not set. Please report this as a possible bug.")
                }
            } yield PersistentMapEntryPutResponseV2()

            updateFuture.recover {
                case _: NotFoundException => throw UpdateNotPerformedException(s"The value of persistent map entry ${request.mapEntryKey} in map ${request.mapPath} was not set. Please report this as a possible bug.")
            }
        }

        val mapIri = knoraIdUtil.makeMapIri(request.mapPath)

        for {
        // Run the update while holding an update lock on the map.
            taskResult <- IriLocker.runWithIriLock(
                request.apiRequestID,
                mapIri,
                () => makeTaskFuture()
            )
        } yield taskResult
    }

    /**
      * Deletes an entry from a persistent map.
      *
      * @param request the request message.
      * @return a confirmation message.
      */
    private def deletePersistentMapEntryV2(request: PersistentMapEntryDeleteRequestV2): Future[PersistentMapEntryDeleteResponseV2] = {
        def makeTaskFuture(): Future[PersistentMapEntryDeleteResponseV2] = {
            val updateFuture = for {
                updateSparql <- Future(queries.sparql.v2.txt.deleteMapEntry(
                    triplestore = settings.triplestoreType,
                    mapNamedGraphIri = OntologyConstants.NamedGraphs.PersistentMapNamedGraph,
                    mapIri = knoraIdUtil.makeMapIri(request.mapPath),
                    mapEntryKey = request.mapEntryKey
                ).toString())

                updateResponse: SparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

                // Check that the map entry no longer exists.
                persistentMapEntry: PersistentMapEntryV2 <- getPersistentMapEntryV2(PersistentMapEntryGetRequestV2(mapPath = request.mapPath, mapEntryKey = request.mapEntryKey))
            } yield PersistentMapEntryDeleteResponseV2()

            // We expect a NotFoundException if the deletion was successful.
            updateFuture.recover {
                case _: NotFoundException => PersistentMapEntryDeleteResponseV2()
            }
        }

        val mapIri = knoraIdUtil.makeMapIri(request.mapPath)

        for {
        // Run the update while holding an update lock on the map.
            taskResult <- IriLocker.runWithIriLock(
                request.apiRequestID,
                mapIri,
                () => makeTaskFuture()
            )
        } yield taskResult
    }

    /**
      * Deletes a persistent map along with all its entries.
      *
      * @param request the request message.
      * @return a confirmation message.
      */
    private def deletePersistentMapV2(request: PersistentMapDeleteRequestV2): Future[PersistentMapDeleteResponseV2] = {
        def makeTaskFuture(): Future[PersistentMapDeleteResponseV2] = {
            val updateFuture = for {
                updateSparql <- Future(queries.sparql.v2.txt.deleteMap(
                    triplestore = settings.triplestoreType,
                    mapNamedGraphIri = OntologyConstants.NamedGraphs.PersistentMapNamedGraph,
                    mapIri = knoraIdUtil.makeMapIri(request.mapPath)
                ).toString())

                updateResponse: SparqlUpdateResponse <- (storeManager ? SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

                // Check that the map no longer exists.
                persistentMap <- getPersistentMapV2(PersistentMapGetRequestV2(mapPath = request.mapPath))
            } yield PersistentMapDeleteResponseV2()

            // We expect a NotFoundException if the deletion was successful.
            updateFuture.recover {
                case _: NotFoundException => PersistentMapDeleteResponseV2()
            }
        }

        val mapIri = knoraIdUtil.makeMapIri(request.mapPath)

        for {
        // Run the update while holding an update lock on the map.
            taskResult <- IriLocker.runWithIriLock(
                request.apiRequestID,
                mapIri,
                () => makeTaskFuture()
            )
        } yield taskResult
    }

    /**
      * Transforms a [[SparqlConstructResponse]], returned by a persistent map query, into a [[PersistentMapV2]].
      *
      * @param requestedMapPath  the path of the map that was requested.
      * @param constructResponse the [[SparqlConstructResponse]]
      * @return a [[PersistentMapV2]].
      */
    private def readConstructResponse(requestedMapPath: String, constructResponse: SparqlConstructResponse): PersistentMapV2 = {
        // Partition the statements in the construct response into statements about the map and statements about the
        // map's entries.
        val (mapStatements: Map[IRI, Seq[(IRI, String)]], mapEntryStatements: Map[IRI, Seq[(IRI, String)]]) = constructResponse.statements.partition {
            case (_: IRI, predicatesAndObjects: Seq[(IRI, String)]) =>
                predicatesAndObjects.exists {
                    case (pred, obj) => pred == OntologyConstants.Rdf.Type && obj == OntologyConstants.KnoraBase.Map
                }
        }

        // There must be exactly one map in the response.

        if (mapStatements.isEmpty) {
            throw NotFoundException(s"No persistent map found at path $requestedMapPath")
        }

        if (mapStatements.size > 1) {
            throw InconsistentTriplestoreDataException(s"Persistent map query returned more than one map: ${mapStatements.keys.mkString(", ")}")
        }

        // Transform the statements about map entries into PersistentMapEntryV2 objects.
        val mapEntries: Set[PersistentMapEntryV2] = mapEntryStatements.map {
            case (entrySubjectIri: IRI, entryPredicatesAndObjects: Seq[(IRI, String)]) =>
                val entryPredicateObjectMap: Map[IRI, String] = entryPredicatesAndObjects.toMap

                PersistentMapEntryV2(
                    key = entryPredicateObjectMap(OntologyConstants.KnoraBase.MapEntryKey),
                    value = entryPredicateObjectMap(OntologyConstants.KnoraBase.MapEntryValue),
                    lastModificationDate = Instant.parse(entryPredicateObjectMap(OntologyConstants.KnoraBase.LastModificationDate))
                )
        }.toSet

        // Transform the statements about the map into a PersistentMapV2, and return it with its entries.

        val (mapIri: IRI, mapPredicateObjectMap: Map[IRI, String]) = mapStatements.head match {
            case (mapSubjectIri: IRI, mapPredicatesAndObjects: Seq[(IRI, String)]) => (mapSubjectIri, mapPredicatesAndObjects.toMap)
        }

        val returnedMapPath = knoraIdUtil.mapIriToMapPath(mapIri)

        if (returnedMapPath != requestedMapPath) {
            throw InconsistentTriplestoreDataException(s"Map path $requestedMapPath was requested, but map path $returnedMapPath was returned")
        }

        PersistentMapV2(
            path = returnedMapPath,
            entries = mapEntries,
            lastModificationDate = Instant.parse(mapPredicateObjectMap(OntologyConstants.KnoraBase.LastModificationDate))
        )
    }
}
