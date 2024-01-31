/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.store.triplestoremessages

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.*
import zio.*
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import java.time.Instant
import scala.collection.mutable

import dsp.errors.*
import dsp.valueobjects.V2
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.*
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.util.rdf.*

/**
 * A response to a [[org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct]] query.
 *
 * @param statements a map of subject IRIs to statements about each subject.
 */
case class SparqlConstructResponse(statements: Map[IRI, Seq[(IRI, String)]], rdfModel: RdfModel) {
  def asExtended(implicit sf: StringFormatter): IO[DataConversionException, SparqlExtendedConstructResponse] =
    SparqlExtendedConstructResponse.make(rdfModel)
}
object SparqlConstructResponse {
  def make(rdfModel: RdfModel): SparqlConstructResponse = {
    val statementMap = mutable.Map.empty[IRI, Seq[(IRI, String)]]
    for (st: Statement <- rdfModel) {
      val subjectIri                  = st.subj.stringValue
      val predicateIri                = st.pred.stringValue
      val objectIri                   = st.obj.stringValue
      val currentStatementsForSubject = statementMap.getOrElse(subjectIri, Vector.empty[(IRI, String)])
      statementMap += (subjectIri -> (currentStatementsForSubject :+ (predicateIri, objectIri)))
    }
    SparqlConstructResponse(statementMap.toMap, rdfModel)
  }
}

case class SparqlExtendedConstructResponse(
  statements: Map[SubjectV2, SparqlExtendedConstructResponse.ConstructPredicateObjects]
)
object SparqlExtendedConstructResponse {

  /**
   * A map of predicate IRIs to literal objects.
   */
  type ConstructPredicateObjects = Map[SmartIri, Seq[LiteralV2]]

  def make(turtle: String)(implicit
    stringFormatter: StringFormatter
  ): IO[DataConversionException, SparqlExtendedConstructResponse] =
    make(RdfFormatUtil.parseToRdfModel(turtle, Turtle))

  def make(
    rdfModel: RdfModel
  )(implicit sf: StringFormatter): IO[DataConversionException, SparqlExtendedConstructResponse] =
    ZIO.attempt {
      val statementMap = mutable.Map.empty[SubjectV2, ConstructPredicateObjects]

      for (st: Statement <- rdfModel) {
        val subject: SubjectV2 = st.subj match {
          case iriNode: IriNode     => IriSubjectV2(iriNode.iri)
          case blankNode: BlankNode => BlankNodeSubjectV2(blankNode.id)
        }

        val predicateIri: SmartIri = st.pred.iri.toSmartIri

        val objectLiteral: LiteralV2 = st.obj match {
          case iriNode: IriNode     => IriLiteralV2(value = iriNode.iri)
          case blankNode: BlankNode => BlankNodeLiteralV2(value = blankNode.id)

          case literal: RdfLiteral =>
            literal match {
              case datatypeLiteral: DatatypeLiteral =>
                datatypeLiteral.datatype match {
                  case datatypeIri if OntologyConstants.Xsd.integerTypes.contains(datatypeIri) =>
                    IntLiteralV2(
                      datatypeLiteral
                        .integerValue(
                          throw InconsistentRepositoryDataException(s"Invalid integer: ${datatypeLiteral.value}")
                        )
                        .toInt
                    )

                  case OntologyConstants.Xsd.DateTime =>
                    DateTimeLiteralV2(
                      ValuesValidator
                        .xsdDateTimeStampToInstant(datatypeLiteral.value)
                        .getOrElse(
                          throw InconsistentRepositoryDataException(s"Invalid xsd:dateTime: ${datatypeLiteral.value}")
                        )
                    )

                  case OntologyConstants.Xsd.Boolean =>
                    BooleanLiteralV2(
                      datatypeLiteral.booleanValue(
                        throw InconsistentRepositoryDataException(s"Invalid xsd:boolean: ${datatypeLiteral.value}")
                      )
                    )

                  case OntologyConstants.Xsd.String => StringLiteralV2(value = datatypeLiteral.value, language = None)

                  case OntologyConstants.Xsd.Decimal =>
                    DecimalLiteralV2(
                      datatypeLiteral.decimalValue(
                        throw InconsistentRepositoryDataException(s"Invalid xsd:decimal: ${datatypeLiteral.value}")
                      )
                    )

                  case OntologyConstants.Xsd.Uri => IriLiteralV2(datatypeLiteral.value)

                  case unknown => throw NotImplementedException(s"The literal type '$unknown' is not implemented.")
                }

              case stringWithLanguage: StringWithLanguage =>
                StringLiteralV2(value = stringWithLanguage.value, language = Some(stringWithLanguage.language))
            }
        }

        val currentStatementsForSubject   = statementMap.getOrElse(subject, Map.empty[SmartIri, Seq[LiteralV2]])
        val currentStatementsForPredicate = currentStatementsForSubject.getOrElse(predicateIri, Seq.empty[LiteralV2])

        val updatedPredicateStatements = currentStatementsForPredicate :+ objectLiteral
        val updatedSubjectStatements   = currentStatementsForSubject + (predicateIri -> updatedPredicateStatements)

        statementMap += (subject -> updatedSubjectStatements)
      }

      SparqlExtendedConstructResponse(statementMap.toMap)
    }.foldZIO(
      _ => ZIO.fail(DataConversionException("Couldn't parse Turtle document")),
      ZIO.succeed(_)
    )
}

/**
 * Indicates whether the repository is up to date.
 *
 * @param message a message providing details of what was done.
 */
case class RepositoryUpdatedResponse(message: String)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
 * Contains the path to the 'ttl' file and the name of the named graph it should be loaded in.
 *
 * @param path to the 'ttl' file
 * @param name of the named graph the data will be load into.
 */
case class RdfDataObject(path: String, name: String)
object RdfDataObject {
  implicit val jsonCodec: JsonCodec[RdfDataObject] = DeriveJsonCodec.gen[RdfDataObject]
}

/**
 * Represents the subject of a statement read from the triplestore.
 */
sealed trait SubjectV2 {
  def value: String
}

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
  def asIriLiteral(errorFun: => Nothing): IriLiteralV2 =
    this match {
      case iriLiteral: IriLiteralV2 => iriLiteral
      case _                        => errorFun
    }

  /**
   * Returns this [[LiteralV2]] as a [[StringLiteralV2]].
   *
   * @param errorFun a function that throws an exception. It will be called if this [[LiteralV2]] is not
   *                 a [[StringLiteralV2]].
   * @return a [[StringLiteralV2]].
   */
  def asStringLiteral(errorFun: => Nothing): StringLiteralV2 =
    this match {
      case stringLiteral: StringLiteralV2 => stringLiteral
      case _                              => errorFun
    }

  /**
   * Returns this [[LiteralV2]] as a [[BooleanLiteralV2]].
   *
   * @param errorFun a function that throws an exception. It will be called if this [[LiteralV2]] is not
   *                 a [[BooleanLiteralV2]].
   * @return a [[BooleanLiteralV2]].
   */
  def asBooleanLiteral(errorFun: => Nothing): BooleanLiteralV2 =
    this match {
      case booleanLiteral: BooleanLiteralV2 => booleanLiteral
      case _                                => errorFun
    }

  /**
   * Returns this [[LiteralV2]] as an [[IntLiteralV2]].
   *
   * @param errorFun a function that throws an exception. It will be called if this [[LiteralV2]] is not
   *                 an [[IntLiteralV2]].
   * @return an [[IntLiteralV2]].
   */
  def asIntLiteral(errorFun: => Nothing): IntLiteralV2 =
    this match {
      case intLiteral: IntLiteralV2 => intLiteral
      case _                        => errorFun
    }

  /**
   * Returns this [[LiteralV2]] as a [[DecimalLiteralV2]].
   *
   * @param errorFun a function that throws an exception. It will be called if this [[LiteralV2]] is not
   *                 a [[DecimalLiteralV2]].
   * @return a [[DecimalLiteralV2]].
   */
  def asDecimalLiteral(errorFun: => Nothing): DecimalLiteralV2 =
    this match {
      case decimalLiteral: DecimalLiteralV2 => decimalLiteral
      case _                                => errorFun
    }

  /**
   * Returns this [[LiteralV2]] as a [[DateTimeLiteralV2]].
   *
   * @param errorFun a function that throws an exception. It will be called if this [[LiteralV2]] is not
   *                 a [[DateTimeLiteralV2]].
   * @return a [[DateTimeLiteralV2]].
   */
  def asDateTimeLiteral(errorFun: => Nothing): DateTimeLiteralV2 =
    this match {
      case dateTimeLiteral: DateTimeLiteralV2 => dateTimeLiteral
      case _                                  => errorFun
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

  def toOntologySchema(targetSchema: OntologySchema): SmartIriLiteralV2 =
    SmartIriLiteralV2(value.toOntologySchema(targetSchema))
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
case class StringLiteralV2(value: String, language: Option[String] = None)
    extends LiteralV2
    with OntologyLiteralV2
    with Ordered[StringLiteralV2] {
  override def toString: String = value

  if (language.isDefined && value.isEmpty) {
    throw BadRequestException(s"String value is missing.")
  }

  def compare(that: StringLiteralV2): Int = this.value.compareTo(that.value)
}

/**
 * Represents a sequence of [[StringLiteralV2]].
 *
 * @param stringLiterals a sequence of [[StringLiteralV2]].
 */
case class StringLiteralSequenceV2(stringLiterals: Vector[StringLiteralV2]) {

  /**
   * Sort sequence of [[StringLiteralV2]] by their language value.
   *
   * @return a [[StringLiteralSequenceV2]] sorted by language value.
   */
  def sortByLanguage: StringLiteralSequenceV2 = StringLiteralSequenceV2(stringLiterals.sortBy(_.language))

  /**
   * Gets the string value of the [[StringLiteralV2]] corresponding to the preferred language.
   * If not available, returns the string value of the fallback language or any available language.
   *
   * @param preferredLang the preferred language.
   * @param fallbackLang  language to use if preferred language is not available.
   */
  def getPreferredLanguage(preferredLang: String, fallbackLang: String): Option[String] = {

    val stringLiteralMap: Map[Option[String], String] = stringLiterals.map { case StringLiteralV2(str, lang) =>
      lang -> str
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
                stringLiteralMap.toVector.sortBy { case (lang, _) =>
                  lang
                }.headOption.map { case (_, obj) =>
                  obj
                }
            }

        }
    }
  }
}

object StringLiteralSequenceV2 {
  val empty: StringLiteralSequenceV2 = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
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
 * A spray-json protocol that parses JSON returned by a SPARQL endpoint. Empty values and empty rows are
 * ignored.
 */
object SparqlResultProtocol extends DefaultJsonProtocol {

  /**
   * Converts a [[JsValue]] to a [[VariableResultsRow]].
   */
  implicit object VariableResultsJsonFormat extends JsonFormat[VariableResultsRow] {
    def read(jsonVal: JsValue): VariableResultsRow = {

      // Collapse the JSON structure into a simpler Map of SPARQL variable names to values.
      val mapToWrap: Map[String, String] = jsonVal.asJsObject.fields.foldLeft(Map.empty[String, String]) {
        case (acc, (key, value)) =>
          value.asJsObject.getFields("value") match {
            case Seq(JsString(valueStr)) if valueStr.nonEmpty => // Ignore empty strings.
              acc + (key -> valueStr)
            case _ => acc
          }
      }

      // Wrap that Map in an ErrorHandlingMap that will gracefully report errors about missing values when they
      // are accessed later.
      VariableResultsRow(
        new ErrorHandlingMap(
          mapToWrap,
          { (key: String) => s"No value found for SPARQL query variable '$key' in query result row" }
        )
      )
    }

    def write(variableResultsRow: VariableResultsRow): JsValue = ???
  }

  /**
   * Converts a [[JsValue]] to a [[SparqlSelectResultBody]].
   */
  implicit object SparqlSelectResponseBodyFormat extends JsonFormat[SparqlSelectResultBody] {
    def read(jsonVal: JsValue): SparqlSelectResultBody =
      jsonVal.asJsObject.fields.get("bindings") match {
        case Some(bindingsJson: JsArray) =>
          // Filter out empty rows.
          SparqlSelectResultBody(bindingsJson.convertTo[Seq[VariableResultsRow]].filter(_.rowMap.keySet.nonEmpty))

        case _ => SparqlSelectResultBody(Nil)
      }

    def write(sparqlSelectResponseBody: SparqlSelectResultBody): JsValue = ???
  }

  implicit val headerFormat: JsonFormat[SparqlSelectResultHeader] = jsonFormat1(SparqlSelectResultHeader)
  implicit val responseFormat: JsonFormat[SparqlSelectResult]     = jsonFormat2(SparqlSelectResult)
}

/**
 * A spray-json protocol for generating Knora API v1 JSON providing data about resources and their properties.
 */
trait TriplestoreJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

  implicit object LiteralV2Format extends JsonFormat[StringLiteralV2] {

    /**
     * Converts a [[StringLiteralV2]] to a [[JsValue]].
     *
     * @param string a [[StringLiteralV2]].
     * @return a [[JsValue]].
     */
    def write(string: StringLiteralV2): JsValue =
      if (string.language.isDefined) {
        // have language tag
        JsObject(
          Map(
            "value"    -> string.value.toJson,
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

    /**
     * Converts a [[JsValue]] to a [[StringLiteralV2]].
     *
     * @param json a [[JsValue]].
     * @return a [[StringLiteralV2]].
     */
    def read(json: JsValue): StringLiteralV2 = json match {
      case stringWithLang: JsObject =>
        stringWithLang.getFields("value", "language") match {
          case Seq(JsString(value), JsString(language)) =>
            StringLiteralV2(
              value = value,
              language = Some(language)
            )
          case Seq(JsString(value)) =>
            StringLiteralV2(
              value = value,
              language = None
            )
          case _ =>
            throw DeserializationException("JSON object with 'value', or 'value' and 'language' fields expected.")
        }
      case JsString(value) => StringLiteralV2(value, None)
      case _               => throw DeserializationException("JSON object with 'value', or 'value' and 'language' expected. ")
    }
  }

  // TODO-mpro: below object needs to be here because of moving value object to separate project which are also partially used in V2.
  // Once dsp.valueobjects.V2.StringLiteralV2 is replaced by LangString value object, it can be removed.
  // By then it is quick fix solution.
  implicit object V2LiteralV2Format extends JsonFormat[V2.StringLiteralV2] {

    /**
     * Converts a [[StringLiteralV2]] to a [[JsValue]].
     *
     * @param string a [[StringLiteralV2]].
     * @return a [[JsValue]].
     */
    def write(string: V2.StringLiteralV2): JsValue =
      if (string.language.isDefined) {
        // have language tag
        JsObject(
          Map(
            "value"    -> string.value.toJson,
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

    /**
     * Converts a [[JsValue]] to a [[StringLiteralV2]].
     *
     * @param json a [[JsValue]].
     * @return a [[StringLiteralV2]].
     */
    def read(json: JsValue): V2.StringLiteralV2 = json match {
      case stringWithLang: JsObject =>
        stringWithLang.getFields("value", "language") match {
          case Seq(JsString(value), JsString(language)) =>
            V2.StringLiteralV2(
              value = value,
              language = Some(language)
            )
          case Seq(JsString(value)) =>
            V2.StringLiteralV2(
              value = value,
              language = None
            )
          case _ =>
            throw DeserializationException("JSON object with 'value', or 'value' and 'language' fields expected.")
        }
      case JsString(value) => V2.StringLiteralV2(value, None)
      case _               => throw DeserializationException("JSON object with 'value', or 'value' and 'language' expected. ")
    }
  }

}
