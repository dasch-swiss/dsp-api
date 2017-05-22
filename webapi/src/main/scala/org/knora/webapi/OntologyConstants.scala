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

package org.knora.webapi

/**
  * Contains string constants for IRIs from ontologies used by the application.
  */
object OntologyConstants {

    object Rdf {
        val RdfPrefixExpansion: IRI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"

        val Type: IRI = RdfPrefixExpansion + "type"
        val Subject: IRI = RdfPrefixExpansion + "subject"
        val Predicate: IRI = RdfPrefixExpansion + "predicate"
        val Object: IRI = RdfPrefixExpansion + "object"
    }

    object Rdfs {
        val RdfsPrefixExpansion = "http://www.w3.org/2000/01/rdf-schema#"

        val Label: IRI = RdfsPrefixExpansion + "label"
        val Comment: IRI = RdfsPrefixExpansion + "comment"
        val SubClassOf: IRI = RdfsPrefixExpansion + "subClassOf"
        val SubPropertyOf: IRI = RdfsPrefixExpansion + "subPropertyOf"
    }

    object Owl {
        val OwlPrefixExpansion: IRI = "http://www.w3.org/2002/07/owl#"

        val Restriction: IRI = OwlPrefixExpansion + "Restriction"
        val OnProperty: IRI = OwlPrefixExpansion + "onProperty"
        val Cardinality: IRI = OwlPrefixExpansion + "cardinality"
        val MinCardinality: IRI = OwlPrefixExpansion + "minCardinality"
        val MaxCardinality: IRI = OwlPrefixExpansion + "maxCardinality"

        val Class = OwlPrefixExpansion + "Class"

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

    object Xsd {
        val XsdPrefixExpansion: IRI = "http://www.w3.org/2001/XMLSchema#"

        val String: IRI = XsdPrefixExpansion + "string"
        val Boolean: IRI = XsdPrefixExpansion + "boolean"
        val Integer: IRI = XsdPrefixExpansion + "integer"
        val Decimal: IRI = XsdPrefixExpansion + "decimal"
        val Uri: IRI = XsdPrefixExpansion + "anyURI"
    }

    object KnoraInternal {
        // The start and end of an internal Knora ontology IRI.
        val InternalOntologyStart = "http://www.knora.org/ontology/"
    }

    object KnoraBase {
        val KnoraBaseOntologyLabel: String = "knora-base"
        val KnoraBaseOntologyIri: IRI = KnoraInternal.InternalOntologyStart + KnoraBaseOntologyLabel

        val KnoraBasePrefix: String = KnoraBaseOntologyLabel + ":"
        val KnoraBasePrefixExpansion: IRI = KnoraBaseOntologyIri + "#"

        val Resource: IRI = KnoraBasePrefixExpansion + "Resource"
        val ExternalResource: IRI = KnoraBasePrefixExpansion + "ExternalResource"
        val Representation: IRI = KnoraBasePrefixExpansion + "Representation"
        val AudioRepresentation: IRI = KnoraBasePrefixExpansion + "AudioRepresentation"
        val DDDRepresentation: IRI = KnoraBasePrefixExpansion + "DDDrepresentation"
        val DocumentRepresentation: IRI = KnoraBasePrefixExpansion + "DocumentRepresentation"
        val MovingImageRepresentation: IRI = KnoraBasePrefixExpansion + "MovingImageRepresentation"
        val StillImageRepresentation: IRI = KnoraBasePrefixExpansion + "StillImageRepresentation"
        val TextRepresentation: IRI = KnoraBasePrefixExpansion + "TextRepresentation"

        val XMLToStandoffMapping: IRI = KnoraBasePrefixExpansion + "XMLToStandoffMapping"
        val MappingElement: IRI = KnoraBasePrefixExpansion + "MappingElement"
        val mappingHasStandoffClass: IRI = KnoraBasePrefixExpansion + "mappingHasStandoffClass"
        val mappingHasStandoffProperty: IRI = KnoraBasePrefixExpansion + "mappingHasStandoffProperty"
        val mappingHasXMLClass: IRI = KnoraBasePrefixExpansion + "mappingHasXMLClass"
        val mappingHasXMLNamespace: IRI = KnoraBasePrefixExpansion + "mappingHasXMLNamespace"
        val mappingHasXMLTagname: IRI = KnoraBasePrefixExpansion + "mappingHasXMLTagname"
        val mappingHasXMLAttribute: IRI = KnoraBasePrefixExpansion + "mappingHasXMLAttribute"
        val mappingHasXMLAttributename: IRI = KnoraBasePrefixExpansion + "mappingHasXMLAttributename"
        val mappingHasStandoffDataTypeClass: IRI = KnoraBasePrefixExpansion + "mappingHasStandoffDataTypeClass"
        val mappingElementRequiresSeparator: IRI = KnoraBasePrefixExpansion + "mappingElementRequiresSeparator"

        val XSLTransformation: IRI = KnoraBasePrefixExpansion + "XSLTransformation"
        val mappingHasDefaultXSLTransformation = KnoraBasePrefixExpansion + "mappingHasDefaultXSLTransformation"

        val IsMainResource = KnoraBasePrefixExpansion + "isMainResource"


        val AbstractResourceClasses = Set(
            Resource,
            ExternalResource,
            Representation,
            AudioRepresentation,
            DDDRepresentation,
            DocumentRepresentation,
            MovingImageRepresentation,
            StillImageRepresentation,
            TextRepresentation
        )

        val ObjectClassConstraint: IRI = KnoraBasePrefixExpansion + "objectClassConstraint"
        val ObjectDatatypeConstraint: IRI = KnoraBasePrefixExpansion + "objectDatatypeConstraint"

        val LinkObj: IRI = KnoraBasePrefixExpansion + "LinkObj"
        val HasLinkTo: IRI = KnoraBasePrefixExpansion + "hasLinkTo"
        val HasLinkToValue: IRI = KnoraBasePrefixExpansion + "hasLinkToValue"
        val Region: IRI = KnoraBasePrefixExpansion + "Region"
        val IsRegionOf: IRI = KnoraBasePrefixExpansion + "isRegionOf"

        val ValueHas: IRI = KnoraBasePrefixExpansion + "valueHas"
        val ObjectCannotBeMarkedAsDeleted: IRI = KnoraBasePrefixExpansion + "objectCannotBeMarkedAsDeleted"

        val ValueHasString: IRI = KnoraBasePrefixExpansion + "valueHasString"
        val ValueHasMapping: IRI = KnoraBasePrefixExpansion + "valueHasMapping"
        val ValueHasInteger: IRI = KnoraBasePrefixExpansion + "valueHasInteger"
        val ValueHasDecimal: IRI = KnoraBasePrefixExpansion + "valueHasDecimal"
        val ValueHasStandoff: IRI = KnoraBasePrefixExpansion + "valueHasStandoff"
        val ValueHasResPtr: IRI = KnoraBasePrefixExpansion + "valueHasResPtr"
        val ValueHasStartJDN: IRI = KnoraBasePrefixExpansion + "valueHasStartJDN"
        val ValueHasEndJDN: IRI = KnoraBasePrefixExpansion + "valueHasEndJDN"
        val ValueHasCalendar: IRI = KnoraBasePrefixExpansion + "valueHasCalendar"
        val ValueHasStartPrecision: IRI = KnoraBasePrefixExpansion + "valueHasStartPrecision"
        val ValueHasEndPrecision: IRI = KnoraBasePrefixExpansion + "valueHasEndPrecision"
        val ValueHasBoolean: IRI = KnoraBasePrefixExpansion + "valueHasBoolean"
        val ValueHasUri: IRI = KnoraBasePrefixExpansion + "valueHasUri"
        val ValueHasColor: IRI = KnoraBasePrefixExpansion + "valueHasColor"
        val ValueHasGeometry: IRI = KnoraBasePrefixExpansion + "valueHasGeometry"
        val ValueHasListNode: IRI = KnoraBasePrefixExpansion + "valueHasListNode"
        val ValueHasIntervalStart: IRI = KnoraBasePrefixExpansion + "valueHasIntervalStart"
        val ValueHasIntervalEnd: IRI = KnoraBasePrefixExpansion + "valueHasIntervalEnd"
        val ValueHasOrder: IRI = KnoraBasePrefixExpansion + "valueHasOrder"
        val ValueHasRefCount: IRI = KnoraBasePrefixExpansion + "valueHasRefCount"
        val ValueHasComment: IRI = KnoraBasePrefixExpansion + "valueHasComment"
        val ValueHasGeonameCode: IRI = KnoraBasePrefixExpansion + "valueHasGeonameCode"

        val PreviousValue: IRI = KnoraBasePrefixExpansion + "previousValue"

        val ResourceProperty: IRI = KnoraBasePrefixExpansion + "resourceProperty"
        val HasValue: IRI = KnoraBasePrefixExpansion + "hasValue"
        val HasFileValue: IRI = KnoraBasePrefixExpansion + "hasFileValue"
        val HasStillImageFileValue: IRI = KnoraBasePrefixExpansion + "hasStillImageFileValue"
        val HasMovingImageFileValue: IRI = KnoraBasePrefixExpansion + "hasMovingImageFileValue"
        val HasAudioFileValue: IRI = KnoraBasePrefixExpansion + "hasAudioFileValue"
        val HasDDDFileValue: IRI = KnoraBasePrefixExpansion + "hasDDDFileValue"
        val HasTextFileValue: IRI = KnoraBasePrefixExpansion + "hasTextFileValue"
        val HasDocumentFileValue: IRI = KnoraBasePrefixExpansion + "hasDocumentFileValue"
        val HasComment: IRI = KnoraBasePrefixExpansion + "hasComment"

        val IsPreview: IRI = KnoraBasePrefixExpansion + "isPreview"
        val ResourceIcon: IRI = KnoraBasePrefixExpansion + "resourceIcon"

        val InternalMimeType: IRI = KnoraBasePrefixExpansion + "internalMimeType"
        val InternalFilename: IRI = KnoraBasePrefixExpansion + "internalFilename"
        val OriginalFilename: IRI = KnoraBasePrefixExpansion + "originalFilename"
        val OriginalMimeType: IRI = KnoraBasePrefixExpansion + "originalMimeType"
        val DimX: IRI = KnoraBasePrefixExpansion + "dimX"
        val DimY: IRI = KnoraBasePrefixExpansion + "dimY"
        val QualityLevel: IRI = KnoraBasePrefixExpansion + "qualityLevel"

        val TextValue: IRI = KnoraBasePrefixExpansion + "TextValue"
        val IntValue: IRI = KnoraBasePrefixExpansion + "IntValue"
        val BooleanValue: IRI = KnoraBasePrefixExpansion + "BooleanValue"
        val UriValue: IRI = KnoraBasePrefixExpansion + "UriValue"
        val DecimalValue: IRI = KnoraBasePrefixExpansion + "DecimalValue"
        val DateValue: IRI = KnoraBasePrefixExpansion + "DateValue"
        val ColorValue: IRI = KnoraBasePrefixExpansion + "ColorValue"
        val GeomValue: IRI = KnoraBasePrefixExpansion + "GeomValue"
        val ListValue: IRI = KnoraBasePrefixExpansion + "ListValue"
        val IntervalValue: IRI = KnoraBasePrefixExpansion + "IntervalValue"
        val LinkValue: IRI = KnoraBasePrefixExpansion + "LinkValue"
        val GeonameValue: IRI = KnoraBasePrefixExpansion + "GeonameValue"
        val FileValue: IRI = KnoraBasePrefixExpansion + "FileValue"
        val AudioFileValue: IRI = KnoraBasePrefixExpansion + "AudioFileValue"
        val DDDFileValue: IRI = KnoraBasePrefixExpansion + "DDDFileValue"
        val DocumentFileValue: IRI = KnoraBasePrefixExpansion + "DocumentFileValue"
        val StillImageFileValue: IRI = KnoraBasePrefixExpansion + "StillImageFileValue"
        val MovingImageFileValue: IRI = KnoraBasePrefixExpansion + "MovingImageFileValue"
        val TextFileValue: IRI = KnoraBasePrefixExpansion + "TextFileValue"

        val ValueClasses: Set[IRI] = Set(
            TextValue,
            IntValue,
            BooleanValue,
            UriValue,
            DecimalValue,
            DateValue,
            ColorValue,
            GeomValue,
            ListValue,
            IntervalValue,
            LinkValue,
            GeonameValue,
            FileValue,
            AudioFileValue,
            DDDFileValue,
            DocumentFileValue,
            StillImageFileValue,
            MovingImageFileValue,
            TextFileValue
        )

        val ListNode: IRI = KnoraBasePrefixExpansion + "ListNode"

        val IsDeleted: IRI = KnoraBasePrefixExpansion + "isDeleted"

        /* Resource creator */
        val AttachedToUser: IRI = KnoraBasePrefixExpansion + "attachedToUser"

        /* Resource's project */
        val AttachedToProject: IRI = KnoraBasePrefixExpansion + "attachedToProject"

        /* User */
        val User: IRI = KnoraBasePrefixExpansion                   + "User"
        val Email: IRI = KnoraBasePrefixExpansion                  + "email"
        val GivenName: IRI = KnoraBasePrefixExpansion              + "givenName"
        val FamilyName: IRI = KnoraBasePrefixExpansion             + "familyName"
        val Password: IRI = KnoraBasePrefixExpansion               + "password"
        val UsersActiveProject: IRI = KnoraBasePrefixExpansion     + "currentproject"
        val Status: IRI = KnoraBasePrefixExpansion                 + "status"
        val PreferredLanguage: IRI = KnoraBasePrefixExpansion      + "preferredLanguage"
        val IsInProject: IRI = KnoraBasePrefixExpansion            + "isInProject"
        val IsInGroup: IRI = KnoraBasePrefixExpansion              + "isInGroup"
        val IsInSystemAdminGroup: IRI = KnoraBasePrefixExpansion   + "isInSystemAdminGroup"
        val IsInProjectAdminGroup: IRI = KnoraBasePrefixExpansion  + "isInProjectAdminGroup"

        /* Project */
        val KnoraProject: IRI = KnoraBasePrefixExpansion           + "knoraProject"
        val ProjectShortname: IRI = KnoraBasePrefixExpansion       + "projectShortname"
        val ProjectLongname: IRI = KnoraBasePrefixExpansion        + "projectLongname"
        val ProjectDescription: IRI = KnoraBasePrefixExpansion     + "projectDescription"
        val ProjectKeywords: IRI = KnoraBasePrefixExpansion        + "projectKeywords"
        val ProjectBasepath: IRI = KnoraBasePrefixExpansion        + "projectBasepath"
        val ProjectLogo: IRI = KnoraBasePrefixExpansion            + "projectLogo"
        val ProjectOntologyGraph: IRI = KnoraBasePrefixExpansion   + "projectOntologyGraph"
        val ProjectDataGraph: IRI = KnoraBasePrefixExpansion       + "projectDataGraph"
        val HasSelfJoinEnabled: IRI = KnoraBasePrefixExpansion     + "hasSelfJoinEnabled"
        val HasProjectAdmin: IRI = KnoraBasePrefixExpansion        + "hasProjectAdmin"

        /* Group */
        val UserGroup: IRI = KnoraBasePrefixExpansion              + "UserGroup"
        val GroupName: IRI = KnoraBasePrefixExpansion              + "groupName"
        val GroupDescription: IRI = KnoraBasePrefixExpansion       + "groupDescription"
        val BelongsToProject: IRI = KnoraBasePrefixExpansion       + "belongsToProject"

        /* Built-In Groups */
        val UnknownUser: IRI = KnoraBasePrefixExpansion            + "UnknownUser"
        val KnownUser: IRI = KnoraBasePrefixExpansion              + "KnownUser"
        val ProjectMember: IRI = KnoraBasePrefixExpansion          + "ProjectMember"
        val Creator: IRI = KnoraBasePrefixExpansion                + "Creator"
        val SystemAdmin: IRI = KnoraBasePrefixExpansion            + "SystemAdmin"
        val ProjectAdmin: IRI = KnoraBasePrefixExpansion           + "ProjectAdmin"

        /* Institution */
        val Institution: IRI = KnoraBasePrefixExpansion + "Institution"

        /* Permissions */
        val HasPermissions: IRI = KnoraBasePrefixExpansion + "hasPermissions"

        val PermissionListDelimiter: Char = '|'
        val GroupListDelimiter: Char = ','

        val RestrictedViewPermission: String = "RV"
        val ViewPermission: String = "V"
        val ModifyPermission: String = "M"
        val DeletePermission: String = "D"
        val ChangeRightsPermission: String = "CR"
        val MaxPermission: String = ChangeRightsPermission

        val ObjectAccessPermissionAbbreviations: Seq[String] = Seq(
            RestrictedViewPermission,
            ViewPermission,
            ModifyPermission,
            DeletePermission,
            ChangeRightsPermission
        )

        val ProjectResourceCreateAllPermission: String = "ProjectResourceCreateAllPermission"
        val ProjectResourceCreateRestrictedPermission: String = "ProjectResourceCreateRestrictedPermission"
        val ProjectAdminAllPermission: String = "ProjectAdminAllPermission"
        val ProjectAdminGroupAllPermission: String = "ProjectAdminGroupAllPermission"
        val ProjectAdminGroupRestrictedPermission: String = "ProjectAdminGroupRestrictedPermission"
        val ProjectAdminRightsAllPermission: String = "ProjectAdminRightsAllPermission"
        val ProjectAdminOntologyAllPermission: String = "ProjectAdminOntologyAllPermission"

        val AdministrativePermissionAbbreviations: Seq[String] = Seq(
            ProjectResourceCreateAllPermission,
            ProjectResourceCreateRestrictedPermission,
            ProjectAdminAllPermission,
            ProjectAdminGroupAllPermission,
            ProjectAdminGroupRestrictedPermission,
            ProjectAdminRightsAllPermission,
            ProjectAdminOntologyAllPermission
        )

        val HasDefaultRestrictedViewPermission: IRI = KnoraBasePrefixExpansion + "hasDefaultRestrictedViewPermission"
        val HasDefaultViewPermission: IRI = KnoraBasePrefixExpansion + "hasDefaultViewPermission"
        val HasDefaultModifyPermission: IRI = KnoraBasePrefixExpansion + "hasDefaultModifyPermission"
        val HasDefaultDeletePermission: IRI = KnoraBasePrefixExpansion + "hasDefaultDeletePermission"
        val HasDefaultChangeRightsPermission: IRI = KnoraBasePrefixExpansion + "hasDefaultChangeRightsPermission"

        val DefaultPermissionProperties: Set[IRI] = Set(
            HasDefaultRestrictedViewPermission,
            HasDefaultViewPermission,
            HasDefaultModifyPermission,
            HasDefaultDeletePermission,
            HasDefaultChangeRightsPermission
        )

        /* Standoff */

        val StandoffTagHasStart: IRI = KnoraBasePrefixExpansion + "standoffTagHasStart"
        val StandoffTagHasEnd: IRI = KnoraBasePrefixExpansion + "standoffTagHasEnd"
        val StandoffTagHasStartIndex: IRI = KnoraBasePrefixExpansion + "standoffTagHasStartIndex"
        val StandoffTagHasEndIndex: IRI = KnoraBasePrefixExpansion + "standoffTagHasEndIndex"
        val StandoffTagHasStartParent: IRI = KnoraBasePrefixExpansion + "standoffTagHasStartParent"
        val StandoffTagHasEndParent: IRI = KnoraBasePrefixExpansion + "standoffTagHasEndParent"
        val StandoffTagHasUUID: IRI = KnoraBasePrefixExpansion + "standoffTagHasUUID"
        val StandoffTagHasOriginalXMLID: IRI = KnoraBasePrefixExpansion + "standoffTagHasOriginalXMLID"
        val StandoffTagHasInternalReference: IRI = KnoraBasePrefixExpansion + "standoffTagHasInternalReference"

        val StandoffTagHasLink: IRI = KnoraBasePrefixExpansion + "standoffTagHasLink"
        val HasStandoffLinkTo: IRI = KnoraBasePrefixExpansion + "hasStandoffLinkTo"
        val HasStandoffLinkToValue: IRI = KnoraBasePrefixExpansion + "hasStandoffLinkToValue"

        val StandoffDateTag: IRI = KnoraBasePrefixExpansion + "StandoffDateTag"
        val StandoffColorTag: IRI = KnoraBasePrefixExpansion + "StandoffColorTag"
        val StandoffIntegerTag: IRI = KnoraBasePrefixExpansion + "StandoffIntegerTag"
        val StandoffDecimalTag: IRI = KnoraBasePrefixExpansion + "StandoffDecimalTag"
        val StandoffIntervalTag: IRI = KnoraBasePrefixExpansion + "StandoffIntervalTag"
        val StandoffBooleanTag: IRI = KnoraBasePrefixExpansion + "StandoffBooleanTag"
        val StandoffLinkTag: IRI = KnoraBasePrefixExpansion + "StandoffLinkTag"
        val StandoffUriTag: IRI = KnoraBasePrefixExpansion + "StandoffUriTag"
        val StandoffInternalReferenceTag: IRI = KnoraBasePrefixExpansion + "StandoffInternalReferenceTag"

        val StandardMapping: IRI = "http://data.knora.org/projects/standoff/mappings/StandardMapping"

        val AdministrativePermission: IRI = KnoraBasePrefixExpansion       + "AdministrativePermission"
        val DefaultObjectAccessPermission: IRI = KnoraBasePrefixExpansion  + "DefaultObjectAccessPermission"
        val ForProject: IRI = KnoraBasePrefixExpansion                     + "forProject"
        val ForGroup: IRI = KnoraBasePrefixExpansion                       + "forGroup"
        val ForResourceClass: IRI = KnoraBasePrefixExpansion               + "forResourceClass"
        val ForProperty: IRI = KnoraBasePrefixExpansion                    + "forProperty"

        val SystemProject: IRI = KnoraBasePrefixExpansion                  + "SystemProject"

        /**
          * The system user is the owner of objects that are created by the system, rather than directly by the user,
          * such as link values for standoff resource references.
          */
        val SystemUser: IRI = KnoraBasePrefixExpansion                     + "SystemUser"

        val CreationDate: IRI = KnoraBasePrefixExpansion + "creationDate"
        val ValueCreationDate: IRI = KnoraBasePrefixExpansion + "valueCreationDate"
    }

    object Standoff {
        val StandoffPrefixExpansion: IRI = KnoraInternal.InternalOntologyStart + "standoff#"

        val StandoffRootTag: IRI = StandoffPrefixExpansion + "StandoffRootTag"
        val StandoffParagraphTag: IRI = StandoffPrefixExpansion + "StandoffParagraphTag"
        val StandoffItalicTag: IRI = StandoffPrefixExpansion + "StandoffItalicTag"
        val StandoffBoldTag: IRI = StandoffPrefixExpansion + "StandoffBoldTag"
        val StandoffUnderlineTag: IRI = StandoffPrefixExpansion + "StandoffUnderlineTag"
        val StandoffStrikethroughTag: IRI = StandoffPrefixExpansion + "StandoffStrikethroughTag"

        val StandoffHeader1Tag: IRI = StandoffPrefixExpansion + "StandoffHeader1Tag"
        val StandoffHeader2Tag: IRI = StandoffPrefixExpansion + "StandoffHeader2Tag"
        val StandoffHeader3Tag: IRI = StandoffPrefixExpansion + "StandoffHeader3Tag"
        val StandoffHeader4Tag: IRI = StandoffPrefixExpansion + "StandoffHeader4Tag"
        val StandoffHeader5Tag: IRI = StandoffPrefixExpansion + "StandoffHeader5Tag"
        val StandoffHeader6Tag: IRI = StandoffPrefixExpansion + "StandoffHeader6Tag"

        val StandoffSuperscriptTag: IRI = StandoffPrefixExpansion + "StandoffSuperscriptTag"
        val StandoffSubscriptTag: IRI = StandoffPrefixExpansion + "StandoffSubscriptTag"
        val StandoffOrderedListTag: IRI = StandoffPrefixExpansion + "StandoffOrderedListTag"
        val StandoffUnorderedListTag: IRI = StandoffPrefixExpansion + "StandoffUnorderedListTag"
        val StandoffListElementTag: IRI = StandoffPrefixExpansion + "StandoffListElementTag"
        val StandoffStyleElementTag: IRI = StandoffPrefixExpansion + "StandoffStyleTag"
    }

    object SalsahGui {
        val SalsahGuiPrefixExpansion: IRI = KnoraInternal.InternalOntologyStart + "salsah-gui#"

        val GuiAttribute: IRI = SalsahGuiPrefixExpansion + "guiAttribute"
        val GuiOrder: IRI = SalsahGuiPrefixExpansion + "guiOrder"
        val GuiElement: IRI = SalsahGuiPrefixExpansion + "guiElement"
        val SimpleText: IRI = SalsahGuiPrefixExpansion + "SimpleText"
        val Textarea: IRI = SalsahGuiPrefixExpansion + "Textarea"
        val Pulldown: IRI = SalsahGuiPrefixExpansion + "Pulldown"
        val Slider: IRI = SalsahGuiPrefixExpansion + "Slider"
        val Spinbox: IRI = SalsahGuiPrefixExpansion + "Spinbox"
        val Searchbox: IRI = SalsahGuiPrefixExpansion + "Searchbox"
        val Date: IRI = SalsahGuiPrefixExpansion + "Date"
        val Geometry: IRI = SalsahGuiPrefixExpansion + "Geometry"
        val Colorpicker: IRI = SalsahGuiPrefixExpansion + "Colorpicker"
        val List: IRI = SalsahGuiPrefixExpansion + "List"
        val Radio: IRI = SalsahGuiPrefixExpansion + "Radio"
        val Checkbox: IRI = SalsahGuiPrefixExpansion + "Checkbox"
        val Richtext: IRI = SalsahGuiPrefixExpansion + "Richtext"
        val Interval: IRI = SalsahGuiPrefixExpansion + "Interval"
        val Geonames: IRI = SalsahGuiPrefixExpansion + "Geonames"
        val Fileupload: IRI = SalsahGuiPrefixExpansion + "Fileupload"

        object attributeNames {
            val resourceClass: String = "restypeid"
            val assignmentOperator: String = "="
        }
    }

    object KnoraXmlImportV1 {
        object ProjectSpecificXmlImportNamespace {
            val XmlImportNamespaceStart: String = KnoraApi.ApiOntologyStart
            val XmlImportNamespaceEnd: String = "/xml-import/v1#"
        }

        val KnoraXmlImportNamespacePrefixLabel: String = "knoraXmlImport"
        val KnoraXmlImportNamespaceV1: IRI = KnoraApi.ApiOntologyStart + KnoraXmlImportNamespacePrefixLabel + "/v1#"

        val Resources: IRI = KnoraXmlImportNamespaceV1 + "resources"
    }

    object KnoraApi {
        // The start and end of a Knora API ontology IRI.
        val ApiOntologyStart = "http://api.knora.org/ontology/"

        val KnoraApiOntologyLabel: String = "knora-api"
        val KnoraApiOntologyIri: IRI = ApiOntologyStart + KnoraApiOntologyLabel

        val KnoraApiPrefix: String = KnoraApiOntologyLabel + ":"

        // TODO: add constants here for the actual IRIs of Knora API v2 ontologies.
    }

    object KnoraApiV2WithValueObject {

        val KnoraApiV2PrefixExpansion: IRI = KnoraApi.KnoraApiOntologyIri + "/v2#"

        val ValueAsString = KnoraApiV2PrefixExpansion + "valueAsString"

        val DateValueHasStartYear = KnoraApiV2PrefixExpansion + "dateValueHasStartYear"
        val DateValueHasEndYear = KnoraApiV2PrefixExpansion + "dateValueHasEndYear"
        val DateValueHasStartMonth = KnoraApiV2PrefixExpansion + "dateValueHasStartMonth"
        val DateValueHasEndMonth = KnoraApiV2PrefixExpansion + "dateValueHasEndMonth"
        val DateValueHasStartDay = KnoraApiV2PrefixExpansion + "dateValueHasStartDay"
        val DateValueHasEndDay = KnoraApiV2PrefixExpansion + "dateValueHasEndDay"
        val DateValueHasCalendar = KnoraApiV2PrefixExpansion + "dateValueHasCalendar"

        val TextValueAsHtml = KnoraApiV2PrefixExpansion + "textValueAsHtml"
        val TextValueAsXml = KnoraApiV2PrefixExpansion + "textValueAsXml"
        val TextValueHasMapping = KnoraApiV2PrefixExpansion + "textValueHasMapping"

        val IntegerValueAsInteger = KnoraApiV2PrefixExpansion + "integerValueAsInteger"

        val DecimalValueAsDecimal = KnoraApiV2PrefixExpansion + "decimalValueAsDecimal"

        val GeometryValueAsGeometry = KnoraApiV2PrefixExpansion + "GeometryValueAsGeometry"

        val LinkValueHasTarget = KnoraApiV2PrefixExpansion + "linkValueHasTarget"
        val LinkValueHasTargetIri = KnoraApiV2PrefixExpansion + "linkValueHasTargetIri"

        val FileValueAsUrl = KnoraApiV2PrefixExpansion + "fileValueAsUrl"
        val FileValueIsPreview = KnoraApiV2PrefixExpansion + "fileValueIsPreview"
        val FileValueHasFilename = KnoraApiV2PrefixExpansion + "fileValueHasFilename"

        val StillImageFileValueHasDimX = KnoraApiV2PrefixExpansion + "stillImageFileValueHasDimX"
        val StillImageFileValueHasDimY = KnoraApiV2PrefixExpansion + "stillImageFileValueHasDimY"
        val StillImageFileValueHasIIIFBaseUrl = KnoraApiV2PrefixExpansion + "stillImageFileValueHasIIIFBaseUrl"


        val IntervalValueHasStart = KnoraApiV2PrefixExpansion + "intervalValueHasStart"
        val IntervalValueHasEnd = KnoraApiV2PrefixExpansion + "intervalValueHasEnd"

        val BooleanValueAsBoolean = KnoraApiV2PrefixExpansion + "booleanValueAsBoolean"

        val HierarchicalListValueAsListNode = KnoraApiV2PrefixExpansion + "hierarchicalListValueAsListNode"

        val ColorValueAsColor = KnoraApiV2PrefixExpansion + "colorValueAsColor"

        val UriValueAsUri = KnoraApiV2PrefixExpansion + "uriValueAsUri"

        val GeonameValueAsGeonameCode = KnoraApiV2PrefixExpansion + "geonameValueAsGeonameCode"
    }

}