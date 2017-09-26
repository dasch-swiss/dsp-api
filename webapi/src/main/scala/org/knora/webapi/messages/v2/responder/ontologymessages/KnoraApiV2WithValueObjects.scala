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
        classIri = OntologyConstants.KnoraApiV2WithValueObject.Resource
    )

    val Region: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.Region,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Resource)
            )
        )
    )

    val Representation: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.Representation,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Resource)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "Repräsentation",
                    "en" -> "Representation",
                    "fr" -> "Répresentation",
                    "it" -> "Rappresentazione"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "A Resource that can store one or more FileValues"
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.HasFileValue -> Cardinality.MustHaveSome
        )
    )

    val StillImageRepresentation: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.StillImageRepresentation,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Representation)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "Repräsentation (Bild)",
                    "en" -> "Representation (Image)",
                    "fr" -> "Répresentation (Image)",
                    "it" -> "Rappresentazione (Immagine)"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "A Resource that can contain two-dimensional still image files"
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.HasStillImageFileValue -> Cardinality.MustHaveSome
        )
    )

    val MovingImageRepresentation: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.MovingImageRepresentation,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Representation)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "Repräsentation (Video)",
                    "en" -> "Representation (Movie)",
                    "fr" -> "Répresentation (Film)",
                    "it" -> "Rappresentazione (Film)"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "A Resource containing moving image data"
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.HasMovingImageFileValue -> Cardinality.MustHaveSome
        )
    )

    val AudioRepresentation: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.AudioRepresentation,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Representation)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "Repräsentation (Audio)",
                    "en" -> "Representation (Audio)",
                    "fr" -> "Répresentation (Audio)",
                    "it" -> "Rappresentazione (Audio)"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "A Resource containing audio data"
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.HasAudioFileValue -> Cardinality.MustHaveSome
        )
    )

    val DDDRepresentation: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.DDDRepresentation,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Representation)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "Repräsentation (3D)",
                    "en" -> "Representation (3D)",
                    "fr" -> "Répresentation (3D)",
                    "it" -> "Rappresentazione (3D)"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "A Resource containing 3D data"
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.HasDDDFileValue -> Cardinality.MustHaveSome
        )
    )

    val TextRepresentation: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.TextRepresentation,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Representation)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "Repräsentation (Text)",
                    "en" -> "Representation (Text)",
                    "fr" -> "Répresentation (Texte)",
                    "it" -> "Rappresentazione (Testo)"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "A Resource containing text files"
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.HasTextFileValue -> Cardinality.MustHaveSome
        )
    )

    val DocumentRepresentation: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.DocumentRepresentation,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Representation)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "Repräsentation (Dokument)",
                    "en" -> "Representation (Document)",
                    "fr" -> "Répresentation (Document)",
                    "it" -> "Rappresentazione (Documento)"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "A Resource containing documents"
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.HasDocumentFileValue -> Cardinality.MustHaveSome
        )
    )

    // TODO: what should go here?
    val XMLToStandoffMapping: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.XMLToStandoffMapping
    )

    // TODO: what should go here?
    val ListNode: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.ListNode
    )

    val ValueAsString: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.ValueAsString,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map("en" -> "A plain string representation of a value")
            )
        ),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.Value),
        objectType = Some(OntologyConstants.Xsd.String)
    )

    val ValueHas: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.ValueHas,
        propertyType = OntologyConstants.Rdf.Property,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.Value)
    )

    val Value: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.Value,
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne
        )
    )

    val ResourceProperty: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.ResourceProperty,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.Resource)
    )

    val HasValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.HasValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ResourceProperty)
            )
        ),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.Resource),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObject.Value)
    )

    val HasLinkTo: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.HasLinkTo,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ResourceProperty)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "hat Link zu",
                    "en" -> "has Link to",
                    "fr" -> "a lien vers",
                    "it" -> "ha Link verso"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a direct connection between two resources"
                )
            )
        ),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.Resource),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObject.Resource)
    )

    val SubjectType: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.SubjectType,
        propertyType = OntologyConstants.Rdf.Property,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Subject type"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Specifies the required type of the subjects of a property"
                )
            )
        )
    )

    val ObjectType: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.ObjectType,
        propertyType = OntologyConstants.Rdf.Property,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Object type"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Specifies the required type of the objects of a property"
                )
            )
        )

    )

    val ResourceIcon: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.ResourceIcon,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.Owl.Class),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Resource icon"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Specifies an icon to be used to represent instances of a resource class"
                )
            )
        )
    )

    val HasColor: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.HasColor,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.Region),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObject.ColorValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.HasValue)
            ),
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
                    "de" -> "Farbe",
                    "en" -> "Color",
                    "fr" -> "Couleur",
                    "it" -> "Colore"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Specifies the color of a Region"
                )
            )
        )
    )

    val HasComment: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.HasComment,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.Resource),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObject.TextValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.HasValue)
            ),
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
                objects = Set(OntologyConstants.SalsahGui.Richtext)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "Kommentar",
                    "en" -> "Comment",
                    "fr" -> "Commentaire",
                    "it" -> "Commento"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a comment on a Resource"
                )
            )
        )
    )

    val HasFileValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.HasFileValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.Representation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObject.FileValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.HasValue)
            ),
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
                objects = Set(OntologyConstants.SalsahGui.Fileupload)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "hat Datei",
                    "en" -> "has file",
                    "fr" -> "a fichier",
                    "it" -> "ha file"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Connects a Representation to a file"
                )
            )
        )
    )

    val HasStillImageFileValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.HasStillImageFileValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.StillImageRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.HasFileValue)
            ),
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
                objects = Set(OntologyConstants.SalsahGui.Fileupload)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "hat Bilddatei",
                    "en" -> "has image file",
                    "fr" -> "a fichier d'image",
                    "it" -> "ha file immagine"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Connects a Representation to an image file"
                )
            )
        )
    )

    val HasMovingImageFileValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.HasMovingImageFileValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.MovingImageRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.HasFileValue)
            ),
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
                objects = Set(OntologyConstants.SalsahGui.Fileupload)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "hat Filmdatei",
                    "en" -> "has movie file",
                    "fr" -> "a fichier de film",
                    "it" -> "ha file film"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Connects a Representation to a moving image file"
                )
            )
        )
    )

    val HasAudioImageFileValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.HasAudioFileValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.AudioRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObject.AudioFileValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.HasFileValue)
            ),
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
                objects = Set(OntologyConstants.SalsahGui.Fileupload)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "hat Audiodatei",
                    "en" -> "has audio file",
                    "fr" -> "a fichier d'audio",
                    "it" -> "ha file audio"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Connects a Representation to an audio file"
                )
            )
        )
    )

    val HasDDDFileValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.HasDDDFileValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.DDDRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObject.DDDFileValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.HasFileValue)
            ),
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
                objects = Set(OntologyConstants.SalsahGui.Fileupload)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "hat 3D-Datei",
                    "en" -> "has 3D file",
                    "fr" -> "a fichier 3D",
                    "it" -> "ha file 3D"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Connects a Representation to a 3D file"
                )
            )
        )
    )

    val HasTextFileValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.HasTextFileValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.TextRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObject.TextFileValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.HasFileValue)
            ),
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
                objects = Set(OntologyConstants.SalsahGui.Fileupload)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "hat Textdatei",
                    "en" -> "has text file",
                    "fr" -> "a fichier de texte",
                    "it" -> "ha file testo"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Connects a Representation to a text file"
                )
            )
        )
    )

    val HasDocumentFileValue: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.HasDocumentFileValue,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.DocumentRepresentation),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObject.DocumentFileValue),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.HasFileValue)
            ),
            makePredicate(
                predicateIri = OntologyConstants.SalsahGui.GuiElement,
                objects = Set(OntologyConstants.SalsahGui.Fileupload)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "de" -> "hat Dokument",
                    "en" -> "has document",
                    "fr" -> "a document",
                    "it" -> "ha documento"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Connects a Representation to a document"
                )
            )
        )
    )

    val TextValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.TextValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Value)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Text value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents text with optional markup."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.TextValueHasMapping -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.TextValueAsXml -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.TextValueAsHtml -> Cardinality.MayHaveOne
        )
    )

    val TextValueAsXml: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.TextValueAsXml,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.TextValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Text value as XML"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "A Text value represented in XML."
                )
            )
        )
    )

    val TextValueHasMapping: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.TextValueHasMapping,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.TextValue),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObject.XMLToStandoffMapping),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Text value has mapping"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "The mapping used to turn standoff into XML and vice versa."
                )
            )
        )
    )

    val TextValueAsHtml: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.TextValueAsHtml,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.TextValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Text value as HTML"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "A text value represented in HTML."
                )
            )
        )
    )

    val DateValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.DateValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Value)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Date value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a date as a period with different possible precisions."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.DateValueHasStartYear -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.DateValueHasEndYear -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.DateValueHasStartMonth -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.DateValueHasEndMonth -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.DateValueHasStartDay -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.DateValueHasEndDay -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.DateValueHasCalendar -> Cardinality.MayHaveOne
        )
    )

    val DateValueHasStartYear: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.DateValueHasStartYear,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.DateValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Date value has start year"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the start year of a date value."
                )
            )
        )
    )

    val DateValueHasEndYear: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.DateValueHasEndYear,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.DateValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Date value has end year"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the end year of a date value."
                )
            )
        )
    )

    val DateValueHasStartMonth: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.DateValueHasStartMonth,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.DateValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Date value has start month"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the start month of a date value."
                )
            )
        )
    )

    val DateValueHasEndMonth: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.DateValueHasEndMonth,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.DateValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Date value has end month"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the end month of a date value."
                )
            )
        )
    )

    val DateValueHasStartDay: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.DateValueHasStartDay,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.DateValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Date value has start day"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the start day of a date value."
                )
            )
        )
    )

    val DateValueHasEndDay: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.DateValueHasEndDay,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.DateValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Date value has end day"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the end day of a date value."
                )
            )
        )
    )

    val DateValueHasCalendar: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.DateValueHasCalendar,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.DateValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Date value has calendar"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the calendar of a date value."
                )
            )
        )
    )

    val LinkValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.LinkValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Value)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Link value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a link from one resource to another."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.LinkValueHasTarget -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.LinkValueHasTargetIri -> Cardinality.MayHaveOne
        )
    )

    val LinkValueHasTarget: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.LinkValueHasTarget,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.LinkValue),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObject.Resource),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Link value has target"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the target resource of a link value."
                )
            )
        )
    )

    val LinkValueHasTargetIri: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.LinkValueHasTargetIri,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.LinkValue),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Link value has target IRI"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the IRI of the target resource of a link value."
                )
            )
        )
    )

    val IntegerValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.IntegerValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Value)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Integer value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents an integer value."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.IntegerValueAsInteger -> Cardinality.MustHaveOne
        )
    )

    val IntegerValueAsInteger: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.IntegerValueAsInteger,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.IntegerValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Integer value as integer"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the literal integer value of an IntegerValue."
                )
            )
        )
    )

    val DecimalValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.DecimalValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Value)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Decimal value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a decimal value."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.DecimalValueAsDecimal -> Cardinality.MustHaveOne
        )
    )

    val DecimalValueAsDecimal: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.DecimalValueAsDecimal,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.DecimalValue),
        objectType = Some(OntologyConstants.Xsd.Decimal),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Decimal value as decimal"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the literal decimal value of a DecimalValue."
                )
            )
        )
    )

    val BooleanValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.BooleanValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Value)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Boolean value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a boolean value."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.BooleanValueAsBoolean -> Cardinality.MustHaveOne
        )
    )

    val BooleanValueAsBoolean: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.BooleanValueAsBoolean,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.BooleanValue),
        objectType = Some(OntologyConstants.Xsd.Boolean),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Boolean value as decimal"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the literal boolean value of a BooleanValue."
                )
            )
        )
    )

    val GeomValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.GeomValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Value)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Geometry value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a geometry value."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.GeometryValueAsGeometry -> Cardinality.MustHaveOne
        )
    )

    val GeometryValueAsGeometry: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.GeometryValueAsGeometry,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.GeomValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Geometry value as JSON"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a 2D geometry value as JSON."
                )
            )
        )
    )

    val IntervalValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.IntervalValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Value)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Interval value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a time interval."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.IntervalValueHasStart -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.IntervalValueHasEnd -> Cardinality.MustHaveOne
        )
    )

    val IntervalValueHasStart: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.IntervalValueHasStart,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.IntervalValue),
        objectType = Some(OntologyConstants.Xsd.Decimal),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Interval value has start"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the start of a time interval."
                )
            )
        )
    )

    val IntervalValueHasEnd: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.IntervalValueHasEnd,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.IntervalValue),
        objectType = Some(OntologyConstants.Xsd.Decimal),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Interval value has end"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the end of a time interval."
                )
            )
        )
    )

    val ListValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.ListValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Value)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "List value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a value in a hierarchical list."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.HierarchicalListValueAsListNode -> Cardinality.MustHaveOne
        )
    )

    val HierarchicalListValueAsListNode: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.HierarchicalListValueAsListNode,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.ListValue),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObject.ListNode),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Hierarchical list value as list node"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a reference to a hierarchical list node."
                )
            )
        )
    )

    val ColorValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.ColorValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Value)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Color value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a color value."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.BooleanValueAsBoolean -> Cardinality.MustHaveOne
        )
    )

    val ColorValueAsColor: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.ColorValueAsColor,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.ColorValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Color value as color"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the literal RGB value of a ColorValue."
                )
            )
        )
    )

    val UriValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.UriValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Value)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "URI value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a URI value."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.UriValueAsUri -> Cardinality.MustHaveOne
        )
    )

    val UriValueAsUri: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.UriValueAsUri,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.UriValue),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "URI value as URI"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the literal URI value of a UriValue."
                )
            )
        )
    )

    val GeonameValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.GeonameValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Value)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Geoname value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a Geoname value."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.GeonameValueAsGeonameCode -> Cardinality.MustHaveOne
        )
    )

    val GeonameValueAsGeonameCode: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.GeonameValueAsGeonameCode,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.GeonameValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Geoname value as Geoname code"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents the literal Geoname code of a GeonameValue."
                )
            )
        )
    )

    val FileValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.FileValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.Value)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "File value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a file value."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueHasFilename -> Cardinality.MustHaveOne
        )
    )

    val FileValueIsPreview: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.FileValueIsPreview,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.FileValue),
        objectType = Some(OntologyConstants.Xsd.Boolean),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "File value is preview"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Indicates whether this is file value is a preview."
                )
            )
        )
    )

    val FileValueAsUrl: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.FileValueAsUrl,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.FileValue),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "File value as URL"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "The URL at which the file can be accessed."
                )
            )
        )
    )

    val FileValueHasFilename: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.FileValueHasFilename,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.FileValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "File value has filename"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "The name of the file that a file value represents."
                )
            )
        )
    )

    val TextFileValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.TextFileValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.FileValue)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Text file value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a text file value."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueHasFilename -> Cardinality.MustHaveOne
        )
    )

    val StillImageFileValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.FileValue)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Still image file value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a still image file value."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueHasFilename -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValueHasDimX -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValueHasDimY -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValueHasIIIFBaseUrl -> Cardinality.MustHaveOne
        )
    )

    val StillImageFileValueHasDimX: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValueHasDimX,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Still image file value has X dimension"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "The horizontal dimension of a still image file value."
                )
            )
        )
    )

    val StillImageFileValueHasDimY: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValueHasDimY,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Still image file value has Y dimension"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "The vertical dimension of a still image file value."
                )
            )
        )
    )

    val StillImageFileValueHasIIIFBaseUrl: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValueHasIIIFBaseUrl,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.StillImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Still image file value has IIIF base URL"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "The IIIF base URL of a still image file value."
                )
            )
        )
    )


    val MovingImageFileValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.FileValue)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Moving image file value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a moving image file value."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueHasFilename -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValueHasDimX -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValueHasDimY -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValueHasFps -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValueHasQualityLevel -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValueHasDuration -> Cardinality.MustHaveOne
        )
    )

    val MovingImageFileValueHasDimX: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValueHasDimX,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Moving image file value has X dimension"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "The horizontal dimension of a moving image file value."
                )
            )
        )
    )

    val MovingImageFileValueHasDimY: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValueHasDimY,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Moving image file value has Y dimension"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "The vertical dimension of a moving image file value."
                )
            )
        )
    )

    val MovingImageFileValueHasFps: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValueHasFps,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Moving image file value has frames per second"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "The number of frames per second in a moving image file value."
                )
            )
        )
    )

    val MovingImageFileValueHasQualityLevel: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValueHasQualityLevel,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Moving image file value has quality level"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "The quality level of a moving image file value."
                )
            )
        )
    )

    val MovingImageFileValueHasDuration: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValueHasDuration,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.MovingImageFileValue),
        objectType = Some(OntologyConstants.Xsd.Decimal),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Moving image file value has duration"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "The duration of a moving image file value."
                )
            )
        )
    )

    val AudioFileValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.AudioFileValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.FileValue)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Audio file value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents an audio file value."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueHasFilename -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.AudioFileValueHasDuration -> Cardinality.MustHaveOne
        )
    )

    val AudioFileValueHasDuration: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.AudioFileValueHasDuration,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObject.AudioFileValue),
        objectType = Some(OntologyConstants.Xsd.Decimal),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubPropertyOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.ValueHas)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Audio file value has duration"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "The duration of an audio file value."
                )
            )
        )
    )

    val DDDFileValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.DDDFileValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.FileValue)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "3D file value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a 3D file value."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueHasFilename -> Cardinality.MustHaveOne
        )
    )

    val DocumentFileValue: ClassEntityInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraApiV2WithValueObject.DocumentFileValue,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.SubClassOf,
                objects = Set(OntologyConstants.KnoraApiV2WithValueObject.FileValue)
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    "en" -> "Document file value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a document file value."
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.FileValueHasFilename -> Cardinality.MustHaveOne
        )
    )

    /**
      * The set of all classes in the `knora-api` v2 ontology in the [[ApiV2WithValueObjects]] schema.
      */
    val Classes = Set(
        Resource,
        Region,
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
    )

    /**
      * The set of all properties in the `knora-api` v2 ontology in the [[ApiV2WithValueObjects]] schema.
      */
    val Properties = Set(
        ValueAsString,
        ValueHas,
        ResourceProperty,
        HasValue,
        HasLinkTo,
        SubjectType,
        ObjectType,
        ResourceIcon,
        HasColor,
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
    )

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
            ontologyIri = OntologyConstants.KnoraApi.KnoraApiOntologyIri,
            objects = objects,
            objectsWithLang = objectsWithLang
        )
    }

    /**
      * Makes a [[PropertyEntityInfoV2]].
      *
      * @param propertyIri  the IRI of the property.
      * @param propertyType the type of the property (owl:ObjectProperty, owl:DatatypeProperty, or rdf:Property).
      * @param predicates   the property's predicates.
      * @param subjectType  the required type of the property's subject.
      * @param objectType   the required type of the property's object.
      * @return a [[PropertyEntityInfoV2]].
      */
    private def makeProperty(propertyIri: IRI,
                             propertyType: IRI,
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
                    predicateIri = OntologyConstants.KnoraApiV2WithValueObject.SubjectType,
                    objects = Set(subjType)
                )
        }

        val maybeObjectTypePred = objectType.map {
            objType =>
                makePredicate(
                    predicateIri = OntologyConstants.KnoraApiV2WithValueObject.ObjectType,
                    objects = Set(objType)
                )
        }

        val predsWithTypes = predicates ++ maybeSubjectTypePred ++ maybeObjectTypePred :+ propTypePred

        PropertyEntityInfoV2(
            propertyIri = propertyIri,
            ontologyIri = OntologyConstants.KnoraApi.KnoraApiOntologyIri,
            ontologySchema = ApiV2WithValueObjects,
            predicates = predsWithTypes.map {
                pred => pred.predicateIri -> pred
            }.toMap
        )
    }

    /**
      * Makes a [[ClassEntityInfoV2]].
      *
      * @param classIri            the IRI of the class.
      * @param predicates          the predicates of the class.
      * @param cardinalities       the cardinalities of the class.
      * @param linkProperties      the set of the class's link properties.
      * @param linkValueProperties the set of the class's link value properties.
      * @param fileValueProperties the set of the class's file value properties.
      * @return a [[ClassEntityInfoV2]].
      */
    private def makeClass(classIri: IRI,
                          predicates: Seq[PredicateInfoV2] = Seq.empty[PredicateInfoV2],
                          cardinalities: Map[IRI, Cardinality.Value] = Map.empty[IRI, Cardinality.Value],
                          linkProperties: Set[IRI] = Set.empty[IRI],
                          linkValueProperties: Set[IRI] = Set.empty[IRI],
                          fileValueProperties: Set[IRI] = Set.empty[IRI]): ClassEntityInfoV2 = {
        val predicatesWithType = predicates :+ makePredicate(
            predicateIri = OntologyConstants.Rdf.Type,
            objects = Set(OntologyConstants.Owl.Class)
        )

        ClassEntityInfoV2(
            classIri = classIri,
            predicates = predicatesWithType.map {
                pred => pred.predicateIri -> pred
            }.toMap,
            linkProperties = linkProperties,
            linkValueProperties = linkValueProperties,
            fileValueProperties = fileValueProperties,
            ontologyIri = OntologyConstants.KnoraApi.KnoraApiOntologyIri,
            ontologySchema = ApiV2WithValueObjects
        )
    }
}
