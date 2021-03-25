/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.util

import java.io.{StringReader, StringWriter}
import java.util

import javax.json._
import org.knora.webapi.exceptions.DataConversionException
import org.knora.webapi.messages.util.rdf.{JsonLDDocument, JsonLDUtil}

import scala.collection.JavaConverters._
import scala.util.matching.Regex

/**
  * Represents an event that can be formatted as JSON, optionally containing embedded JSON-LD documents.
  *
  * @param metadata the event metadata.
  * @param jsonLDDocuments a map of JSON object keys to JSON-LD documents to be embedded under those keys.
  */
case class JsonEvent(metadata: JsonObject, jsonLDDocuments: Map[String, JsonLDDocument] = Map.empty) {
  import org.knora.webapi.messages.util.JsonEvent._

  /**
    * Formats a [[JsonEvent]] as a JSON string.
    *
    * @return the corresponding JSON string.
    */
  def format: String = {
    // Are there any JSON-LD documents to embed?
    val jsonObject: JsonObject = if (jsonLDDocuments.nonEmpty) {
      // Yes. Build a JSON object from scratch.
      val jsonObjectBuilder: JsonObjectBuilder = Json.createObjectBuilder()

      // Add the contents of the metadata object.
      for ((jsonKey: String, jsonValue: JsonValue) <- metadata.asScala) {
        jsonObjectBuilder.add(jsonKey, jsonValue)
      }

      // Embed the JSON-LD documents.
      for ((jsonLDKey: String, document: JsonLDDocument) <- jsonLDDocuments) {
        val jsonKey = JsonLDKeyPrefix + jsonLDKey
        val jsonLDDocumentAsJsonObject: JsonObject = document.makeCompactedJavaxJsonObject()
        jsonObjectBuilder.add(jsonKey, jsonLDDocumentAsJsonObject)
      }

      jsonObjectBuilder.build()
    } else {
      // There are no JSON-LD documents to embed. Just use the JSON object that was provided for the metadata.
      metadata
    }

    // Format the JSON.
    val config = new util.HashMap[String, Boolean]()
    val jsonWriterFactory: JsonWriterFactory = Json.createWriterFactory(config)
    val stringWriter = new StringWriter()
    val jsonWriter = jsonWriterFactory.createWriter(stringWriter)
    jsonWriter.write(jsonObject)
    jsonWriter.close()
    stringWriter.toString
  }
}

object JsonEvent {
  // The prefix of JSON keys whose values are JSON-LD documents.
  private val JsonLDKeyPrefix = "jsonld__"

  // A regex for identifying JSON keys whose values are JSON-LD documents.
  private val JsonLDKeyPrefixRegex: Regex = ("^" + JsonLDKeyPrefix + "(.*)$").r

  /**
	 * Parses a JSON string to a [[JsonEvent]].
	 *
	 * @param jsonString the JSON string to be parsed.
	 * @return the corresponding [[JsonEvent]].
	 */
  def parse(jsonString: String): JsonEvent = {

    /**
		 * Requires a [[JsonValue]] to be a [[JsonObject]].
		 */
    def requireJsonObject(jsonValue: JsonValue): JsonObject = {
      jsonValue match {
        case jsonValueAsJsonObject: JsonObject => jsonValueAsJsonObject
        case _                                 => throw DataConversionException("JSON object expected")
      }
    }

    // Parse the JSON.
    val jsonObject: JsonObject = requireJsonObject(Json.createReader(new StringReader(jsonString)).read())

    // Separate the event metadata from the embedded JSON-LD documents.

    val metadataBuilder: JsonObjectBuilder = Json.createObjectBuilder()
    val jsonLDDocuments: collection.mutable.Map[String, JsonLDDocument] = collection.mutable.Map.empty

    for ((jsonKey: String, jsonValue: JsonValue) <- jsonObject.asScala) {
      // Does this key refer to an embedded JSON-LD document?
      jsonKey match {
        case JsonLDKeyPrefixRegex(jsonLDKey) =>
          // Yes. Convert it to a JsonLDDocument and add it to the collection of embedded JSON-LD documents.
          val jsonLDDocument: JsonLDDocument = JsonLDUtil.jsonToJsonLD(requireJsonObject(jsonValue))
          jsonLDDocuments.put(jsonLDKey, jsonLDDocument)

        case _ =>
          // No. Add it to the metadata.
          metadataBuilder.add(jsonKey, jsonValue)
      }
    }

    JsonEvent(metadata = metadataBuilder.build(), jsonLDDocuments = jsonLDDocuments.toMap)
  }
}
