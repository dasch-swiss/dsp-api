# Domain Model

In the context of [DEV-1415: Domain Model](https://linear.app/dasch/project/domain-model-e39ceb242242)
we attempted to gain a clear overview over the DSP's domain,
as implicitly modelled by the ontologies, code, validations and documentation of the DSP-API.

The following document aims to give a higher level overview of said domain.

!!! Note

    - As a high level overview, this document does not aim for exhaustivity.
    - Naming is tried to be kept as simple as possible, 
      while trying to consolidate different naming schemes
      (ontologies, code, API),
      which in result means that no naming scheme is strictly followed.
    - The split between V2 and Admin is arbitrary as those are intertwined within the system.
      It merely serves the purpose of organizing the presented entities.

## Domain Entities

The following Entity-Relationship-Diagrams visualize the top level entities present in the DSP. 
The attributes of these entities should be exhaustive; 
cardinalities or validation constraints are normally not depicted. 
The indicated relationships are of conceptual nature and are more complicated in the actual system.


```mermaid
---
title: Admin
---
erDiagram
    User {
        IRI id
        string userName "unique"
        string email "unique"
        string givenName
        string familyName
        string password
        string language "2 character ISO language code"
        boolean status
        boolean systemAdmin
        IRI isInProject
        IRI isInProjectAdminGroup
    }

    Project {
        IRI id
        string shortcode "4 character hex"
        string shortname "xsd:NCNAME"
        string longname "optional"
        langstring description
        string keywords
        boolean status
        boolean selfjoin
        string logo "optional"
        string restrictedViewSize
        string restrictedViewWatermark
    }

    Group {
        IRI id
        string name
        langstring description
        IRI project
        boolean status
        boolean selfjoin
    }

    ListNode {
        IRI id
        IRI projectIri "only for root node"
        langstring labels
        langstring comments
        string name
        boolean isRootNode
        IRI hasSublistNode
        IRI hasRootNode
        integer listNodePosition
    }
    ObjectAccessPermission {
        string hasPermission "the RV, V, M, D, CR string"
        IRI project
        IRI group
    }
    DefaultObjectAccessPermission {
        string hasPermission "the RV, V, M, D, CR string"
        IRI project
        IRI group
        IRI property
        IRI resourceClass
    }
    AdministrativePermission {
        string hasPermission "a different string representation"
        IRI project
        IRI group
    }

    User }|--|{ Project: "is member/admin of"
    User }|--|{ Group: "is member of"
    ListNode }o--|| Project: "belongs to"
    Project ||--|{ DefaultObjectAccessPermission: defines
    Group }o--o{ AdministrativePermission: "is granted"
    Group }o--o{ ObjectAccessPermission: "is granted"
    DefaultObjectAccessPermission ||--|| ObjectAccessPermission: "is realized as"
```

!!! danger "Unclear/Unexpected Stuff"

    - User.phone?
    - Institution? (name, description, website, phone, address, email)
    - Project.belongsToInstitution?


```mermaid
---
title: Overview V2
---
erDiagram
    Ontology ||--o{ ResourceClass: "consists of"
    Ontology ||--o{ Cardinality: "consists of"
    ResourceClass ||--o{ Cardinality: defines
    Cardinality ||--|| Property: specifies
    ResourceClass ||--o{ Property: has
    ResourceClass ||--o{ Resource: "can be instantiated as"
    Property ||--o{ Value: "can be instantiated as"
    Resource ||--o{ Value: contains
    Value ||--|| ObjectAccessPermission: grants
    Resource ||--|| ObjectAccessPermission: grants
    ObjectAccessPermission }o--o{ Group: "to"
    Resource }o--|| User: "attached to"
    Resource }o--|| Project: "attached to"
```


```mermaid
---
title: Ontology
---
erDiagram
    Ontology {
        IRI id
        string ontologyName
        IRI attachedToProject
        string label
        string comment "optional"
        boolean isShared
        date lastModificationDate
    }
    ResourceClass {
        IRI id
        langstring label
        langstring comment
        IRI subClassOf
        Cardinality cardinalities
    }
    Property {
        IRI id
        IRI subjectType
        IRI objectType
        langstring label
        langstring comment
        IRI subPropertyOf
        IRI guiElement
        string guiAttribute
    }
    Cardinality {
        IRI property "the property this cardinality refers to"
        owl_cardinality cardinality "1, 0-1, 0-n, 1-n"
        integer guiOrder
    }
    Ontology ||--o{ ResourceClass: "consists of"
    Ontology ||--o{ Property: "consists of (?)"
    ResourceClass ||--o{ Cardinality: defines
    Cardinality ||--|| Property: specifies
```


```mermaid
---
title: Data
---
erDiagram
    Resource {
        IRI id
        string label
        boolean isDeleted
        IRI hasStandoffLinkTo "+Value"
        IRI attachedToUser
        IRI attachedToProject
        string hasPermission
        date creationDate
        date lastModificationDate
        date deleteDate
        IRI deletedBy
        string deleteComment
    }
    Value {
        IRI id
        date valueCreationDate
        IRI attachedToUser
        string hasPermission
        integer valueHasOrder
        langstring valueHasComment
        boolean isDeleted
        date deleteDate
        IRI deletedBy
        langstring deleteComment
        IRI previousValue
        string valueHasString
        UUID valueHasUUID
    }
    Resource ||--o{ Value: contains
    Value ||--|| ValueLiteral: "is represented by"
    Value }o--o{ OtherResource: "links to"
    Value }o--o{ ListNode: "links to"
```



## System Instances of Classes

Apart from class and property definitions, 
`knora-base` and `knora-admin` also provide a small number of class instances 
that should be present in any running DSP stack:

```mermaid
---
title: User Groups
---
erDiagram
    UserGroup ||--|| UnknownUser: ""
    UserGroup ||--|| KnownUser: ""
    UserGroup ||--|| Creator: ""
    UserGroup ||--|| ProjectMember: ""
    UserGroup ||--|| ProjectAdmin: ""
    UserGroup ||--|| SystemAdmin: ""

```

```mermaid
---
title: Built-in Users
---
erDiagram
    User ||--o{ AnonymousUser: ""
    User ||--o{ SystemUser: ""

```

```mermaid
---
title: Built-in Projects
---
erDiagram
    Project ||--|| SystemProject: ""
    Project ||--|| DefaultSharedOntologiesProject: ""

```


## Class Hierarchy

While `knora-admin` and `salsah-gui` have relatively flat class hierarchies, 
in `knora-base` there are very complicated - yet highly relevant - inheritance structures. 
The following class diagrams try to model these structures. 
For the sake of comprehensibility, it was necessary to split the ontology into multiple diagrams,
even though this obliterates the evident connections between those diagrams.

```mermaid
---
title: Resources
---
classDiagram
  %% Classes
  class Resource {
    string label
    boolean isDeleted
    Resource hasStandoffLinkTo
    LinkValue hasStandoffLinkToValue
    User attachedToUser
    Project attachedToProject
    string hasPermissions
    date creationDate
    date lastModificationDate
    date deleteDate
    User deletedBy
    string deleteComment
  }
  class Annotation {
    TextValue hasComment
    Resource isAnnotationOf
    LinkValue isAnnotationOfValue
  }
  class LinkObj{
    TextValue hasComment
    Resource hasLinkTo
    LinkValue hasLinkToValue
  }
  class Representation {
    FileValue hasFileValue
  }
  class ExternalResource {
    ExternalResValue hasExternalResValue
  }
  class Region {
    ColorValue hasColor
    Representation isRegionOf
    GeomValue hasGemoetry
    LinkValue isRegionOfValue
    TextValue hasComment
  }
  class ArchiveRepresentation {
    ArchiveFileValue hasArchiveFileValue
  }
  class AudioRepresentation {
    AudioFileValue hasAudioFileValue
  }
  class DDDRepresentation {
    DDDFileValue hasDDDFileValue
  }
  class DocumentRepresentation {
    DocumentFileValue hasDocumentFileValue
  }
  class MovingImageRepresentation {
    MovingImageFileValue hasMovingImageFileValue
  }
  class StillImageRepresentation {
    StillImageFileValue hasStillImageFileValue
  }
  class TextRepresentation {
    TextFileValue hasTextFileValue
  }

  class AudioFileValue {
    decimal duration
  }
  class DocumentFileValue {
    integer pageCount
    integer dimX
    integer dimY
  }
  class StillImageFileValue {
    integer dimX
    integer dimY
  }

  %% Relationships
  Resource <|-- Annotation
  Resource <|-- Representation
  Resource <|-- LinkObj
  Resource <|-- ExternalResource
  Resource <|-- DeletedResource
  Resource <|-- Region
  Representation <|-- ArchiveRepresentation
  Representation <|-- AudioRepresentation
  Representation <|-- DDDRepresentation
  Representation <|-- DocumentRepresentation
  Representation <|-- MovingImageRepresentation
  Representation <|-- StillImageRepresentation
  Representation <|-- TextRepresentation
  TextRepresentation <|-- XSLTransformation
```

```mermaid
---
title: Values
---
classDiagram
  %% Classes
  class Value {
    date valueCreationDate
    User attachedToUser
    string hasPermissions
    integer valueHasOrder
    string valueHasComment
    boolean isDeleted
    date deleteDate
    User deletedBy
    string deleteComment
    Value previousValue
    string valueHasString
    string valueHasUUID
  }
  class ColorBase {
    string valueHasColor
  }
  class DateBase {
    string valueHasCalendar
    string valueHasEndPrecision
    integer valueHasEndJDN
    string valueHasStartPrecision
    integer valueHasStartJDN
  }
  class IntBase {
    integer valueHasInteger
  }
  class BooleanBase {
    boolean valueHasBoolean
  }
  class DecimalBase {
    decimal valueHasDecimal
  }
  class UriBase {
    URI valueHasUri
  }
  class IntervalBase {
    decimal valueHasIntervalStart
    decimal valueHasIntervalEnd
  }
  class TimeBase {
    date valueHasTimeStamp
  }
  class ListValue {
    ListNode valueHasListNode
  }
  class TextValue {
    StandoffTag valueHasStandoff
    integer valueHasMaxStandoffStartIndex
    string valueHasLanguage
    XSLToStandoffMApping valueHasMapping
  }
  class LinkValue {
    integer valueHasRefCount
  }
  class GeomValue {
    string valueHasGeometry
  }
  class GeonameValue {
    string valueHasGeonameCode
  }
  class ExternalResValue {
    string extResId
    string extResProvider
    string extResAccessInfo
  }
  class FileValue {
    string internalFileName
    string internalMimeType
    string originalFileName
    string originalMimeType
  }

  %% Relationships
  ValueBase <|-- Value
  ValueBase <|-- ColorBase
  ValueBase <|-- DateBase
  ValueBase <|-- IntBase
  ValueBase <|-- BooleanBase
  ValueBase <|-- DecimalBase
  ValueBase <|-- UriBase
  ValueBase <|-- IntervalBase
  ValueBase <|-- TimeBase
  Value <|-- ListValue
  Value <|-- TextValue
  Value <|-- LinkValue
  Value <|-- GeomValue
  Value <|-- GeonameValue
  Value <|-- ExternalResValue
  Value <|-- FileValue
  Value <|-- DeletedValue
  Value <|-- ColorValue
  ColorBase <|-- ColorValue
  Value <|-- DateValue
  DateBase <|-- DateValue
  Value <|-- IntValue
  IntBase <|-- IntValue
  Value <|-- BooleanValue
  BooleanBase <|-- BooleanValue
  Value <|-- DecimalValue
  DecimalBase <|-- DecimalValue
  Value <|-- UriValue
  UriBase <|-- UriValue
  Value <|-- IntervalValue
  IntervalBase <|-- IntervalValue
  Value <|-- TimeValue
  TimeBase <|-- TimeValue
  FileValue <|-- ArchiveFileValue
  FileValue <|-- AudioFileValue
  FileValue <|-- DDDFileValue
  FileValue <|-- DocumentFileValue
  FileValue <|-- MovingImageFileValue
  FileValue <|-- StillImageFileValue
  FileValue <|-- TextFileValue
```

## Domain User Stories

The following section provides a list of use cases / user stories. 
They should ideally cover all cases of what a user may want to do when they interact with the DSP.

!!! info "Legend"

    The different bullet points mean:

    - [ ] Not yet possible
    - [x] Fully implemented
    - partially implemented / needs checking

  - Administration
      - User Administration
          - Join the DSP as a user  
            Currently no self-sign-up, only system admins can add users.
          - [x] Add team members to a project  
          - [x] Remove team members from project  
          - Create groups with certain permissions attached
          - Modify permissions for a group
          - [x] Add project members to groups for for giving users particular permissions  
          - [x] Remove project members from groups
      - Project Administration
          - Create a project  
            Only possible for system administrators.
          - [x] Delete a project
          - Define default object access permissions for the project
          - Update Project metadata
              - [x] in DSP
              - in META  
                Only possible through the DSP-META repo
  - Data Modeling
      - [x] Add one or more data models (ontologies) to a project
      - [x] Update ontology metadata
      - [x] Add Resource Classes to a datamodel
      - Change resource classes
      - [x] Delete Resource classes
      - [x] Define reuseable properties for a datamodel
      - Edit properties
      - [x] Delete properties
      - [x] Add properties to a resource class with a defined cardinality
      - [x] Remove properties from a resource class
      - Change the cardinality of a property on a resource class
  - Data Generation
      - [x] Creating Resources
      - Updating Resources
          - changing the resource type
          - [x] adding values
          - [x] updating values
          - annotating/commenting on values
          - [x] deleting values
      - Linking Resources
          - [x] to other project resources
          - to external resources
      - Annotating/Commenting on Resources
      - [x] Deleting Resources
    - [ ] Data Archiving
        - [ ] Publish the existing data  
          Only possible through changing permissions (which App doesn't allow)
        - [ ] Archive it as a fixed, stable version  
          Currently not possible
  - Data Reuse
      - Browsing
          - [x] Browse Projects
          - [x] Browse Project Metadata
          - Inspect a projects datamodel(s)
          - Browse a projects data
              - [ ] all data
              - [x] by resource class
              - [ ] matching filters/facettes
      - [x] Searching  
        With the caveat that certain searches that are possible may not be good, fast or intuitive
          - [x] Search for projects covering a certain topic
          - [x] Search for a project of which one knows it already exists
          - [x] Search for a datapoint of which one already knows it exists
          - [x] Search for data matching criteria within a project
          - [x] Search for data matching criteria across projects
      - Programmatic reuse
          - [ ] download datasets/corpora as a dump (ideally in diverse formats)
          - [x] retrieve data matching certain search/filter criteria
          - [x] retrieve single resources/values by identifiers
