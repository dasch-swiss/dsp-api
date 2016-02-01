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

import java.io.InputStream

import dispatch._
import spray.json._

import scala.collection.immutable.IndexedSeq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source

/**
  * Makes HTTP connections to SALSAH's API and Knora's API and compares the JSON they return. The SALSAH result is assumed
  * to be the correct one, except that the Knora result is allowed to have extra key-value pairs in objects, and some values may
  * be configured to be ignored (or checked just for their presence and type). Differences between SALSAH and Knora IDs are
  * taken into account. Automatic conversions between strings and numbers are assumed. Any other differences are printed to the console.
  */
object CompareApiResults extends App {

    // private val username = "geer"
    // private val password = "DeineMutter"

    private val mappingReader = new SalsahOntologyMappingReader()
    private val iriUtil = new KnoraIriUtil()
    private var verbose = false

    private var currentResourceID: Option[String] = None
    private var currentResourceTypeID: Option[String] = None
    private var currentResourceTypeName: Option[String] = None

    /**
      * SALSAH JSON object keys that should not be compared.
      */
    private val ignoreObjectKeys = Set(
        "resclass_name",
        "resclass_iconsrc",
        "resclass_icon",
        "restype_iconsrc",
        "vid",
        "is_annotation", // obsolete
        "language_id",
        "token",
        "vt_php_constant",
        "vt_name",
        "vt_value_field",
        "gui_attributes",
        "lastmod_utc"
    )

    /**
      * SALSAH JSON object keys whose values should be compared only to ensure that they are present and of the same type and don't contain "TODO".
      */
    private val justCheckType = Set(
        "path",
        "lastmod",
        "value_iconsrcs",
        "rights",
        "value_rights",
        "label",
        "restype_label",
        "description",
        "restype_description",
        "iconsrc",
        "email",
        "username",
        "user_id",
        "firstname",
        "lastname",
        "lang",
        "vocabulary"
    )

    /**
      * SALSAH JSON object keys in which TODOs are OK, because we can't fix them yet and they would otherwise make the output too long.
      */
    private val todoOK = Set(
        "path"
    )

    if (args.length < 2) {
        println("Usage: CompareApiResults [-v] <SALSAH-URL> <Knora-URL>")
        println("    -v Print verbose output.")
    } else {
        if (args(0) == "-v") {
            verbose = true
        }

        val salsahUrl = args(args.length - 2)
        val knoraUrl = args.last
        val testResults = compareUrls(salsahUrl, knoraUrl)
        testResults.foreach(println)
        Http.shutdown()
    }

    /**
      * Given the current JSON context (a Vector of strings representing the path that has been followed through the JSON tree), converts it to a string for output.
      * @param context the current JSON context.
      * @return
      */
    private def contextToString(context: Vector[String]): String = {
        context.mkString(" -> ")
    }

    /**
      * Adds verbose output to the Vector of test results.
      * @param context the current JSON context.
      * @param results the current test results.
      * @param result the new result to add.
      * @return the new Vector of test results.
      */
    private def addVerboseResult(context: Vector[String], results: Vector[String], result: String): Vector[String] = {
        if (verbose) {
            val contextStr = contextToString(context)
            results :+ s"$contextStr: $result"
        } else {
            results
        }
    }

    /**
      * Adds an error message to the Vector of test results.
      * @param context the current JSON context.
      * @param results the current test results.
      * @param error the error message to add.
      * @return the new Vector of test results.
      */
    private def addError(context: Vector[String], results: Vector[String], error: String): Vector[String] = {
        val contextStr = contextToString(context)
        results :+ s"Error: $contextStr: $error"
    }

    /**
      * Accesses a SALSAH URL and a Knora URL over HTTP and compares the results.
      * @param salsahUrl the SALSAH URL.
      * @param knoraUrl the Knora URL.
      * @return the test results.
      */
    private def compareUrls(salsahUrl: String, knoraUrl: String): Vector[String] = {
        val salsahJson = getJsonResponse(salsahUrl)
        val knoraJson = getJsonResponse(knoraUrl)

        currentResourceID = salsahJson match {
            case JsObject(fields) =>
                fields.get("resdata") match {
                    case Some(resdata: JsObject) =>
                        Some(convertToString(resdata.fields("res_id")))
                    case other => None
                }
            case other => None
        }

        currentResourceTypeID = salsahJson match {
            case JsObject(fields) =>
                fields.get("resinfo") match {
                    case Some(resinfo: JsObject) =>
                        Some(convertToString(resinfo.fields("restype_id")))
                    case other => None
                }
            case other => None
        }

        currentResourceTypeName = salsahJson match {
            case JsObject(fields) =>
                fields.get("restype_info") match {
                    case Some(resTypeInfo: JsObject) =>
                        Some(convertToString(resTypeInfo.fields("name")))
                    case other => None
                }
            case other => None
        }

        compareJsValues(salsahJson, knoraJson, None, Vector.empty[String], Vector.empty[String])
    }

    /**
      * Compares two JSON values, one from SALSAH and one from Knora.
      * @param salsahValue the value from SALSAH.
      * @param knoraValue the value from Knora.
      * @param currentIncomingResourceTypeID the current incoming resource ID, if any.
      * @param context the current JSON context.
      * @param results the test results so far.
      * @return the new Vector of test results.
      */
    private def compareJsValues(salsahValue: JsValue, knoraValue: JsValue, currentIncomingResourceTypeID: Option[String], context: Vector[String], results: Vector[String]): Vector[String] = {
        (salsahValue, knoraValue) match {
            case (salsahObj: JsObject, knoraObj: JsObject) => compareJsObjects(salsahObj, knoraObj, isRefProp = false, isResourceContext = false, currentIncomingResourceTypeID, context, results)
            case (salsahArray: JsArray, knoraArray: JsArray) => compareJsArrays(salsahArray, knoraArray, isIncoming = false, isValueRestype = false, context, results)
            case (salsahString: JsString, knoraString: JsString) => compareJsStrings(salsahString, knoraString, context, results)
            case (salsahString: JsString, JsNumber(knoraNumberValue)) => compareJsStrings(salsahString, JsString(knoraNumberValue.toString()), context, results)
            case (JsNumber(salsahNumberValue), knoraString: JsString) => compareJsStrings(JsString(salsahNumberValue.toString()), knoraString, context, results)
            case (salsahNumber: JsNumber, knoraNumber: JsNumber) => compareJsNumbers(salsahNumber, knoraNumber, context, results)
            case (salsahBoolean: JsBoolean, knoraBoolean: JsBoolean) => compareJsBooleans(salsahBoolean, knoraBoolean, context, results)
            case (JsNull, knoraVal) => compareJsNull(knoraVal, context, results)
            case (salsahVal, knoraVal) => addError(context, results, s"$salsahVal != $knoraVal")
        }
    }

    /**
      * Given a JsObject that may contain arrays (such as arrays of locations and incoming resource references), sorts the arrays in appropriate ways
      * so that they can be compared, and optionally converts certain SALSAH IDs to Knora IRIs.
      * @param obj the object that may contain arrays.
      * @param convertIDs if true, converts certain SALSAH IDs to Knora IRIs (others are converted in `convertSalsahKeyValuePairToKnora`).
      * @param isRefProp true if the object represents a resource reference property.
      * @param isResourceContext true if the object represents a resource context.
      * @return the converted object.
      */
    private def normalizeArrays(obj: JsObject, convertIDs: Boolean, isRefProp: Boolean, isResourceContext: Boolean): JsObject = {
        // Represents a SALSAH value object (a cross-section of parallel arrays) for sorting.
        case class SalsahValueObj(value_id: JsValue,
                                  value: Option[JsValue],
                                  comment: Option[JsValue],
                                  value_iconsrc: Option[JsValue],
                                  value_restype: Option[JsValue],
                                  value_firstprop: Option[JsValue],
                                  value_rights: Option[JsValue])

        // Represents a SALSAH resource context item (a cross-section of parallel arrays) for sorting.
        case class SalsahResourceContextItem(res_id: JsValue,
                                             preview: JsValue,
                                             firstprop: JsValue,
                                             region: JsValue)

        // Given an Option that may contain a JsArray, returns the item at a certain index in the array, or None.
        def maybeItem(maybeJsArray: Option[JsValue], index: Int): Option[JsValue] = {
            maybeJsArray match {
                case Some(JsArray(elements)) => Some(elements(index))
                case other => None
            }
        }

        // Given an Option that may contain a JsValue, returns the value, or JsNull.
        def maybeJsNull(maybeVal: Option[JsValue]): JsValue = {
            maybeVal match {
                case Some(jsVal) => jsVal
                case None => JsNull
            }
        }

        // Sort properties in resource type definitions.

        val fields = obj.fields

        val resourceTypeNormalizedFields = obj.fields.get("properties") match {
            case Some(JsArray(propertiesVector)) =>
                val convertedPropertiesVector = if (convertIDs) {
                    propertiesVector.filterNot {
                        item => convertToString(item.asJsObject.fields("name")).endsWith("_rt") // Rich text properties have been merged with normal text properties
                    }.map {
                        item =>
                            val resourceTypeProperties = mappingReader.propertyNames(currentResourceTypeName.get)
                            val propertyFields = item.asJsObject.fields
                            val propertyID = convertToString(propertyFields("id"))
                            val convertedPropertyID = JsString(resourceTypeProperties(mappingReader.propertyNumericIDs(propertyID)))
                            // println(s"Converted property ID $propertyID to $convertedPropertyID")
                            val fullPropertyName = convertToString(propertyFields("vocabulary")) + ":" + convertToString(propertyFields("name"))
                            val convertedPropertyName = JsString(resourceTypeProperties(fullPropertyName))
                            // println(s"Converted property name $fullPropertyName to $convertedPropertyName")
                            val convertedValueTypeID = JsString(mappingReader.valueTypeNumericIDs(convertToString(propertyFields("valuetype_id"))))
                            val convertedPropertyFields: Map[String, JsValue] = propertyFields +
                                ("id" -> convertedPropertyID) +
                                ("name" -> convertedPropertyName) +
                                ("valuetype_id" -> convertedValueTypeID)
                            JsObject(convertedPropertyFields)
                    }
                } else {
                    propertiesVector
                }

                val sortedProperties = convertedPropertiesVector.sortWith {
                    case (leftProperty, rightProperty) =>
                        convertToString(leftProperty.asJsObject.fields("id")) < convertToString(rightProperty.asJsObject.fields("id"))
                }

                /*
                println("Sorted properties:")
                for (property <- sortedProperties) println(property.asJsObject.fields("id"))
                println()
                */

                fields + ("properties" -> JsArray(sortedProperties))
            case other => fields
        }

        // Normalize parallel arrays representing property values.

        val resourceFullNormalizedFields = resourceTypeNormalizedFields.get("value_ids") match {
            case Some(JsArray(valueIDsVector)) =>
                // Get the parallel arrays representing SALSAH value objects.

                val valuesArray = resourceTypeNormalizedFields.get("values")
                val commentsArray = resourceTypeNormalizedFields.get("comments")
                val iconsArray = resourceTypeNormalizedFields.get("value_iconsrcs")
                val restypesArray = resourceTypeNormalizedFields.get("value_restype")
                val firstpropsArray = resourceTypeNormalizedFields.get("value_firstprops")
                val rightsArray = resourceTypeNormalizedFields.get("value_rights")

                // Deparallelize them for sorting, converting IDs if requested.

                val deparallelized: IndexedSeq[SalsahValueObj] = for {
                    i <- valueIDsVector.indices

                    valueIDItem = valueIDsVector(i)
                    value_id = if (convertIDs) {
                        val valueIDStr = convertToString(valueIDItem)

                        // There's no value ID 0, it's used only as a dummy value.
                        if (valueIDStr == "0") {
                            valueIDItem
                        } else if (isRefProp) {
                            maybeItem(valuesArray, i) match {
                                case Some(JsString(targetResID)) =>
                                    JsString(iriUtil.salsahResourceId2Iri(targetResID))
                                case other => JsString("")
                            }
                        } else {
                            JsString(iriUtil.salsahValueId2Iri(currentResourceID.get, valueIDStr))
                        }
                    } else {
                        valueIDItem
                    }

                    value = if (convertIDs && isRefProp) {
                        maybeItem(valuesArray, i) match {
                            case Some(JsString(targetResID)) =>
                                Some(JsString(iriUtil.salsahResourceId2Iri(targetResID)))
                            case other => None
                        }
                    } else {
                        maybeItem(valuesArray, i)
                    }

                    comment = maybeItem(commentsArray, i)
                    value_iconsrc = maybeItem(iconsArray, i)
                    value_restype = maybeItem(restypesArray, i)
                    value_firstprop = maybeItem(firstpropsArray, i)
                    value_rights = maybeItem(rightsArray, i)
                } yield SalsahValueObj(value_id, value, comment, value_iconsrc, value_restype, value_firstprop, value_rights)

                // Sort them.

                val sorted = deparallelized.sortWith {
                    case (leftValueObj, rightValueObj) => convertToString(leftValueObj.value_id) < convertToString(rightValueObj.value_id)
                }

                // Reparallelize them.

                val sortedValueIDs = JsArray(sorted.map(_.value_id).toVector)
                val sortedValues = JsArray(sorted.map(elem => maybeJsNull(elem.value)).toVector)
                val sortedComments = JsArray(sorted.map(elem => maybeJsNull(elem.comment)).toVector)
                val sortedIcons = JsArray(sorted.map(elem => maybeJsNull(elem.value_iconsrc)).toVector)
                val sortedRestypes = JsArray(sorted.map(elem => maybeJsNull(elem.value_restype)).toVector)
                val sortedFirstprops = JsArray(sorted.map(elem => maybeJsNull(elem.value_firstprop)).toVector)
                val sortedRights = JsArray(sorted.map(elem => maybeJsNull(elem.value_rights)).toVector)

                resourceTypeNormalizedFields +
                    ("value_ids" -> sortedValueIDs) +
                    ("values" -> sortedValues) +
                    ("comments" -> sortedComments) +
                    ("value_iconsrcs" -> sortedIcons) +
                    ("value_restype" -> sortedRestypes) +
                    ("value_firstprops" -> sortedFirstprops) +
                    ("value_rights" -> sortedRights)

            case other => resourceTypeNormalizedFields
        }

        // Normalize a resource context or the representations of a resource.

        val locationsNormalizedFields = if (isResourceContext && resourceFullNormalizedFields.get("res_id").nonEmpty) {
            // Get the parallel arrays representing items in the resource context.

            val resIDs = resourceFullNormalizedFields("res_id").asInstanceOf[JsArray].elements
            val previews = resourceFullNormalizedFields("preview").asInstanceOf[JsArray].elements
            val firstprops = resourceFullNormalizedFields("firstprop").asInstanceOf[JsArray].elements
            val regions = resourceFullNormalizedFields("region").asInstanceOf[JsArray].elements


            // Deparallelize the parallel arrays for sorting, converting resource IDs if requested.

            val deparallelized = for (i <- resIDs.indices) yield SalsahResourceContextItem(
                if (convertIDs) JsString(iriUtil.salsahResourceId2Iri(resIDs(i).asInstanceOf[JsString].value)) else resIDs(i),
                previews(i),
                firstprops(i),
                regions(i)
            )

            // Sort the resource context items by resource ID.

            val sorted = deparallelized.sortWith {
                case (leftContextItem, rightContextItem) =>
                    convertToString(leftContextItem.res_id) < convertToString(rightContextItem.res_id)
            }

            // Reparallelize them.

            val sortedResIDs = JsArray(sorted.map(_.res_id).toVector)
            val sortedPreviews = JsArray(sorted.map(_.preview).toVector)
            val sortedFirstprops = JsArray(sorted.map(_.firstprop).toVector)
            val sortedRegions = JsArray(sorted.map(_.region).toVector)

            resourceFullNormalizedFields +
                ("res_id" -> sortedResIDs) +
                ("preview" -> sortedPreviews) +
                ("firstprop" -> sortedFirstprops) +
                ("region" -> sortedRegions)
        } else {
            // Sort resource representations.

            resourceFullNormalizedFields.get("locations") match {
                case Some(JsArray(locationsVector)) =>
                    val sortedLocations = locationsVector.sortWith {
                        case (left, right) =>
                            convertToNumber(left.asJsObject.fields("nx")) < convertToNumber(right.asJsObject.fields("nx"))
                    }

                    resourceFullNormalizedFields + ("locations" -> JsArray(sortedLocations))
                case other => resourceFullNormalizedFields
            }
        }

        // Normalize incoming resource references.

        val incomingNormalizedFields = locationsNormalizedFields.get("incoming") match {
            case Some(JsArray(incomingVector)) =>
                val convertedIncomingVector = if (convertIDs) {
                    incomingVector.map {
                        elem =>
                            val elemFields = elem.asJsObject.fields
                            val extResIdFields = elemFields("ext_res_id").asJsObject.fields
                            val convertedID = iriUtil.salsahResourceId2Iri(convertToString(extResIdFields("id")))
                            val convertedExtResID = JsObject(extResIdFields + ("id" -> JsString(convertedID)))
                            JsObject(elemFields + ("ext_res_id" -> convertedExtResID))
                    }
                } else {
                    incomingVector
                }

                val sortedIncoming = convertedIncomingVector.sortWith {
                    case (left, right) =>
                        convertToString(left.asJsObject.fields("ext_res_id").asJsObject.fields("id")) < convertToString(right.asJsObject.fields("ext_res_id").asJsObject.fields("id"))
                }

                locationsNormalizedFields + ("incoming" -> JsArray(sortedIncoming))
            case other => locationsNormalizedFields
        }

        // Normalize hierarchical lists.

        // Recursively converts list node IDs in a hierarchical list.
        def convertIDsInTree(treeType: String, treeVector: Vector[JsValue]): Vector[JsValue] = {
            // Converts the IDs of sibling nodes in a hierarchical list.
            def convertSiblingIDs(siblings: Vector[JsValue]): Vector[JsValue] = {
                siblings.map {
                    case JsObject(siblingFields) =>
                        val originalID = siblingFields("id").asInstanceOf[JsString].value
                        val convertedID = treeType match {
                            case "hlist" => iriUtil.salsahHListId2Iri(originalID)
                            case "selection" => iriUtil.salsahSelectionId2Iri(originalID)
                            case other => originalID
                        }

                        JsObject(siblingFields + ("id" -> JsString(convertedID)))
                    case other => other
                }
            }

            // Convert the IDs of the nodes on this level, then recursively convert the IDs in their lists of child nodes.
            convertSiblingIDs(treeVector).map {
                case JsObject(treeNodeFields) =>
                    treeNodeFields.get("children") match {
                        case Some(JsArray(children)) =>
                            if (children.nonEmpty) {
                                JsObject(treeNodeFields + ("children" -> JsArray(convertIDsInTree(treeType, children))))
                            } else {
                                JsObject(treeNodeFields)
                            }
                        case other => JsObject(treeNodeFields)
                    }
                case other => other
            }
        }

        // See if we have a hierarchical list ("hlist" or "selection") with IDs that need to be converted.

        val treeFields = if (convertIDs) {
            // Make a List containing an Option for the value of "hlist" and an Option for the value of "selection",
            // then get the first Option that's defined in the List, if any.
            val tree: Option[(String, JsValue)] = Vector("hlist", "selection").map {
                name => name -> incomingNormalizedFields.get(name)
            }.collectFirst {
                case (name, Some(list)) => (name, list)
            }

            // Was either of them defined?
            tree match {
                // If so, convert its IDs.
                case Some((name, JsArray(treeVector))) =>
                    incomingNormalizedFields + (name -> JsArray(convertIDsInTree(name, treeVector)))
                case other => incomingNormalizedFields
            }
        } else {
            incomingNormalizedFields
        }

        JsObject(treeFields)
    }

    /**
      * Compares two JSON objects, one from SALSAH and one from Knora.
      * @param salsahObject the object from SALSAH.
      * @param knoraObject the object from Knora.
      * @param isRefProp true if the objects represent a resource reference property.
      * @param isResourceContext true if the objects represent a resource context.
      * @param currentIncomingResourceTypeID the current incoming resource ID, if any.
      * @param context the current JSON context.
      * @param results the test results so far.
      * @return the new Vector of test results.
      */
    private def compareJsObjects(salsahObject: JsObject, knoraObject: JsObject, isRefProp: Boolean, isResourceContext: Boolean, currentIncomingResourceTypeID: Option[String], context: Vector[String], results: Vector[String]): Vector[String] = {
        def sortAttributes(attributesJsValue: JsValue): JsValue = {
            attributesJsValue match {
                case JsString(attributes) =>
                    JsString(attributes.split(";").sorted.mkString(";"))
                case other => attributesJsValue
            }
        }

        val (convertedSalsahObject, convertedKnoraObject) = (normalizeArrays(salsahObject, convertIDs = true, isRefProp, isResourceContext), normalizeArrays(knoraObject, convertIDs = false, isRefProp, isResourceContext))
        val objectResults = addVerboseResult(context, results, "Got two objects")
        val objectContext = context :+ "object"
        convertedSalsahObject.fields.foldLeft(objectResults) {
            case (acc, (salsahKey, salsahValue)) =>
                if (ignoreObjectKeys.contains(salsahKey)) {
                    acc
                } else {
                    val (salsahConvertedKey, salsahConvertedValue) = convertSalsahKeyValuePairToKnora(salsahKey, salsahValue, currentIncomingResourceTypeID)
                    if (justCheckType.contains(salsahKey)) {
                        convertedKnoraObject.fields.get(salsahConvertedKey) match {
                            case Some(knoraValue: JsValue) =>
                                if (knoraValue.getClass == salsahConvertedValue.getClass) {
                                    knoraValue match {
                                        case JsString(strVal) if strVal.contains("TODO") && !todoOK.contains(salsahKey) =>
                                            addError(objectContext, acc, s"For key '$salsahKey', Knora's value contains 'TODO'")
                                        case other =>
                                            addVerboseResult(objectContext, acc, s"Key '$salsahKey' is present and of same type ($salsahConvertedValue, $knoraValue)")
                                    }
                                } else {
                                    addError(objectContext, acc, s"Key '$salsahKey' is present but not of the same type: $salsahConvertedValue != $knoraValue")
                                }
                            case None =>
                                addError(objectContext, acc, s"Key '$salsahKey' is missing")
                        }
                    } else {
                        val keyContext = objectContext :+ s"key '$salsahKey' ('$salsahConvertedKey')"
                        convertedKnoraObject.fields.get(salsahConvertedKey) match {
                            case Some(knoraObject: JsObject) =>
                                val isRefProp = salsahKey == "salsah:part_of" || salsahKey == "salsah:region_of"
                                val isResourceContext = salsahKey == "resource_context"

                                // Remove obsolete "iconsrc" from dummy "__location__" property.
                                val tweakedSalsahObject = if (salsahKey == "__location__") {
                                    JsObject(salsahConvertedValue.asJsObject.fields - "iconsrc")
                                } else if (salsahKey == "restype_info") {
                                    JsObject(salsahConvertedValue.asJsObject.fields + ("name" -> JsString(mappingReader.resourceTypeNames(currentResourceTypeName.get))))
                                } else {
                                    salsahConvertedValue.asJsObject
                                }

                                acc ++ compareJsObjects(tweakedSalsahObject, knoraObject, isRefProp, isResourceContext, currentIncomingResourceTypeID, keyContext, Vector.empty[String])
                            case Some(knoraArray: JsArray) =>
                                val isIncoming = salsahKey == "incoming"
                                val isValueRestype = salsahKey == "value_restype"
                                acc ++ compareJsArrays(salsahConvertedValue.asInstanceOf[JsArray], knoraArray, isIncoming, isValueRestype, keyContext, results)
                            case Some(JsString(strVal)) if salsahConvertedKey == "attributes" =>
                                acc ++ compareJsValues(sortAttributes(salsahConvertedValue), sortAttributes(JsString(strVal)), currentIncomingResourceTypeID, keyContext, Vector.empty[String])
                            case Some(otherKnoraValue: JsValue) =>
                                acc ++ compareJsValues(salsahConvertedValue, otherKnoraValue, currentIncomingResourceTypeID, keyContext, Vector.empty[String])
                            case None =>
                                if (!(isResourceContext && salsahKey == "locations")) {
                                    // SALSAH returns full locations in context query results, Knora doesn't
                                    addError(objectContext, acc, s"Key '$salsahKey' ('$salsahConvertedKey') missing")
                                } else {
                                    acc
                                }
                        }
                    }
                }
        }
    }

    /**
      * Given a key-value pair from SALSAH, converts certain SALSAH IDs to Knora IDs for comparison (others are converted in `normalizeArrays`).
      * @param salsahKey the key.
      * @param salsahValue the value.
      * @param currentIncomingResourceTypeID the current incoming resource ID, if any.
      * @return the converted key-value pair.
      */
    private def convertSalsahKeyValuePairToKnora(salsahKey: String, salsahValue: JsValue, currentIncomingResourceTypeID: Option[String]): (String, JsValue) = {
        val convertedKey = currentResourceTypeID match {
            case Some(resID) =>
                mappingReader.propertyNames(mappingReader.resourceTypeNumericIDs(resID)).get(salsahKey) match {
                    case Some(convertedProperty) => convertedProperty
                    case None => salsahKey
                }
            case None => salsahKey
        }

        val convertedValue = salsahValue match {
            case JsString(strVal) =>
                val convertedStr = salsahKey match {
                    case "project_id" => iriUtil.salsahProjectId2Iri(strVal)
                    case "person_id" => iriUtil.salsahPersonId2Iri(strVal)
                    case "restype_id" => mappingReader.resourceTypeNames(mappingReader.resourceTypeNumericIDs(strVal))
                    case "restype_name" => mappingReader.resourceTypeNames(strVal)
                    case "pid" =>
                        val salsahPropertyName = mappingReader.propertyNumericIDs(strVal)
                        val salsahResourceName = currentIncomingResourceTypeID match {
                            case Some(resID) => mappingReader.resourceTypeNumericIDs(resID)
                            case None => mappingReader.resourceTypeNumericIDs(currentResourceTypeID.get)
                        }
                        val resourceProperties = mappingReader.propertyNames(salsahResourceName)
                        resourceProperties.get(salsahPropertyName) match {
                            case Some(convertedPropName) => convertedPropName
                            case None =>
                                println(s"Error: Couldn't find Knora IRI for SALSAH property $salsahPropertyName of resource $salsahResourceName")
                                strVal
                        }
                    case "valuetype_id" => if (currentResourceTypeName.isEmpty) mappingReader.valueTypeNumericIDs(strVal) else strVal
                    case "res_id" => iriUtil.salsahResourceId2Iri(strVal)
                    case "parent_res_id" => iriUtil.salsahResourceId2Iri(strVal)
                    case "canonical_res_id" => iriUtil.salsahResourceId2Iri(strVal)
                    case other => strVal
                }
                JsString(convertedStr)
            case other => salsahValue
        }

        convertedKey -> convertedValue
    }

    /**
      * Compares two JSON arrays, one from SALSAH and one from Knora.
      * @param salsahArray the array from SALSAH.
      * @param knoraArray the array from Knora.
      * @param isIncoming true if this is an array of incoming resource references.
      * @param context the current JSON context.
      * @param results the test results so far.
      * @return the new Vector of test results.
      */
    private def compareJsArrays(salsahArray: JsArray, knoraArray: JsArray, isIncoming: Boolean, isValueRestype: Boolean, context: Vector[String], results: Vector[String]): Vector[String] = {
        val arrayContext = context :+ "array"

        val salsahElements = salsahArray.elements
        val knoraElements = knoraArray.elements

        if (salsahElements.length != knoraElements.length) {
            addError(context, results, s"Array ${salsahArray.elements} doesn't have the same length as array ${knoraArray.elements}")
        } else {
            val arrayResults = addVerboseResult(context, results, "Got two arrays of the same length")
            val zipped = salsahElements.zip(knoraElements)

            if (isValueRestype) {
                val arraysMatch = !zipped.exists {
                    case (salsahValue, knoraValue) =>
                        salsahValue.getClass != knoraValue.getClass
                }

                if (arraysMatch) {
                    addVerboseResult(arrayContext, arrayResults, "Compared value_restype arrays")
                } else {
                    addError(arrayContext, arrayResults, "The values in the value_restype arrays aren't all of the same types")
                }
            } else {
                zipped.foldLeft(arrayResults) {
                    case (acc, (salsahValue, knoraValue)) =>
                        val currentIncomingResourceTypeID = if (isIncoming) {
                            salsahValue match {
                                case JsObject(fields) =>
                                    Some(convertToString(fields("resinfo").asJsObject.fields("restype_id")))
                                case other => None
                            }
                        } else {
                            None
                        }

                        acc ++ compareJsValues(salsahValue, knoraValue, currentIncomingResourceTypeID, arrayContext, Vector.empty[String])
                }
            }
        }
    }

    /**
      * Compares two JSON strings, one from SALSAH, one from Knora.
      * @param salsahString the string from SALSAH.
      * @param knoraString the string from Knora.
      * @param context the current JSON context.
      * @param results the test results so far.
      * @return the new Vector of test results.
      */
    private def compareJsStrings(salsahString: JsString, knoraString: JsString, context: Vector[String], results: Vector[String]): Vector[String] = {
        val stringsMatch = (salsahString.value == knoraString.value) ||
            (Set(salsahString.value, knoraString.value) == Set("textarea", "richtext"))

        if (stringsMatch) {
            addVerboseResult(context, results, s"Compared strings: $salsahString = $knoraString")
        } else {
            addError(context, results, s"$salsahString != $knoraString")
        }
    }

    /**
      * Compares two JSON numbers, one from SALSAH, one from Knora.
      * @param salsahNumber the number from SALSAH.
      * @param knoraNumber the number from Knora.
      * @param context the current JSON context.
      * @param results the test results so far.
      * @return the new Vector of test results.
      */
    private def compareJsNumbers(salsahNumber: JsNumber, knoraNumber: JsNumber, context: Vector[String], results: Vector[String]): Vector[String] = {
        if (salsahNumber.value != knoraNumber.value) {
            addError(context, results, s"$salsahNumber != $knoraNumber")
        } else {
            addVerboseResult(context, results, s"$salsahNumber = $knoraNumber")
        }
    }

    /**
      * Compares two JSON boolean values, one from SALSAH, one from Knora.
      * @param salsahBoolean the boolean value from SALSAH.
      * @param knoraBoolean the boolean value from Knora.
      * @param context the current JSON context.
      * @param results the test results so far.
      * @return the new Vector of test results.
      */
    private def compareJsBooleans(salsahBoolean: JsBoolean, knoraBoolean: JsBoolean, context: Vector[String], results: Vector[String]): Vector[String] = {
        if (salsahBoolean.value != knoraBoolean.value) {
            addError(context, results, s"$salsahBoolean != $knoraBoolean")
        } else {
            addVerboseResult(context, results, s"$salsahBoolean = $knoraBoolean")
        }
    }

    /**
      * Compares a JSON null from SALSAH with a value from Knora.
      * @param knoraVal the value from Knora.
      * @param context the current JSON context.
      * @param results the test results so far.
      * @return the new Vector of test results.
      */
    private def compareJsNull(knoraVal: JsValue, context: Vector[String], results: Vector[String]): Vector[String] = {
        knoraVal match {
            case JsNull => addVerboseResult(context, results, "Compared nulls")
            case str: JsString => addVerboseResult(context, results, "Compared null with string")
            case other => addError(context, results, s"null != $other")
        }
    }

    /**
      * Converts any JSON value to a string.
      * @param valueToConvert the value to convert.
      * @return a string representation of the value.
      */
    private def convertToString(valueToConvert: JsValue): String = {
        valueToConvert match {
            case JsString(value) => value
            case JsNumber(value) => value.toString()
            case JsBoolean(value) => value.toString
            case JsNull => "null"
            case JsArray(elements) => "[array]"
            case JsObject(fields) => "[object]"
        }
    }

    /**
      * Converts any JSON value to a number.
      * @param valueToConvert the value to convert.
      * @return a [[BigDecimal]] representing the value.
      */
    private def convertToNumber(valueToConvert: JsValue): BigDecimal = {
        valueToConvert match {
            case JsNumber(value) => value
            case JsString(value) => BigDecimal(value)
            case other => BigDecimal(0)
        }
    }

    /**
      * Accesses an API endpoint and returns the response as parsed JSON.
      * @param urlStr the URL of the API endpoint.
      * @return the response as parsed JSON.
      */
    private def getJsonResponse(urlStr: String): JsValue = {
        val req = url(urlStr) /*.as_!(username, password)*/ .GET
        val responseFuture = Http(req)
        val response = responseFuture()
        val responseStream: InputStream = response.getResponseBodyAsStream
        val responseStreamString = Source.fromInputStream(responseStream, "UTF-8").mkString
        // println(responseStreamString)
        responseStreamString.parseJson
    }
}
