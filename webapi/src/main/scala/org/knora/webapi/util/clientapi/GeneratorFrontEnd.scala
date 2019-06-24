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

import java.net.URLEncoder

import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.knora.webapi.messages.v2.responder.ontologymessages.{ClassInfoContentV2, InputOntologyV2, PropertyInfoContentV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld.JsonLDUtil
import org.knora.webapi.util.{SmartIri, StringFormatter}
import org.knora.webapi.{ClientApiGenerationException, InconsistentTriplestoreDataException, OntologyConstants}

import scala.collection.mutable
import scala.util.Try

/**
  * The front end of the client code generator. It is responsible for producing [[ClientClassDefinition]] objects
  * representing the Knora classes used in an API.
  */
class GeneratorFrontEnd(useHttps: Boolean, host: String, port: Int) {
    private val httpClient: HttpClient = HttpClients.createDefault
    private val ontologies: mutable.Map[SmartIri, InputOntologyV2] = mutable.Map.empty

    /**
      * Returns a set of [[ClientClassDefinition]] instances representing the Knora classes used by a client API.
      */
    def getClientClassDefs(clientApi: ClientApi): Set[ClientClassDefinition] = {
        /**
          * Recursively gets class definitions.
          *
          * @param classIri the IRI of a class whose definition is needed.
          * @param definitionAcc the class definitions collected so far.
          * @return the additional class definitions that were found.
          */
        def getClassDefsRec(classIri: SmartIri, definitionAcc: Map[SmartIri, ClientClassDefinition]): Map[SmartIri, ClientClassDefinition] = {
            val classOntology: InputOntologyV2 = getOntology(classIri.getOntologyFromEntity)
            val rdfClassDef: ClassInfoContentV2 = classOntology.classes.getOrElse(classIri, throw ClientApiGenerationException(s"Class <$classIri> not found"))

            val rdfPropertyIris: Set[SmartIri] = rdfClassDef.directCardinalities.keySet.filter {
                propertyIri => propertyIri.isKnoraEntityIri
            }

            val rdfPropertyDefs: Map[SmartIri, PropertyInfoContentV2] = rdfPropertyIris.map {
                propertyIri =>
                    val ontology = getOntology(propertyIri.getOntologyFromEntity)
                    propertyIri -> ontology.properties.getOrElse(propertyIri, throw ClientApiGenerationException(s"Property <$propertyIri> not found"))
            }.toMap

            val clientClassDef = rdfClassDef2ClientClassDef(rdfClassDef, rdfPropertyDefs)
            val newDefinitionAcc = definitionAcc + (clientClassDef.classIri -> clientClassDef)

            // Recursively get definitions of classes used as property object types.
            val propertyObjectClassDefs: Map[SmartIri, ClientClassDefinition] = clientClassDef.properties.foldLeft(newDefinitionAcc) {
                case (acc, clientPropertyDef) =>
                    clientPropertyDef.objectType match {
                        case classRef: ClassRef =>
                            // Do we have this class's definition already?
                            if (newDefinitionAcc.contains(classRef.classIri)) {
                                // Yes. Nothing more to do.
                                acc
                            } else {
                                // No. Get it recursively.
                                acc ++ getClassDefsRec(classIri = classRef.classIri, definitionAcc = acc)
                            }

                        case _ => acc
                    }
            }

            propertyObjectClassDefs ++ propertyObjectClassDefs
        }

        clientApi.classIrisUsed.foldLeft(Map.empty[SmartIri, ClientClassDefinition]) {
            case (acc, classIri) => getClassDefsRec(classIri = classIri, definitionAcc = acc)
        }.values.toSet
    }

    /**
      * Gets an ontology from Knora.
      *
      * @param ontologyIri the IRI of the ontology.
      * @return an [[InputOntologyV2]] representing the ontology.
      */
    private def getOntology(ontologyIri: SmartIri): InputOntologyV2 = {
        ontologies.get(ontologyIri) match {
            case Some(ontology) => ontology

            case None =>
                val schema = if (useHttps) "https" else "http"
                val ontologyApiPath = s"/v2/ontologies/allentities/${URLEncoder.encode(ontologyIri.toString, "UTF-8")}"
                val uri = s"$schema://$host:$port/$ontologyApiPath"
                val httpGet = new HttpGet(uri)
                val response = httpClient.execute(httpGet)

                val responseStrTry: Try[String] = Try {
                    val statusCode = response.getStatusLine.getStatusCode

                    if (statusCode / 100 != 2) {
                        throw ClientApiGenerationException(s"Knora responded with error $statusCode: ${response.getStatusLine.getReasonPhrase}")
                    }

                    Option(response.getEntity) match {
                        case Some(entity) => EntityUtils.toString(entity)
                        case None => throw ClientApiGenerationException(s"Knora returned an empty response.")
                    }
                }

                val responseStr: String = responseStrTry.get
                val ontology = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(responseStr)).unescape
                ontologies.put(ontologyIri, ontology)
                ontology
        }
    }

    /**
      * Converts RDF class definitions into [[ClientClassDefinition]] instances.
      *
      * @param rdfClassDef     the class definition to be converted.
      * @param rdfPropertyDefs the definitions of the properties used in the class.
      * @return a [[ClientClassDefinition]] describing the class.
      */
    private def rdfClassDef2ClientClassDef(rdfClassDef: ClassInfoContentV2, rdfPropertyDefs: Map[SmartIri, PropertyInfoContentV2]): ClientClassDefinition = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val isResourceClass = rdfClassDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2Complex.IsResourceClass.toSmartIri)

        if (isResourceClass) {
            rdfResourceClassDef2ClientClassDef(rdfClassDef, rdfPropertyDefs)
        } else {
            rdfNonResourceClassDef2ClientClassDef(rdfClassDef, rdfPropertyDefs)
        }
    }

    /**
      * Converts Knora resource class definitions into [[ClientClassDefinition]] instances.
      *
      * @param rdfClassDef     the class definition to be converted.
      * @param rdfPropertyDefs the definitions of the properties used in the class.
      * @return a [[ClientClassDefinition]] describing the class.
      */
    private def rdfResourceClassDef2ClientClassDef(rdfClassDef: ClassInfoContentV2, rdfPropertyDefs: Map[SmartIri, PropertyInfoContentV2]): ClientClassDefinition = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val classDescription: Option[String] = rdfClassDef.getPredicateStringLiteralObject(OntologyConstants.Rdfs.Comment.toSmartIri)

        val cardinalitiesWithoutLinkProps = rdfClassDef.directCardinalities.filter {
            case (propertyIri, _) =>
                rdfPropertyDefs.get(propertyIri) match {
                    case Some(rdfPropertyDef) => !rdfPropertyDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2Complex.IsLinkProperty.toSmartIri)
                    case None => true
                }
        }

        val clientPropertyDefs: Vector[ClientPropertyDefinition] = cardinalitiesWithoutLinkProps.map {
            case (propertyIri, knoraCardinalityInfo) =>
                val propertyName = propertyIri.getEntityName

                if (propertyIri.isKnoraEntityIri) {
                    val rdfPropertyDef = rdfPropertyDefs(propertyIri)
                    val propertyDescription: Option[String] = rdfPropertyDef.getPredicateStringLiteralObject(OntologyConstants.Rdfs.Comment.toSmartIri)
                    val ontologyObjectType: SmartIri = rdfPropertyDef.requireIriObject(OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri, throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-api:objectType"))
                    val isResourceProp = rdfPropertyDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2Complex.IsResourceProperty.toSmartIri)
                    val isEditable = rdfPropertyDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2Complex.IsEditable.toSmartIri)

                    if (isResourceProp) {
                        val isLinkValueProp = rdfPropertyDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2Complex.IsLinkValueProperty.toSmartIri)

                        val clientObjectType: ClientObjectType = if (isLinkValueProp) {
                            LinkVal(ontologyObjectType)
                        } else {
                            resourcePropObjectTypeToClientObjectType(ontologyObjectType)
                        }

                        ClientPropertyDefinition(
                            propertyName = propertyName,
                            propertyDescription = propertyDescription,
                            propertyIri = propertyIri,
                            objectType = clientObjectType,
                            cardinality = knoraCardinalityInfo.cardinality,
                            isEditable = isEditable
                        )
                    } else {
                        ClientPropertyDefinition(
                            propertyName = propertyName,
                            propertyDescription = propertyDescription,
                            propertyIri = propertyIri,
                            objectType = nonResourcePropObjectTypeToClientObjectType(ontologyObjectType),
                            cardinality = knoraCardinalityInfo.cardinality,
                            isEditable = isEditable
                        )
                    }
                } else {
                    ClientPropertyDefinition(
                        propertyName = propertyName,
                        propertyDescription = None,
                        propertyIri = propertyIri,
                        objectType = StringLiteral,
                        cardinality = knoraCardinalityInfo.cardinality,
                        isEditable = propertyIri.toString == OntologyConstants.Rdfs.Label // Labels of resources are editable
                    )
                }
        }.toVector.sortBy(_.propertyIri)

        ClientClassDefinition(
            className = makeClientClassName(rdfClassDef.classIri),
            classDescription = classDescription,
            classIri = rdfClassDef.classIri,
            properties = clientPropertyDefs.sortBy(_.propertyIri)
        )
    }

    /**
      * Converts Knora non-resource class definitions into [[ClientClassDefinition]] instances.
      *
      * @param rdfClassDef     the class definition to be converted.
      * @param rdfPropertyDefs the definitions of the properties used in the class.
      * @return a [[ClientClassDefinition]] describing the class.
      */
    private def rdfNonResourceClassDef2ClientClassDef(rdfClassDef: ClassInfoContentV2, rdfPropertyDefs: Map[SmartIri, PropertyInfoContentV2]): ClientClassDefinition = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val classDescription: Option[String] = rdfClassDef.getPredicateStringLiteralObject(OntologyConstants.Rdfs.Comment.toSmartIri)

        val clientPropertyDefs = rdfClassDef.directCardinalities.map {
            case (propertyIri, knoraCardinalityInfo) =>
                val propertyName = propertyIri.getEntityName

                if (propertyIri.isKnoraEntityIri) {
                    val rdfPropertyDef = rdfPropertyDefs(propertyIri)
                    val propertyDescription: Option[String] = rdfPropertyDef.getPredicateStringLiteralObject(OntologyConstants.Rdfs.Comment.toSmartIri)
                    val ontologyObjectType: SmartIri = rdfPropertyDef.getPredicateIriObject(OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri).getOrElse(OntologyConstants.Xsd.String.toSmartIri)

                    ClientPropertyDefinition(
                        propertyName = propertyName,
                        propertyDescription = propertyDescription,
                        propertyIri = propertyIri,
                        objectType = nonResourcePropObjectTypeToClientObjectType(ontologyObjectType),
                        cardinality = knoraCardinalityInfo.cardinality,
                        isEditable = true
                    )
                } else {
                    ClientPropertyDefinition(
                        propertyName = propertyName,
                        propertyDescription = None,
                        propertyIri = propertyIri,
                        objectType = StringLiteral,
                        cardinality = knoraCardinalityInfo.cardinality,
                        isEditable = true
                    )
                }
        }.toVector.sortBy(_.propertyIri)

        ClientClassDefinition(
            className = makeClientClassName(rdfClassDef.classIri),
            classDescription = classDescription,
            classIri = rdfClassDef.classIri,
            properties = clientPropertyDefs.sortBy(_.propertyIri)
        )
    }

    /**
      * Given the IRI of an RDF class, creates a client class name for it.
      *
      * @param classIri the class IRI.
      * @return the client class name.
      */
    private def makeClientClassName(classIri: SmartIri): String = {
        classIri.getEntityName.capitalize
    }

    /**
      * Given a Knora value object type, returns the corresponding [[ClientObjectType]].
      *
      * @param ontologyObjectType the RDF type.
      * @return the corresponding [[ClientObjectType]].
      */
    private def resourcePropObjectTypeToClientObjectType(ontologyObjectType: SmartIri): ClientObjectType = {
        ontologyObjectType.toString match {
            case OntologyConstants.KnoraApiV2Complex.Value => AbstractKnoraVal
            case OntologyConstants.KnoraApiV2Complex.TextValue => TextVal
            case OntologyConstants.KnoraApiV2Complex.IntValue => IntVal
            case OntologyConstants.KnoraApiV2Complex.DecimalValue => DecimalVal
            case OntologyConstants.KnoraApiV2Complex.BooleanValue => BooleanVal
            case OntologyConstants.KnoraApiV2Complex.DateValue => DateVal
            case OntologyConstants.KnoraApiV2Complex.GeomValue => GeomVal
            case OntologyConstants.KnoraApiV2Complex.IntervalValue => IntervalVal
            case OntologyConstants.KnoraApiV2Complex.ListValue => ListVal
            case OntologyConstants.KnoraApiV2Complex.UriValue => UriVal
            case OntologyConstants.KnoraApiV2Complex.GeonameValue => GeonameVal
            case OntologyConstants.KnoraApiV2Complex.ColorValue => ColorVal
            case OntologyConstants.KnoraApiV2Complex.StillImageFileValue => StillImageFileVal
            case OntologyConstants.KnoraApiV2Complex.MovingImageFileValue => MovingImageFileVal
            case OntologyConstants.KnoraApiV2Complex.AudioFileValue => AudioFileVal
            case OntologyConstants.KnoraApiV2Complex.DDDFileValue => DDDFileVal
            case OntologyConstants.KnoraApiV2Complex.TextFileValue => TextFileVal
            case OntologyConstants.KnoraApiV2Complex.DocumentFileValue => DocumentFileVal
            case _ => throw ClientApiGenerationException(s"Unexpected value type: $ontologyObjectType")
        }
    }

    /**
      * Given an object type that is not a Knora value type, returns the corresponding [[ClientObjectType]].
      *
      * @param ontologyObjectType the RDF type.
      * @return the corresponding [[ClientObjectType]].
      */
    private def nonResourcePropObjectTypeToClientObjectType(ontologyObjectType: SmartIri): ClientObjectType = {
        ontologyObjectType.toString match {
            case OntologyConstants.Xsd.String => StringLiteral
            case OntologyConstants.Xsd.Boolean => BooleanLiteral
            case OntologyConstants.Xsd.Integer => IntegerLiteral
            case OntologyConstants.Xsd.Decimal => DecimalLiteral
            case OntologyConstants.Xsd.Uri => UriLiteral
            case OntologyConstants.Xsd.DateTime | OntologyConstants.Xsd.DateTimeStamp => DateTimeStampLiteral

            case _ => ClassRef(
                className = makeClientClassName(ontologyObjectType),
                classIri = ontologyObjectType
            )
        }
    }
}
