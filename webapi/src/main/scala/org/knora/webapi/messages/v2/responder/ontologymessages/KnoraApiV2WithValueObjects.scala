/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.messages.v2.responder.ontologymessages

import org.knora.webapi._
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{SmartIri, StringFormatter}

/**
  * Represents the `knora-api` ontology, version 2, in the [[ApiV2WithValueObjects]] schema.
  */
object KnoraApiV2WithValueObjects {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

    val OntologyMetadata = OntologyMetadataV2(
        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
        label = Some("The default knora-api ontology")
    )

    val Resource: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.Resource,
        isResourceClass = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "Ressource",
                    LanguageCodes.EN -> "Resource",
                    LanguageCodes.FR -> "Ressource",
                    LanguageCodes.IT -> "Risorsa"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a Knora resource."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.Rdfs.Label -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.CreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasIncomingLinks -> Cardinality.MayHaveMany
        )
    )

    val Result: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.Result,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "Ergebnis",
                    LanguageCodes.EN -> "result",
                    LanguageCodes.FR -> "résultat",
                    LanguageCodes.IT -> "risultato"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Provides a message indicating that an operation was successful"
                )
            )
        ),
        objectType = Some(OntologyConstants.Xsd.String)
    )

    val IsEditable: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.IsEditable,
        propertyType = OntologyConstants.Owl.AnnotationProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "is editable"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Indicates whether a property of a Knora resource class be edited via the Knora API"
                )
            )
        ),
        objectType = Some(OntologyConstants.Xsd.Boolean)
    )

    val IsLinkProperty: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.IsLinkProperty,
        propertyType = OntologyConstants.Owl.AnnotationProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "is link property"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Indicates whether a property points to a resource"
                )
            )
        ),
        objectType = Some(OntologyConstants.Xsd.Boolean)
    )

    val IsLinkValueProperty: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.IsLinkValueProperty,
        propertyType = OntologyConstants.Owl.AnnotationProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "is link value property"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Indicates whether a property points to a link value (reification)"
                )
            )
        ),
        objectType = Some(OntologyConstants.Xsd.Boolean)
    )

    val IsInherited: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.IsInherited,
        propertyType = OntologyConstants.Owl.AnnotationProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "is inherited"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Indicates whether a cardinality has been inherited from a base class"
                )
            )
        ),
        subjectType = Some(OntologyConstants.Owl.Restriction),
        objectType = Some(OntologyConstants.Xsd.Boolean)
    )

    val CanBeInstantiated: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.CanBeInstantiated,
        propertyType = OntologyConstants.Owl.AnnotationProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "can be instantiated"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Indicates whether a class is a Knora resource class that can be instantiated via the Knora API"
                )
            )
        ),
        objectType = Some(OntologyConstants.Xsd.Boolean)
    )

    val OntologyName: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.OntologyName,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "ontology name"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the short name of an ontology"
                )
            )
        ),
        objectType = Some(OntologyConstants.Xsd.String)
    )

    val ProjectIri: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.ProjectIri,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "project IRI"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the IRI of a Knora project"
                )
            )
        ),
        objectType = Some(OntologyConstants.Xsd.Uri)
    )

    val HasPermissions: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "has permissions"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Specifies the permissions granted by a resource or value"
                )
            )
        ),
        objectType = Some(OntologyConstants.Xsd.String) // TODO: make a datatype for this.
    )

    val HasStandoffLinkTo: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo),
        isLinkProp = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "hat Standofflink zu",
                    LanguageCodes.EN -> "has standoff link to",
                    LanguageCodes.FR -> "a lien standoff vers",
                    LanguageCodes.IT -> "ha link standoff verso"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a direct connection between two resources, generated by standoff markup"
                )
            )
        ),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource)
    )


    val HasStandoffLinkToValue: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue),
        isLinkValueProp = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "hat Standofflink zu",
                    LanguageCodes.EN -> "has standoff link to",
                    LanguageCodes.FR -> "a lien standoff vers",
                    LanguageCodes.IT -> "ha link standoff verso"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a direct connection between two resources, generated by standoff markup"
                )
            )
        ),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue)
    )

    val CreationDate: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.CreationDate,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Creation date"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Indicates when a resource was created"
                )
            )
        ),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        objectType = Some(OntologyConstants.Xsd.DateTimeStamp)
    )

    val LastModificationDate: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Last modification date"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Indicates when a resource was last modified"
                )
            )
        ),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        objectType = Some(OntologyConstants.Xsd.DateTimeStamp)
    )

    val IsPartOf: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.IsPartOf,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo),
        isLinkProp = true,
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "ist Teil von",
                    LanguageCodes.EN -> "is part of",
                    LanguageCodes.FR -> "fait partie de",
                    LanguageCodes.IT -> "fa parte di"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Indicates that this resource is part of another resource"
                )
            )
        )
    )

    val IsPartOfValue: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.IsPartOfValue,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue),
        isEditable = true,
        isLinkValueProp = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "ist Teil von",
                    LanguageCodes.EN -> "is part of",
                    LanguageCodes.FR -> "fait partie de",
                    LanguageCodes.IT -> "fa parte di"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Indicates that this resource is part of another resource"
                )
            )
        )
    )

    val HasIncomingLinks: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasIncomingLinks,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue),
        isEditable = true,
        isLinkValueProp = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "hat eingehende Verweise",
                    LanguageCodes.EN -> "has incoming links"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Indicates that this resource referred to by another resource"
                )
            )
        )
    )

    val ForbiddenResource: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.ForbiddenResource,
        isResourceClass = true,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A ForbiddenResource is a proxy for a resource that the client has insufficient permissions to see."
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A ForbiddenResource is a proxy for a resource that the client has insufficient permissions to see."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.HasComment -> Cardinality.MustHaveSome
        ),
        inheritedCardinalities = Resource.allCardinalities
    )

    val Region: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.Region,
        isResourceClass = true,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        canBeInstantiated = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "Region",
                    LanguageCodes.EN -> "Region",
                    LanguageCodes.FR -> "Région",
                    LanguageCodes.IT -> "Regione"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a geometric region of a resource. The geometry is represented currently as JSON string."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.HasColor -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.IsRegionOf -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.IsRegionOfValue -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasGeometry -> Cardinality.MustHaveSome,
            OntologyConstants.KnoraApiV2WithValueObjects.HasComment -> Cardinality.MustHaveSome
        ),
        inheritedCardinalities = Resource.allCardinalities
    )

    val IsRegionOf: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.IsRegionOf,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Region),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Representation),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo),
        isLinkProp = true,
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "ist Region von",
                    LanguageCodes.EN -> "is region of",
                    LanguageCodes.FR -> "est région de",
                    LanguageCodes.IT -> "è regione di"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Indicates which representation a region refers to"
                )
            )
        )
    )

    val IsRegionOfValue: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.IsRegionOfValue,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Region),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue),
        isEditable = true,
        isLinkValueProp = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "ist Region von",
                    LanguageCodes.EN -> "is region of",
                    LanguageCodes.FR -> "est région de",
                    LanguageCodes.IT -> "è regione di"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Indicates which representation a region refers to"
                )
            )
        )
    )

    val LinkObject: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.LinkObj,
        isResourceClass = true,
        canBeInstantiated = true,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Link Object"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a generic link object."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.HasComment -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue -> Cardinality.MustHaveOne
        ),
        inheritedCardinalities = Resource.allCardinalities
    )

    val Representation: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.Representation,
        isResourceClass = true,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "Repräsentation",
                    LanguageCodes.EN -> "Representation",
                    LanguageCodes.FR -> "Répresentation",
                    LanguageCodes.IT -> "Rappresentazione"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A Resource that can store one or more FileValues"
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue -> Cardinality.MustHaveSome
        ),
        inheritedCardinalities = Resource.allCardinalities
    )

    val StillImageRepresentation: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.StillImageRepresentation,
        isResourceClass = true,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Representation),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "Repräsentation (Bild)",
                    LanguageCodes.EN -> "Representation (Image)",
                    LanguageCodes.FR -> "Répresentation (Image)",
                    LanguageCodes.IT -> "Rappresentazione (Immagine)"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A Resource that can contain two-dimensional still image files"
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.HasStillImageFileValue -> Cardinality.MustHaveSome
        ),
        inheritedCardinalities = Resource.allCardinalities
    )

    val MovingImageRepresentation: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.MovingImageRepresentation,
        isResourceClass = true,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Representation),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "Repräsentation (Video)",
                    LanguageCodes.EN -> "Representation (Movie)",
                    LanguageCodes.FR -> "Répresentation (Film)",
                    LanguageCodes.IT -> "Rappresentazione (Film)"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A Resource containing moving image data"
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.HasMovingImageFileValue -> Cardinality.MustHaveSome
        ),
        inheritedCardinalities = Resource.allCardinalities
    )

    val AudioRepresentation: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.AudioRepresentation,
        isResourceClass = true,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Representation),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "Repräsentation (Audio)",
                    LanguageCodes.EN -> "Representation (Audio)",
                    LanguageCodes.FR -> "Répresentation (Audio)",
                    LanguageCodes.IT -> "Rappresentazione (Audio)"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A Resource containing audio data"
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.HasAudioFileValue -> Cardinality.MustHaveSome
        ),
        inheritedCardinalities = Resource.allCardinalities
    )

    val DDDRepresentation: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.DDDRepresentation,
        isResourceClass = true,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Representation),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "Repräsentation (3D)",
                    LanguageCodes.EN -> "Representation (3D)",
                    LanguageCodes.FR -> "Répresentation (3D)",
                    LanguageCodes.IT -> "Rappresentazione (3D)"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A Resource containing 3D data"
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.HasDDDFileValue -> Cardinality.MustHaveSome
        ),
        inheritedCardinalities = Resource.allCardinalities
    )

    val TextRepresentation: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.TextRepresentation,
        isResourceClass = true,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Representation),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "Repräsentation (Text)",
                    LanguageCodes.EN -> "Representation (Text)",
                    LanguageCodes.FR -> "Répresentation (Texte)",
                    LanguageCodes.IT -> "Rappresentazione (Testo)"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A Resource containing text files"
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.HasTextFileValue -> Cardinality.MustHaveSome
        ),
        inheritedCardinalities = Resource.allCardinalities
    )

    val DocumentRepresentation: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.DocumentRepresentation,
        isResourceClass = true,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Representation),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "Repräsentation (Dokument)",
                    LanguageCodes.EN -> "Representation (Document)",
                    LanguageCodes.FR -> "Répresentation (Document)",
                    LanguageCodes.IT -> "Rappresentazione (Documento)"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A Resource containing documents"
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.HasDocumentFileValue -> Cardinality.MustHaveSome
        ),
        inheritedCardinalities = Resource.allCardinalities
    )

    // TODO: what should go here?
    val XMLToStandoffMapping: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.XMLToStandoffMapping
    )

    // TODO: what should go here?
    val ListNode: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.ListNode
    )

    val ValueAsString: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(LanguageCodes.EN -> "A plain string representation of a value")
            )
        ),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Value),
        objectType = Some(OntologyConstants.Xsd.String)
    )

    val ValueHas: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.ValueHas,
        propertyType = OntologyConstants.Rdf.Property,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Value)
    )

    val Value: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.Value,
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions -> Cardinality.MustHaveOne
        ),
        isValueClass = true
    )

    val ValueCreationDate: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Value creation date"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Indicates when a value was created"
                )
            )
        ),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        objectType = Some(OntologyConstants.Xsd.DateTimeStamp)
    )

    val HasValue: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Value)
    )

    val HasLinkTo: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        isLinkProp = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "hat Link zu",
                    LanguageCodes.EN -> "has Link to",
                    LanguageCodes.FR -> "a lien vers",
                    LanguageCodes.IT -> "ha Link verso"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a direct connection between two resources"
                )
            )
        ),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource)
    )

    val HasLinkToValue: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue),
        isLinkValueProp = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "hat Link zu",
                    LanguageCodes.EN -> "has Link to",
                    LanguageCodes.FR -> "a lien vers",
                    LanguageCodes.IT -> "ha Link verso"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a direct connection between two resources"
                )
            )
        ),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue)
    )

    val SubjectType: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType,
        propertyType = OntologyConstants.Rdf.Property,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Subject type"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Specifies the required type of the subjects of a property"
                )
            )
        )
    )

    val ObjectType: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType,
        propertyType = OntologyConstants.Rdf.Property,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Object type"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Specifies the required type of the objects of a property"
                )
            )
        )

    )

    val ResourceIcon: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.ResourceIcon,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.Owl.Class),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Resource icon"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Specifies an icon to be used to represent instances of a resource class"
                )
            )
        )
    )

    val HasColor: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasColor,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Region),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.ColorValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElementProp,
                objects = Set(OntologyConstants.SalsahGui.Colorpicker)
            ),
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiAttribute,
                objects = Set("ncolors=8")
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "Farbe",
                    LanguageCodes.EN -> "Color",
                    LanguageCodes.FR -> "Couleur",
                    LanguageCodes.IT -> "Colore"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Specifies the color of a Region"
                )
            )
        )
    )

    val HasGeometry: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasGeometry,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Region),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.GeomValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElementProp,
                objects = Set(OntologyConstants.SalsahGui.Geometry)
            ),
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiAttribute,
                objects = Set("width=95%;rows=4;wrap=soft")
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "Geometrie",
                    LanguageCodes.EN -> "Geometry",
                    LanguageCodes.FR -> "Géometrie",
                    LanguageCodes.IT -> "Geometria"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a geometrical shape."
                )
            )
        )
    )

    val HasComment: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasComment,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.TextValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElementProp,
                objects = Set(OntologyConstants.SalsahGui.Richtext)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "Kommentar",
                    LanguageCodes.EN -> "Comment",
                    LanguageCodes.FR -> "Commentaire",
                    LanguageCodes.IT -> "Commento"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a comment on a Resource"
                )
            )
        )
    )

    val HasFileValue: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Representation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.FileValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElementProp,
                objects = Set(OntologyConstants.SalsahGui.Fileupload)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "hat Datei",
                    LanguageCodes.EN -> "has file",
                    LanguageCodes.FR -> "a fichier",
                    LanguageCodes.IT -> "ha file"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Connects a Representation to a file"
                )
            )
        )
    )

    val HasStillImageFileValue: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasStillImageFileValue,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.StillImageRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElementProp,
                objects = Set(OntologyConstants.SalsahGui.Fileupload)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "hat Bilddatei",
                    LanguageCodes.EN -> "has image file",
                    LanguageCodes.FR -> "a fichier d'image",
                    LanguageCodes.IT -> "ha file immagine"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Connects a Representation to an image file"
                )
            )
        )
    )

    val HasMovingImageFileValue: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasMovingImageFileValue,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.MovingImageRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElementProp,
                objects = Set(OntologyConstants.SalsahGui.Fileupload)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "hat Filmdatei",
                    LanguageCodes.EN -> "has movie file",
                    LanguageCodes.FR -> "a fichier de film",
                    LanguageCodes.IT -> "ha file film"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Connects a Representation to a moving image file"
                )
            )
        )
    )

    val HasAudioImageFileValue: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasAudioFileValue,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.AudioRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.AudioFileValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElementProp,
                objects = Set(OntologyConstants.SalsahGui.Fileupload)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "hat Audiodatei",
                    LanguageCodes.EN -> "has audio file",
                    LanguageCodes.FR -> "a fichier d'audio",
                    LanguageCodes.IT -> "ha file audio"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Connects a Representation to an audio file"
                )
            )
        )
    )

    val HasDDDFileValue: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasDDDFileValue,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DDDRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DDDFileValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElementProp,
                objects = Set(OntologyConstants.SalsahGui.Fileupload)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "hat 3D-Datei",
                    LanguageCodes.EN -> "has 3D file",
                    LanguageCodes.FR -> "a fichier 3D",
                    LanguageCodes.IT -> "ha file 3D"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Connects a Representation to a 3D file"
                )
            )
        )
    )

    val HasTextFileValue: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasTextFileValue,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.TextRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.TextFileValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElementProp,
                objects = Set(OntologyConstants.SalsahGui.Fileupload)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "hat Textdatei",
                    LanguageCodes.EN -> "has text file",
                    LanguageCodes.FR -> "a fichier de texte",
                    LanguageCodes.IT -> "ha file testo"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Connects a Representation to a text file"
                )
            )
        )
    )

    val HasDocumentFileValue: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasDocumentFileValue,
        isResourceProp = true,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DocumentRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DocumentFileValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElementProp,
                objects = Set(OntologyConstants.SalsahGui.Fileupload)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.DE -> "hat Dokument",
                    LanguageCodes.EN -> "has document",
                    LanguageCodes.FR -> "a document",
                    LanguageCodes.IT -> "ha documento"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Connects a Representation to a document"
                )
            )
        )
    )

    val TextValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.TextValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Value),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Text value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents text with optional markup."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.TextValueHasMapping -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsXml -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsHtml -> Cardinality.MayHaveOne
        ),
        inheritedCardinalities = Value.allCardinalities,
        isValueClass = true
    )

    val TextValueAsXml: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsXml,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.TextValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Text value as XML"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A Text value represented in XML."
                )
            )
        )
    )

    val TextValueHasMapping: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.TextValueHasMapping,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.TextValue),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.XMLToStandoffMapping),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Text value has mapping"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The mapping used to turn standoff into XML and vice versa."
                )
            )
        )
    )

    val TextValueAsHtml: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsHtml,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.TextValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Text value as HTML"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A text value represented in HTML."
                )
            )
        )
    )

    val DateValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.DateValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Value),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Date value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a date as a period with different possible precisions."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar -> Cardinality.MayHaveOne
        ),
        inheritedCardinalities = Value.allCardinalities,
        isValueClass = true
    )

    val DateValueHasStartYear: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DateValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Date value has start year"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the start year of a date value."
                )
            )
        )
    )

    val DateValueHasEndYear: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DateValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Date value has end year"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the end year of a date value."
                )
            )
        )
    )

    val DateValueHasStartMonth: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DateValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Date value has start month"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the start month of a date value."
                )
            )
        )
    )

    val DateValueHasEndMonth: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DateValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Date value has end month"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the end month of a date value."
                )
            )
        )
    )

    val DateValueHasStartDay: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DateValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Date value has start day"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the start day of a date value."
                )
            )
        )
    )

    val DateValueHasEndDay: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DateValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Date value has end day"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the end day of a date value."
                )
            )
        )
    )

    val DateValueHasStartEra: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DateValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Date value has start era"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the start era of a date value."
                )
            )
        )
    )

    val DateValueHasEndEra: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DateValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Date value has end era"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the end era of a date value."
                )
            )
        )
    )
    val DateValueHasCalendar: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DateValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Date value has calendar"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the calendar of a date value."
                )
            )
        )
    )

    val LinkValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.LinkValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Value),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Link value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a link from one resource to another."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTarget -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTargetIri -> Cardinality.MayHaveOne
        ),
        inheritedCardinalities = Value.allCardinalities,
        isValueClass = true
    )

    val LinkValueHasTarget: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTarget,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Link value has target"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the target resource of a link value."
                )
            )
        )
    )

    val LinkValueHasTargetIri: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTargetIri,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Link value has target IRI"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the IRI of the target resource of a link value."
                )
            )
        )
    )

    val IntValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.IntValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Value),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Integer value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents an integer value."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.IntValueAsInt -> Cardinality.MustHaveOne
        ),
        inheritedCardinalities = Value.allCardinalities,
        isValueClass = true
    )

    val IntValueAsInt: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.IntValueAsInt,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.IntValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Integer value as integer"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the literal integer value of an IntValue."
                )
            )
        )
    )

    val DecimalValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.DecimalValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Value),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Decimal value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a decimal value."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.DecimalValueAsDecimal -> Cardinality.MustHaveOne
        ),
        inheritedCardinalities = Value.allCardinalities,
        isValueClass = true
    )

    val DecimalValueAsDecimal: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.DecimalValueAsDecimal,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DecimalValue),
        objectType = Some(OntologyConstants.Xsd.Decimal),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Decimal value as decimal"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the literal decimal value of a DecimalValue."
                )
            )
        )
    )

    val BooleanValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.BooleanValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Value),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Boolean value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a boolean value."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.BooleanValueAsBoolean -> Cardinality.MustHaveOne
        ),
        inheritedCardinalities = Value.allCardinalities,
        isValueClass = true
    )

    val BooleanValueAsBoolean: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.BooleanValueAsBoolean,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.BooleanValue),
        objectType = Some(OntologyConstants.Xsd.Boolean),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Boolean value as decimal"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the literal boolean value of a BooleanValue."
                )
            )
        )
    )

    val GeomValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.GeomValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Value),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Geometry value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a geometry value."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.GeometryValueAsGeometry -> Cardinality.MustHaveOne
        ),
        inheritedCardinalities = Value.allCardinalities,
        isValueClass = true
    )

    val GeometryValueAsGeometry: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.GeometryValueAsGeometry,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.GeomValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Geometry value as JSON"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a 2D geometry value as JSON."
                )
            )
        )
    )

    val IntervalValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.IntervalValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Value),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Interval value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a time interval."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasStart -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasEnd -> Cardinality.MustHaveOne
        ),
        inheritedCardinalities = Value.allCardinalities,
        isValueClass = true
    )

    val IntervalValueHasStart: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasStart,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.IntervalValue),
        objectType = Some(OntologyConstants.Xsd.Decimal),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Interval value has start"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the start of a time interval."
                )
            )
        )
    )

    val IntervalValueHasEnd: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasEnd,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.IntervalValue),
        objectType = Some(OntologyConstants.Xsd.Decimal),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Interval value has end"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the end of a time interval."
                )
            )
        )
    )

    val ListValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.ListValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Value),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "List value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a value in a hierarchical list."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.HierarchicalListValueAsListNode -> Cardinality.MustHaveOne
        ),
        inheritedCardinalities = Value.allCardinalities,
        isValueClass = true
    )

    val HierarchicalListValueAsListNode: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HierarchicalListValueAsListNode,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.ListValue),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.ListNode),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Hierarchical list value as list node"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a reference to a hierarchical list node."
                )
            )
        )
    )

    val ColorValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.ColorValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Value),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Color value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a color value."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.BooleanValueAsBoolean -> Cardinality.MustHaveOne
        ),
        inheritedCardinalities = Value.allCardinalities,
        isValueClass = true
    )

    val ColorValueAsColor: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.ColorValueAsColor,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.ColorValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Color value as color"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the literal RGB value of a ColorValue."
                )
            )
        )
    )

    val UriValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.UriValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Value),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "URI value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a URI value."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.UriValueAsUri -> Cardinality.MustHaveOne
        ),
        inheritedCardinalities = Value.allCardinalities,
        isValueClass = true
    )

    val UriValueAsUri: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.UriValueAsUri,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.UriValue),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "URI value as URI"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the literal URI value of a UriValue."
                )
            )
        )
    )

    val GeonameValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.GeonameValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Value),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Geoname value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a Geoname value."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.GeonameValueAsGeonameCode -> Cardinality.MustHaveOne
        ),
        inheritedCardinalities = Value.allCardinalities,
        isValueClass = true
    )

    val GeonameValueAsGeonameCode: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.GeonameValueAsGeonameCode,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.GeonameValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Geoname value as Geoname code"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the literal Geoname code of a GeonameValue."
                )
            )
        )
    )

    val FileValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.FileValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.Value),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "File value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a file value."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename -> Cardinality.MustHaveOne
        ),
        inheritedCardinalities = Value.allCardinalities,
        isValueClass = true
    )

    val FileValueIsPreview: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.FileValueIsPreview,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.FileValue),
        objectType = Some(OntologyConstants.Xsd.Boolean),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "File value is preview"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Indicates whether this is file value is a preview."
                )
            )
        )
    )

    val FileValueAsUrl: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.FileValue),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "File value as URL"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The URL at which the file can be accessed."
                )
            )
        )
    )

    val FileValueHasFilename: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.FileValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "File value has filename"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The name of the file that a file value represents."
                )
            )
        )
    )

    val TextFileValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.TextFileValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.FileValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Text file value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a text file value."
                )
            )
        ),
        inheritedCardinalities = FileValue.allCardinalities,
        isValueClass = true
    )

    val StillImageFileValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.FileValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Still image file value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a still image file value."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimX -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimY -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasIIIFBaseUrl -> Cardinality.MustHaveOne
        ),
        inheritedCardinalities = FileValue.allCardinalities,
        isValueClass = true
    )

    val StillImageFileValueHasDimX: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimX,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Still image file value has X dimension"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The horizontal dimension of a still image file value."
                )
            )
        )
    )

    val StillImageFileValueHasDimY: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimY,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Still image file value has Y dimension"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The vertical dimension of a still image file value."
                )
            )
        )
    )

    val StillImageFileValueHasIIIFBaseUrl: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasIIIFBaseUrl,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Still image file value has IIIF base URL"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The IIIF base URL of a still image file value."
                )
            )
        )
    )


    val MovingImageFileValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.FileValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Moving image file value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a moving image file value."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasDimX -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasDimY -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasFps -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasQualityLevel -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasDuration -> Cardinality.MustHaveOne
        ),
        inheritedCardinalities = FileValue.allCardinalities,
        isValueClass = true
    )

    val MovingImageFileValueHasDimX: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasDimX,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Moving image file value has X dimension"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The horizontal dimension of a moving image file value."
                )
            )
        )
    )

    val MovingImageFileValueHasDimY: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasDimY,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Moving image file value has Y dimension"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The vertical dimension of a moving image file value."
                )
            )
        )
    )

    val MovingImageFileValueHasFps: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasFps,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Moving image file value has frames per second"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The number of frames per second in a moving image file value."
                )
            )
        )
    )

    val MovingImageFileValueHasQualityLevel: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasQualityLevel,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Moving image file value has quality level"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The quality level of a moving image file value."
                )
            )
        )
    )

    val MovingImageFileValueHasDuration: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasDuration,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Decimal),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Moving image file value has duration"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The duration of a moving image file value."
                )
            )
        )
    )

    val AudioFileValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.AudioFileValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.FileValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Audio file value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents an audio file value."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.AudioFileValueHasDuration -> Cardinality.MustHaveOne
        ),
        inheritedCardinalities = FileValue.allCardinalities,
        isValueClass = true
    )

    val AudioFileValueHasDuration: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.AudioFileValueHasDuration,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.AudioFileValue),
        objectType = Some(OntologyConstants.Xsd.Decimal),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Audio file value has duration"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The duration of an audio file value."
                )
            )
        )
    )

    val DDDFileValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.DDDFileValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.FileValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "3D file value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a 3D file value."
                )
            )
        ),
        inheritedCardinalities = FileValue.allCardinalities,
        isValueClass = true
    )

    val DocumentFileValue: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.DocumentFileValue,
        subClassOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.FileValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Document file value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents a document file value."
                )
            )
        ),
        inheritedCardinalities = FileValue.allCardinalities,
        isValueClass = true
    )

    /**
      * All the classes in the `knora-api` v2 ontology in the [[ApiV2WithValueObjects]] schema.
      */
    val Classes: Map[SmartIri, ReadClassInfoV2] = Set(
        Resource,
        ForbiddenResource,
        Region,
        LinkObject,
        Representation,
        StillImageRepresentation,
        MovingImageRepresentation,
        AudioRepresentation,
        DDDRepresentation,
        TextRepresentation,
        DocumentRepresentation,
        XMLToStandoffMapping,
        ListNode,
        Value,
        TextValue,
        DateValue,
        LinkValue,
        IntValue,
        DecimalValue,
        BooleanValue,
        GeomValue,
        IntervalValue,
        ListValue,
        ColorValue,
        UriValue,
        GeonameValue,
        FileValue,
        TextFileValue,
        StillImageFileValue,
        MovingImageFileValue,
        AudioFileValue,
        DDDFileValue,
        DocumentFileValue
    ).map {
        classInfo => classInfo.entityInfoContent.classIri -> classInfo
    }.toMap

    /**
      * All the properties in the `knora-api` v2 ontology in the [[ApiV2WithValueObjects]] schema.
      */
    val Properties: Map[SmartIri, ReadPropertyInfoV2] = Set(
        Result,
        IsEditable,
        IsLinkProperty,
        IsLinkValueProperty,
        IsInherited,
        CanBeInstantiated,
        OntologyName,
        ProjectIri,
        HasPermissions,
        ValueAsString,
        ValueHas,
        ValueCreationDate,
        HasValue,
        HasLinkTo,
        HasLinkToValue,
        HasIncomingLinks,
        HasStandoffLinkTo,
        HasStandoffLinkToValue,
        CreationDate,
        LastModificationDate,
        SubjectType,
        ObjectType,
        ResourceIcon,
        IsPartOf,
        IsPartOfValue,
        IsRegionOf,
        IsRegionOfValue,
        HasColor,
        HasGeometry,
        HasComment,
        HasFileValue,
        HasStillImageFileValue,
        HasMovingImageFileValue,
        HasAudioImageFileValue,
        HasDDDFileValue,
        HasTextFileValue,
        HasDocumentFileValue,
        TextValueAsXml,
        TextValueHasMapping,
        TextValueAsHtml,
        DateValueHasStartYear,
        DateValueHasEndYear,
        DateValueHasStartMonth,
        DateValueHasEndMonth,
        DateValueHasStartDay,
        DateValueHasEndDay,
        DateValueHasStartEra,
        DateValueHasEndEra,
        DateValueHasCalendar,
        LinkValueHasTarget,
        LinkValueHasTargetIri,
        IntValueAsInt,
        DecimalValueAsDecimal,
        BooleanValueAsBoolean,
        GeometryValueAsGeometry,
        IntervalValueHasStart,
        IntervalValueHasEnd,
        HierarchicalListValueAsListNode,
        ColorValueAsColor,
        UriValueAsUri,
        GeonameValueAsGeonameCode,
        FileValueIsPreview,
        FileValueAsUrl,
        FileValueHasFilename,
        StillImageFileValueHasDimX,
        StillImageFileValueHasDimY,
        StillImageFileValueHasIIIFBaseUrl,
        MovingImageFileValueHasDimX,
        MovingImageFileValueHasDimY,
        MovingImageFileValueHasFps,
        MovingImageFileValueHasQualityLevel,
        MovingImageFileValueHasDuration,
        AudioFileValueHasDuration
    ).map {
        propertyInfo => propertyInfo.entityInfoContent.propertyIri -> propertyInfo
    }.toMap

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Convenience functions for building ontology entities, to make the code above more concise.

    /**
      * Makes a [[PredicateInfoV2]].
      *
      * @param predicateIri    the IRI of the predicate.
      * @param objects         the non-language-specific objects of the predicate.
      * @param objectsWithLang the language-specific objects of the predicate.
      * @return a [[PredicateInfoV2]].
      */
    private def makePredicate(predicateIri: IRI,
                              objects: Set[String] = Set.empty[String],
                              objectsWithLang: Map[String, String] = Map.empty[String, String]): PredicateInfoV2 = {
        PredicateInfoV2(
            predicateIri = predicateIri.toSmartIri,
            objects = objects,
            objectsWithLang = objectsWithLang
        )
    }

    /**
      * Makes a [[ReadPropertyInfoV2]].
      *
      * @param propertyIri        the IRI of the property.
      * @param propertyType       the type of the property (owl:ObjectProperty, owl:DatatypeProperty, or rdf:Property).
      * @param isResourceProp true if this is a subproperty of `knora-api:hasValue` or `knora-api:hasLinkTo`.
      * @param subPropertyOf      the set of direct superproperties of this property.
      * @param isEditable         true if this is a Knora resource property that can be edited via the Knora API.
      * @param isLinkValueProp    true if the property points to a link value (reification).
      * @param predicates         the property's predicates.
      * @param subjectType        the required type of the property's subject.
      * @param objectType         the required type of the property's object.
      * @return a [[ReadPropertyInfoV2]].
      */
    private def makeProperty(propertyIri: IRI,
                             propertyType: IRI,
                             isResourceProp: Boolean = false,
                             subPropertyOf: Set[IRI] = Set.empty[IRI],
                             isEditable: Boolean = false,
                             isLinkProp: Boolean = false,
                             isLinkValueProp: Boolean = false,
                             predicates: Seq[PredicateInfoV2] = Seq.empty[PredicateInfoV2],
                             subjectType: Option[IRI] = None,
                             objectType: Option[IRI] = None): ReadPropertyInfoV2 = {
        val propTypePred = makePredicate(
            predicateIri = OntologyConstants.Rdf.Type,
            objects = Set(propertyType)
        )

        val maybeSubjectTypePred = subjectType.map {
            subjType =>
                makePredicate(
                    predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType,
                    objects = Set(subjType)
                )
        }

        val maybeObjectTypePred = objectType.map {
            objType =>
                makePredicate(
                    predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType,
                    objects = Set(objType)
                )
        }

        val predsWithTypes = predicates ++ maybeSubjectTypePred ++ maybeObjectTypePred :+ propTypePred

        ReadPropertyInfoV2(
            entityInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri.toSmartIri,
                ontologySchema = ApiV2WithValueObjects,
                predicates = predsWithTypes.map {
                    pred => pred.predicateIri -> pred
                }.toMap,
                subPropertyOf = subPropertyOf.map(iri => iri.toSmartIri)
            ),
            isResourceProp = isResourceProp,
            isEditable = isEditable,
            isLinkProp = isLinkProp,
            isLinkValueProp = isLinkValueProp
        )
    }

    /**
      * Makes a [[ReadClassInfoV2]].
      *
      * @param classIri               the IRI of the class.
      * @param subClassOf             the set of direct superclasses of this class.
      * @param predicates             the predicates of the class.
      * @param isResourceClass        true if this is a subclass of `knora-api:Resource`.
      * @param canBeInstantiated      true if this is a Knora resource class that can be instantiated via the Knora API.
      * @param directCardinalities    the direct cardinalities of the class.
      * @param inheritedCardinalities the inherited cardinalities of the class.
      * @return a [[ReadClassInfoV2]].
      */
    private def makeClass(classIri: IRI,
                          subClassOf: Set[IRI] = Set.empty[IRI],
                          predicates: Seq[PredicateInfoV2] = Seq.empty[PredicateInfoV2],
                          isResourceClass: Boolean = false,
                          canBeInstantiated: Boolean = false,
                          isValueClass: Boolean = false,
                          directCardinalities: Map[IRI, Cardinality.Value] = Map.empty[IRI, Cardinality.Value],
                          inheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] = Map.empty[SmartIri, KnoraCardinalityInfo]): ReadClassInfoV2 = {
        val rdfType = OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            objects = Set(OntologyConstants.Owl.Class)
        )

        ReadClassInfoV2(
            entityInfoContent = ClassInfoContentV2(
                classIri = classIri.toSmartIri,
                predicates = predicates.map {
                    pred => pred.predicateIri -> pred
                }.toMap + rdfType,
                directCardinalities = directCardinalities.map {
                    case (propertyIri, cardinality) => propertyIri.toSmartIri -> KnoraCardinalityInfo(cardinality)
                },
                subClassOf = subClassOf.map(iri => iri.toSmartIri),
                ontologySchema = ApiV2WithValueObjects
            ),
            inheritedCardinalities = inheritedCardinalities,
            canBeInstantiated = canBeInstantiated,
            isValueClass = isValueClass
        )
    }

}
