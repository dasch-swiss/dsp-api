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
        cardinalities = Map(OntologyConstants.KnoraApiV2WithValueObject.ValueAsString -> Cardinality.MayHaveOne)
    )

    val ResourceProperty: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.ResourceProperty,
        propertyType = OntologyConstants.Owl.ObjectProperty
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
        propertyType = OntologyConstants.Rdf.Property
    )

    val ObjectType: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.ObjectType,
        propertyType = OntologyConstants.Rdf.Property
    )

    val ResourceIcon: PropertyEntityInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObject.ResourceIcon,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.Owl.Class),
        objectType = Some(OntologyConstants.Xsd.String)
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
                    "en" -> "Represents a comment on a resource as a TextValue"
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
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    "en" -> "Represents a text value"
                )
            )
        ),
        cardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObject.TextValueHasMapping -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.TextValueAsXml -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObject.TextValueAsHtml -> Cardinality.MayHaveOne
        )
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
