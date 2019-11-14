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

package org.knora.webapi.util.clientapi

import org.knora.webapi.ClientApiGenerationException
import org.knora.webapi.util.{SmartIri, StringFormatter}

/**
  * Parses type annotations representing collection types for use in generated client code.
  */
object ClientCollectionTypeParser {

    /**
      * Parses a collection type annotation.
      *
      * @param typeStr the type annotation to be parsed.
      * @param ontologyIri the ontology IRI supplied with the type annotation.
      * @return the parsed collection type annotation.
      */
    def parse(typeStr: String, ontologyIri: SmartIri): CollectionType = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        // Tokenise the input.
        val tokens: Vector[TypeToken] = tokenise(typeStr)

        // Parse the type annotation.
        val (objectType, remainingTokens) = parseType(
            typeStr = typeStr,
            tokens = tokens,
            ontologyIri = ontologyIri
        )

        // Are there any tokens left?
        if (remainingTokens.isEmpty) {
            // No. Validate the collection type.
            objectType match {
                case collectionType: CollectionType => collectionType
                case _ => throw ClientApiGenerationException(s"Expected a collection type: $typeStr")
            }
        } else {
            throw ClientApiGenerationException(s"Invalid type: $typeStr")
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Token classes

    /**
      * A token in a type annotation.
      */
    private sealed trait TypeToken

    /**
      * An identifier.
      */
    private sealed trait Identifier extends TypeToken

    /**
      * A built-in identifier.
      */
    private sealed trait BuiltInIdentifier extends Identifier

    /**
      * The name of a datatype.
      */
    private trait DatatypeIdentifier extends BuiltInIdentifier {
        def toClientDatatype: ClientDatatype
    }

    /**
      * The string datatype.
      */
    private case object StringIdentifier extends DatatypeIdentifier {
        override def toString: String = "String"

        override def toClientDatatype: ClientDatatype = StringDatatype
    }

    /**
      * The boolean datatype.
      */
    private case object BooleanIdentifier extends DatatypeIdentifier {
        override def toString: String = "Boolean"

        override def toClientDatatype: ClientDatatype = BooleanDatatype
    }

    /**
      * The integer datatype.
      */
    private case object IntegerIdentifier extends DatatypeIdentifier {
        override def toString: String = "Integer"

        override def toClientDatatype: ClientDatatype = IntegerDatatype
    }

    /**
      * The decimal datatype.
      */
    private case object DecimalIdentifier extends DatatypeIdentifier {
        override def toString: String = "Decimal"

        override def toClientDatatype: ClientDatatype = DecimalDatatype
    }

    /**
      * The URI datatype.
      */
    private case object UriIdentifier extends DatatypeIdentifier {
        override def toString: String = "URI"

        override def toClientDatatype: ClientDatatype = UriDatatype
    }

    /**
      * The `xsd:dateTimeStamp` datatype.
      */
    private case object DateTimeStampIdentifier extends DatatypeIdentifier {
        override def toString: String = "DateTimeStamp"

        override def toClientDatatype: ClientDatatype = DateTimeStampDatatype
    }

    /**
      * The `Map` collection type.
      */
    private case object MapIdentifier extends BuiltInIdentifier {
        override def toString: String = "Map"
    }

    /**
      * The `Array` collection type.
      */
    private case object ArrayIdentifier extends BuiltInIdentifier {
        override def toString: String = "Array"
    }

    /**
      * A map of identifier names to built-in identifiers.
      */
    private val builtInIdentifiers: Map[String, BuiltInIdentifier] = Seq(
        StringIdentifier,
        BooleanIdentifier,
        IntegerIdentifier,
        DecimalIdentifier,
        UriIdentifier,
        DateTimeStampIdentifier,
        MapIdentifier,
        ArrayIdentifier
    ).map {
        token => token.toString -> token
    }.toMap

    /**
      * A token representing punctuation.
      */
    private sealed trait PunctuationToken extends TypeToken {
        def toChar: Char
    }

    /**
      * The `[` token.
      */
    private case object OpenBracketToken extends PunctuationToken {
        def toChar: Char = '['
    }

    /**
      * The `]` token.
      */
    private case object CloseBracketToken extends PunctuationToken {
        def toChar: Char = ']'
    }

    /**
      * The `,` token.
      */
    private case object CommaToken extends PunctuationToken {
        def toChar: Char = ','
    }

    /**
      * A map of characters to punctuation tokens.
      */
    private val punctuation: Map[Char, PunctuationToken] = Seq(
        OpenBracketToken,
        CloseBracketToken,
        CommaToken
    ).map {
        token => token.toChar -> token
    }.toMap

    /**
      * An identifier representing a class name in the target language.
      */
    private case class ClassRefIdentifier(className: String) extends Identifier {
        override def toString: String = className

        /**
          * Converts this token to a [[ClassRef]], using the specified ontology IRI.
          *
          * @param ontologyIri the IRI of the ontology supplied with the type annotation.
          */
        def toClassRef(ontologyIri: SmartIri)(implicit stringFormatter: StringFormatter): ClassRef = {
            ClassRef(className = className, classIri = ontologyIri.makeEntityIri(className))
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Tokeniser

    /**
      * Tokenises a type annotation.
      *
      * @param typeStr the string to be tokenised.
      * @return the tokens representing the type annotation.
      */
    private def tokenise(typeStr: String): Vector[TypeToken] = {
        tokeniseRec(
            typeStr = typeStr,
            pos = 0,
            currentIdentifier = "",
            tokens = Vector.empty
        )
    }

    /**
      * Recursively tokenises a type annotation.
      *
      * @param typeStr           the string to be tokenised.
      * @param pos               the current position in the string.
      * @param currentIdentifier the identifier currently being tokenised.
      * @param tokens            the tokens that have been collected so far.
      * @return the tokens representing the type annotation.
      */
    @scala.annotation.tailrec
    private def tokeniseRec(typeStr: String, pos: Int, currentIdentifier: String, tokens: Vector[TypeToken]): Vector[TypeToken] = {
        /**
          * Returns the tokens that have been collected so far, including a token representing `currentIdentifier`
          * if it is not empty.
          */
        def collectTokens: Vector[TypeToken] = {
            // Are there characters in currentIdentifier?
            if (currentIdentifier.nonEmpty) {
                // Yes. Is it a built-in identifier?
                val identifierToken = builtInIdentifiers.get(currentIdentifier) match {
                    case Some(builtInIdentifier) =>
                        // Yes.
                        builtInIdentifier

                    case None =>
                        // No. It must be a class reference.
                        ClassRefIdentifier(currentIdentifier)
                }

                // Return the tokens collected so far, plus the identifier token.
                tokens :+ identifierToken
            } else {
                // There are no characters in currentIdentifier. Just return the tokens collected so far.
                tokens
            }
        }

        // Are we at the end of the input?
        if (pos == typeStr.length) {
            // Yes. Return the tokens collected so far, including currentIdentifier if it is not empty.
            collectTokens
        } else {
            // Get the next character.
            val char: Char = typeStr.charAt(pos)

            // Skip whitespace.
            if (char == ' ') {
                tokeniseRec(
                    typeStr = typeStr,
                    pos = pos + 1,
                    currentIdentifier = currentIdentifier,
                    tokens = tokens
                )
            } else {
                // Is this a punctuation character?
                punctuation.get(char) match {
                    case Some(punctuationToken) =>
                        // Yes. Get any identifier preceding the punctuation, add the punctuation token,
                        // and recurse.
                        val collectedTokens = collectTokens :+ punctuationToken

                        tokeniseRec(
                            typeStr = typeStr,
                            pos = pos + 1,
                            currentIdentifier = "",
                            tokens = collectedTokens
                        )

                    case None =>
                        // This is not a punctuation character. Is it a Unicode letter?
                        if (Character.isLetter(char)) {
                            // Yes. It must be part of an identifier. Recurse.
                            tokeniseRec(
                                typeStr = typeStr,
                                pos = pos + 1,
                                currentIdentifier = currentIdentifier + char,
                                tokens = tokens
                            )
                        } else {
                            // It's not a Unicode letter, so it's an invalid character.
                            throw ClientApiGenerationException(s"Unexpected character '$char' in object type name: $typeStr")
                        }
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Parser

    /**
      * Recursively parses a type annotation.
      *
      * @param typeStr the string representation of the type annotation being parsed.
      * @param tokens the tokens not yet parsed.
      * @param ontologyIri the IRI of the ontology supplied with the type annotation.
      * @return the parsed type annotation and the tokens that follow it.
      */
    private def parseType(typeStr: String, tokens: Vector[TypeToken], ontologyIri: SmartIri)(implicit stringFormatter: StringFormatter): (ClientObjectType, Vector[TypeToken]) = {
        tokens.headOption match {
            case Some(token) =>
                token match {
                    case MapIdentifier =>
                        parseMap(
                            typeStr = typeStr,
                            tokens = tokens.tail,
                            ontologyIri = ontologyIri
                        )

                    case ArrayIdentifier =>
                        parseArray(
                            typeStr = typeStr,
                            tokens = tokens.tail,
                            ontologyIri = ontologyIri
                        )

                    case datatypeToken: DatatypeIdentifier => (datatypeToken.toClientDatatype, tokens.tail)

                    case classRefToken: ClassRefIdentifier => (classRefToken.toClassRef(ontologyIri), tokens.tail)

                    case _ => throw ClientApiGenerationException(s"Invalid type: $typeStr")
                }

            case None => throw ClientApiGenerationException(s"Invalid type: $typeStr")
        }
    }

    /**
      * Parses a `Map` type annotation.
      *
      * @param typeStr the string representation of the type annotation being parsed.
      * @param tokens the tokens not yet parsed.
      * @param ontologyIri the IRI of the ontology supplied with the type annotation.
      * @return a [[MapType]] and the tokens that follow it.
      */
    private def parseMap(typeStr: String, tokens: Vector[TypeToken], ontologyIri: SmartIri)(implicit stringFormatter: StringFormatter): (MapType, Vector[TypeToken]) = {
        // Consume the open bracket.
        val afterOpenBracket: Vector[TypeToken] = consumePunctuation(
            typeStr = typeStr,
            tokens = tokens,
            punctuationToken = OpenBracketToken
        )

        // Get the key type.
        val (keyType: ClientObjectType, afterKeyType: Vector[TypeToken]) = parseType(
            typeStr = typeStr,
            tokens = afterOpenBracket,
            ontologyIri = ontologyIri
        )

        // Validate the key type.
        val validKeyType: MapKeyDatatype = keyType match {
            case mapKeyDatatype: MapKeyDatatype => mapKeyDatatype
            case _ => throw ClientApiGenerationException(s"Invalid map key type: $typeStr")
        }

        // Consume the comma separating the key type from the value type.
        val afterComma: Vector[TypeToken] = consumePunctuation(
            typeStr = typeStr,
            tokens = afterKeyType,
            punctuationToken = CommaToken
        )

        // Get the value type.
        val (valueType: ClientObjectType, afterValueType: Vector[TypeToken]) = parseType(
            typeStr = typeStr,
            tokens = afterComma,
            ontologyIri = ontologyIri
        )

        // Validate the value type.
        val validValueType: MapValueType = valueType match {
            case mapValueType: MapValueType => mapValueType
            case _ => throw ClientApiGenerationException(s"Invalid map value type: $typeStr")
        }

        // Consume the close bracket.
        val afterCloseBracket = consumePunctuation(
            typeStr = typeStr,
            tokens = afterValueType,
            punctuationToken = CloseBracketToken
        )

        (MapType(keyType = validKeyType, valueType = validValueType), afterCloseBracket)
    }

    /**
      * Parses an `Array` type annotation.
      *
      * @param typeStr the string representation of the type annotation being parsed.
      * @param tokens the tokens not yet parsed.
      * @param ontologyIri the IRI of the ontology supplied with the type annotation.
      * @return a [[ArrayType]] and the tokens that follow it.
      */
    private def parseArray(typeStr: String, tokens: Vector[TypeToken], ontologyIri: SmartIri)(implicit stringFormatter: StringFormatter): (ArrayType, Vector[TypeToken]) = {
        // Consume the open bracket.
        val afterOpenBracket: Vector[TypeToken] = consumePunctuation(
            typeStr = typeStr,
            tokens = tokens,
            punctuationToken = OpenBracketToken
        )

        // Get the element type.
        val (elementType: ClientObjectType, afterKeyType: Vector[TypeToken]) = parseType(
            typeStr = typeStr,
            tokens = afterOpenBracket,
            ontologyIri = ontologyIri
        )

        // Validate the element type.
        val validElementType = elementType match {
            case arrayElementType: ArrayElementType => arrayElementType
            case _ => throw ClientApiGenerationException(s"Invalid array element type: $typeStr")
        }

        // Consume the close bracket.
        val afterCloseBracket = consumePunctuation(
            typeStr = typeStr,
            tokens = afterKeyType,
            punctuationToken = CloseBracketToken
        )

        (ArrayType(elementType = validElementType), afterCloseBracket)
    }

    /**
      * Consumes a punctuation token.
      *
      * @param typeStr the string representation of the type annotation being parsed.
      * @param tokens the tokens not yet parsed.
      * @param punctuationToken the expected punctuation token.
      * @return the tokens following the punctuation token.
      */
    private def consumePunctuation(typeStr: String, tokens: Vector[TypeToken], punctuationToken: PunctuationToken): Vector[TypeToken] = {
        // Is there a next token?
        tokens.headOption match {
            case Some(token) =>
                // Yes. Is it the expected punctuation token?
                if (token == punctuationToken) {
                    // Yes. Return the tokens following the punctuation token.
                    tokens.tail
                } else {
                    throw ClientApiGenerationException(s"Expected '${punctuationToken.toChar}': $typeStr")
                }

            case None => throw ClientApiGenerationException(s"Expected '${punctuationToken.toChar}': $typeStr")
        }
    }
}
