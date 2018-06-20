/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import java.io.StringReader

import akka.actor.{Actor, ActorLogging, ActorSystem, Status}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Accept, BasicHttpCredentials}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.util.FastFuture
import akka.stream.ActorMaterializer
import org.apache.commons.lang3.StringUtils
import org.eclipse.rdf4j
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.rio.RDFHandler
import org.eclipse.rdf4j.rio.turtle._
import org.knora.webapi.SettingsConstants._
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.store.triplestore.RdfDataObjectFactory
import org.knora.webapi.util.ActorUtil.future2Message
import org.knora.webapi.util.SparqlResultProtocol._
import org.knora.webapi.util.{FakeTriplestore, StringFormatter}
import spray.json._

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

/**
  * Submits SPARQL queries and updates to a triplestore over HTTP. Supports different triplestores, which can be configured in
  * `application.conf`.
  */
class HttpTriplestoreConnector extends Actor with ActorLogging {

    // MIME type constants.
    private val mimeTypeApplicationSparqlResultsJson = MediaType.applicationWithFixedCharset("sparql-results+json", HttpCharsets.`UTF-8`) // JSON is always UTF-8
    private val mimeTypeTextTurtle = MediaType.text("turtle") // Turtle is always UTF-8
    private val mimeTypeApplicationSparqlUpdate = MediaType.applicationWithFixedCharset("sparql-update", HttpCharsets.`UTF-8`) // SPARQL 1.1 Protocol §3.2.2, "UPDATE using POST directly"

    private implicit val system: ActorSystem = context.system
    private implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    private implicit val materializer: ActorMaterializer = ActorMaterializer()
    private val settings = Settings(system)
    private val triplestoreType = settings.triplestoreType

    // Provides client HTTP connections.
    private val http = Http(context.system)

    // Use HTTP basic authentication.
    private val authorizationHeader = headers.Authorization(BasicHttpCredentials(settings.triplestoreUsername, settings.triplestorePassword))


    // The path for SPARQL queries.
    private val queryRequestPath = triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => s"/repositories/${settings.triplestoreDatabaseName}"
        case HTTP_FUSEKI_TS_TYPE if !settings.fusekiTomcat => s"/${settings.triplestoreDatabaseName}/query"
        case HTTP_FUSEKI_TS_TYPE if settings.fusekiTomcat => s"/${settings.fusekiTomcatContext}/${settings.triplestoreDatabaseName}/query"
    }

    // The URI for SPARQL queries.
    private val queryUri = Uri(
        scheme = "http",
        authority = Uri.Authority(Uri.Host(settings.triplestoreHost), port = settings.triplestorePort),
        path = Uri.Path(queryRequestPath)
    )

    // The path for SPARQL update operations.
    private val updateRequestPath = triplestoreType match {
        case HTTP_GRAPH_DB_TS_TYPE => s"/repositories/${settings.triplestoreDatabaseName}/statements"
        case HTTP_FUSEKI_TS_TYPE if !settings.fusekiTomcat => s"/${settings.triplestoreDatabaseName}/update"
        case HTTP_FUSEKI_TS_TYPE if settings.fusekiTomcat => s"/${settings.fusekiTomcatContext}/${settings.triplestoreDatabaseName}/update"
    }

    // The URI for SPARQL update operations.
    private val updateUri = Uri(
        scheme = "http",
        authority = Uri.Authority(Uri.Host(settings.triplestoreHost), port = settings.triplestorePort),
        path = Uri.Path(updateRequestPath)
    )

    private val logDelimiter = "\n" + StringUtils.repeat('=', 80) + "\n"

    /**
      * Receives a message requesting a SPARQL select or update, and returns an appropriate response message or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case SparqlSelectRequest(sparql) => future2Message(sender(), sparqlHttpSelect(sparql), log)
        case SparqlConstructRequest(sparql) => future2Message(sender(), sparqlHttpConstruct(sparql), log)
        case SparqlExtendedConstructRequest(sparql) => future2Message(sender(), sparqlHttpExtendedConstruct(sparql), log)
        case SparqlUpdateRequest(sparql) => future2Message(sender(), sparqlHttpUpdate(sparql), log)
        case SparqlAskRequest(sparql) => future2Message(sender(), sparqlHttpAsk(sparql), log)
        case ResetTriplestoreContent(rdfDataObjects) => future2Message(sender(), resetTripleStoreContent(rdfDataObjects), log)
        case DropAllTriplestoreContent() => future2Message(sender(), dropAllTriplestoreContent(), log)
        case InsertTriplestoreContent(rdfDataObjects) => future2Message(sender(), insertDataIntoTriplestore(rdfDataObjects), log)
        case HelloTriplestore(msg) if msg == triplestoreType => sender ! HelloTriplestore(triplestoreType)
        case CheckRepositoryRequest() => future2Message(sender(), checkRepository(), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Given a SPARQL SELECT query string, runs the query, returning the result as a [[SparqlSelectResponse]].
      *
      * @param sparql the SPARQL SELECT query string.
      * @return a [[SparqlSelectResponse]].
      */
    private def sparqlHttpSelect(sparql: String): Future[SparqlSelectResponse] = {
        // println(sparql)

        def parseJsonResponse(sparql: String, resultStr: String): Future[SparqlSelectResponse] = {
            val parseTry = Try {
                resultStr.parseJson.convertTo[SparqlSelectResponse]
            }

            parseTry match {
                case Success(parsed) => Future.successful(parsed)
                case Failure(e) =>
                    log.error(e, s"Couldn't parse response from triplestore:$logDelimiter$resultStr${logDelimiter}in response to SPARQL query:$logDelimiter$sparql")
                    Future.failed(TriplestoreResponseException("Couldn't parse JSON from triplestore", e, log))
            }
        }

        for {
            // Are we using the fake triplestore?
            resultStr <- if (settings.useFakeTriplestore) {
                // Yes: get the response from it.
                Future(FakeTriplestore.data(sparql))
            } else {
                // No: get the response from the real triplestore over HTTP.
                getTriplestoreHttpResponse(sparql, isUpdate = false)
            }

            // Are we preparing a fake triplestore?
            _ = if (settings.prepareFakeTriplestore) {
                // Yes: add the query and the response to it.
                FakeTriplestore.add(sparql, resultStr, log)
            }

            // _ = println(s"SPARQL: $logDelimiter$sparql")
            // _ = println(s"Result: $logDelimiter$resultStr")

            // Parse the response as a JSON object and generate a response message.
            responseMessage <- parseJsonResponse(sparql, resultStr)
        } yield responseMessage
    }

    /**
      * Given a SPARQL CONSTRUCT query string, runs the query, returning the result as a [[SparqlConstructResponse]].
      *
      * @param sparql the SPARQL CONSTRUCT query string.
      * @return a [[SparqlConstructResponse]]
      */
    private def sparqlHttpConstruct(sparql: String): Future[SparqlConstructResponse] = {
        // println(logDelimiter + sparql)

        /**
          * Converts a graph in parsed Turtle to a [[SparqlConstructResponse]].
          */
        class ConstructResponseTurtleHandler extends RDFHandler {
            /**
              * A collection of all the statements in the input file, grouped and sorted by subject IRI.
              */
            private var statements = Map.empty[IRI, Seq[(IRI, String)]]

            override def handleComment(comment: IRI): Unit = {}

            /**
              * Adds a statement to the collection `statements`.
              *
              * @param st the statement to be added.
              */
            override def handleStatement(st: Statement): Unit = {
                val subjectIri = st.getSubject.stringValue
                val predicateIri = st.getPredicate.stringValue
                val objectIri = st.getObject.stringValue
                val currentStatementsForSubject: Seq[(IRI, String)] = statements.getOrElse(subjectIri, Vector.empty[(IRI, String)])
                statements += (subjectIri -> (currentStatementsForSubject :+ (predicateIri, objectIri)))
            }

            override def endRDF(): Unit = {}

            override def handleNamespace(prefix: IRI, uri: IRI): Unit = {}

            override def startRDF(): Unit = {}

            def getConstructResponse: SparqlConstructResponse = {
                SparqlConstructResponse(statements)
            }
        }

        def parseTurtleResponse(sparql: String, turtleStr: String): Future[SparqlConstructResponse] = {
            val parseTry = Try {
                val turtleParser = new TurtleParser()
                val handler = new ConstructResponseTurtleHandler
                turtleParser.setRDFHandler(handler)
                turtleParser.parse(new StringReader(turtleStr), "")
                handler.getConstructResponse
            }

            parseTry match {
                case Success(parsed) => Future.successful(parsed)
                case Failure(e) =>
                    log.error(e, s"Couldn't parse response from triplestore:$logDelimiter$turtleStr${logDelimiter}in response to SPARQL query:$logDelimiter$sparql")
                    Future.failed(TriplestoreResponseException("Couldn't parse Turtle from triplestore", e, log))
            }
        }

        for {
            turtleStr <- getTriplestoreHttpResponse(sparql, isUpdate = false, isConstruct = true)
            response <- parseTurtleResponse(sparql, turtleStr)
        } yield response
    }

    /**
      * Given a SPARQL CONSTRUCT query string, runs the query, returning the result as a [[SparqlExtendedConstructResponse]].
      *
      * @param sparql the SPARQL CONSTRUCT query string.
      * @return a [[SparqlExtendedConstructResponse]]
      */
    private def sparqlHttpExtendedConstruct(sparql: String): Future[SparqlExtendedConstructResponse] = {

        // println(logDelimiter + sparql)

        /**
          * Converts a graph in parsed Turtle to a [[SparqlExtendedConstructResponse]].
          */
        class ConstructResponseTurtleHandler extends RDFHandler {

            private val stringFormatter = StringFormatter.getGeneralInstance

            /**
              * A collection of all the statements in the input file, grouped and sorted by subject IRI.
              */
            private var statements = Map.empty[SubjectV2, Map[IRI, Seq[LiteralV2]]]

            override def handleComment(comment: IRI): Unit = {}

            /**
              * Adds a statement to the collection `statements`.
              *
              * @param st the statement to be added.
              */
            override def handleStatement(st: Statement): Unit = {
                val subject: SubjectV2 = st.getSubject match {
                    case iri: rdf4j.model.IRI => IriSubjectV2(iri.stringValue)
                    case blankNode: rdf4j.model.BNode => BlankNodeSubjectV2(blankNode.getID)
                    case other => throw InconsistentTriplestoreDataException(s"Unsupported subject in construct query result: $other")
                }

                val predicateIri = st.getPredicate.stringValue

                // log.debug("sparqlHttpExtendedConstruct - handleStatement - object: {}", st.getObject)

                val objectLiteral: LiteralV2 = st.getObject match {
                    case iri: rdf4j.model.IRI => IriLiteralV2(value = iri.stringValue)
                    case blankNode: rdf4j.model.BNode => BlankNodeLiteralV2(value = blankNode.getID)

                    case literal: rdf4j.model.Literal => literal.getDatatype.toString match {
                        case OntologyConstants.Rdf.LangString => StringLiteralV2(value = literal.stringValue, language = literal.getLanguage.asScala)
                        case OntologyConstants.Xsd.String => StringLiteralV2(value = literal.stringValue, language = None)
                        case OntologyConstants.Xsd.Boolean => BooleanLiteralV2(value = literal.booleanValue)
                        case OntologyConstants.Xsd.Int | OntologyConstants.Xsd.Integer | OntologyConstants.Xsd.NonNegativeInteger => IntLiteralV2(value = literal.intValue)
                        case OntologyConstants.Xsd.Decimal => DecimalLiteralV2(value = literal.decimalValue)
                        case OntologyConstants.Xsd.DateTimeStamp => DateTimeLiteralV2(stringFormatter.toInstant(literal.stringValue, throw InconsistentTriplestoreDataException(s"Invalid xsd:dateTimeStamp: ${literal.stringValue}")))
                        case unknown => throw NotImplementedException(s"The literal type '$unknown' is not implemented.")
                    }

                    case other => throw InconsistentTriplestoreDataException(s"Unsupported object in construct query result: $other")
                }

                // log.debug("sparqlHttpExtendedConstruct - handleStatement - objectLiteral: {}", objectLiteral)

                val currentStatementsForSubject: Map[IRI, Seq[LiteralV2]] = statements.getOrElse(subject, Map.empty[IRI, Seq[LiteralV2]])
                val currentStatementsForPredicate: Seq[LiteralV2] = currentStatementsForSubject.getOrElse(predicateIri, Seq.empty[LiteralV2])

                val updatedPredicateStatements = currentStatementsForPredicate :+ objectLiteral
                val updatedSubjectStatements = currentStatementsForSubject + (predicateIri -> updatedPredicateStatements)

                statements += (subject -> updatedSubjectStatements)
            }

            override def endRDF(): Unit = {}

            override def handleNamespace(prefix: IRI, uri: IRI): Unit = {}

            override def startRDF(): Unit = {}

            def getConstructResponse: SparqlExtendedConstructResponse = {
                SparqlExtendedConstructResponse(statements)
            }
        }

        def parseTurtleResponse(sparql: String, turtleStr: String): Future[SparqlExtendedConstructResponse] = {
            val parseTry = Try {
                val turtleParser = new TurtleParser()
                val handler = new ConstructResponseTurtleHandler
                turtleParser.setRDFHandler(handler)
                turtleParser.parse(new StringReader(turtleStr), "query-result.ttl")
                handler.getConstructResponse
            }

            parseTry match {
                case Success(parsed) => Future.successful(parsed)
                case Failure(e) =>
                    log.error(e, s"Couldn't parse response from triplestore:$logDelimiter$turtleStr${logDelimiter}in response to SPARQL query:$logDelimiter$sparql")
                    Future.failed(TriplestoreResponseException("Couldn't parse Turtle from triplestore", e, log))
            }
        }

        for {
            turtleStr <- getTriplestoreHttpResponse(sparql, isUpdate = false, isConstruct = true)
            response <- parseTurtleResponse(sparql, turtleStr)
        } yield response
    }

    /**
      * Performs a SPARQL update operation.
      *
      * @param sparqlUpdate the SPARQL update.
      * @return a [[SparqlUpdateResponse]].
      */
    private def sparqlHttpUpdate(sparqlUpdate: String): Future[SparqlUpdateResponse] = {
        // println(logDelimiter + sparqlUpdate)

        for {
            // Send the request to the triplestore.
            _ <- getTriplestoreHttpResponse(sparqlUpdate, isUpdate = true)

            // If we're using GraphDB, update the full-text search index.
            _ = if (triplestoreType == HTTP_GRAPH_DB_TS_TYPE) {
                val indexUpdateSparqlString =
                    """
                        PREFIX luc: <http://www.ontotext.com/owlim/lucene#>
                        INSERT DATA { luc:fullTextSearchIndex luc:updateIndex _:b1 . }
                    """
                getTriplestoreHttpResponse(indexUpdateSparqlString, isUpdate = true)
            }
        } yield SparqlUpdateResponse()
    }

    /**
      * Performs a SPARQL ASK query.
      *
      * @param sparql the SPARQL ASK query.
      * @return a [[SparqlAskResponse]].
      */
    def sparqlHttpAsk(sparql: String): Future[SparqlAskResponse] = {
        for {
            resultString <- getTriplestoreHttpResponse(sparql, isUpdate = false)
            _ = log.debug("sparqlHttpAsk - resultString: {}", resultString)

            result: Boolean = resultString.parseJson.asJsObject.getFields("boolean").head.convertTo[Boolean]
        } yield SparqlAskResponse(result)
    }

    private def resetTripleStoreContent(rdfDataObjects: Seq[RdfDataObject]): Future[ResetTriplestoreContentACK] = {
        log.debug("resetTripleStoreContent")
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
            Await.result(getTriplestoreHttpResponse(dropAllSparqlString, isUpdate = true), 180.seconds)

            log.debug("==>> Drop All Data End")
            Future.successful(DropAllTriplestoreContentACK())
        } catch {
            case e: Exception => Future.failed(TriplestoreResponseException("Failed to execute DROP ALL", e, log))
        }
    }

    private def insertDataIntoTriplestore(rdfDataObjects: Seq[RdfDataObject]): Future[InsertTriplestoreContentACK] = {
        try {
            log.debug("==>> Loading Data Start")

            val defaultRdfDataList = settings.tripleStoreConfig.getConfigList("default-rdf-data")
            val defaultRdfDataObjectList = defaultRdfDataList.asScala.map {
                config => RdfDataObjectFactory(config)
            }

            val completeRdfDataObjectList = defaultRdfDataObjectList ++ rdfDataObjects

            for (elem <- completeRdfDataObjectList) {

                GraphProtocolAccessor.post(elem.name, elem.path)

                if (triplestoreType == HTTP_GRAPH_DB_TS_TYPE) {
                    /* need to update the lucene index */
                    val indexUpdateSparqlString =
                        """
                            PREFIX luc: <http://www.ontotext.com/owlim/lucene#>
                            INSERT DATA { luc:fullTextSearchIndex luc:updateIndex _:b1 . }
                    """
                    Await.result(getTriplestoreHttpResponse(indexUpdateSparqlString, isUpdate = true), 30.seconds)
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

    /**
      * Checks connection to the triplestore.
      */
    private def checkRepository(): Future[CheckRepositoryResponse] = {
        try {

            log.info("checkRepository entered")

            // call endpoint returning all repositories

            val scheme = if (settings.triplestoreUseHttps) {
                "https"
            } else {
                "http"
            }

            val headers = List(
                authorizationHeader,
                Accept(MediaTypes.`application/json`)
            )

            val getRepositoriesUri: Uri = triplestoreType match {
                case HTTP_GRAPH_DB_TS_TYPE => {
                    Uri(
                        scheme = scheme,
                        authority = Uri.Authority(Uri.Host(settings.triplestoreHost), port = settings.triplestorePort),
                        path = Uri.Path("/rest/repositories")
                    )
                }
                case _ => throw UnsuportedTriplestoreException("checkRepository only supports GraphDB.")
            }

            val getRepositoriesRequest: HttpRequest = HttpRequest(
                method = HttpMethods.GET,
                uri = getRepositoriesUri,
                headers = headers
            )

            val jsonFuture = for {
                response: HttpMessage <- Http().singleRequest(getRepositoriesRequest)
                _ = log.info("checkRepository - response: {}", response)

                json: JsArray <- response match {
                    case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[JsArray]
                    case other => throw new Exception(other.toString())
                }
                _ = log.info("checkRepository - json: {}", json.prettyPrint)

            } yield json

            val json: JsArray = Await.result(jsonFuture, 500.milliseconds)

            // parse json and check if the repository defined in 'application.conf' is present and correctly defined

            FastFuture.successful(CheckRepositoryResponse(repositoryStatus = RepositoryStatus.ServiceAvailable, msg = "Triplestore is available."))

        } catch {
            case e: Exception => {
                println("checkRepository - exception", e)
                FastFuture.successful(CheckRepositoryResponse(repositoryStatus = RepositoryStatus.ServiceUnavailable, msg = "Triplestore not available."))
            }
        }
    }

    /**
      * Submits a SPARQL request to the triplestore and returns the response as a string.
      *
      * @param sparql      the SPARQL request to be submitted.
      * @param isUpdate    `true` if this is an update request.
      * @param isConstruct `true` if this is a CONSTRUCT request.
      * @return the triplestore's response.
      */
    private def getTriplestoreHttpResponse(sparql: String, isUpdate: Boolean, isConstruct: Boolean = false): Future[String] = {
        val request = if (isUpdate) {
            // Send updates as application/sparql-update (as per SPARQL 1.1 Protocol §3.2.2, "UPDATE using POST directly").

            val entity = HttpEntity(mimeTypeApplicationSparqlUpdate, sparql)
            val headers = List(authorizationHeader)

            HttpRequest(
                method = HttpMethods.POST,
                uri = updateUri,
                entity = entity,
                headers = headers
            )
        } else {
            // Send queries as application/x-www-form-urlencoded (as per SPARQL 1.1 Protocol §2.1.2,
            // "query via POST with URL-encoded parameters"), so we can include the "infer" parameter when using GraphDB.

            val maybeInfer = if (triplestoreType == HTTP_GRAPH_DB_TS_TYPE) {
                Some("infer" -> "true")
            } else {
                None
            }

            val formData = FormData(
                Map("query" -> sparql) ++ maybeInfer
            )

            // Construct the Accept header if needed.
            val acceptContentType = if (isConstruct) {
                // CONSTRUCT queries should return Turtle.
                Accept(MediaRange(mimeTypeTextTurtle))
            } else {
                // SELECT queries should return JSON.
                Accept(MediaRange(mimeTypeApplicationSparqlResultsJson))
            }

            val headers = List(authorizationHeader, acceptContentType)

            HttpRequest(
                method = HttpMethods.POST,
                uri = queryUri,
                entity = formData.toEntity,
                headers = headers
            )
        }

        val triplestoreResponseFuture = for {
            // _ = println(request.toString())

            requestStartTime: Long <- FastFuture.successful {
                if (settings.profileQueries) {
                    System.currentTimeMillis()
                } else {
                    0
                }
            }

            // Send the HTTP request.
            response <- http.singleRequest(request)

            // Convert the HTTP response body to a string.
            responseString <- response.entity.toStrict(20.seconds).map(_.data.decodeString("UTF-8"))

            _ = if (!response.status.isSuccess) {
                throw TriplestoreResponseException(s"Triplestore responded with HTTP code ${response.status}: $responseString")
            }

            _ = if (settings.profileQueries) {
                val requestDuration = System.currentTimeMillis() - requestStartTime
                log.debug(s"${logDelimiter}Query took $requestDuration millis:\n\n$sparql$logDelimiter")
            }
        } yield responseString

        // If an exception was thrown during the connection to the triplestore, wrap it in
        // a TriplestoreConnectionException.
        triplestoreResponseFuture.recover {
            case tre: TriplestoreResponseException => throw tre
            case e: Exception => throw TriplestoreConnectionException("Failed to connect to triplestore", e, log)
        }
    }
}