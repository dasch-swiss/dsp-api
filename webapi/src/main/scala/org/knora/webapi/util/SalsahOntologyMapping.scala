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

package org.knora.webapi.util

import java.io.File

import org.knora.webapi.OntologyConstants
import spray.json._

import scala.collection.breakOut
import scala.io.Source

/**
  * Translates SALSAH's resource types, properties, representation types, value types, and GUI element names into their Knora equivalents.
  */
class SalsahOntologyMappingReader {

    /**
      * A spray-json protocol for reading JSON configuration files that are used for exporting vocabularies from SALSAH.
      */
    private object SalsahOntologyMappingProtocol extends DefaultJsonProtocol {

        implicit object SalsahOntologyMappingJsonFormat extends RootJsonFormat[SalsahOntologyMapping] {
            def read(ontologyMappingJsValue: JsValue): SalsahOntologyMapping = {
                def makeIri(knoraOntologyName: String, entityName: String): String = {
                    if (knoraOntologyName == "salsah") {
                        val endOfIri = entityName.replace(":", "#")
                        s"http://www.knora.org/ontology/$endOfIri"
                    } else {
                        s"http://www.knora.org/ontology/$knoraOntologyName#$entityName"
                    }
                }

                def convertResourceType(resourceTypeMappingJsValue: JsValue, knoraOntologyName: String): SalsahResourceType = {
                    val resourceTypeFields = resourceTypeMappingJsValue.asJsObject.fields
                    val knoraResourceTypeIri = makeIri(knoraOntologyName, resourceTypeFields("type").asInstanceOf[JsString].value)

                    val propMappings = resourceTypeFields("props").asJsObject.fields
                    val properties = propMappings.map {
                        case (salsahPropertyName, propertyDesc) =>
                            salsahPropertyName -> makeIri(knoraOntologyName, propertyDesc.asJsObject.fields("predicate").asInstanceOf[JsArray].elements.last.asInstanceOf[JsString].value)
                    }

                    val representations = resourceTypeFields.get("representations") match {
                        case Some(representationsMappings) =>
                            representationsMappings.asJsObject.fields.map {
                                case (salsahRepresentationName, knoraRepresentationName) =>
                                    salsahRepresentationName -> makeIri(knoraOntologyName, knoraRepresentationName.asInstanceOf[JsString].value)
                            }
                        case None => Map.empty[String, String]
                    }

                    SalsahResourceType(knoraResourceTypeIri, properties, representations)
                }

                val (salsahOntologyName, mapping) = ontologyMappingJsValue.asJsObject.fields.head
                val mappingContents = mapping.asJsObject.fields
                val knoraOntologyName = mappingContents("ontology").asInstanceOf[JsString].value

                val jsonResourceTypes: Map[String, JsValue] = mappingContents - "ontology"

                val resourceTypes = jsonResourceTypes.map {
                    case (salsahResourceTypeName, resourceTypeMapping) =>
                        s"$salsahOntologyName:$salsahResourceTypeName" -> convertResourceType(resourceTypeMapping, knoraOntologyName)
                }

                SalsahOntologyMapping(salsahOntologyName, knoraOntologyName, resourceTypes)
            }

            def write(mapping: SalsahOntologyMapping): JsValue = ???
        }

    }

    import SalsahOntologyMappingProtocol._

    private val translationDir = new File("src/main/resources/salsah-ontology-translation")

    /**
      * The ontology mappings loaded from the JSON configuration files.
      */
    val ontologyMappings: Map[String, SalsahOntologyMapping] = {
        val files = translationDir.listFiles.filter(_.getName.endsWith(".json"))
        val fileStrings = files.map(Source.fromFile(_).mkString)
        val mappings = fileStrings.map(_.parseJson.convertTo[SalsahOntologyMapping])
        mappings.map {
            mapping => mapping.salsahName -> mapping
        }(breakOut)
    }

    /**
      * A `Map` of all SALSAH resource type names to their Knora IRIs.
      */
    val resourceTypeNames: Map[String, String] = ontologyMappings.foldLeft(Map.empty[String, String]) {
        case (acc, (_, ontologyMapping)) =>
            acc ++ ontologyMapping.resourceTypes.map {
                case (salsahResourceTypeName, resourceType) => salsahResourceTypeName -> resourceType.knoraIri
            }
    }

    /**
      * A `Map` of all SALSAH resource type names to maps of their SALSAH property names to Knora IRIs.
      */
    val propertyNames: Map[String, Map[String, String]] = ontologyMappings.foldLeft(Map.empty[String, Map[String, String]]) {
        case (ontologyPropertiesAcc, (_, ontologyMapping)) =>
            ontologyPropertiesAcc ++ ontologyMapping.resourceTypes.foldLeft(Map.empty[String, Map[String, String]]) {
                case (resourceTypesPropertiesAcc, (salsahResourceTypeName, resourceType)) => resourceTypesPropertiesAcc + (salsahResourceTypeName -> resourceType.properties)
            }
    }

    /**
      * A `Map` of all SALSAH file value names to their Knora IRIs.
      */
    val fileValueNames: Map[String, String] = ontologyMappings.foldLeft(Map.empty[String, String]) {
        case (ontologyFileValuesAcc, (_, ontologyMapping)) =>
            ontologyFileValuesAcc ++ ontologyMapping.resourceTypes.foldLeft(Map.empty[String, String]) {
                case (resourceTypesFileValuesAcc, (_, resourceType)) => resourceTypesFileValuesAcc ++ resourceType.fileValueTypes
            }
    }

    private def parseTableFile(filename: String): Map[String, String] = {
        Source.fromFile(new File(translationDir.getPath, filename)).getLines().map {
            line =>
                val splitLine = line.split("\\s+")
                splitLine(0) -> (splitLine(1) + ":" + splitLine(2))
        }.toMap
    }

    /**
      * A `Map` of all SALSAH resource type numeric IDs to their SALSAH names.
      */
    val resourceTypeNumericIDs: Map[String, String] = parseTableFile("resource-types.txt")

    /**
      * A `Map` of all SALSAH property numeric IDs to their SALSAH names.
      */
    val propertyNumericIDs: Map[String, String] = parseTableFile("properties.txt")

    /**
      * A `Map` of SALSAH value type numeric IDs to their Knora IRIs.
      */
    val valueTypeNumericIDs = Map(
        "1" -> OntologyConstants.KnoraBase.TextValue,
        "2" -> OntologyConstants.KnoraBase.IntValue,
        "3" -> OntologyConstants.KnoraBase.DecimalValue,
        "4" -> OntologyConstants.KnoraBase.DateValue,
        "5" -> OntologyConstants.KnoraBase.DateValue,
        "6" -> OntologyConstants.KnoraBase.LinkValue, // for a link to a resource
        "7" -> OntologyConstants.KnoraBase.ListValue, // TODO: is this correct?
        "9" -> OntologyConstants.KnoraBase.IntervalValue,
        "10" -> OntologyConstants.KnoraBase.GeomValue,
        "11" -> OntologyConstants.KnoraBase.ColorValue,
        "12" -> OntologyConstants.KnoraBase.ListValue,
        "14" -> OntologyConstants.KnoraBase.TextValue
    )
}

/**
  * Represents a JSON configuration file for exporting a vocabulary from SALSAH. Each file maps a SALSAH vocabulary onto a Knora
  * ontology.
  * @param salsahName the name of the SALSAH vocabulary.
  * @param knoraName the name of the corresponding Knora ontology.
  * @param resourceTypes a map of SALSAH resource type names to [[SalsahResourceType]] objects.
  */
case class SalsahOntologyMapping(salsahName: String, knoraName: String, resourceTypes: Map[String, SalsahResourceType])

/**
  * Represents a mapping between a SALSAH resource type and the corresponding Knora resource class.
  * @param knoraIri the IRI of the Knora resource class.
  * @param properties a map of SALSAH property names to the corresponding Knora property IRIs, representing the properties
  *                   that the resource class can have.
  * @param fileValueTypes a map of SALSAH file value type names to the corresponding Knora file value type IRIs, representing
  *                       the file value types that the resource class can have.
  */
case class SalsahResourceType(knoraIri: String, properties: Map[String, String], fileValueTypes: Map[String, String])

