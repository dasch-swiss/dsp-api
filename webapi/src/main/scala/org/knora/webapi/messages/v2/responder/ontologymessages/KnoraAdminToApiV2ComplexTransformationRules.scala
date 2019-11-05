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

package org.knora.webapi.messages.v2.responder.ontologymessages

import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{OntologyLiteralV2, SmartIriLiteralV2, StringLiteralV2}
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{SmartIri, StringFormatter}

/**
  * Rules for converting `knora-admin` from the internal schema to the [[ApiV2Complex]] schema.
  * See also [[OntologyConstants.CorrespondingIris]].
  */
object KnoraAdminToApiV2ComplexTransformationRules extends OntologyTransformationRules {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getInstanceForConstantOntologies

    /**
      * The metadata to be used for the transformed ontology.
      */
    override val ontologyMetadata: OntologyMetadataV2 = OntologyMetadataV2(
        ontologyIri = OntologyConstants.KnoraAdminV2.KnoraAdminOntologyIri.toSmartIri,
        projectIri = Some(OntologyConstants.KnoraAdmin.SystemProject.toSmartIri),
        label = Some("The knora-admin ontology in the complex schema")
    )

    private val Users: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Users,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.UsersResponse),
        objectType = Some(OntologyConstants.KnoraAdminV2.UserClass),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "users"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The users returned in a UsersResponse."
                )
            )
        )
    )

    private val UserProperty: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.UserProperty,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.UserResponse),
        objectType = Some(OntologyConstants.KnoraAdminV2.UserClass),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "user"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The user returned in a UserResponse."
                )
            )
        )
    )

    private val ID: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.ID,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "ID"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The ID of the enclosing object."
                )
            )
        )
    )

    private val Token: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Token,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.UserClass),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "token"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The user's token."
                )
            )
        )
    )

    private val Ontologies: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Ontologies,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.ProjectClass),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "ontologies"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The ontologies attached to a project."
                )
            )
        )
    )

    private val SessionID: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.SessionID,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.UserClass),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "session ID"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The user's session ID."
                )
            )
        )
    )

    private val ProjectDescription: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.ProjectDescription,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        objectType = Some(OntologyConstants.KnoraAdminV2.StringLiteral),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "description"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A description of a project."
                )
            )
        )
    )

    private val UsersResponse: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.UsersResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "users response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a collection of users."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.Users -> Cardinality.MayHaveMany
        )
    )

    private val UserResponse = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.UserResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "user response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a single user."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.UserProperty -> Cardinality.MustHaveOne
        )
    )

    private val ProjectsResponse: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.ProjectsResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "projects response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a collection of projects."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.Projects -> Cardinality.MayHaveMany
        )
    )

    private val ProjectResponse = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.ProjectResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "project response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a single project."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.ProjectProperty -> Cardinality.MustHaveOne
        )
    )

    private val GroupsResponse: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.GroupsResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "groups response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a collection of groups."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.Groups -> Cardinality.MayHaveMany
        )
    )

    private val GroupResponse = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.GroupResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "group response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a single group."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.GroupProperty -> Cardinality.MustHaveOne
        )
    )

    private val Groups: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Groups,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        objectType = Some(OntologyConstants.KnoraAdminV2.GroupClass),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "groups"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A collection of groups."
                )
            )
        )
    )

    private val Members: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Members,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        objectType = Some(OntologyConstants.KnoraAdminV2.UserClass),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "members"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The members of a group or project."
                )
            )
        )
    )

    private val GroupProperty: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.GroupProperty,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.GroupResponse),
        objectType = Some(OntologyConstants.KnoraAdminV2.GroupClass),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "group"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A single group."
                )
            )
        )
    )

    private val KeywordsProperty: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.KeywordsProperty,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.KeywordsResponse),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "keywords"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Project keywords."
                )
            )
        )
    )

    private val KeywordsResponse: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.KeywordsResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "keywords response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing project keywords."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.KeywordsProperty -> Cardinality.MayHaveMany
        )
    )

    private val MembersResponse: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.MembersResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "members response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a collection of group or project members."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.Members -> Cardinality.MayHaveMany
        )
    )

    private val AdministrativePermissionsResponse: ReadClassInfoV2 = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.AdministrativePermissionsResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "administrative permissions response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a collection of administrative permissions."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.AdministrativePermissions -> Cardinality.MayHaveMany
        )
    )

    private val AdministrativePermissionResponse = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.AdministrativePermissionResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "administrative permission response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a single administrative permission."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.AdministrativePermissionProperty -> Cardinality.MustHaveOne
        )
    )

    private val UpdateUserRequest = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.UpdateUserRequest,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "update user request"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A request to update a user."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.Username -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.Email -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.GivenName -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.FamilyName -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.Lang -> Cardinality.MayHaveOne
        )
    )

    private val AdministrativePermissionProperty: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.AdministrativePermissionProperty,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.AdministrativePermissionResponse),
        objectType = Some(OntologyConstants.KnoraAdminV2.AdministrativePermissionClass),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "administrative permission"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Provides a single administrative permission."
                )
            )
        )
    )

    private val AdministrativePermissions: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.AdministrativePermissions,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.AdministrativePermissionsResponse),
        objectType = Some(OntologyConstants.KnoraAdminV2.AdministrativePermissionClass),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "administrative permissions"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "Provides a collection of administrative permissions."
                )
            )
        )
    )

    private val ForGroup: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.ForGroup,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.AdministrativePermissionClass),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "for group"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The group that the permission applies to."
                )
            )
        )
    )

    private val ForProject: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.ForProject,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.AdministrativePermissionClass),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "for project"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The project that the permission applies to."
                )
            )
        )
    )

    private val ForResourceClass: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.ForResourceClass,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.AdministrativePermissionClass),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "for resource class"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The resource class that the permission applies to."
                )
            )
        )
    )

    private val ForProperty: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.ForProperty,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.AdministrativePermissionClass),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "for property"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The property that the permission applies to."
                )
            )
        )
    )

    private val HasPermissions: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.HasPermissions,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.AdministrativePermissionClass),
        objectType = Some(OntologyConstants.KnoraAdminV2.Permission),
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
                    LanguageCodes.EN -> "The permissions granted by an AdministrativePermission."
                )
            )
        )
    )

    private val Name: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Name,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "name"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The name of the enclosing object."
                )
            )
        )
    )

    private val AdditionalInformation: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.AdditionalInformation,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.Permission),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "additional information"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "An IRI representing additional information about the permission."
                )
            )
        )
    )

    private val PermissionCode: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.PermissionCode,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.Permission),
        objectType = Some(OntologyConstants.Xsd.Integer),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "permission code"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A permission's numeric permission code."
                )
            )
        )
    )

    private val Iri: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Iri,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "iri"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The IRI of the enclosing object."
                )
            )
        )
    )

    private val Settings: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Settings,
        propertyType = OntologyConstants.Owl.ObjectProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.ProjectRestrictedViewSettingsResponse),
        objectType = Some(OntologyConstants.KnoraAdminV2.ProjectRestrictedViewSettings),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "settings"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A project's restricted view settings."
                )
            )
        )
    )

    private val Size: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Size,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.ProjectRestrictedViewSettings),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "size"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The image size used in restricted image view in a project."
                )
            )
        )
    )

    private val Watermark: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Watermark,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.ProjectRestrictedViewSettings),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "watermark"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The watermark used in restricted image view in a project."
                )
            )
        )
    )

    private val ProjectIri: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.ProjectIri,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.CreateGroupRequest),
        objectType = Some(OntologyConstants.Xsd.Uri),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "project iri"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The IRI of a project."
                )
            )
        )
    )

    private val ProjectRestrictedViewSettings = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.ProjectRestrictedViewSettings,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "project restricted view settings"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A project's restricted view settings."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.Size -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.Watermark -> Cardinality.MayHaveOne
        )
    )

    private val ProjectRestrictedViewSettingsResponse = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.ProjectRestrictedViewSettingsResponse,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "project restricted view settings response"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A response providing a project's restricted view settings."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.Settings -> Cardinality.MustHaveOne
        )
    )

    private val AdministrativePermissionClass = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.AdministrativePermissionClass,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "administrative permission"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "An administrative permission."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.ForGroup -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.ForProject -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.ForProperty -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.ForResourceClass -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.HasPermissions -> Cardinality.MustHaveSome,
            OntologyConstants.KnoraAdminV2.Iri -> Cardinality.MustHaveOne
        )
    )

    private val Permission = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.Permission,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "permission"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A permission."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.AdditionalInformation -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.Name -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraAdminV2.PermissionCode -> Cardinality.MayHaveOne
        )
    )

    private val CreateGroupRequest = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.CreateGroupRequest,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "create group"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A request to create a group."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.Name -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraAdminV2.GroupDescription -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.ProjectIri -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraAdminV2.Status -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraAdminV2.SelfJoin -> Cardinality.MustHaveOne
        )
    )

    private val UpdateGroupRequest = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.UpdateGroupRequest,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "update group"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A request to update a group."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.Name -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.GroupDescription -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.Status -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.SelfJoin -> Cardinality.MayHaveOne
        )
    )

    private val UpdateProjectRequest = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.UpdateProjectRequest,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "update project"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A request to update a project."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.Shortname -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.Longname -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.ProjectDescription -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraAdminV2.KeywordsProperty -> Cardinality.MayHaveMany,
            OntologyConstants.KnoraAdminV2.Logo -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.Status -> Cardinality.MayHaveOne,
            OntologyConstants.KnoraAdminV2.SelfJoin -> Cardinality.MayHaveOne
        )
    )

    private val Value: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Value,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.StringLiteral),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "value"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The value of a string literal."
                )
            )
        )
    )

    private val Language: ReadPropertyInfoV2 = makeProperty(
        propertyIri = OntologyConstants.KnoraAdminV2.Language,
        propertyType = OntologyConstants.Owl.DatatypeProperty,
        subjectType = Some(OntologyConstants.KnoraAdminV2.StringLiteral),
        objectType = Some(OntologyConstants.Xsd.String),
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "language"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "The language of a string literal."
                )
            )
        )
    )

    private val StringLiteral = makeClass(
        classIri = OntologyConstants.KnoraAdminV2.StringLiteral,
        predicates = Seq(
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Label,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "string literal"
                )
            ),
            makePredicate(
                predicateIri = OntologyConstants.Rdfs.Comment,
                objectsWithLang = Map(
                    LanguageCodes.EN -> "A string with an optional language tag."
                )
            )
        ),
        directCardinalities = Map(
            OntologyConstants.KnoraAdminV2.Value -> Cardinality.MustHaveOne,
            OntologyConstants.KnoraAdminV2.Language -> Cardinality.MayHaveOne
        )
    )

    /**
      * Properties to remove from the ontology before converting it to the target schema.
      * See also [[OntologyConstants.CorrespondingIris]].
      */
    override val internalPropertiesToRemove: Set[SmartIri] = Set(
        OntologyConstants.KnoraAdmin.ProjectRestrictedViewSize,
        OntologyConstants.KnoraAdmin.ProjectRestrictedViewWatermark,
        OntologyConstants.KnoraAdmin.BelongsToInstitution,
        OntologyConstants.KnoraAdmin.ForProject,
        OntologyConstants.KnoraAdmin.ForGroup,
        OntologyConstants.KnoraAdmin.ForResourceClass,
        OntologyConstants.KnoraAdmin.ForProperty,
        OntologyConstants.KnoraAdmin.GroupName,
        OntologyConstants.KnoraAdmin.IsInGroup
    ).map(_.toSmartIri)

    /**
      * Classes to remove from the ontology before converting it to the target schema.
      */
    override val internalClassesToRemove: Set[SmartIri] = Set(
        OntologyConstants.KnoraAdmin.Institution,
        OntologyConstants.KnoraAdmin.Permission,
        OntologyConstants.KnoraAdmin.AdministrativePermission,
        OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission
    ).map(_.toSmartIri)

    /**
      * Cardinalities to add to the User class.
      */
    private val UserCardinalities = Map(
        OntologyConstants.KnoraAdminV2.ID -> Cardinality.MayHaveOne,
        OntologyConstants.KnoraAdminV2.Password -> Cardinality.MayHaveOne,
        OntologyConstants.KnoraAdminV2.Token -> Cardinality.MayHaveOne,
        OntologyConstants.KnoraAdminV2.SessionID -> Cardinality.MayHaveOne,
        OntologyConstants.KnoraAdminV2.SystemAdmin -> Cardinality.MayHaveOne,
        OntologyConstants.KnoraAdminV2.Groups -> Cardinality.MayHaveMany,
        OntologyConstants.KnoraAdminV2.Permissions -> Cardinality.MustHaveOne
    )

    /**
      * Cardinalities to add to the Group class.
      */
    private val GroupCardinalities = Map(
        OntologyConstants.KnoraAdminV2.ID -> Cardinality.MayHaveOne,
        OntologyConstants.KnoraAdminV2.Name -> Cardinality.MustHaveOne,
        OntologyConstants.KnoraAdminV2.SelfJoin -> Cardinality.MustHaveOne
    )

    /**
      * Cardinalities to add to the Project class.
      */
    private val ProjectCardinalities = Map(
        OntologyConstants.KnoraAdminV2.ID -> Cardinality.MayHaveOne,
        OntologyConstants.KnoraAdminV2.Ontologies -> Cardinality.MayHaveMany,
        OntologyConstants.KnoraAdminV2.SelfJoin -> Cardinality.MustHaveOne
    )

    /**
      * After the ontology has been converted to the target schema, these cardinalities must be
      * added to the specified classes.
      */
    override val externalCardinalitiesToAdd: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]] = Map(
        OntologyConstants.KnoraAdminV2.UserClass -> UserCardinalities,
        OntologyConstants.KnoraAdminV2.GroupClass -> GroupCardinalities,
        OntologyConstants.KnoraAdminV2.ProjectClass -> ProjectCardinalities
    ).map {
        case (classIri, cardinalities) =>
            classIri.toSmartIri -> cardinalities.map {
                case (propertyIri, cardinality) =>
                    propertyIri.toSmartIri -> Cardinality.KnoraCardinalityInfo(cardinality)
            }
    }

    /**
      * Classes that need to be added to the ontology after converting it to the target schema.
      */
    override val externalClassesToAdd: Map[SmartIri, ReadClassInfoV2] = Set(
        UpdateUserRequest,
        UsersResponse,
        UserResponse,
        ProjectsResponse,
        ProjectResponse,
        UpdateProjectRequest,
        GroupsResponse,
        GroupResponse,
        CreateGroupRequest,
        UpdateGroupRequest,
        AdministrativePermissionsResponse,
        AdministrativePermissionResponse,
        AdministrativePermissionClass,
        Permission,
        MembersResponse,
        KeywordsResponse,
        ProjectRestrictedViewSettings,
        ProjectRestrictedViewSettingsResponse,
        StringLiteral
    ).map {
        classInfo => classInfo.entityInfoContent.classIri -> classInfo
    }.toMap

    /**
      * Properties that need to be added to the ontology after converting it to the target schema.
      * See also [[OntologyConstants.CorrespondingIris]].
      */
    override val externalPropertiesToAdd: Map[SmartIri, ReadPropertyInfoV2] = Set(
        Users,
        UserProperty,
        ID,
        Token,
        SessionID,
        Ontologies,
        ProjectDescription,
        AdministrativePermissionProperty,
        AdministrativePermissions,
        ForGroup,
        ForProject,
        ForResourceClass,
        ForProperty,
        HasPermissions,
        Name,
        AdditionalInformation,
        PermissionCode,
        Iri,
        Groups,
        Members,
        GroupProperty,
        KeywordsProperty,
        Settings,
        Size,
        Watermark,
        ProjectIri,
        Value,
        Language
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
      * @param propertyIri   the IRI of the property.
      * @param propertyType  the type of the property (owl:ObjectProperty, owl:DatatypeProperty, or rdf:Property).
      * @param predicates    the property's predicates.
      * @param subjectType   the required type of the property's subject.
      * @param objectType    the required type of the property's object.
      * @return a [[ReadPropertyInfoV2]].
      */
    private def makeProperty(propertyIri: IRI,
                             propertyType: IRI,
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
                    predicateIri = OntologyConstants.KnoraApiV2Complex.SubjectType,
                    objects = Seq(SmartIriLiteralV2(subjType.toSmartIri))
                )
        }

        val maybeObjectTypePred = objectType.map {
            objType =>
                makePredicate(
                    predicateIri = OntologyConstants.KnoraApiV2Complex.ObjectType,
                    objects = Seq(SmartIriLiteralV2(objType.toSmartIri))
                )
        }

        val predsWithTypes = predicates ++ maybeSubjectTypePred ++ maybeObjectTypePred :+ propTypePred

        ReadPropertyInfoV2(
            entityInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri.toSmartIri,
                ontologySchema = ApiV2Complex,
                predicates = predsWithTypes.map {
                    pred => pred.predicateIri -> pred
                }.toMap
            )
        )
    }

    /**
      * Makes a [[ReadClassInfoV2]].
      *
      * @param classIri            the IRI of the class.
      * @param predicates          the predicates of the class.
      * @param directCardinalities the direct cardinalities of the class.
      * @return a [[ReadClassInfoV2]].
      */
    private def makeClass(classIri: IRI,
                          predicates: Seq[PredicateInfoV2] = Seq.empty[PredicateInfoV2],
                          directCardinalities: Map[IRI, Cardinality.Value] = Map.empty[IRI, Cardinality.Value]): ReadClassInfoV2 = {
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
                subClassOf = Set.empty,
                ontologySchema = ApiV2Complex
            ),
            allBaseClasses = Set.empty
        )
    }
}
