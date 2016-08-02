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

package org.knora.webapi

/**
  * Contains string constants for IRIs from ontologies used by the application.
  */
object OntologyConstants {

    object Rdf {
        val Type = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        val Subject = "http://www.w3.org/1999/02/22-rdf-syntax-ns#subject"
        val Predicate = "http://www.w3.org/1999/02/22-rdf-syntax-ns#predicate"
        val Object = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object"
    }

    object Rdfs {
        val Label = "http://www.w3.org/2000/01/rdf-schema#label"
        val Comment = "http://www.w3.org/2000/01/rdf-schema#comment"
        val SubclassOf = "http://www.w3.org/2000/01/rdf-schema#subClassOf"
    }

    object Owl {
        val Restriction = "http://www.w3.org/2002/07/owl#Restriction"

        val OnProperty = "http://www.w3.org/2002/07/owl#onProperty"

        val Cardinality = "http://www.w3.org/2002/07/owl#cardinality"
        val MinCardinality = "http://www.w3.org/2002/07/owl#minCardinality"
        val MaxCardinality = "http://www.w3.org/2002/07/owl#maxCardinality"


        /**
          * Cardinality IRIs expressed as OWL restrictions, which specify the properties that resources of
          * a particular type can have.
          */
        val cardinalityOWLRestrictions = Set(
            Cardinality,
            MinCardinality,
            MaxCardinality
        )
    }


    object KnoraBase {

        val KNORA_BASE_PREFIX = "http://www.knora.org/ontology/knora-base#"

        val Resource = "http://www.knora.org/ontology/knora-base#Resource"

        val ObjectClassConstraint = "http://www.knora.org/ontology/knora-base#objectClassConstraint"

        val HasLinkTo = "http://www.knora.org/ontology/knora-base#hasLinkTo"
        val IsRegionOf = "http://www.knora.org/ontology/knora-base#isRegionOf"

        val ValueHasString = "http://www.knora.org/ontology/knora-base#valueHasString"
        val ValueHasInteger = "http://www.knora.org/ontology/knora-base#valueHasInteger"
        val ValueHasFloat = "http://www.knora.org/ontology/knora-base#valueHasFloat"
        val ValueHasStandoff = "http://www.knora.org/ontology/knora-base#valueHasStandoff"
        val ValueHasResPtr = "http://www.knora.org/ontology/knora-base#valueHasResPtr"
        val ValueHasStartJDC = "http://www.knora.org/ontology/knora-base#valueHasStartJDC"
        val ValueHasEndJDC = "http://www.knora.org/ontology/knora-base#valueHasEndJDC"
        val ValueHasCalendar = "http://www.knora.org/ontology/knora-base#valueHasCalendar"
        val ValueHasStartPrecision = "http://www.knora.org/ontology/knora-base#valueHasStartPrecision"
        val ValueHasEndPrecision = "http://www.knora.org/ontology/knora-base#valueHasEndPrecision"
        val ValueHasColor = "http://www.knora.org/ontology/knora-base#valueHasColor"
        val ValueHasGeometry = "http://www.knora.org/ontology/knora-base#valueHasGeometry"
        val ValueHasListNode = "http://www.knora.org/ontology/knora-base#valueHasListNode"
        val ValueHasIntervalStart = "http://www.knora.org/ontology/knora-base#valueHasIntervalStart"
        val ValueHasIntervalEnd = "http://www.knora.org/ontology/knora-base#valueHasIntervalEnd"
        val ValueHasTime = "http://www.knora.org/ontology/knora-base#valueHasTime"
        val ValueHasOrder = "http://www.knora.org/ontology/knora-base#valueHasOrder"
        val ValueHasRefCount = "http://www.knora.org/ontology/knora-base#valueHasRefCount"
        val ValueHasComment = "http://www.knora.org/ontology/knora-base#valueHasComment"

        val PreviousValue = "http://www.knora.org/ontology/knora-base#previousValue"

        val HasFileValue = "http://www.knora.org/ontology/knora-base#hasFileValue"
        val HasStillImageFileValue = "http://www.knora.org/ontology/knora-base#hasStillImageFileValue"
        val HasMovingImageFileValue = "http://www.knora.org/ontology/knora-base#hasMovingImageFileValue"
        val HasAudioFileValue = "http://www.knora.org/ontology/knora-base#hasAudioFileValue"
        val HasDDDFileValue = "http://www.knora.org/ontology/knora-base#hasDDDFileValue"
        val HasTextFileValue = "http://www.knora.org/ontology/knora-base#hasTextFileValue"
        val HasDocumentFileValue = "http://www.knora.org/ontology/knora-base#hasDocumentFileValue"

        val IsPreview = "http://www.knora.org/ontology/knora-base#isPreview"
        val ResourceIcon = "http://www.knora.org/ontology/knora-base#resourceIcon"
        val PreferredLanguage = "http://www.knora.org/ontology/knora-base#preferredLanguage"

        val InternalMimeType = "http://www.knora.org/ontology/knora-base#internalMimeType"
        val InternalFilename = "http://www.knora.org/ontology/knora-base#internalFilename"
        val OriginalFilename = "http://www.knora.org/ontology/knora-base#originalFilename"
        val DimX = "http://www.knora.org/ontology/knora-base#dimX"
        val DimY = "http://www.knora.org/ontology/knora-base#dimY"
        val QualityLevel = "http://www.knora.org/ontology/knora-base#qualityLevel"

        val TextValue = "http://www.knora.org/ontology/knora-base#TextValue"
        val IntValue = "http://www.knora.org/ontology/knora-base#IntValue"
        val FloatValue = "http://www.knora.org/ontology/knora-base#FloatValue"
        val DateValue = "http://www.knora.org/ontology/knora-base#DateValue"
        val ColorValue = "http://www.knora.org/ontology/knora-base#ColorValue"
        val GeomValue = "http://www.knora.org/ontology/knora-base#GeomValue"
        val ListValue = "http://www.knora.org/ontology/knora-base#ListValue"
        val IntervalValue = "http://www.knora.org/ontology/knora-base#IntervalValue"
        val TimeValue = "http://www.knora.org/ontology/knora-base#TimeValue"
        val StillImageFileValue = "http://www.knora.org/ontology/knora-base#StillImageFileValue"
        val MovingImageFileValue = "http://www.knora.org/ontology/knora-base#MovingImageFileValue"
        val FileValue = "http://www.knora.org/ontology/knora-base#FileValue"
        val LinkValue = "http://www.knora.org/ontology/knora-base#LinkValue"

        val IsDeleted = "http://www.knora.org/ontology/knora-base#isDeleted"


        val StandoffHasAttribute = "http://www.knora.org/ontology/knora-base#standoffHasAttribute"
        val StandoffHasStart = "http://www.knora.org/ontology/knora-base#standoffHasStart"
        val StandoffHasEnd = "http://www.knora.org/ontology/knora-base#standoffHasEnd"
        val StandoffHasHref = "http://www.knora.org/ontology/knora-base#standoffHasHref"
        val StandoffHasLink = "http://www.knora.org/ontology/knora-base#standoffHasLink"
        val HasStandoffLinkTo = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"
        val HasStandoffLinkToValue = "http://www.knora.org/ontology/knora-base#hasStandoffLinkToValue"

        val AttachedToUser = "http://www.knora.org/ontology/knora-base#attachedToUser"
        val AttachedToProject = "http://www.knora.org/ontology/knora-base#attachedToProject"

        val Description = "http://www.knora.org/ontology/knora-base#description"


        /* User */
        val User = KNORA_BASE_PREFIX + "User"
        val Username = KNORA_BASE_PREFIX + "userid"
        val Email = KNORA_BASE_PREFIX + "email"
        val Password = KNORA_BASE_PREFIX + "password"
        val UsersActiveProject = KNORA_BASE_PREFIX + "currentproject"
        val IsActiveUser = KNORA_BASE_PREFIX + "isActiveUser"
        val IsInProject = KNORA_BASE_PREFIX + "isInProject"
        val IsInGroup = KNORA_BASE_PREFIX + "isInGroup"

        /* Project */
        val Project = KNORA_BASE_PREFIX + "Project"
        val ProjectShortname = KNORA_BASE_PREFIX + "projectShortname"
        val ProjectLongname = KNORA_BASE_PREFIX + "projectLongname"
        val ProjectDescription = KNORA_BASE_PREFIX + "projectDescription"
        val ProjectKeywords = KNORA_BASE_PREFIX + "projectKeywords"
        val ProjectBasepath = KNORA_BASE_PREFIX + "projectBasepath"
        val ProjectLogo = KNORA_BASE_PREFIX + "projectLogo"
        val ProjectOntologyGraph = KNORA_BASE_PREFIX + "projectOntologyGraph"
        val ProjectDataGraph = KNORA_BASE_PREFIX + "projectDataGraph"
        val IsActiveProject = KNORA_BASE_PREFIX + "isActiveProject"
        val HasSelfJoinEnabled = KNORA_BASE_PREFIX + "hasSelfJoinEnabled"
        val HasProjectAdmin = KNORA_BASE_PREFIX + "hasProjectAdmin"

        /* Group */
        val Group = KNORA_BASE_PREFIX + "UserGroup"
        val GroupName = KNORA_BASE_PREFIX + "groupName"
        val GroupDescription = KNORA_BASE_PREFIX + "groupDescription"
        val BelongsToProject = KNORA_BASE_PREFIX + "belongsToProject"
        val IsActiveGroup = KNORA_BASE_PREFIX + "isActiveGroup"

        /* Built-In Groups */
        val UnknownUser = "http://www.knora.org/ontology/knora-base#UnknownUser"
        val KnownUser = "http://www.knora.org/ontology/knora-base#KnownUser"
        val ProjectMember = "http://www.knora.org/ontology/knora-base#ProjectMember"
        val Creator = "http://www.knora.org/ontology/knora-base#Creator"
        val SystemAdmin = "http://www.knora.org/ontology/knora-base#SystemAdmin"
        val ProjectAdmin = "http://www.knora/ontology/knora-base#ProjectAdmin"
        val GroupAdmin = "http://www.knora.org/ontology/knora-base#GroupAdmin"

        /* Institution */
        val Institution = KNORA_BASE_PREFIX + "Institution"

        /* Permissions */
        val AdministrativePermission = KNORA_BASE_PREFIX + "AdministrativePermission"
        val DefaultObjectAccessPermission = KNORA_BASE_PREFIX + "DefaultObjectAccessPermission"
        val ForProject = KNORA_BASE_PREFIX + "forProject"
        val ForGroup = KNORA_BASE_PREFIX + "forGroup"
        val ForResourceClass = KNORA_BASE_PREFIX + "forResourceClass"
        val ForProperty = KNORA_BASE_PREFIX + "forProperty"

        val AllProjects = KNORA_BASE_PREFIX + "AllProjects"
        val AllGroups = KNORA_BASE_PREFIX + "AllGroups"
        val AllResourceClasses = KNORA_BASE_PREFIX + "AllResourceClasses"
        val AllProperties = KNORA_BASE_PREFIX + "AllProperties"


        /* Object Access Permission Properties */
        val HasRestrictedViewPermission = KNORA_BASE_PREFIX + "hasRestrictedViewPermission"
        val HasViewPermission = KNORA_BASE_PREFIX + "hasViewPermission"
        val HasModifyPermission = KNORA_BASE_PREFIX + "hasModifyPermission"
        val HasDeletePermission = KNORA_BASE_PREFIX + "hasDeletePermission"
        val HasChangeRightsPermission = KNORA_BASE_PREFIX + "hasChangeRightsPermission"
        val HasMaxPermission = HasChangeRightsPermission


        /* Resource Creation Permission Properties */
        val HasResourceCreationPermission = KNORA_BASE_PREFIX + "hasResourceCreationPermission"
        val HasRestrictedProjectResourceCreatePermission = KNORA_BASE_PREFIX + "hasRestrictedProjectResourceCreatePermission"

        /* Resource Creation Permission Value Instances */
        val ProjectResourceCreateAllPermission = KNORA_BASE_PREFIX + "ProjectResourceCreateAllPermission"


        /* Project Administration Permission Properties */
        val HasProjectAdministrationPermission = KNORA_BASE_PREFIX + "hasProjectAdministrationPermission"
        val HasRestrictedProjectGroupAdminPermission = KNORA_BASE_PREFIX + "hasRestrictedProjectGroupAdminPermission"

        /* Project Administration Permission Value Instances */
        val ProjectAllAdminPermission = KNORA_BASE_PREFIX + "ProjectAllAdminPermission"
        val ProjectAllGroupAdminPermission = KNORA_BASE_PREFIX + "ProjectAllGroupAdminPermission"
        val ProjectRightsAdminPermission = KNORA_BASE_PREFIX + "ProjectRightsAdminPermission"


        /* Ontology Administration Permission Properties */
        val HasOntologyAdministrationPermission = KNORA_BASE_PREFIX + "hasOntologyAdministrationPermission"

        /* Ontology Administration Permission Value Instances */
        val ProjectOntologyAdminPermission = KNORA_BASE_PREFIX + "ProjectOntologyAdminPermission"


        /* Default Object Access Permissions */
        val HasDefaultObjectAccessPermission = KNORA_BASE_PREFIX + "hasDefaultObjectAccessPermission"
        val HasDefaultRestrictedViewPermission = KNORA_BASE_PREFIX + "hasDefaultRestrictedViewPermission"
        val HasDefaultViewPermission = KNORA_BASE_PREFIX + "hasDefaultViewPermission"
        val HasDefaultModifyPermission = KNORA_BASE_PREFIX + "hasDefaultModifyPermission"
        val HasDefaultDeletePermission = KNORA_BASE_PREFIX + "hasDefaultDeletePermission"
        val HasDefaultChangeRightsPermission = KNORA_BASE_PREFIX + "hasDefaultChangeRightsPermission"

        val defaultPermissionProperties = Set(
            HasDefaultRestrictedViewPermission,
            HasDefaultViewPermission,
            HasDefaultModifyPermission,
            HasDefaultDeletePermission,
            HasDefaultChangeRightsPermission
        )

    }

    object SalsahGui {
        val GuiAttribute = "http://www.knora.org/ontology/salsah-gui#guiAttribute"
        val GuiOrder = "http://www.knora.org/ontology/salsah-gui#guiOrder"
        val GuiElement = "http://www.knora.org/ontology/salsah-gui#guiElement"
        val SimpleText = "http://www.knora.org/ontology/salsah-gui#SimpleText"
        val Textarea = "http://www.knora.org/ontology/salsah-gui#Textarea"
        val Pulldown = "http://www.knora.org/ontology/salsah-gui#Pulldown"
        val Slider = "http://www.knora.org/ontology/salsah-gui#Slider"
        val Spinbox = "http://www.knora.org/ontology/salsah-gui#Spinbox"
        val Searchbox = "http://www.knora.org/ontology/salsah-gui#Searchbox"
        val Date = "http://www.knora.org/ontology/salsah-gui#Date"
        val Geometry = "http://www.knora.org/ontology/salsah-gui#Geometry"
        val Colorpicker = "http://www.knora.org/ontology/salsah-gui#Colorpicker"
        val List = "http://www.knora.org/ontology/salsah-gui#List"
        val Radio = "http://www.knora.org/ontology/salsah-gui#Radio"
        val Richtext = "http://www.knora.org/ontology/salsah-gui#Richtext"
        val Time = "http://www.knora.org/ontology/salsah-gui#Time"
        val Interval = "http://www.knora.org/ontology/salsah-gui#Interval"
        val Geonames = "http://www.knora.org/ontology/salsah-gui#Geonames"
        object attributeNames {
            val resourceClass = "restypeid"
            val assignmentOperator = "="
        }
    }

    object Foaf {
        val GivenName = "http://xmlns.com/foaf/0.1/givenName"
        val FamilyName = "http://xmlns.com/foaf/0.1/familyName"
        val Name = "http://xmlns.com/foaf/0.1/name"
    }

}
