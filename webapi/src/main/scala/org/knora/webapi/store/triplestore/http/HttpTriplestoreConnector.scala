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

package org.knora.webapi.store.triplestore.http

import java.io.StringReader
import java.util

import akka.actor.{Actor, ActorLogging, ActorSystem, Status}
import akka.stream.ActorMaterializer
import org.apache.commons.lang3.StringUtils
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.AuthCache
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet, HttpPost}
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.{BasicAuthCache, BasicCredentialsProvider, CloseableHttpClient, HttpClients}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.apache.http.{Consts, HttpHost, NameValuePair}
import org.eclipse.rdf4j
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.rio.RDFHandler
import org.eclipse.rdf4j.rio.turtle._
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.store.triplestore.RdfDataObjectFactory
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.SparqlResultProtocol._
import org.knora.webapi.util.{FakeTriplestore, StringFormatter}
import spray.json._

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
  * Submits SPARQL queries and updates to a triplestore over HTTP. Supports different triplestores, which can be configured in
  * `application.conf`.
  */
class HttpTriplestoreConnector extends Actor with ActorLogging {

    // MIME type constants.
    private val mimeTypeApplicationJson = "application/json"
    private val mimeTypeApplicationSparqlResultsJson = "application/sparql-results+json"
    private val mimeTypeTextTurtle = "text/turtle"
    private val mimeTypeApplicationSparqlUpdate = "application/sparql-update"

    private implicit val system: ActorSystem = context.system
    private val settings = Settings(system)
    implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)
    private implicit val materializer: ActorMaterializer = ActorMaterializer()

    private val triplestoreType = settings.triplestoreType

    private val targetHost: HttpHost = new HttpHost(settings.triplestoreHost, settings.triplestorePort, "http")

    private val credsProvider: BasicCredentialsProvider = new BasicCredentialsProvider
    credsProvider.setCredentials(new AuthScope(targetHost.getHostName, targetHost.getPort), new UsernamePasswordCredentials(settings.triplestoreUsername, settings.triplestorePassword))

    // Reading data should be quick.
    private val queryTimeoutMillis = settings.triplestoreQueryTimeout.toMillis.toInt

    private val queryRequestConfig = RequestConfig.custom()
        .setConnectTimeout(queryTimeoutMillis)
        .setConnectionRequestTimeout(queryTimeoutMillis)
        .setSocketTimeout(queryTimeoutMillis)
        .build

    private val queryHttpClient: CloseableHttpClient = HttpClients.custom
        .setDefaultCredentialsProvider(credsProvider)
        .setDefaultRequestConfig(queryRequestConfig)
        .build

    // An update for a bulk import could take a while.
    private val updateTimeoutMillis = settings.triplestoreUpdateTimeout.toMillis.toInt

    private val updateTimeoutConfig = RequestConfig.custom()
        .setConnectTimeout(updateTimeoutMillis)
        .setConnectionRequestTimeout(updateTimeoutMillis)
        .setSocketTimeout(updateTimeoutMillis)
        .build

    private val updateHttpClient: CloseableHttpClient = HttpClients.custom
        .setDefaultCredentialsProvider(credsProvider)
        .setDefaultRequestConfig(updateTimeoutConfig)
        .build

    private val queryPath: String = s"/repositories/${settings.triplestoreDatabaseName}"
    private val updatePath: String = s"/repositories/${settings.triplestoreDatabaseName}/statements"
    private val logDelimiter = "\n" + StringUtils.repeat('=', 80) + "\n"

    /**
      * Receives a message requesting a SPARQL select or update, and returns an appropriate response message or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive: PartialFunction[Any, Unit] = {
        case SparqlSelectRequest(sparql) => try2Message(sender(), sparqlHttpSelect(sparql), log)
        case SparqlConstructRequest(sparql) => try2Message(sender(), sparqlHttpConstruct(sparql), log)
        case SparqlExtendedConstructRequest(sparql) => try2Message(sender(), sparqlHttpExtendedConstruct(sparql), log)
        case SparqlUpdateRequest(sparql) => try2Message(sender(), sparqlHttpUpdate(sparql), log)
        case SparqlAskRequest(sparql) => try2Message(sender(), sparqlHttpAsk(sparql), log)
        case ResetTriplestoreContent(rdfDataObjects) => try2Message(sender(), resetTripleStoreContent(rdfDataObjects), log)
        case DropAllTriplestoreContent() => try2Message(sender(), dropAllTriplestoreContent(), log)
        case InsertTriplestoreContent(rdfDataObjects) => try2Message(sender(), insertDataIntoTriplestore(rdfDataObjects), log)
        case HelloTriplestore(msg) if msg == triplestoreType => sender ! HelloTriplestore(triplestoreType)
        case CheckRepositoryRequest() => try2Message(sender(), checkRepository(), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Given a SPARQL SELECT query string, runs the query, returning the result as a [[SparqlSelectResponse]].
      *
      * @param sparql the SPARQL SELECT query string.
      * @return a [[SparqlSelectResponse]].
      */
    private def sparqlHttpSelect(sparql: String): Try[SparqlSelectResponse] = {
        // println(sparql)

        def parseJsonResponse(sparql: String, resultStr: String): Try[SparqlSelectResponse] = {
            val parseTry = Try {
                resultStr.parseJson.convertTo[SparqlSelectResponse]
            }

            parseTry match {
                case Success(parsed) => Success(parsed)
                case Failure(e) =>
                    log.error(e, s"Couldn't parse response from triplestore:$logDelimiter$resultStr${logDelimiter}in response to SPARQL query:$logDelimiter$sparql")
                    Failure(TriplestoreResponseException("Couldn't parse JSON from triplestore", e, log))
            }
        }

        for {
            // Are we using the fake triplestore?
            resultStr <- if (settings.useFakeTriplestore) {
                // Yes: get the response from it.
                Try(FakeTriplestore.data(sparql))
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
    private def sparqlHttpConstruct(sparql: String): Try[SparqlConstructResponse] = {
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

        def parseTurtleResponse(sparql: String, turtleStr: String): Try[SparqlConstructResponse] = {
            val parseTry = Try {
                val turtleParser = new TurtleParser()
                val handler = new ConstructResponseTurtleHandler
                turtleParser.setRDFHandler(handler)
                turtleParser.parse(new StringReader(turtleStr), "")
                handler.getConstructResponse
            }

            parseTry match {
                case Success(parsed) => Success(parsed)
                case Failure(e) =>
                    log.error(e, s"Couldn't parse response from triplestore:$logDelimiter$turtleStr${logDelimiter}in response to SPARQL query:$logDelimiter$sparql")
                    Failure(TriplestoreResponseException("Couldn't parse Turtle from triplestore", e, log))
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
    private def sparqlHttpExtendedConstruct(sparql: String): Try[SparqlExtendedConstructResponse] = {

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

        def parseTurtleResponse(sparql: String, turtleStr: String): Try[SparqlExtendedConstructResponse] = {
            val parseTry = Try {
                val turtleParser = new TurtleParser()
                val handler = new ConstructResponseTurtleHandler
                turtleParser.setRDFHandler(handler)
                turtleParser.parse(new StringReader(turtleStr), "query-result.ttl")
                handler.getConstructResponse
            }

            parseTry match {
                case Success(parsed) => Success(parsed)
                case Failure(e) =>
                    log.error(e, s"Couldn't parse response from triplestore:$logDelimiter$turtleStr${logDelimiter}in response to SPARQL query:$logDelimiter$sparql")
                    Failure(TriplestoreResponseException("Couldn't parse Turtle from triplestore", e, log))
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
    private def sparqlHttpUpdate(sparqlUpdate: String): Try[SparqlUpdateResponse] = {
        // println(logDelimiter + sparqlUpdate)

        for {
            // Send the request to the triplestore.
            _ <- getTriplestoreHttpResponse(sparqlUpdate, isUpdate = true)

            // If we're using GraphDB, update the full-text search index.
            _ = if (triplestoreType == TriplestoreTypes.HttpGraphDBSE | triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
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
    def sparqlHttpAsk(sparql: String): Try[SparqlAskResponse] = {
        for {
            resultString <- getTriplestoreHttpResponse(sparql, isUpdate = false)
            _ = log.debug("sparqlHttpAsk - resultString: {}", resultString)

            result: Boolean = resultString.parseJson.asJsObject.getFields("boolean").head.convertTo[Boolean]
        } yield SparqlAskResponse(result)
    }

    private def resetTripleStoreContent(rdfDataObjects: Seq[RdfDataObject]): Try[ResetTriplestoreContentACK] = {
        log.debug("resetTripleStoreContent")
        val resetTriplestoreResult = for {

            // drop old content
            _ <- dropAllTriplestoreContent()

            // insert new content
            _ <- insertDataIntoTriplestore(rdfDataObjects)

            // any errors throwing exceptions until now are already covered so we can ACK the request
            result = ResetTriplestoreContentACK()
        } yield result

        resetTriplestoreResult
    }

    private def dropAllTriplestoreContent(): Try[DropAllTriplestoreContentACK] = {

        log.debug("==>> Drop All Data Start")

        val dropAllSparqlString =
            """
                DROP ALL
            """

        val response: Try[DropAllTriplestoreContentACK] = for {
            result: String <- getTriplestoreHttpResponse(dropAllSparqlString, isUpdate = true)
            _ = log.debug(s"==>> Drop All Data End, Result: $result")
        } yield DropAllTriplestoreContentACK()

        response.recover {
            case t: Exception => throw TriplestoreResponseException("Reset: Failed to execute DROP ALL", t, log)
        }

        response
    }

    /**
      * Inserts the data referenced inside the `rdfDataObjects`.
      *
      * @param rdfDataObjects a sequence of paths and graph names referencing data that needs to be inserted.
      * @return [[InsertTriplestoreContentACK]]
      */
    private def insertDataIntoTriplestore(rdfDataObjects: Seq[RdfDataObject]): Try[InsertTriplestoreContentACK] = {
        try {
            log.debug("==>> Loading Data Start")

            val defaultRdfDataList = settings.tripleStoreConfig.getConfigList("default-rdf-data")
            val defaultRdfDataObjectList = defaultRdfDataList.asScala.map {
                config => RdfDataObjectFactory(config)
            }

            val completeRdfDataObjectList = defaultRdfDataObjectList ++ rdfDataObjects

            for (elem <- completeRdfDataObjectList) {

                GraphProtocolAccessor.post(elem.name, elem.path)

                log.debug(s"added: ${elem.name}")
            }

            if (triplestoreType == TriplestoreTypes.HttpGraphDBSE || triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
                /* need to update the lucene index */
                val indexUpdateSparqlString =
                    """
                        PREFIX luc: <http://www.ontotext.com/owlim/lucene#>
                        INSERT DATA { luc:fullTextSearchIndex luc:updateIndex _:b1 . }
                    """
                getTriplestoreHttpResponse(indexUpdateSparqlString, isUpdate = true)
            }

            log.debug("==>> Loading Data End")
            Success(InsertTriplestoreContentACK())
        } catch {
            case e: TriplestoreUnsupportedFeatureException => Failure(e)
            case e: Exception => Failure(TriplestoreResponseException("Reset: Failed to execute insert into triplestore", e, log))
        }

    }

    /**
      * Checks connection to the triplestore.
      */
    private def checkRepository(): Try[CheckRepositoryResponse] = {

        // needs to be a local import or other things don't work (spray json black magic)
        import org.knora.webapi.messages.store.triplestoremessages.GraphDBJsonProtocol._

        try {

            log.debug("checkRepository entered")

            // call endpoint returning all repositories

            val authCache: AuthCache = new BasicAuthCache
            val basicAuth: BasicScheme = new BasicScheme
            authCache.put(targetHost, basicAuth)

            val context: HttpClientContext = HttpClientContext.create
            context.setCredentialsProvider(credsProvider)
            context.setAuthCache(authCache)

            val httpGet = new HttpGet("/rest/repositories")
            httpGet.addHeader("Accept", mimeTypeApplicationJson)

            val responseStr = {
                var maybeResponse: Option[CloseableHttpResponse] = None

                try {
                    maybeResponse = Some(queryHttpClient.execute(targetHost, httpGet, context))
                    EntityUtils.toString(maybeResponse.get.getEntity)
                } finally {
                    maybeResponse match {
                        case Some(response) => response.close()
                        case None => ()
                    }
                }
            }

            val jsonArr = JsonParser(responseStr).asInstanceOf[JsArray]

            // parse json and check if the repository defined in 'application.conf' is present and correctly defined

            val repositories: Seq[GraphDBRepository] = jsonArr.elements.map(_.convertTo[GraphDBRepository])

            val idShouldBe = settings.triplestoreDatabaseName
            val sesameTypeShouldBe = "owlim:MonitorRepository"

            val neededRepo = repositories.filter(_.id == idShouldBe).filter(_.sesameType == sesameTypeShouldBe)
            if (neededRepo.length == 1) {
                // everything looks good
                Success(CheckRepositoryResponse(repositoryStatus = RepositoryStatus.ServiceAvailable, msg = "Triplestore is available."))
            } else {
                // none of the available repositories meet our requirements
                Success(CheckRepositoryResponse(repositoryStatus = RepositoryStatus.NotInitialized, msg = s"None of the available repositories meet our requirements of id: $idShouldBe, sesameType: $sesameTypeShouldBe."))
            }
        } catch {
            case e: Exception =>
                // println("checkRepository - exception", e)
                Success(CheckRepositoryResponse(repositoryStatus = RepositoryStatus.ServiceUnavailable, msg = s"Triplestore not available: ${e.getMessage}"))
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
    private def getTriplestoreHttpResponse(sparql: String, isUpdate: Boolean, isConstruct: Boolean = false): Try[String] = {

        val authCache: AuthCache = new BasicAuthCache
        val basicAuth: BasicScheme = new BasicScheme
        authCache.put(targetHost, basicAuth)

        val httpContext: HttpClientContext = HttpClientContext.create
        httpContext.setCredentialsProvider(credsProvider)
        httpContext.setAuthCache(authCache)

        val (httpClient: CloseableHttpClient, httpPost: HttpPost) = if (isUpdate) {
            // Send updates as application/sparql-update (as per SPARQL 1.1 Protocol §3.2.2, "UPDATE using POST directly").
            val requestEntity = new StringEntity(sparql, ContentType.create(mimeTypeApplicationSparqlUpdate, "UTF-8"))
            val updateHttpPost = new HttpPost(updatePath)
            updateHttpPost.setEntity(requestEntity)
            (updateHttpClient, updateHttpPost)
        } else {
            // Send queries as application/x-www-form-urlencoded (as per SPARQL 1.1 Protocol §2.1.2,
            // "query via POST with URL-encoded parameters"), so we can include the "infer" parameter when using GraphDB.

            val formParams = new util.ArrayList[NameValuePair]()

            if (triplestoreType == TriplestoreTypes.HttpGraphDBSE || triplestoreType == TriplestoreTypes.HttpGraphDBFree) {
                formParams.add(new BasicNameValuePair("infer", "true"))
            }

            formParams.add(new BasicNameValuePair("query", sparql))
            val requestEntity = new UrlEncodedFormEntity(formParams, Consts.UTF_8)
            val queryHttpPost = new HttpPost(queryPath)
            queryHttpPost.setEntity(requestEntity)

            if (isConstruct) {
                queryHttpPost.addHeader("Accept", mimeTypeTextTurtle)
            } else {
                queryHttpPost.addHeader("Accept", mimeTypeApplicationSparqlResultsJson)
            }

            (queryHttpClient, queryHttpPost)
        }

        val triplestoreResponseTry = Try {
            var maybeResponse: Option[CloseableHttpResponse] = None

            try {
                maybeResponse = Some(httpClient.execute(targetHost, httpPost, httpContext))

                val responseEntityStr: String = Option(maybeResponse.get.getEntity) match {
                    case Some(responseEntity) => EntityUtils.toString(responseEntity)
                    case None => ""
                }

                val statusCode: Int = maybeResponse.get.getStatusLine.getStatusCode
                val statusCategory: Int = statusCode / 100

                if (statusCategory != 2) {
                    throw TriplestoreResponseException(s"Triplestore responded with HTTP code $statusCode: $responseEntityStr")
                }

                responseEntityStr
            } finally {
                maybeResponse match {
                    case Some(response) => response.close()
                    case None => ()
                }
            }
        }

        triplestoreResponseTry.recover {
            case tre: TriplestoreResponseException => throw tre
            case e: Exception => throw TriplestoreConnectionException("Failed to connect to triplestore", e, log)
        }
    }
}