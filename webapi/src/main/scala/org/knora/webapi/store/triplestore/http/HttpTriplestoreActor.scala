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

package org.knora.webapi.store.triplestore.http

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

import akka.actor.{Actor, ActorLogging, Status}
import dispatch._
import org.apache.commons.lang3.StringUtils
import org.knora.webapi.SettingsConstants._
import org.knora.webapi._
import org.knora.webapi.messages.v1.store.triplestoremessages._
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{FakeTriplestore, SparqlUtil}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source
import scala.util.{Failure, Success}

/**
  * Submits SPARQL queries and updates to a triplestore over HTTP. Supports different triplestores, which can be configured in
  * `application.conf`.
  */
class HttpTriplestoreActor extends Actor with ActorLogging {
    // HTTP header constants.
    private val headerAccept = "Accept"

    // MIME type constants.
    private val mimeTypeFormUrlEncoded = "application/x-www-form-urlencoded"
    private val mimeTypeApplicationSparqlResultsJson = "application/sparql-results+json"

    implicit val system = context.system
    private implicit val executionContext = system.dispatcher
    private val settings = Settings(system)
    private val tsType = settings.triplestoreType

    // A base HTTP request containing the triplestore's host and port, with a username and password for authentication.
    private val triplestore = host(settings.triplestoreHost, settings.triplestorePort).as_!(settings.triplestoreUsername, settings.triplestorePassword)

    // HTTP paths for SPARQL queries.
    private val queryRequestPath = settings.triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => triplestore / "graphdb-server" / "repositories" / settings.triplestoreDatabaseName
        case HTTP_GRAPH_DB_FREE_TS_TYPE => triplestore / "repositories" / settings.triplestoreDatabaseName
        case HTTP_SESAME_TS_TYPE => triplestore / "graphdb-server" / "repositories" / settings.triplestoreDatabaseName
        case HTTP_FUSEKI_TS_TYPE if !settings.fusekiTomcat => triplestore / settings.triplestoreDatabaseName / "query"
        case HTTP_FUSEKI_TS_TYPE if settings.fusekiTomcat => triplestore / settings.fusekiTomcatContext / settings.triplestoreDatabaseName / "query"
    }

    // HTTP paths for SPARQL Update operations.
    private val updateRequestPath = settings.triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => triplestore / "graphdb-server" / "repositories" / settings.triplestoreDatabaseName / "statements"
        case HTTP_GRAPH_DB_FREE_TS_TYPE => triplestore / "repositories" / settings.triplestoreDatabaseName / "statements"
        case HTTP_SESAME_TS_TYPE => triplestore / "graphdb-server" / "repositories" / settings.triplestoreDatabaseName / "statements"
        case HTTP_FUSEKI_TS_TYPE if !settings.fusekiTomcat => triplestore / settings.triplestoreDatabaseName / "update"
        case HTTP_FUSEKI_TS_TYPE if settings.fusekiTomcat => triplestore / settings.fusekiTomcatContext / settings.triplestoreDatabaseName / "update"
    }

    // Send POST requests as application/x-www-form-urlencoded (as per SPARQL 1.1. Protocol §2.1.2,
    // "query via POST with URL-encoded parameters"), because Sesame doesn't support SPARQL 1.1 Protocol
    // §2.1.3 ("query via POST directly").

    // Supposedly it's not meaningful to specify UTF-8 encoding with MIME type application/x-www-form-urlencoded, but Unicode
    // characters aren't handled correctly unless we do.

    private val queryRequest = settings.triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => queryRequestPath.
            POST.
            setContentType(mimeTypeFormUrlEncoded, StandardCharsets.UTF_8.name).
            addParameter("infer", "false") // Turn off reasoning.
        case HTTP_GRAPH_DB_FREE_TS_TYPE => queryRequestPath.
            POST.
            setContentType(mimeTypeFormUrlEncoded, StandardCharsets.UTF_8.name).
            addParameter("infer", "false") // Turn off reasoning.
        case HTTP_FUSEKI_TS_TYPE => queryRequestPath.
            POST.
            setContentType(mimeTypeFormUrlEncoded, StandardCharsets.UTF_8.name)
        case HTTP_SESAME_TS_TYPE => queryRequestPath.
            POST.
            setContentType(mimeTypeFormUrlEncoded, StandardCharsets.UTF_8.name)
        case ts_type => throw TriplestoreUnsupportedFeatureException(s"HttpTriplestoreActor does not support: $ts_type")
    }

    private val updateRequest = settings.triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => updateRequestPath.
            POST.
            setContentType(mimeTypeFormUrlEncoded, StandardCharsets.UTF_8.name).
            addParameter("infer", "true") // Turn on reasoning, which is needed for consistency checking.
        case HTTP_GRAPH_DB_FREE_TS_TYPE => updateRequestPath.
            POST.
            setContentType(mimeTypeFormUrlEncoded, StandardCharsets.UTF_8.name).
            addParameter("infer", "true") // Turn on reasoning, which is needed for consistency checking.
        case HTTP_FUSEKI_TS_TYPE => updateRequestPath.
            POST.
            setContentType(mimeTypeFormUrlEncoded, StandardCharsets.UTF_8.name)
        case HTTP_SESAME_TS_TYPE => updateRequestPath.
            POST.
            setContentType(mimeTypeFormUrlEncoded, StandardCharsets.UTF_8.name)
        case ts_type => throw TriplestoreUnsupportedFeatureException(s"HttpTriplestoreActor does not support: $ts_type")
    }


    private val logDelimiter = "\n" + StringUtils.repeat('=', 80) + "\n"

    /**
      * Receives a message requesting a SPARQL select or update, and returns an appropriate response message or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case SparqlSelectRequest(sparql) => future2Message(sender(), sparqlHttpSelect(sparql), log)
        case SparqlUpdateRequest(sparql) => future2Message(sender(), sparqlHttpUpdate(sparql), log)
        case ResetTriplestoreContent(rdfDataObjects) => future2Message(sender(), resetTripleStoreContent(rdfDataObjects), log)
        case DropAllTriplestoreContent() => future2Message(sender(), dropAllTriplestoreContent(), log)
        case InsertTriplestoreContent(rdfDataObjects) => future2Message(sender(), insertDataIntoTriplestore(rdfDataObjects), log)
        case HelloTriplestore(msg) if msg == tsType => sender ! HelloTriplestore(tsType)
        case CheckConnection => checkTriplestore()
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Given the SPARQL SELECT query string, runs the query, returning the result as a [[SparqlSelectResponse]].
      * @param sparql the SPARQL SELECT query string
      * @return a [[SparqlSelectResponse]].
      */
    private def sparqlHttpSelect(sparql: String): Future[SparqlSelectResponse] = {
        for {
        // Are we using the fake triplestore?
            resultStr <- if (settings.useFakeTriplestore) {
                // Yes: get the response from it.
                Future(FakeTriplestore.data(sparql))
            } else {
                // No: get the response from the real triplestore over HTTP.
                getTriplestoreHttpResponse(sparql, update = false)
            }

            // Are we preparing a fake triplestore?
            _ = if (settings.prepareFakeTriplestore) {
                // Yes: add the query and the response to it.
                FakeTriplestore.add(sparql, resultStr, log)
            }

            // _ = println(s"SPARQL: $logDelimiter$sparql")
            // _ = println(s"Result: $logDelimiter$resultStr")

            // Parse the response as a JSON object and generate a response message.
            responseMessage <- SparqlUtil.parseJsonResponse(sparql, resultStr, log)
        } yield responseMessage
    }

    /**
      * Performs a SPARQL update operation.
      * @param sparqlUpdate the SPARQL update.
      * @return a [[SparqlUpdateResponse]].
      */
    private def sparqlHttpUpdate(sparqlUpdate: String): Future[SparqlUpdateResponse] = {
        // println(logDelimiter + sparqlUpdate)

        for {
            // Send the request to the triplestore.
            _ <- getTriplestoreHttpResponse(sparqlUpdate, update = true)

            // If we're using GraphDB, update the full-text search index.
            _ = if (tsType == HTTP_GRAPH_DB_TS_TYPE) {
                val indexUpdateSparqlString =
                    """
                        PREFIX luc: <http://www.ontotext.com/owlim/lucene#>
                        INSERT DATA { luc:fullTextSearchIndex luc:updateIndex _:b1 . }
                    """
                getTriplestoreHttpResponse(indexUpdateSparqlString, update = true)
            }
        } yield SparqlUpdateResponse()
    }

    /**
      * Submits a SPARQL request to the triplestore and returns the response as a string.
      * @param sparql the SPARQL request to be submitted.
      * @param update `true` if this is an update request.
      * @return the triplestore's response.
      */
    private def getTriplestoreHttpResponse(sparql: String, update: Boolean): Future[String] = {
        val request = if (update) {
            updateRequest.addParameter("update", sparql)
        } else {
            queryRequest.addParameter("query", sparql).addHeader(headerAccept, mimeTypeApplicationSparqlResultsJson)
        }

        //println(s"request url: ${request.url}")

        val triplestoreResponseFuture = for {
            response <- Http(request)

            // Do the conversion from bytes to characters here, rather than letting Dispatch do it, because
            // Dispatch doesn't figure out that the response should be parsed as UTF-8.
            responseStream: InputStream = response.getResponseBodyAsStream
            responseStreamString = Source.fromInputStream(responseStream, "UTF-8").mkString

            // _ = log.debug(s"Sent SPARQL request:\n$sparql${logDelimiter}Got response from triplestore:\n$responseStreamString")

            // Find out whether the triplestore's HTTP response code indicates success (2XX or 3XX) or failure (anything else).

            responseCodeCategory = response.getStatusCode / 100

            _ = if (!(responseCodeCategory == 2 || responseCodeCategory == 3)) {
                throw TriplestoreResponseException(s"Triplestore responded with HTTP code ${response.getStatusCode}: $responseStreamString")
            }
        } yield responseStreamString

        // If an exception was thrown during the connection to the triplestore, wrap it in
        // a TriplestoreConnectionException for clarity.
        triplestoreResponseFuture.recover {
            case tre: TriplestoreResponseException => throw tre
            case e: Exception => throw TriplestoreConnectionException("Failed to connect to triplestore", e, log)
        }
    }

    private def resetTripleStoreContent(rdfDataObjects: Seq[RdfDataObject]): Future[ResetTriplestoreContentACK] = {

        log.debug("should not see this")
        val resetTriplestoreResult = for {

        // drop old content
            dropResult <- dropAllTriplestoreContent()

            // insert new content
            insertResult <- insertDataIntoTriplestore(rdfDataObjects)

            // any errors throwing exceptions until now are already covered so we can ACK the request
            result = ResetTriplestoreContentACK()
        } yield result

        resetTriplestoreResult
    }

    private def dropAllTriplestoreContent(): Future[DropAllTriplestoreContentACK] = {

        try {
            log.debug("==>> Drop All Data Start")
            val dropAllSparqlString =
                """
                    DROP ALL
                """
            Await.result(getTriplestoreHttpResponse(dropAllSparqlString, update = true), 180.seconds)

            log.debug("==>> Drop All Data End")
            Future.successful(DropAllTriplestoreContentACK())
        } catch {
            case e: Exception => Future.failed(TriplestoreResponseException("Failed to execute DROP ALL", e, log))
        }
    }

    private def insertDataIntoTriplestore(rdfDataObjects: Seq[RdfDataObject]): Future[InsertTriplestoreContentACK] = {

        try {
            log.debug("==>> Loading Data Start")

            for (elem <- rdfDataObjects) {

                GraphProtocolAccessor.put(elem.name, elem.path)

                if (tsType == HTTP_GRAPH_DB_TS_TYPE || tsType == HTTP_GRAPH_DB_FREE_TS_TYPE) {
                    /* need to update the lucene index */
                    val indexUpdateSparqlString =
                        """
                            PREFIX luc: <http://www.ontotext.com/owlim/lucene#>
                            INSERT DATA { luc:fullTextSearchIndex luc:updateIndex _:b1 . }
                        """
                    Await.result(getTriplestoreHttpResponse(indexUpdateSparqlString, update = true), 5.seconds)
                }

                log.debug(s"added: ${elem.name}")
            }

            log.debug("==>> Loading Data End")
            Future.successful(InsertTriplestoreContentACK())
        } catch {
            case e: TriplestoreUnsupportedFeatureException => Future.failed(e)
            case e: Exception => Future.failed(TriplestoreResponseException("Failed to execute insert into triplestore", e, log))
        }

    }

    private def checkTriplestore() {

        val sparql = "SELECT ?s ?p ?o WHERE { ?s ?p ?o  } LIMIT 10"

        val request = queryRequest.addParameter("query", sparql).addHeader(headerAccept, mimeTypeApplicationSparqlResultsJson)

        val resppnseStrFuture = for {
            response <- Http(request)
            responseStr = response.getResponseBody

            // Find out whether the triplestore's HTTP response code indicates success (2XX or 3XX) or failure (anything else).
            responseCodeCategory = response.getStatusCode / 100

            _ = if (!(responseCodeCategory == 2 || responseCodeCategory == 3)) {
                throw TriplestoreResponseException(s"Triplestore responded with HTTP code ${response.getStatusCode}: $responseStr")
            }

        } yield responseStr

        resppnseStrFuture onComplete {
            case Success(responseStr) => log.info(s"Connection OK: ${responseStr.length}")
            case Failure(t) => log.error("Failed to connect to triplestore: " + t.getMessage)
        }
    }
}
