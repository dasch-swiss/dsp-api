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

import org.knora.webapi.messages.v2.responder.ontologymessages.{ClassInfoContentV2, PropertyInfoContentV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{SmartIri, StringFormatter}
import org.knora.webapi.{ClientApiGenerationException, InconsistentTriplestoreDataException, OntologyConstants}

class GeneratorFrontEnd {
    def ontologyClassDef2ClientClassDef(ontologyClassDef: ClassInfoContentV2, ontologyKnoraPropertyDefs: Map[SmartIri, PropertyInfoContentV2]): ClientClassDefinition = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val isResourceClass = ontologyClassDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2WithValueObjects.IsResourceClass.toSmartIri)

        if (isResourceClass) {
            ontologyResourceClassDef2ClientClassDef(ontologyClassDef, ontologyKnoraPropertyDefs)
        } else {
            ontologyNonResourceClassDef2ClientClassDef(ontologyClassDef, ontologyKnoraPropertyDefs)
        }
    }

    private def ontologyResourceClassDef2ClientClassDef(ontologyClassDef: ClassInfoContentV2, ontologyKnoraPropertyDefs: Map[SmartIri, PropertyInfoContentV2]): ClientClassDefinition = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val cardinalitiesWithoutLinkProps = ontologyClassDef.directCardinalities.filter {
            case (propertyIri, _) =>
                ontologyKnoraPropertyDefs.get(propertyIri) match {
                    case Some(ontologyPropertyDef) => !ontologyPropertyDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2WithValueObjects.IsLinkProperty.toSmartIri)
                    case None => true
                }
        }

        val clientPropertyDefs: Vector[ClientPropertyDefinition] = cardinalitiesWithoutLinkProps.map {
            case (propertyIri, knoraCardinalityInfo) =>
                val propertyName = propertyIri.getEntityName

                if (propertyIri.isKnoraEntityIri) {
                    val ontologyPropertyDef = ontologyKnoraPropertyDefs(propertyIri)
                    val ontologyObjectType: SmartIri = ontologyPropertyDef.requireIriObject(OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri, throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-api:objectType"))
                    val isResourceProp = ontologyPropertyDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2WithValueObjects.IsResourceProperty.toSmartIri)
                    val isEditable = ontologyPropertyDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2WithValueObjects.IsEditable.toSmartIri)

                    if (isResourceProp) {
                        val isLinkValueProp = ontologyPropertyDef.getPredicateBooleanObject(OntologyConstants.KnoraApiV2WithValueObjects.IsLinkValueProperty.toSmartIri)

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
            className = makeClientClassName(ontologyClassDef.classIri),
            classIri = ontologyClassDef.classIri,
            properties = clientPropertyDefs
        )
    }

    private def ontologyNonResourceClassDef2ClientClassDef(ontologyClassDef: ClassInfoContentV2, ontologyKnoraPropertyDefs: Map[SmartIri, PropertyInfoContentV2]): ClientClassDefinition = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val clientPropertyDefs = ontologyClassDef.directCardinalities.map {
            case (propertyIri, knoraCardinalityInfo) =>
                val propertyName = propertyIri.getEntityName

                if (propertyIri.isKnoraEntityIri) {
                    val ontologyPropertyDef = ontologyKnoraPropertyDefs(propertyIri)
                    val ontologyObjectType: SmartIri = ontologyPropertyDef.requireIriObject(OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri, throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-api:objectType"))

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
            className = makeClientClassName(ontologyClassDef.classIri),
            classIri = ontologyClassDef.classIri,
            properties = clientPropertyDefs
        )
    }

    private def makeClientClassName(classIri: SmartIri): String = {
        classIri.getEntityName.capitalize
    }

    private def resourcePropObjectTypeToClientObjectType(ontologyObjectType: SmartIri): ClientObjectType = {
        ontologyObjectType.toString match {
            case OntologyConstants.KnoraApiV2WithValueObjects.TextValue => ClientTextValue
            case OntologyConstants.KnoraApiV2WithValueObjects.IntValue => ClientIntValue
            case OntologyConstants.KnoraApiV2WithValueObjects.DecimalValue => ClientDecimalValue
            case OntologyConstants.KnoraApiV2WithValueObjects.BooleanValue => ClientBooleanValue
            case OntologyConstants.KnoraApiV2WithValueObjects.DateValue => ClientDateValue
            case OntologyConstants.KnoraApiV2WithValueObjects.GeomValue => ClientGeomValue
            case OntologyConstants.KnoraApiV2WithValueObjects.IntervalValue => ClientIntervalValue
            case OntologyConstants.KnoraApiV2WithValueObjects.ListValue => ClientListValue
            case OntologyConstants.KnoraApiV2WithValueObjects.UriValue => ClientUriValue
            case OntologyConstants.KnoraApiV2WithValueObjects.GeonameValue => ClientGeonameValue
            case OntologyConstants.KnoraApiV2WithValueObjects.ColorValue => ClientColorValue
            case OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValue => ClientStillImageFileValue
            case OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValue => ClientMovingImageFileValue
            case OntologyConstants.KnoraApiV2WithValueObjects.AudioFileValue => ClientAudioFileValue
            case OntologyConstants.KnoraApiV2WithValueObjects.DDDFileValue => ClientDDDFileValue
            case OntologyConstants.KnoraApiV2WithValueObjects.TextFileValue => ClientTextFileValue
            case OntologyConstants.KnoraApiV2WithValueObjects.DocumentFileValue => ClientDocumentFileValue
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
