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

package org.knora.webapi.messages.store.triplestoremessages

import java.io.{File, StringReader}
import java.time.Instant

import akka.event.LoggingAdapter
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.commons.lang3.StringUtils
import org.eclipse.rdf4j
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.rio.RDFHandler
import org.eclipse.rdf4j.rio.turtle.TurtleParser
import org.knora.webapi._
import org.knora.webapi.messages.store.StoreRequest
import org.knora.webapi.messages.store.triplestoremessages.RepositoryStatus.RepositoryStatus
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{ErrorHandlingMap, SmartIri, StringFormatter}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsObject, JsString, JsValue, JsonFormat, NullOptions, RootJsonFormat, _}

import scala.collection.mutable
import scala.compat.java8.OptionConverters._
import scala.util.{Failure, Success, Try}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

sealed trait TriplestoreRequest extends StoreRequest

/**
  * Simple message for initial actor functionality.
  */
case class HelloTriplestore(txt: String) extends TriplestoreRequest

/**
  * Simple message for checking the connection to the triplestore.
  */
case class CheckConnection() extends TriplestoreRequest

/**
  * Simple message for acknowledging connection check
  */
case class CheckConnectionACK()

/**
  * Represents a SPARQL SELECT query to be sent to the triplestore. A successful response will be a [[SparqlSelectResponse]].
  *
  * @param sparql the SPARQL string.
  */
case class SparqlSelectRequest(sparql: String) extends TriplestoreRequest

/**
  * Represents a response to a SPARQL SELECT query, containing a parsed representation of the response (JSON, etc.)
  * returned by the triplestore
  *
  * @param head    the header of the response, containing the variable names.
  * @param results the body of the response, containing rows of query results.
  */
case class SparqlSelectResponse(head: SparqlSelectResponseHeader, results: SparqlSelectResponseBody) {

    /**
      * Returns the contents of the first row of results.
      *
      * @return a [[Map]] representing the contents of the first row of results.
      */
    @throws[InconsistentTriplestoreDataException]("if the query returned no results.")
    def getFirstRow: VariableResultsRow = {
        if (results.bindings.isEmpty) {
            throw TriplestoreResponseException(s"A SPARQL query unexpectedly returned an empty result")
        }

        results.bindings.head
    }
}

/**
  * Represents the header of a JSON response to a SPARQL SELECT query.
  *
  * @param vars the names of the variables that were used in the SPARQL SELECT statement.
  */
case class SparqlSelectResponseHeader(vars: Seq[String])

/**
  * Represents the body of a JSON response to a SPARQL SELECT query.
  *
  * @param bindings the bindings of values to the variables used in the SPARQL SELECT statement.
  *                 Empty rows are not allowed.
  */
case class SparqlSelectResponseBody(bindings: Seq[VariableResultsRow]) {
    require(bindings.forall(_.rowMap.nonEmpty), "Empty rows are not allowed in a SparqlSelectResponseBody")
}


/**
  * Represents a row of results in a JSON response to a SPARQL SELECT query.
  *
  * @param rowMap a map of variable names to values in the row. An empty string is not allowed as a variable
  *               name or value.
  */
case class VariableResultsRow(rowMap: ErrorHandlingMap[String, String]) {
    require(rowMap.forall {
        case (key, value) => key.nonEmpty && value.nonEmpty
    }, "An empty string is not allowed as a variable name or value in a VariableResultsRow")
}

/**
  * Represents a SPARQL CONSTRUCT query to be sent to the triplestore. A successful response will be a
  * [[SparqlConstructResponse]].
  *
  * @param sparql the SPARQL string.
  */
case class SparqlConstructRequest(sparql: String) extends TriplestoreRequest

/**
  * Represents a SPARQL CONSTRUCT query to be sent to the triplestore. The triplestore's will be
  * written to the specified file in Trig format. A successful response message will be a [[FileWrittenResponse]].
  *
  * @param sparql     the SPARQL string.
  * @param graphIri   the named graph IRI to be used in the TriG file.
  * @param outputFile the file to be written.
  */
case class SparqlConstructFileRequest(sparql: String, graphIri: IRI, outputFile: File) extends TriplestoreRequest

/**
  * Indicates that a file was written successfully.
  */
case class FileWrittenResponse()

/**
  * A response to a [[SparqlConstructRequest]].
  *
  * @param statements a map of subject IRIs to statements about each subject.
  */
case class SparqlConstructResponse(statements: Map[IRI, Seq[(IRI, String)]])

/**
  * Represents a SPARQL CONSTRUCT query to be sent to the triplestore. A successful response will be a
  * [[SparqlExtendedConstructResponse]].
  *
  * @param sparql the SPARQL string.
  */
case class SparqlExtendedConstructRequest(sparql: String) extends TriplestoreRequest

/**
  * Parses Turtle documents and converts them to [[SparqlExtendedConstructResponse]] objects.
  */
object SparqlExtendedConstructResponse {
    /**
      * A map of predicate IRIs to literal objects.
      */
    type ConstructPredicateObjects = Map[SmartIri, Seq[LiteralV2]]

    private val logDelimiter = "\n" + StringUtils.repeat('=', 80) + "\n"

    /**
      * Converts a graph in parsed Turtle to a [[SparqlExtendedConstructResponse]].
      */
    class ConstructResponseTurtleHandler extends RDFHandler {

        implicit private val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        /**
          * A collection of all the statements in the input file, grouped and sorted by subject IRI.
          */
        private val statements: mutable.Map[SubjectV2, ConstructPredicateObjects] = mutable.Map.empty

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

            val predicateIri: SmartIri = st.getPredicate.stringValue.toSmartIri

            val objectLiteral: LiteralV2 = st.getObject match {
                case iri: rdf4j.model.IRI => IriLiteralV2(value = iri.stringValue)
                case blankNode: rdf4j.model.BNode => BlankNodeLiteralV2(value = blankNode.getID)

                case literal: rdf4j.model.Literal => literal.getDatatype.toString match {
                    case OntologyConstants.Rdf.LangString => StringLiteralV2(value = literal.stringValue, language = literal.getLanguage.asScala)
                    case OntologyConstants.Xsd.String => StringLiteralV2(value = literal.stringValue, language = None)
                    case OntologyConstants.Xsd.Boolean => BooleanLiteralV2(value = literal.booleanValue)
                    case OntologyConstants.Xsd.Int | OntologyConstants.Xsd.Integer | OntologyConstants.Xsd.NonNegativeInteger => IntLiteralV2(value = literal.intValue)
                    case OntologyConstants.Xsd.Decimal => DecimalLiteralV2(value = literal.decimalValue)
                    case OntologyConstants.Xsd.DateTime => DateTimeLiteralV2(stringFormatter.xsdDateTimeStampToInstant(literal.stringValue, throw InconsistentTriplestoreDataException(s"Invalid xsd:dateTime: ${literal.stringValue}")))
                    case OntologyConstants.Xsd.Uri => IriLiteralV2(value = literal.stringValue)
                    case unknown => throw NotImplementedException(s"The literal type '$unknown' is not implemented.")
                }

                case other => throw InconsistentTriplestoreDataException(s"Unsupported object in construct query result: $other")
            }

            val currentStatementsForSubject: Map[SmartIri, Seq[LiteralV2]] = statements.getOrElse(subject, Map.empty[SmartIri, Seq[LiteralV2]])
            val currentStatementsForPredicate: Seq[LiteralV2] = currentStatementsForSubject.getOrElse(predicateIri, Seq.empty[LiteralV2])

            val updatedPredicateStatements = currentStatementsForPredicate :+ objectLiteral
            val updatedSubjectStatements = currentStatementsForSubject + (predicateIri -> updatedPredicateStatements)

            statements += (subject -> updatedSubjectStatements)
        }

        override def endRDF(): Unit = {}

        override def handleNamespace(prefix: IRI, uri: IRI): Unit = {}

        override def startRDF(): Unit = {}

        def getConstructResponse: SparqlExtendedConstructResponse = SparqlExtendedConstructResponse(statements.toMap)
    }

    /**
      * Parses a Turtle document, converting it to a [[SparqlExtendedConstructResponse]].
      *
      * @param turtleStr the Turtle document.
      * @param log       a [[LoggingAdapter]].
      * @return a [[SparqlExtendedConstructResponse]] representing the document.
      */
    def parseTurtleResponse(turtleStr: String, log: LoggingAdapter): Try[SparqlExtendedConstructResponse] = {
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
                log.error(e, s"Couldn't parse Turtle document:$logDelimiter$turtleStr$logDelimiter")
                Failure(DataConversionException("Couldn't parse Turtle document"))
        }
    }
}

/**
  * A response to a [[SparqlExtendedConstructRequest]].
  *
  * @param statements a map of subjects to statements about each subject.
  */
case class SparqlExtendedConstructResponse(statements: Map[SubjectV2, SparqlExtendedConstructResponse.ConstructPredicateObjects])

/**
  * Requests a named graph, which will be written to the specified file in Trig format. A successful response
  * will be a [[FileWrittenResponse]].
  *
  * @param graphIri   the IRI of the named graph.
  * @param outputFile the destination file.
  */
case class GraphFileRequest(graphIri: IRI, outputFile: File) extends TriplestoreRequest

/**
  * Represents a SPARQL Update operation to be performed.
  *
  * @param sparql the SPARQL string.
  */
case class SparqlUpdateRequest(sparql: String) extends TriplestoreRequest

/**
  * Indicates that the requested SPARQL Update was executed and returned no errors.
  */
case class SparqlUpdateResponse()


/**
  * Represents a SPARQL ASK query to be sent to the triplestore. A successful response will be a
  * [[SparqlAskResponse]].
  *
  * @param sparql the SPARQL string.
  */
case class SparqlAskRequest(sparql: String) extends TriplestoreRequest

/**
  * Represents a response to a SPARQL ASK query, containing the result.
  *
  * @param result of the query.
  */
case class SparqlAskResponse(result: Boolean)

/**
  * Message for resetting the contents of the triplestore and loading a fresh set of data. The data needs to be
  * stored in an accessible path and supplied via the [[RdfDataObject]].
  *
  * @param rdfDataObjects  contains a list of [[RdfDataObject]].
  * @param prependDefaults denotes if a default set defined in application.conf should be also loaded
  */
case class ResetTriplestoreContent(rdfDataObjects: Seq[RdfDataObject], prependDefaults: Boolean = true) extends TriplestoreRequest

/**
  * Sent as a response to [[ResetTriplestoreContent]] if the request was processed successfully.
  */
case class ResetTriplestoreContentACK()

/**
  * Message for removing all content from the triple store.
  */
case class DropAllTriplestoreContent() extends TriplestoreRequest

/**
  * Sent as a response to [[DropAllTriplestoreContent]] if the request was processed successfully.
  */
case class DropAllTriplestoreContentACK()

/**
  * Inserts data into the triplestore.
  *
  * @param rdfDataObjects contains a list of [[RdfDataObject]].
  */
case class InsertTriplestoreContent(rdfDataObjects: Seq[RdfDataObject]) extends TriplestoreRequest

/**
  * Sent as a response to [[InsertTriplestoreContent]] if the request was processed successfully.
  */
case class InsertTriplestoreContentACK()

/**
  * Initialize the triplestore. This will initiate the (re)creation of the repository and adding data to it.
  *
  * @param rdfDataObject contains a list of [[RdfDataObject]].
  */
case class InitTriplestore(rdfDataObject: RdfDataObject) extends TriplestoreRequest

/**
  * Initialization ((re)creation of repository and loading of data) is finished successfully.
  */
case class InitTriplestoreACK()

/**
  * Ask triplestore if it the repository is ready
  */
case class CheckRepositoryRequest() extends TriplestoreRequest

/**
  * Response indicating whether the triplestore has finished initialization and is ready for processing messages
  *
  * @param repositoryStatus the state of the repository.
  * @param msg              further description.
  */
case class CheckRepositoryResponse(repositoryStatus: RepositoryStatus, msg: String)

/**
  * Updates the triplestore's full-text search index.
  *
  * @param subjectIri if a subject has changed, update the index for that subject. Otherwise, updates
  *                   the index to add any subjects not yet indexed.
  */
case class SearchIndexUpdateRequest(subjectIri: Option[String] = None) extends TriplestoreRequest


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Repository status
  * - ServiceUnavailable: GraphDB is not responding to HTTP requests.
  * - NotInitialized: GraphDB is responding to HTTP requests but the repository defined in 'application.conf' is missing.
  * - ServiceAvailable: Everything is OK.
  */
object RepositoryStatus extends Enumeration {
    type RepositoryStatus = Value
    val ServiceUnavailable, NotInitialized, ServiceAvailable = Value
}

/**
  * Contains the path to the 'ttl' file and the name of the named graph it should be loaded in.
  *
  * @param path to the 'ttl' file
  * @param name of the named graph the data will be load into.
  */
case class RdfDataObject(path: String, name: String)

/**
  * Represents the subject of a statement read from the triplestore.
  */
sealed trait SubjectV2

/**
  * Represents an IRI used as the subject of a statement.
  */
case class IriSubjectV2(value: IRI) extends SubjectV2 {
    override def toString: IRI = value
}

/**
  * Represents a blank node identifier used as the subject of a statement.
  */
case class BlankNodeSubjectV2(value: String) extends SubjectV2 {
    override def toString: String = value
}

/**
  * Represents a literal read from the triplestore. There are different subclasses
  * representing literals with the extended type information stored in the triplestore.
  */
sealed trait LiteralV2 {
    /**
      * Returns this [[LiteralV2]] as an [[IriLiteralV2]].
      *
      * @param errorFun a function that throws an exception. It will be called if this [[LiteralV2]] is not
      *                 an [[IriLiteralV2]].
      * @return an [[IriLiteralV2]].
      */
    def asIriLiteral(errorFun: => Nothing): IriLiteralV2 = {
        this match {
            case iriLiteral: IriLiteralV2 => iriLiteral
            case _ => errorFun
        }
    }

    /**
      * Returns this [[LiteralV2]] as a [[StringLiteralV2]].
      *
      * @param errorFun a function that throws an exception. It will be called if this [[LiteralV2]] is not
      *                 a [[StringLiteralV2]].
      * @return a [[StringLiteralV2]].
      */
    def asStringLiteral(errorFun: => Nothing): StringLiteralV2 = {
        this match {
            case stringLiteral: StringLiteralV2 => stringLiteral
            case _ => errorFun
        }
    }

    /**
      * Returns this [[LiteralV2]] as a [[BooleanLiteralV2]].
      *
      * @param errorFun a function that throws an exception. It will be called if this [[LiteralV2]] is not
      *                 a [[BooleanLiteralV2]].
      * @return a [[BooleanLiteralV2]].
      */
    def asBooleanLiteral(errorFun: => Nothing): BooleanLiteralV2 = {
        this match {
            case booleanLiteral: BooleanLiteralV2 => booleanLiteral
            case _ => errorFun
        }
    }

    /**
      * Returns this [[LiteralV2]] as an [[IntLiteralV2]].
      *
      * @param errorFun a function that throws an exception. It will be called if this [[LiteralV2]] is not
      *                 an [[IntLiteralV2]].
      * @return an [[IntLiteralV2]].
      */
    def asIntLiteral(errorFun: => Nothing): IntLiteralV2 = {
        this match {
            case intLiteral: IntLiteralV2 => intLiteral
            case _ => errorFun
        }
    }

    /**
      * Returns this [[LiteralV2]] as a [[DecimalLiteralV2]].
      *
      * @param errorFun a function that throws an exception. It will be called if this [[LiteralV2]] is not
      *                 a [[DecimalLiteralV2]].
      * @return a [[DecimalLiteralV2]].
      */
    def asDecimalLiteral(errorFun: => Nothing): DecimalLiteralV2 = {
        this match {
            case decimalLiteral: DecimalLiteralV2 => decimalLiteral
            case _ => errorFun
        }
    }


    /**
      * Returns this [[LiteralV2]] as a [[DateTimeLiteralV2]].
      *
      * @param errorFun a function that throws an exception. It will be called if this [[LiteralV2]] is not
      *                 a [[DateTimeLiteralV2]].
      * @return a [[DateTimeLiteralV2]].
      */
    def asDateTimeLiteral(errorFun: => Nothing): DateTimeLiteralV2 = {
        this match {
            case dateTimeLiteral: DateTimeLiteralV2 => dateTimeLiteral
            case _ => errorFun
        }
    }
}

/**
  * Represents a literal read from an ontology in the triplestore.
  */
sealed trait OntologyLiteralV2

/**
  * Represents an IRI literal.
  *
  * @param value the IRI.
  */
case class IriLiteralV2(value: IRI) extends LiteralV2 {
    override def toString: IRI = value
}

/**
  * Represents an IRI literal as a [[SmartIri]].
  *
  * @param value the IRI.
  */
case class SmartIriLiteralV2(value: SmartIri) extends OntologyLiteralV2 {
    override def toString: IRI = value.toString

    def toOntologySchema(targetSchema: OntologySchema) = SmartIriLiteralV2(value.toOntologySchema(targetSchema))
}

/**
  * Represents a blank node identifier.
  *
  * @param value the identifier of the blank node.
  */
case class BlankNodeLiteralV2(value: String) extends LiteralV2 {
    override def toString: String = value
}

/**
  * Represents a string with an optional language tag. Allows sorting inside collections by value.
  *
  * @param value    the string value.
  * @param language the optional language tag.
  */
case class StringLiteralV2(value: String, language: Option[String] = None) extends LiteralV2 with OntologyLiteralV2 with Ordered[StringLiteralV2] {
    override def toString: String = value

    def compare(that: StringLiteralV2): Int = this.value.compareTo(that.value)

    def requireLanguage(errorFun: => Nothing): StringLiteralWithLanguageV2 = {
        language match {
            case Some(definedLanguage) => StringLiteralWithLanguageV2(value = value, language = definedLanguage)
            case None => errorFun
        }
    }
}

/**
  * Represents a string literal with a required language tag.
  *
  * @param value    the string value.
  * @param language the language tag.
  */
case class StringLiteralWithLanguageV2(value: String, language: String) extends Ordered[StringLiteralWithLanguageV2] {
    override def toString: String = value

    def compare(that: StringLiteralWithLanguageV2): Int = this.value.compareTo(that.value)
}

/**
  * Represents a sequence of [[StringLiteralV2]].
  *
  * @param stringLiterals a sequence of [[StringLiteralV2]].
  */
case class StringLiteralSequenceV2(stringLiterals: Vector[StringLiteralV2]) {

    /**
      * Sort sequence of [[StringLiteralV2]] by their text value.
      *
      * @return a [[StringLiteralSequenceV2]] sorted by string value.
      */
    def sortByStringValue: StringLiteralSequenceV2 = {
        StringLiteralSequenceV2(stringLiterals.sortBy(_.value))
    }

    /**
      * Gets the string value of the [[StringLiteralV2]] corresponding to the preferred language.
      * If not available, returns the string value of the fallback language or any available language.
      *
      * @param preferredLang the preferred language.
      * @param fallbackLang  language to use if preferred language is not available.
      */
    def getPreferredLanguage(preferredLang: String, fallbackLang: String): Option[String] = {

        val stringLiteralMap: Map[Option[String], String] = stringLiterals.map {
            case StringLiteralV2(str, lang) => lang -> str
        }.toMap

        stringLiteralMap.get(Some(preferredLang)) match {
            // Is the string value available in the user's preferred language?
            case Some(strVal: String) =>
                // Yes.
                Some(strVal)
            case None =>
                // The string value is not available in the user's preferred language. Is it available
                // in the system default language?
                stringLiteralMap.get(Some(fallbackLang)) match {
                    case Some(strValFallbackLang) =>
                        // Yes.
                        Some(strValFallbackLang)
                    case None =>
                        // The string value is not available in the system default language. Is it available
                        // without a language tag?
                        stringLiteralMap.get(None) match {
                            case Some(strValWithoutLang) =>
                                // Yes.
                                Some(strValWithoutLang)
                            case None =>
                                // The string value is not available without a language tag. Sort the
                                // available `StringLiteralV2` by language code to get a deterministic result,
                                // and return the object in the language with the lowest sort
                                // order.
                                stringLiteralMap.toVector.sortBy {
                                    case (lang, _) => lang
                                }.headOption.map {
                                    case (_, obj) => obj
                                }
                        }

                }
        }
    }
}

/**
  * Represents a boolean value.
  *
  * @param value the boolean value.
  */
case class BooleanLiteralV2(value: Boolean) extends LiteralV2 with OntologyLiteralV2 {
    override def toString: String = value.toString
}

/**
  * Represents an integer value.
  *
  * @param value the integer value.
  */
case class IntLiteralV2(value: Int) extends LiteralV2 {
    override def toString: String = value.toString
}

/**
  * Represents a decimal value.
  *
  * @param value the decimal value.
  */
case class DecimalLiteralV2(value: BigDecimal) extends LiteralV2 {
    override def toString: String = value.toString
}

/**
  * Represents a timestamp.
  *
  * @param value the timestamp value.
  */
case class DateTimeLiteralV2(value: Instant) extends LiteralV2 {
    override def toString: String = value.toString
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about resources and their properties.
  */
trait TriplestoreJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit object StringLiteralV2Format extends JsonFormat[StringLiteralV2] {
        /**
          * Converts a [[StringLiteralV2]] to a [[JsValue]].
          *
          * @param string a [[StringLiteralV2]].
          * @return a [[JsValue]].
          */
        def write(string: StringLiteralV2): JsValue = {

            if (string.language.isDefined) {
                // have language tag
                JsObject(
                    Map(
                        "value" -> string.value.toJson,
                        "language" -> string.language.toJson
                    )
                )
            } else {
                // no language tag
                JsObject(
                    Map(
                        "value" -> string.value.toJson
                    )
                )
            }
        }

        /**
          * Converts a [[JsValue]] to a [[StringLiteralV2]].
          *
          * @param json a [[JsValue]].
          * @return a [[StringLiteralV2]].
          */
        def read(json: JsValue): StringLiteralV2 = json match {
            case stringWithLang: JsObject => stringWithLang.getFields("value", "language") match {
                case Seq(JsString(value), JsString(language)) => StringLiteralV2(
                    value = value,
                    language = Some(language)
                )
                case Seq(JsString(value)) => StringLiteralV2(
                    value = value,
                    language = None
                )
                case _ => throw DeserializationException("JSON object with 'value', or 'value' and 'language' fields expected.")
            }
            case JsString(value) => StringLiteralV2(value, None)
            case _ => throw DeserializationException("JSON object with 'value', or 'value' and 'language' expected. ")
        }
    }

    implicit object StringLiteralWithLanguageV2Format extends JsonFormat[StringLiteralWithLanguageV2] {
        /**
          * Converts a [[StringLiteralWithLanguageV2]] to a [[JsValue]].
          *
          * @param string a [[StringLiteralWithLanguageV2]].
          * @return a [[JsValue]].
          */
        def write(string: StringLiteralWithLanguageV2): JsValue = {
            JsObject(
                Map(
                    "value" -> string.value.toJson,
                    "language" -> string.language.toJson
                )
            )
        }

        /**
          * Converts a [[JsValue]] to a [[StringLiteralWithLanguageV2]].
          *
          * @param json a [[JsValue]].
          * @return a [[StringLiteralV2]].
          */
        def read(json: JsValue): StringLiteralWithLanguageV2 = {
            json match {
                case stringWithLang: JsObject => stringWithLang.getFields("value", "language") match {
                    case Seq(JsString(value), JsString(language)) => StringLiteralWithLanguageV2(
                        value = value,
                        language = language
                    )

                    case _ => throw DeserializationException("JSON object with 'value' and 'language' fields expected.")
                }

                case _ => throw DeserializationException("JSON object with 'value' and 'language' expected. ")
            }
        }
    }

    implicit val rdfDataObjectFormat: RootJsonFormat[RdfDataObject] = jsonFormat2(RdfDataObject)
    implicit val resetTriplestoreContentFormat: RootJsonFormat[ResetTriplestoreContent] = jsonFormat2(ResetTriplestoreContent)

}