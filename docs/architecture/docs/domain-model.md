# Domain Model

Note:

- The listing of Entities in this document are not exhaustive, 
  instead they represent the most relevant entities.
- The naming of attributes is not consistent: 
  It sometimes follows the request payload and sometimes the ontologies. 
  Plural/Singular is not a reliably representing cardinalities, but can serve as an indication.
- The split between Admin and V2 is somewhat arbitrary, 
  as the distinction in the RESTful API does not fully align with the distinction in the ontologies.


## General

```mermaid
erDiagram
    IRI
    LangString
```

## Admin

```mermaid
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

Confusions:
- User.phone?
- Institution? (name, description, website, phone, address, email)
- Project.belongsToInstitution?

## V2

### Overview

```mermaid
erDiagram
    %% Ontology
    Ontology
    ResourceClass
    Property
    Cardinality {
        IRI owl_property "the property this cardinality refers to"
        owl_cardinality cardinality "1, 0-1, 0-n, 1-n"
    }
    Ontology ||--o{ ResourceClass: "consists of"
    Ontology ||--o{ Property: "consists of (?)"
    ResourceClass ||--o{ Cardinality: defines
    Cardinality ||--|| Property: specifies
    
    %% Data
    Resource
    ResourceClass ||--o{ Resource: "can be instantiated as"
    Value
    Property ||--o{ Value: "can be instantiated as"
    Resource ||--o{ Value: contains

    Value ||--|| ObjectAccessPermission: grants
    Resource ||--|| ObjectAccessPermission: grants
    ObjectAccessPermission }o--o{ Group: "to"
```


## System Instances

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
