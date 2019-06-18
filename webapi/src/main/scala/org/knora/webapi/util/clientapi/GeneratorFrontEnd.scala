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
  * The front-end of the client code generator.
  */
class GeneratorFrontEnd(useHttps: Boolean, host: String, port: Int) {
    private val httpClient: HttpClient = HttpClients.createDefault
    private val ontologies: mutable.Map[SmartIri, InputOntologyV2] = mutable.Map.empty

    /**
      * Returns a set of [[ClientClassDefinition]] instances representing the Knora API classes used by a client API.
      */
    def getClientClassDefs(clientApi: ClientApi): Set[ClientClassDefinition] = {
        getClassIris(clientApi).map {
            classIri =>
                val classOntology: InputOntologyV2 = getOntology(classIri)
                val rdfClassDef: ClassInfoContentV2 = classOntology.classes.getOrElse(classIri, throw ClientApiGenerationException(s"Class <$classIri> not found"))

                val rdfPropertyIris: Set[SmartIri] = rdfClassDef.directCardinalities.keySet.filter {
                    propertyIri => propertyIri.isKnoraEntityIri
                }

                val rdfPropertyDefs: Map[SmartIri, PropertyInfoContentV2] = rdfPropertyIris.map {
                    propertyIri =>
                        val ontology = getOntology(propertyIri.getOntologyFromEntity)
                        propertyIri -> ontology.properties.getOrElse(propertyIri, throw ClientApiGenerationException(s"Property <$propertyIri> not found"))
                }.toMap

                rdfClassDef2ClientClassDef(rdfClassDef, rdfPropertyDefs)
        }
    }

    /**
      * Returns the IRIs of the Knora API classes used by a client API.
      */
    private def getClassIris(clientApi: ClientApi): Set[SmartIri] = {
        clientApi.endpoints.flatMap {
            endpoint =>
                endpoint.functions.flatMap {
                    function =>
                        val maybeReturnedClass: Option[SmartIri] = function.returnType match {
                            case classRef: ClientClassReference => Some(classRef.classIri)
                            case _ => None
                        }

                        val paramClasses: Set[SmartIri] = function.params.map {
                            param => param.objectType
                        }.collect {
                            case classRef: ClientClassReference => classRef.classIri
                        }.toSet

                        paramClasses ++ maybeReturnedClass
                }
        }
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
                val ontology = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(responseStr))
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

    private def rdfResourceClassDef2ClientClassDef(rdfClassDef: ClassInfoContentV2, rdfPropertyDefs: Map[SmartIri, PropertyInfoContentV2]): ClientClassDefinition = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

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
                    val ontologyObjectType: SmartIri = rdfPropertyDef.requireIriObject(OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri, throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-api:objectType"))
                    val isResourceProp = rdfPropertyDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2Complex.IsResourceProperty.toSmartIri)
                    val isEditable = rdfPropertyDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2Complex.IsEditable.toSmartIri)

                    if (isResourceProp) {
                        val isLinkValueProp = rdfPropertyDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2Complex.IsLinkValueProperty.toSmartIri)

                        val clientObjectType: ClientObjectType = if (isLinkValueProp) {
                            ClientLinkValue(ontologyObjectType)
                        } else {
                            resourcePropObjectTypeToClientObjectType(ontologyObjectType)
                        }

                        ClientPropertyDefinition(
                            propertyName = propertyName,
                            propertyIri = propertyIri,
                            objectType = clientObjectType,
                            cardinality = knoraCardinalityInfo.cardinality,
                            isEditable = isEditable
                        )
                    } else {
                        ClientPropertyDefinition(
                            propertyName = propertyName,
                            propertyIri = propertyIri,
                            objectType = nonResourcePropObjectTypeToClientObjectType(ontologyObjectType),
                            cardinality = knoraCardinalityInfo.cardinality,
                            isEditable = isEditable
                        )
                    }
                } else {
                    ClientPropertyDefinition(
                        propertyName = propertyName,
                        propertyIri = propertyIri,
                        objectType = ClientStringLiteral,
                        cardinality = knoraCardinalityInfo.cardinality,
                        isEditable = propertyIri.toString == OntologyConstants.Rdfs.Label // Labels of resources are editable
                    )
                }
        }.toVector.sortBy(_.propertyIri)

        ClientClassDefinition(
            className = makeClientClassName(rdfClassDef.classIri),
            classIri = rdfClassDef.classIri,
            properties = clientPropertyDefs
        )
    }

    private def rdfNonResourceClassDef2ClientClassDef(rdfClassDef: ClassInfoContentV2, rdfPropertyDefs: Map[SmartIri, PropertyInfoContentV2]): ClientClassDefinition = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val clientPropertyDefs = rdfClassDef.directCardinalities.map {
            case (propertyIri, knoraCardinalityInfo) =>
                val propertyName = propertyIri.getEntityName

                if (propertyIri.isKnoraEntityIri) {
                    val rdfPropertyDef = rdfPropertyDefs(propertyIri)
                    val ontologyObjectType: SmartIri = rdfPropertyDef.getPredicateIriObject(OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri).getOrElse(OntologyConstants.Xsd.String.toSmartIri)

                    ClientPropertyDefinition(
                        propertyName = propertyName,
                        propertyIri = propertyIri,
                        objectType = nonResourcePropObjectTypeToClientObjectType(ontologyObjectType),
                        cardinality = knoraCardinalityInfo.cardinality,
                        isEditable = true
                    )
                } else {
                    ClientPropertyDefinition(
                        propertyName = propertyName,
                        propertyIri = propertyIri,
                        objectType = ClientStringLiteral,
                        cardinality = knoraCardinalityInfo.cardinality,
                        isEditable = true
                    )
                }
        }.toVector.sortBy(_.propertyIri)

        ClientClassDefinition(
            className = makeClientClassName(rdfClassDef.classIri),
            classIri = rdfClassDef.classIri,
            properties = clientPropertyDefs
        )
    }

    private def makeClientClassName(classIri: SmartIri): String = {
        classIri.getEntityName.capitalize
    }

    private def resourcePropObjectTypeToClientObjectType(ontologyObjectType: SmartIri): ClientObjectType = {
        ontologyObjectType.toString match {
            case OntologyConstants.KnoraApiV2Complex.Value => ClientAbstractKnoraValue
            case OntologyConstants.KnoraApiV2Complex.TextValue => ClientTextValue
            case OntologyConstants.KnoraApiV2Complex.IntValue => ClientIntValue
            case OntologyConstants.KnoraApiV2Complex.DecimalValue => ClientDecimalValue
            case OntologyConstants.KnoraApiV2Complex.BooleanValue => ClientBooleanValue
            case OntologyConstants.KnoraApiV2Complex.DateValue => ClientDateValue
            case OntologyConstants.KnoraApiV2Complex.GeomValue => ClientGeomValue
            case OntologyConstants.KnoraApiV2Complex.IntervalValue => ClientIntervalValue
            case OntologyConstants.KnoraApiV2Complex.ListValue => ClientListValue
            case OntologyConstants.KnoraApiV2Complex.UriValue => ClientUriValue
            case OntologyConstants.KnoraApiV2Complex.GeonameValue => ClientGeonameValue
            case OntologyConstants.KnoraApiV2Complex.ColorValue => ClientColorValue
            case OntologyConstants.KnoraApiV2Complex.StillImageFileValue => ClientStillImageFileValue
            case OntologyConstants.KnoraApiV2Complex.MovingImageFileValue => ClientMovingImageFileValue
            case OntologyConstants.KnoraApiV2Complex.AudioFileValue => ClientAudioFileValue
            case OntologyConstants.KnoraApiV2Complex.DDDFileValue => ClientDDDFileValue
            case OntologyConstants.KnoraApiV2Complex.TextFileValue => ClientTextFileValue
            case OntologyConstants.KnoraApiV2Complex.DocumentFileValue => ClientDocumentFileValue
            case _ => throw ClientApiGenerationException(s"Unexpected value type: $ontologyObjectType")
        }
    }

    private def nonResourcePropObjectTypeToClientObjectType(ontologyObjectType: SmartIri): ClientObjectType = {
        ontologyObjectType.toString match {
            case OntologyConstants.Xsd.String => ClientStringLiteral
            case OntologyConstants.Xsd.Boolean => ClientBooleanLiteral
            case OntologyConstants.Xsd.Integer => ClientIntegerLiteral
            case OntologyConstants.Xsd.Decimal => ClientDecimalLiteral
            case OntologyConstants.Xsd.DateTime | OntologyConstants.Xsd.DateTimeStamp => ClientDateTimeStampLiteral
            case _ => ClientClassReference(
                className = makeClientClassName(ontologyObjectType),
                classIri = ontologyObjectType
            )
        }
    }
}
