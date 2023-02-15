# System Instances

Apart from class and property definitions, 
`knora-base` and `knora-admin` provide a small number of class instances 
that should be present in any running DSP stack:

## Built-in User Groups
```mermaid
classDiagram
%% Classes
  class UserGroup {

  }
  class UnknownUser {

  }
  class KnownUser {

  }
  class Creator {

  }
  class ProjectMember {

  }
  class ProjectAdmin {

  }
  class SystemAdmin {

  }
%% Relationships
  UserGroup <|-- UnknownUser
  UserGroup <|-- KnownUser
  UserGroup <|-- Creator
  UserGroup <|-- ProjectMember
  UserGroup <|-- ProjectAdmin
  UserGroup <|-- SystemAdmin

```

## Built-in Users
```mermaid
classDiagram
%% Classes
  class User {

  }
  class AnonymousUser {

  }
  class SystemUser {

  }
%% Relationships
  User <|-- AnonymousUser
  User <|-- SystemUser

```

## Built-in Projects
```mermaid
classDiagram
%% Classes
  class Project {

  }
  class SystemProject {

  }
  class DefaultSharedOntologiesProject {

  }
%% Relationships
  Project <|-- SystemProject
  Project <|-- DefaultSharedOntologiesProject

```

