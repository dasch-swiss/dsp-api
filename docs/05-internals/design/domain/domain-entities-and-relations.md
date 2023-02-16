# Domain Entities and Relations

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

The following Diagrams visualize the top level entities present in the DSP. 
The attributes of these entities should be exhaustive.
Cardinalities or validation constraints are normally not depicted. 
The indicated relationships are of conceptual nature and are more complicated in the actual system.


### Admin

```mermaid
erDiagram
    %% entities
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
        integer listNodePosition
    }
    DefaultObjectAccessPermission {
        IRI id
        string hasPermission "the 'RV, V, M, D, CR' string"
    }
    AdministrativePermission {
        IRI id
        string hasPermission "a different string representation"
    }
    Property {}
    ResourceClass {}

    %% relations
    User }|--|{ Project: "is member/admin of"
    User }o--|{ Group: "is member of"
    Group }o--|| Project: "belongs to"
    ListNode }o--|| Project: "belongs to"
    ListNode }o--o{ ListNode: "hasSubListNode"
    ListNode |o--o| ListNode: "hasRootNode"
    AdministrativePermission |{--o| Project: "points to"
    AdministrativePermission |{--|{ Group: "points to"
    
    DefaultObjectAccessPermission |{--o{ Group: "points to"
    DefaultObjectAccessPermission |{--|| Project: "points to"
    DefaultObjectAccessPermission |{--o{ Property: "points to"
    DefaultObjectAccessPermission |{--o{ ResourceClass: "points to"
    
```

!!! danger "Unclear/Unexpected Stuff"

    - User.phone?
    - Institution? (name, description, website, phone, address, email)
    - Project.belongsToInstitution?

### Overview V2

```mermaid
erDiagram
    Ontology ||--o{ ResourceClass: "consists of"
    Ontology ||--o{ Property: "consists of"
    ResourceClass o{--o{ Cardinality: defines
    Cardinality ||--|| Property: on
    ResourceClass ||--o{ Resource: "can be instantiated as"
    Property ||--o{ Value: "can be instantiated as"
    Resource ||--o{ Value: has
    Value }o--|| ObjectAccessPermission: grants
    Resource }o--|| ObjectAccessPermission: grants
    Resource }o--|| User: "attached to"
    Resource }o--|| Project: "attached to"
```

### Ontology

```mermaid
erDiagram
    Project {}
    Ontology {
        IRI id
        string ontologyName
        string label
        string comment "optional"
        boolean isShared
        date lastModificationDate
    }
    ResourceClass {
        IRI id
        langstring label
        langstring comment
    }
    Property {
        IRI id
        langstring label
        langstring comment
        string guiAttribute
    }
    GuiElement {}
    Cardinality {
        owl_cardinality cardinality "1, 0-1, 0-n, 1-n"
        integer guiOrder
    }
    Ontology o{--|| Project: "attached to"
    Ontology ||--o{ ResourceClass: "consists of"
    Ontology ||--o{ Property: "consists of"
    ResourceClass }o--o{ ResourceClass: subClassOf
    ResourceClass ||--o{ Cardinality: defines
    Cardinality ||--|| Property: specifies
    Property }o--o{ Property: subPropertyOf
    Property }o--o| ResourceClass: subjectType
    Property }o--o| ResourceClass: objectType
    Property }o--|| GuiElement: has
    Cardinality ||--|| Property: specifies
```

### Data

```mermaid
erDiagram
    User {}
    Project {}
    Resource {
        IRI id
        string label
        boolean isDeleted
        string hasPermission
        date creationDate
        date lastModificationDate
        date deleteDate
        string deleteComment
    }
    Value {
        IRI id
        date valueCreationDate
        string hasPermission
        integer valueHasOrder
        langstring valueHasComment
        boolean isDeleted
        date deleteDate
        langstring deleteComment
        string valueHasString
        UUID valueHasUUID
    }
    Resource ||--o{ Value: contains
    Resource o{--o{ Value: hasStandoffLinkTo
    Resource o{--|| User: attachedToUser
    Resource o{--o| User: deletedBy
    Resource o{--|| Project: attachedToProject
    Value ||--|| ValueLiteral: "is represented by"
    Value }o--o{ Resource: "links to"
    Value }o--o{ ListNode: "links to"
    Value o{--|| User: attachedToUser
    Value o{--o| User: deletedBy
    Value o|--o| Value: previousValue
```
