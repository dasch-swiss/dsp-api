/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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
import org.knora.webapi.messages.store.triplestoremessages.{OntologyLiteralV2, SmartIriLiteralV2, StringLiteralV2}
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{SmartIri, StringFormatter}

/**
  * Represents entities that must be added to the `knora-api` ontology, version 2, in the [[ApiV2WithValueObjects]] schema,
  * when it is generated from `knora-base`.
  */
object KnoraApiV2WithValueObjects {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

    val OntologyMetadata = OntologyMetadataV2(
        ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
        label = Some("The default knora-api ontology")
    )

    private val Result: ReadPropertyInfoV2 = makeProperty(
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

    private val IsEditable: ReadPropertyInfoV2 = makeProperty(
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
        subjectType = Some(OntologyConstants.Owl.ObjectProperty),
        objectType = Some(OntologyConstants.Xsd.Boolean)
    )

    private val IsLinkProperty: ReadPropertyInfoV2 = makeProperty(
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
        subjectType = Some(OntologyConstants.Owl.ObjectProperty),
        objectType = Some(OntologyConstants.Xsd.Boolean)
    )

    private val IsLinkValueProperty: ReadPropertyInfoV2 = makeProperty(
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
        subjectType = Some(OntologyConstants.Owl.ObjectProperty),
        objectType = Some(OntologyConstants.Xsd.Boolean)
    )

    private val IsInherited: ReadPropertyInfoV2 = makeProperty(
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

    private val CanBeInstantiated: ReadPropertyInfoV2 = makeProperty(
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

    private val HasOntologies: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasOntologies,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "has ontologies"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Points to information about one or more ontologies"
                )
            )
        )
    )

    private val HasClasses: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasClasses,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "has classes"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Points to information about one or more classes"
                )
            )
        ),
        objectType = Some(OntologyConstants.Owl.Class)
    )

    private val HasProperties: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasProperties,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "has properties"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Points to information about one or more properties"
                )
            )
        ),
        objectType = Some(OntologyConstants.Rdf.Property)
    )

    private val HasIndividuals: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasIndividuals,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "has individuals"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Points to information about one or more OWL named individuals"
                )
            )
        ),
        objectType = Some(OntologyConstants.Owl.NamedIndividual)
    )

    private val OntologyName: ReadPropertyInfoV2 = makeProperty(
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

    private val ProjectIri: ReadPropertyInfoV2 = makeProperty(
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

    private val HasIncomingLink: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.HasIncomingLink,
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

    private val ForbiddenResource: ReadClassInfoV2 = makeClass(
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
        )
    )

    private val ValueAsString: ReadPropertyInfoV2 = makeProperty(
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

    private val SubjectType: ReadPropertyInfoV2 = makeProperty(
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

    private val ObjectType: ReadPropertyInfoV2 = makeProperty(
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

    private val TextValueHasStandoff: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.TextValueHasStandoff,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.TextValue),
        objectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.StandoffTag),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "text value has standoff"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Standoff markup attached to a text value."
                )
            )
        )
    )

    private val TextValueAsXml: ReadPropertyInfoV2 = makeProperty(
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

    private val TextValueAsHtml: ReadPropertyInfoV2 = makeProperty(
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

    private val DateValueHasStartYear: ReadPropertyInfoV2 = makeProperty(
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

    private val DateValueHasEndYear: ReadPropertyInfoV2 = makeProperty(
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

    private val DateValueHasStartMonth: ReadPropertyInfoV2 = makeProperty(
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

    private val DateValueHasEndMonth: ReadPropertyInfoV2 = makeProperty(
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

    private val DateValueHasStartDay: ReadPropertyInfoV2 = makeProperty(
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

    private val DateValueHasEndDay: ReadPropertyInfoV2 = makeProperty(
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

    private val DateValueHasStartEra: ReadPropertyInfoV2 = makeProperty(
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

    private val DateValueHasEndEra: ReadPropertyInfoV2 = makeProperty(
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

    private val DateValueHasCalendar: ReadPropertyInfoV2 = makeProperty(
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

    private val LinkValueHasTarget: ReadPropertyInfoV2 = makeProperty(
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

    private val LinkValueHasTargetIri: ReadPropertyInfoV2 = makeProperty(
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

    private val IntValueAsInt: ReadPropertyInfoV2 = makeProperty(
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

    private val DecimalValueAsDecimal: ReadPropertyInfoV2 = makeProperty(
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

    private val BooleanValueAsBoolean: ReadPropertyInfoV2 = makeProperty(
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

    private val GeometryValueAsGeometry: ReadPropertyInfoV2 = makeProperty(
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

    private val ListValueAsListNode: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.ListValueAsListNode,
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

    private val ListValueAsListNodeLabel: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraApiV2WithValueObjects.ListValueAsListNodeLabel,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subPropertyOf = Set(OntologyConstants.KnoraApiV2WithValueObjects.ValueHas),
        subjectType = Some(OntologyConstants.KnoraApiV2WithValueObjects.ListValue),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Hierarchical list value as list node name"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Represents the name of the list node pointed to."
                )
            )
        )
    )

    private val ColorValueAsColor: ReadPropertyInfoV2 = makeProperty(
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

    private val UriValueAsUri: ReadPropertyInfoV2 = makeProperty(
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

    private val GeonameValueAsGeonameCode: ReadPropertyInfoV2 = makeProperty(
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

    private val FileValueIsPreview: ReadPropertyInfoV2 = makeProperty(
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

    private val FileValueAsUrl: ReadPropertyInfoV2 = makeProperty(
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

    private val FileValueHasFilename: ReadPropertyInfoV2 = makeProperty(
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

    private val StillImageFileValueHasDimX: ReadPropertyInfoV2 = makeProperty(
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

    private val StillImageFileValueHasDimY: ReadPropertyInfoV2 = makeProperty(
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

    private val StillImageFileValueHasIIIFBaseUrl: ReadPropertyInfoV2 = makeProperty(
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

    private val MovingImageFileValueHasDimX: ReadPropertyInfoV2 = makeProperty(
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

    private val MovingImageFileValueHasDimY: ReadPropertyInfoV2 = makeProperty(
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

    private val MovingImageFileValueHasFps: ReadPropertyInfoV2 = makeProperty(
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

    private val MovingImageFileValueHasQualityLevel: ReadPropertyInfoV2 = makeProperty(
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

    private val MovingImageFileValueHasDuration: ReadPropertyInfoV2 = makeProperty(
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

    private val AudioFileValueHasDuration: ReadPropertyInfoV2 = makeProperty(
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

    /**
      * Rules for transforming entities from `knora-base` into the corresponding entities from `knora-api`
      * in the [[ApiV2WithValueObjects]] schema.
      */
    object KnoraBaseTransformationRules {

        private val ResourceCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.HasIncomingLink -> Cardinality.MayHaveMany
        )

        private val DateBaseCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartYear -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndYear -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartMonth -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndMonth -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartDay -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndDay -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasStartEra -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasEndEra -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.DateValueHasCalendar -> Cardinality.MayHaveOne
        )

        private val UriBaseCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.UriValueAsUri -> Cardinality.MustHaveOne
        )

        private val BooleanBaseCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.BooleanValueAsBoolean -> Cardinality.MustHaveOne
        )

        private val IntBaseCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.IntValueAsInt -> Cardinality.MustHaveOne
        )

        private val DecimalBaseCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.DecimalValueAsDecimal -> Cardinality.MustHaveOne
        )

        private val IntervalBaseCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasStart -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.IntervalValueHasEnd -> Cardinality.MustHaveOne
        )

        private val ColorBaseCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ColorValueAsColor -> Cardinality.MustHaveOne
        )

        private val ValueCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ValueAsString -> Cardinality.MayHaveOne
        )

        private val TextValueCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.TextValueHasStandoff -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsXml -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.TextValueAsHtml -> Cardinality.MayHaveOne
        )

        private val GeomValueCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.GeometryValueAsGeometry -> Cardinality.MustHaveOne
        )

        private val ListValueCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.ListValueAsListNode -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.ListValueAsListNodeLabel -> Cardinality.MustHaveOne
        )

        private val GeonameValueCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.GeonameValueAsGeonameCode -> Cardinality.MustHaveOne
        )

        private val FileValueCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueIsPreview -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename -> Cardinality.MustHaveOne
        )

        private val StillImageFileValueCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimX -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimY -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasIIIFBaseUrl -> Cardinality.MustHaveOne
        )

        private val MovingImageFileValueCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasDimX -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasDimY -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasFps -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasQualityLevel -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraApiV2WithValueObjects.MovingImageFileValueHasDuration -> Cardinality.MustHaveOne
        )

        private val AudioFileValueCardinalities = Map(
            OntologyConstants.KnoraApiV2WithValueObjects.AudioFileValueHasDuration -> Cardinality.MustHaveOne
        )

        /**
          * Properties to remove from `knora-base` before converting it to the [[ApiV2WithValueObjects]] schema.
          */
        val KnoraBasePropertiesToRemove: Set[SmartIri] = Set(
            OntologyConstants.KnoraBase.IsDeleted,
            OntologyConstants.KnoraBase.DeleteDate,
            OntologyConstants.KnoraBase.DeleteComment,
            OntologyConstants.KnoraBase.ObjectCannotBeMarkedAsDeleted,
            OntologyConstants.KnoraBase.ObjectDatatypeConstraint,
            OntologyConstants.KnoraBase.ObjectClassConstraint,
            OntologyConstants.KnoraBase.SubjectClassConstraint,
            OntologyConstants.KnoraBase.StandoffParentClassConstraint,
            OntologyConstants.KnoraBase.ValueHasStandoff,
            OntologyConstants.KnoraBase.ValueHasMapping,
            OntologyConstants.KnoraBase.HasMappingElement,
            OntologyConstants.KnoraBase.MappingHasStandoffClass,
            OntologyConstants.KnoraBase.MappingHasStandoffProperty,
            OntologyConstants.KnoraBase.MappingHasXMLClass,
            OntologyConstants.KnoraBase.MappingHasXMLNamespace,
            OntologyConstants.KnoraBase.MappingHasXMLTagname,
            OntologyConstants.KnoraBase.MappingHasXMLAttribute,
            OntologyConstants.KnoraBase.MappingHasXMLAttributename,
            OntologyConstants.KnoraBase.MappingHasDefaultXSLTransformation,
            OntologyConstants.KnoraBase.MappingHasStandoffDataTypeClass,
            OntologyConstants.KnoraBase.MappingElementRequiresSeparator,
            OntologyConstants.KnoraBase.IsRootNode,
            OntologyConstants.KnoraBase.HasRootNode,
            OntologyConstants.KnoraBase.HasSubListNode,
            OntologyConstants.KnoraBase.ListNodeName,
            OntologyConstants.KnoraBase.ListNodePosition,
            OntologyConstants.KnoraBase.ValueHasMapping,
            OntologyConstants.KnoraBase.ValueHasCalendar,
            OntologyConstants.KnoraBase.ValueHasColor,
            OntologyConstants.KnoraBase.ValueHasStartJDN,
            OntologyConstants.KnoraBase.ValueHasEndJDN,
            OntologyConstants.KnoraBase.ValueHasStartPrecision,
            OntologyConstants.KnoraBase.ValueHasEndPrecision,
            OntologyConstants.KnoraBase.ValueHasDecimal,
            OntologyConstants.KnoraBase.ValueHasGeometry,
            OntologyConstants.KnoraBase.ValueHasGeonameCode,
            OntologyConstants.KnoraBase.ValueHasInteger,
            OntologyConstants.KnoraBase.ValueHasBoolean,
            OntologyConstants.KnoraBase.ValueHasUri,
            OntologyConstants.KnoraBase.ValueHasIntervalStart,
            OntologyConstants.KnoraBase.ValueHasIntervalEnd,
            OntologyConstants.KnoraBase.ValueHasListNode,
            OntologyConstants.KnoraBase.Duration,
            OntologyConstants.KnoraBase.DimX,
            OntologyConstants.KnoraBase.DimY,
            OntologyConstants.KnoraBase.Fps,
            OntologyConstants.KnoraBase.QualityLevel,
            OntologyConstants.KnoraBase.InternalFilename,
            OntologyConstants.KnoraBase.InternalMimeType,
            OntologyConstants.KnoraBase.OriginalFilename,
            OntologyConstants.KnoraBase.OriginalMimeType,
            OntologyConstants.KnoraBase.IsPreview,
            OntologyConstants.KnoraBase.ValueHasOrder,
            OntologyConstants.KnoraBase.PreviousValue,
            OntologyConstants.KnoraBase.ValueHasRefCount,
            OntologyConstants.KnoraBase.ValueHasString,
            OntologyConstants.KnoraBase.PreviousValue,
            OntologyConstants.KnoraBase.HasExtResValue,
            OntologyConstants.KnoraBase.ExtResAccessInfo,
            OntologyConstants.KnoraBase.ExtResId,
            OntologyConstants.KnoraBase.ExtResProvider,
            OntologyConstants.KnoraBase.MapEntryKey,
            OntologyConstants.KnoraBase.MapEntryValue,
            OntologyConstants.KnoraBase.IsInMap,
            OntologyConstants.KnoraBase.ForProject, // TODO: remove admin stuff from here when it's moved to a separate ontology.
            OntologyConstants.KnoraBase.ForGroup,
            OntologyConstants.KnoraBase.ForResourceClass,
            OntologyConstants.KnoraBase.ForProperty,
            OntologyConstants.KnoraBase.Address,
            OntologyConstants.KnoraBase.Email,
            OntologyConstants.KnoraBase.GivenName,
            OntologyConstants.KnoraBase.FamilyName,
            OntologyConstants.KnoraBase.Password,
            OntologyConstants.KnoraBase.UsersActiveProject,
            OntologyConstants.KnoraBase.Status,
            OntologyConstants.KnoraBase.PreferredLanguage,
            OntologyConstants.KnoraBase.IsInProject,
            OntologyConstants.KnoraBase.IsInProjectAdminGroup,
            OntologyConstants.KnoraBase.IsInGroup,
            OntologyConstants.KnoraBase.IsInSystemAdminGroup,
            OntologyConstants.KnoraBase.InstitutionDescription,
            OntologyConstants.KnoraBase.InstitutionName,
            OntologyConstants.KnoraBase.InstitutionWebsite,
            OntologyConstants.KnoraBase.Phone,
            OntologyConstants.KnoraBase.KnoraProject,
            OntologyConstants.KnoraBase.ProjectShortname,
            OntologyConstants.KnoraBase.ProjectShortcode,
            OntologyConstants.KnoraBase.ProjectLongname,
            OntologyConstants.KnoraBase.ProjectDescription,
            OntologyConstants.KnoraBase.ProjectKeyword,
            OntologyConstants.KnoraBase.ProjectLogo,
            OntologyConstants.KnoraBase.BelongsToInstitution,
            OntologyConstants.KnoraBase.ProjectOntology,
            OntologyConstants.KnoraBase.HasSelfJoinEnabled,
            OntologyConstants.KnoraBase.GroupName,
            OntologyConstants.KnoraBase.GroupDescription,
            OntologyConstants.KnoraBase.BelongsToProject

        ).map(_.toSmartIri)

        /**
          * Classes to remove from `knora-base` before converting it to the [[ApiV2WithValueObjects]] schema.
          */
        val KnoraBaseClassesToRemove: Set[SmartIri] = Set(
            OntologyConstants.KnoraBase.DefaultObjectAccessPermission,
            OntologyConstants.KnoraBase.XSLTransformation,
            OntologyConstants.KnoraBase.MappingElement,
            OntologyConstants.KnoraBase.MappingComponent,
            OntologyConstants.KnoraBase.MappingStandoffDataTypeClass,
            OntologyConstants.KnoraBase.MappingXMLAttribute,
            OntologyConstants.KnoraBase.XMLToStandoffMapping,
            OntologyConstants.KnoraBase.ExternalResource,
            OntologyConstants.KnoraBase.ExternalResValue,
            OntologyConstants.KnoraBase.Map,
            OntologyConstants.KnoraBase.MapEntry,
            OntologyConstants.KnoraBase.Permission, // TODO: remove admin stuff from here when it's moved to a separate ontology.
            OntologyConstants.KnoraBase.UserGroup,
            OntologyConstants.KnoraBase.Institution,
            OntologyConstants.KnoraBase.AdministrativePermission
        ).map(_.toSmartIri)

        /**
          * After `knora-base` has been converted to the [[ApiV2WithValueObjects]] schema, these cardinalities must be
          * added to the specified classes to obtain `knora-api`.
          */
        val KnoraApiCardinalitiesToAdd: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]] = Map(
            OntologyConstants.KnoraBase.Resource -> ResourceCardinalities,
            OntologyConstants.KnoraBase.DateBase -> DateBaseCardinalities,
            OntologyConstants.KnoraBase.UriBase -> UriBaseCardinalities,
            OntologyConstants.KnoraBase.BooleanBase -> BooleanBaseCardinalities,
            OntologyConstants.KnoraBase.IntBase -> IntBaseCardinalities,
            OntologyConstants.KnoraBase.DecimalBase -> DecimalBaseCardinalities,
            OntologyConstants.KnoraBase.IntervalBase -> IntervalBaseCardinalities,
            OntologyConstants.KnoraBase.ColorBase -> ColorBaseCardinalities,
            OntologyConstants.KnoraBase.Value -> ValueCardinalities,
            OntologyConstants.KnoraBase.TextValue -> TextValueCardinalities,
            OntologyConstants.KnoraBase.GeomValue -> GeomValueCardinalities,
            OntologyConstants.KnoraBase.ListValue -> ListValueCardinalities,
            OntologyConstants.KnoraBase.GeonameValue -> GeonameValueCardinalities,
            OntologyConstants.KnoraBase.FileValue -> FileValueCardinalities,
            OntologyConstants.KnoraBase.StillImageFileValue -> StillImageFileValueCardinalities,
            OntologyConstants.KnoraBase.MovingImageFileValue -> MovingImageFileValueCardinalities,
            OntologyConstants.KnoraBase.AudioFileValue -> AudioFileValueCardinalities
        ).map {
            case (classIri, cardinalities) =>
                classIri.toSmartIri.toOntologySchema(ApiV2WithValueObjects) -> cardinalities.map {
                    case (propertyIri, cardinality) =>
                        propertyIri.toSmartIri.toOntologySchema(ApiV2WithValueObjects) -> Cardinality.KnoraCardinalityInfo(cardinality)
                }
        }

        /**
          * Classes that need to be added to `knora-base`, after converting it to the [[ApiV2WithValueObjects]] schema, to obtain `knora-api`.
          */
        val KnoraApiClassesToAdd: Map[SmartIri, ReadClassInfoV2] = Set(
            ForbiddenResource
        ).map {
            classInfo => classInfo.entityInfoContent.classIri -> classInfo
        }.toMap

        /**
          * Properties that need to be added to `knora-base`, after converting it to the [[ApiV2WithValueObjects]] schema, to obtain `knora-api`.
          */
        val KnoraApiPropertiesToAdd: Map[SmartIri, ReadPropertyInfoV2] = Set(
            HasOntologies,
            HasClasses,
            HasProperties,
            HasIndividuals,
            Result,
            IsEditable,
            IsLinkProperty,
            IsLinkValueProperty,
            IsInherited,
            CanBeInstantiated,
            OntologyName,
            ProjectIri,
            ValueAsString,
            HasIncomingLink,
            SubjectType,
            ObjectType,
            TextValueHasStandoff,
            TextValueAsXml,
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
            ListValueAsListNode,
            ListValueAsListNodeLabel,
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
    }

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
                              objects: Seq[OntologyLiteralV2] = Seq.empty[OntologyLiteralV2],
                              objectsWithLang: Map[String, String] = Map.empty[String, String]): PredicateInfoV2 = {
        PredicateInfoV2(
            predicateIri = predicateIri.toSmartIri,
            objects = objects ++ objectsWithLang.map {
                case (lang, str) => StringLiteralV2(str, Some(lang))
            }
        )
    }

    /**
      * Makes a [[ReadPropertyInfoV2]].
      *
      * @param propertyIri     the IRI of the property.
      * @param propertyType    the type of the property (owl:ObjectProperty, owl:DatatypeProperty, or rdf:Property).
      * @param isResourceProp  true if this is a subproperty of `knora-api:hasValue` or `knora-api:hasLinkTo`.
      * @param subPropertyOf   the set of direct superproperties of this property.
      * @param isEditable      true if this is a Knora resource property that can be edited via the Knora API.
      * @param isLinkValueProp true if the property points to a link value (reification).
      * @param predicates      the property's predicates.
      * @param subjectType     the required type of the property's subject.
      * @param objectType      the required type of the property's object.
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
            objects = Seq(SmartIriLiteralV2(propertyType.toSmartIri))
        )

        val maybeSubjectTypePred = subjectType.map {
            subjType =>
                makePredicate(
                    predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType,
                    objects = Seq(SmartIriLiteralV2(subjType.toSmartIri))
                )
        }

        val maybeObjectTypePred = objectType.map {
            objType =>
                makePredicate(
                    predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType,
                    objects = Seq(SmartIriLiteralV2(objType.toSmartIri))
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
            objects = Seq(SmartIriLiteralV2(OntologyConstants.Owl.Class.toSmartIri))
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
            isResourceClass = isResourceClass,
            isValueClass = isValueClass
        )
    }

}
