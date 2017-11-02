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

/**
  * Represents the `knora-api` ontology, version 2, in the [[ApiV2WithValueObjects]] schema.
  */
object KnoraApiV2WithValueObjects {
    val Resource: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.Resource,
        subClassOf = Set(OntologyConstants.SchemaOrg.Thing),
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
        cardinalities = Map(
            OntologyConstants.SchemaOrg.Name -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.CreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate -> Cardinality.MayHaveOne
        )
    )

    val Result: PropertyEntityInfoV2 = makeProperty(
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

    val IsEditable: PropertyEntityInfoV2 = makeProperty(
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

    val IsLinkValueProperty: PropertyEntityInfoV2 = makeProperty(
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

    val CanBeInstantiated: PropertyEntityInfoV2 = makeProperty(
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

    val HasPermissions: PropertyEntityInfoV2 = makeProperty(
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

    val HasStandoffLinkTo: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo),
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


    val HasStandoffLinkToValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue,
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

    val CreationDate: PropertyEntityInfoV2 = makeProperty(
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

    val LastModificationDate: PropertyEntityInfoV2 = makeProperty(
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

    val Region: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.Region,
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
        cardinalities = Map(
            OntologyConstants.SchemaOrg.Name -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.CreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasColor -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.IsRegionOf -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.IsRegionOfValue -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasGeometry -> Cardinality.MustHaveSome,
            OntologyConstants.KnoraApiV2WithValueObjects.HasComment -> Cardinality.MustHaveSome
        )
    )

    val IsRegionOf: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.IsRegionOf,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Region),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Representation),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo),
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

    val IsRegionOfValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.IsRegionOfValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Region),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.LinkValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue),
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

    val LinkObject: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.LinkObj,
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
        cardinalities = Map(
            OntologyConstants.SchemaOrg.Name -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkToValue -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.CreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasComment -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue -> Cardinality.MustHaveOne
        )
    )

    val Representation: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.Representation,
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
        cardinalities = Map(
            OntologyConstants.SchemaOrg.Name -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.CreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue -> Cardinality.MustHaveSome
        )
    )

    val StillImageRepresentation: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.StillImageRepresentation,
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
        cardinalities = Map(
            OntologyConstants.SchemaOrg.Name -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.CreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasStillImageFileValue -> Cardinality.MustHaveSome
        )
    )

    val MovingImageRepresentation: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.MovingImageRepresentation,
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
        cardinalities = Map(
            OntologyConstants.SchemaOrg.Name -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.CreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasMovingImageFileValue -> Cardinality.MustHaveSome
        )
    )

    val AudioRepresentation: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.AudioRepresentation,
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
        cardinalities = Map(
            OntologyConstants.SchemaOrg.Name -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.CreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasAudioFileValue -> Cardinality.MustHaveSome
        )
    )

    val DDDRepresentation: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.DDDRepresentation,
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
        cardinalities = Map(
            OntologyConstants.SchemaOrg.Name -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.CreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasDDDFileValue -> Cardinality.MustHaveSome
        )
    )

    val TextRepresentation: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.TextRepresentation,
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.HasTextFileValue -> Cardinality.MustHaveSome
        )
    )

    val DocumentRepresentation: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.DocumentRepresentation,
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
        cardinalities = Map(
            OntologyConstants.SchemaOrg.Name -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffLinkTo -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.HasPermissions -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.CreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HasDocumentFileValue -> Cardinality.MustHaveSome
        )
    )

    // TODO: what should go here?
    val XMLToStandoffMapping: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.XMLToStandoffMapping
    )

    // TODO: what should go here?
    val ListNode: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.ListNode
    )

    val ValueAsString: PropertyEntityInfoV2 = makeProperty(
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

    val ValueHas: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.ValueHas,
        propertyType = OntologyConstants.Rdf.Property,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Value)
    )

    val Value: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.Value,
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne
        )
    )

    val ValueCreationDate: PropertyEntityInfoV2 = makeProperty(
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

    val HasValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Value)
    )

    val HasLinkTo: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkTo,
        propertyType = OntologyConstants.Owl.ObjectProperty,
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

    val HasLinkToValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasLinkToValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue),
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

    val SubjectType: PropertyEntityInfoV2 = makeProperty(
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

    val ObjectType: PropertyEntityInfoV2 = makeProperty(
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

    val ResourceIcon: PropertyEntityInfoV2 = makeProperty(
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

    val HasColor: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasColor,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Region),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.ColorValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
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

    val HasGeometry: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasGeometry,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Region),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.GeomValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
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

    val HasComment: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasComment,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Resource),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.TextValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
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

    val HasFileValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.Representation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.FileValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
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

    val HasStillImageFileValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasStillImageFileValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.StillImageRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
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

    val HasMovingImageFileValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasMovingImageFileValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.MovingImageRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
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

    val HasAudioImageFileValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasAudioFileValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.AudioRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.AudioFileValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
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

    val HasDDDFileValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasDDDFileValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DDDRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DDDFileValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
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

    val HasTextFileValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasTextFileValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.TextRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.TextFileValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
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

    val HasDocumentFileValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasDocumentFileValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DocumentRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.DocumentFileValue),
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.HasFileValue),
        isEditable = true,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
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

    val TextValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.TextValueHasMapping -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsXml -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsHtml -> Cardinality.MayHaveOne
        )
    )

    val TextValueAsXml: PropertyEntityInfoV2 = makeProperty(
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

    val TextValueHasMapping: PropertyEntityInfoV2 = makeProperty(
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

    val TextValueAsHtml: PropertyEntityInfoV2 = makeProperty(
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

    val DateValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar -> Cardinality.MayHaveOne
        )
    )

    val DateValueHasStartYear: PropertyEntityInfoV2 = makeProperty(
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

    val DateValueHasEndYear: PropertyEntityInfoV2 = makeProperty(
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

    val DateValueHasStartMonth: PropertyEntityInfoV2 = makeProperty(
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

    val DateValueHasEndMonth: PropertyEntityInfoV2 = makeProperty(
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

    val DateValueHasStartDay: PropertyEntityInfoV2 = makeProperty(
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

    val DateValueHasEndDay: PropertyEntityInfoV2 = makeProperty(
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

    val DateValueHasCalendar: PropertyEntityInfoV2 = makeProperty(
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

    val LinkValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTarget -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.LinkValueHasTargetIri -> Cardinality.MayHaveOne
        )
    )

    val LinkValueHasTarget: PropertyEntityInfoV2 = makeProperty(
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

    val LinkValueHasTargetIri: PropertyEntityInfoV2 = makeProperty(
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

    val IntegerValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObjects.IntegerValue,
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.IntegerValueAsInteger -> Cardinality.MustHaveOne
        )
    )

    val IntegerValueAsInteger: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.IntegerValueAsInteger,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.IntegerValue),
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
                    LanguageCodes.EN -> "Represents the literal integer value of an IntegerValue."
                )
            )
        )
    )

    val DecimalValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DecimalValueAsDecimal -> Cardinality.MustHaveOne
        )
    )

    val DecimalValueAsDecimal: PropertyEntityInfoV2 = makeProperty(
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

    val BooleanValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.BooleanValueAsBoolean -> Cardinality.MustHaveOne
        )
    )

    val BooleanValueAsBoolean: PropertyEntityInfoV2 = makeProperty(
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

    val GeomValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.GeometryValueAsGeometry -> Cardinality.MustHaveOne
        )
    )

    val GeometryValueAsGeometry: PropertyEntityInfoV2 = makeProperty(
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

    val IntervalValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasStart -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasEnd -> Cardinality.MustHaveOne
        )
    )

    val IntervalValueHasStart: PropertyEntityInfoV2 = makeProperty(
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

    val IntervalValueHasEnd: PropertyEntityInfoV2 = makeProperty(
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

    val ListValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.HierarchicalListValueAsListNode -> Cardinality.MustHaveOne
        )
    )

    val HierarchicalListValueAsListNode: PropertyEntityInfoV2 = makeProperty(
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

    val ColorValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.BooleanValueAsBoolean -> Cardinality.MustHaveOne
        )
    )

    val ColorValueAsColor: PropertyEntityInfoV2 = makeProperty(
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

    val UriValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.UriValueAsUri -> Cardinality.MustHaveOne
        )
    )

    val UriValueAsUri: PropertyEntityInfoV2 = makeProperty(
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

    val GeonameValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.GeonameValueAsGeonameCode -> Cardinality.MustHaveOne
        )
    )

    val GeonameValueAsGeonameCode: PropertyEntityInfoV2 = makeProperty(
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

    val FileValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename -> Cardinality.MustHaveOne
        )
    )

    val FileValueIsPreview: PropertyEntityInfoV2 = makeProperty(
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

    val FileValueAsUrl: PropertyEntityInfoV2 = makeProperty(
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

    val FileValueHasFilename: PropertyEntityInfoV2 = makeProperty(
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

    val TextFileValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename -> Cardinality.MustHaveOne
        )
    )

    val StillImageFileValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimX -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimY -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasIIIFBaseUrl -> Cardinality.MustHaveOne
        )
    )

    val StillImageFileValueHasDimX: PropertyEntityInfoV2 = makeProperty(
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

    val StillImageFileValueHasDimY: PropertyEntityInfoV2 = makeProperty(
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

    val StillImageFileValueHasIIIFBaseUrl: PropertyEntityInfoV2 = makeProperty(
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


    val MovingImageFileValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasDimX -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasDimY -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasFps -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasQualityLevel -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasDuration -> Cardinality.MustHaveOne
        )
    )

    val MovingImageFileValueHasDimX: PropertyEntityInfoV2 = makeProperty(
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

    val MovingImageFileValueHasDimY: PropertyEntityInfoV2 = makeProperty(
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

    val MovingImageFileValueHasFps: PropertyEntityInfoV2 = makeProperty(
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

    val MovingImageFileValueHasQualityLevel: PropertyEntityInfoV2 = makeProperty(
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

    val MovingImageFileValueHasDuration: PropertyEntityInfoV2 = makeProperty(
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

    val AudioFileValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.AudioFileValueHasDuration -> Cardinality.MustHaveOne
        )
    )

    val AudioFileValueHasDuration: PropertyEntityInfoV2 = makeProperty(
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

    val DDDFileValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename -> Cardinality.MustHaveOne
        )
    )

    val DocumentFileValue: ClassEntityInfoV2 = makeClass(
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
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ValueCreationDate -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename -> Cardinality.MustHaveOne
        )
    )

    /**
      * All the classes in the `knora-api` v2 ontology in the [[ApiV2WithValueObjects]] schema.
      */
    val Classes: Map[IRI, ClassEntityInfoV2] = Set(
        Resource,
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
        IntegerValue,
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
        classInfo => classInfo.classIri -> classInfo
    }.toMap

    /**
      * All the properties in the `knora-api` v2 ontology in the [[ApiV2WithValueObjects]] schema.
      */
    val Properties: Map[IRI, PropertyEntityInfoV2] = Set(
        Result,
        IsEditable,
        IsLinkValueProperty,
        CanBeInstantiated,
        HasPermissions,
        ValueAsString,
        ValueHas,
        ValueCreationDate,
        HasValue,
        HasLinkTo,
        HasLinkToValue,
        HasStandoffLinkTo,
        HasStandoffLinkToValue,
        CreationDate,
        LastModificationDate,
        SubjectType,
        ObjectType,
        ResourceIcon,
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
        DateValueHasCalendar,
        LinkValueHasTarget,
        LinkValueHasTargetIri,
        IntegerValueAsInteger,
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
        propertyInfo => propertyInfo.propertyIri -> propertyInfo
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
            predicateIri = predicateIri,
            ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri,
            objects = objects,
            objectsWithLang = objectsWithLang
        )
    }

    /**
      * Makes a [[PropertyEntityInfoV2]].
      *
      * @param propertyIri   the IRI of the property.
      * @param propertyType  the type of the property (owl:ObjectProperty, owl:DatatypeProperty, or rdf:Property).
      * @param subPropertyOf the set of direct superproperties of this property.
      * @param isEditable    true if this is a Knora resource property that can be edited via the Knora API.
      * @param isLinkValueProp true if the property points to a link value (reification).
      * @param predicates    the property's predicates.
      * @param subjectType   the required type of the property's subject.
      * @param objectType    the required type of the property's object.
      * @return a [[PropertyEntityInfoV2]].
      */
    private def makeProperty(propertyIri: IRI,
                             propertyType: IRI,
                             subPropertyOf: Set[IRI] = Set.empty[IRI],
                             isEditable: Boolean = false,
                             isLinkValueProp: Boolean = false,
                             predicates: Seq[PredicateInfoV2] = Seq.empty[PredicateInfoV2],
                             subjectType: Option[IRI] = None,
                             objectType: Option[IRI] = None): PropertyEntityInfoV2 = {
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

        PropertyEntityInfoV2(
            propertyIri = propertyIri,
            ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri,
            isEditable = isEditable,
            isLinkValueProp = isLinkValueProp,
            ontologySchema = ApiV2WithValueObjects,
            predicates = predsWithTypes.map {
                pred => pred.predicateIri -> pred
            }.toMap,
            subPropertyOf = subPropertyOf
        )
    }

    /**
      * Makes a [[ClassEntityInfoV2]].
      *
      * @param classIri            the IRI of the class.
      * @param subClassOf          the set of direct superclasses of this class.
      * @param predicates          the predicates of the class.
      * @param canBeInstantiated   true if this is a Knora resource class that can be instantiated via the Knora API.
      * @param cardinalities       the cardinalities of the class.
      * @return a [[ClassEntityInfoV2]].
      */
    private def makeClass(classIri: IRI,
                          subClassOf: Set[IRI] = Set.empty[IRI],
                          predicates: Seq[PredicateInfoV2] = Seq.empty[PredicateInfoV2],
                          canBeInstantiated: Boolean = false,
                          cardinalities: Map[IRI, Cardinality.Value] = Map.empty[IRI, Cardinality.Value]): ClassEntityInfoV2 = {
        val predicatesWithType = predicates :+ makePredicate(
            predicateIri = OntologyConstants.Rdf.Type,
            objects = Set(OntologyConstants.Owl.Class)
        )

        ClassEntityInfoV2(
            classIri = classIri,
            predicates = predicatesWithType.map {
                pred => pred.predicateIri -> pred
            }.toMap,
            canBeInstantiated = canBeInstantiated,
            cardinalities = cardinalities,
            subClassOf = subClassOf,
            ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri,
            ontologySchema = ApiV2WithValueObjects
        )
    }

}
