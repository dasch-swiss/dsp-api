# Identity & Access

Part of the dsp-api context map; see [the root index](../../../CONTEXT.md). This file lives under
`docs/contexts/` only until the context is extracted into its own module; the extraction PR moves it to
that module's root directory (see [`MODULARIZATION-PLAN.md`](../../../MODULARIZATION-PLAN.md)).

Identity & Access owns Users, Groups, memberships, permission administration, and effective
permission profiles. It exists so that who someone is, and what they are administratively allowed to
do inside a Project, has one owner.

## Ownership notes

- It depends on Projects because memberships and administrative permissions are project-scoped.
- Authentication, JWT handling, and request scopes are a separate technical module, not part of this
  context.
- Fine-grained object-access enforcement stays with the context that owns the protected object.
- `UserIri` is a shared-kernel value type. User, Group, and permission details cross a context
  boundary only through a port the consuming context declares, and Identity & Access implements
  those adapters, for example the ports Project Migration needs for export
  ([ADR-0011](../../adr/0011-cross-context-access-ports-and-adapters.md)).

## Language

**User**:
An authenticated identity that acts within Projects.
_Avoid_: Account

**Group**:
A named set of Users used to grant permissions.
_Avoid_: Role

**Membership**:
The association of a User with a Project or Group.
_Avoid_: Project ownership

**Permission profile**:
A User's effective Groups and administration flags, computed by Identity & Access and carried on the
authenticated request.
_Avoid_: JWT scope

**Administrative permission**:
A Project-scoped or Group-scoped grant governing administrative actions.
_Avoid_: Object-access permission

**Default object-access permission**:
The default access assigned to objects created in a Project, also called DOAP.
_Avoid_: Administrative permission

**Policy**:
The function-level requirement an endpoint declares, such as Public, Authenticated, SystemAdmin,
ProjectAdmin, ProjectMember, SelfOrSystemAdmin, ProjectAdminOfAnyProject, SystemAdminOrExportService
or ObjectAccess ([ADR-0013](../../adr/0013-authorization-policy-enforcement.md)). SelfOrSystemAdmin
admits the User named by the request and System administrators; ProjectAdminOfAnyProject admits System
administrators and Project administrators of at least one Project; SystemAdminOrExportService admits
System administrators and a trusted **Service identity**, which only sees the exports it created
([ADR-0014](../../adr/0014-service-identity-for-exports.md)). It is the only Policy that admits a caller
who is not a User.
_Avoid_: Administrative permission, Permission policy, Scope

**Service identity**:
A trusted machine client, named by a service name in the server's configuration, that may trigger,
poll, download and delete project exports. It is not a **User**: it has no account, no Memberships, no
Groups and no Permission profile, and it is never looked up as a User. A service identity sees only the
exports it created; ownership is keyed on its name, not on its key. In code an export handler receives
the caller as an `ExportCaller`: `Admin` for a System administrator, `Service` for a service identity.
_Avoid_: Service user, Machine user, Technical user, System user

**Service token**:
An Ed25519-signed JWT that a Service identity mints for itself with its own private key, naming the
key in its `kid` header, its name in `iss` and `sub`, and this server in `aud`, at most 15 minutes
long-lived. The server holds only the public keys; a service token is not a user's JWT and grants no
**Scope**.
_Avoid_: API key, Service account token

**Project candidate**:
An authenticated User who passed the endpoint's Policy but has not yet been checked against a specific
Project.
_Avoid_: Project administrator

**Project admin grant** and **Project member grant**:
Proof that the decision point checked a User against one Project; the domain write takes its target from
the grant's Project.
_Avoid_: Administrative permission

## Relationships

- **Identity & Access** owns Project and Group **Memberships** and computes a User's **Permission
  profile**.
- A **User** holds zero or more **Memberships**; each **Membership** names exactly one **Project** or
  **Group**.
- An **Administrative permission** is scoped to a **Project** or a **Group**.
- A **Default object-access permission** supplies the initial **Object-access permission** of objects
  created in a **Project**.
- **Authentication** depends on **Identity & Access**: an authenticated request carries an effective
  identity and **Permission profile**.
- A **Service token** authenticates a **Service identity**, never a **User**; only the
  SystemAdminOrExportService **Policy** accepts it, and every other endpoint answers 401.
- **Resources & Values** evaluates its **Object-access permissions** against a **Permission profile**
  using the shared **Permission policy**.
- **Project Migration** and **Operations** read Users, Groups, and permissions through ports they
  declare and **Identity & Access** implements.

## Example dialogue

> **Dev:** "The request already carries a JWT **Scope**. Can I use it to decide whether this **User**
> may edit the **Resource**?"
>
> **Domain expert:** "No. A **Scope** only gates the endpoint. Editing is decided from the
> **Permission profile** against the Resource's **Object-access permission**."
>
> **Dev:** "So where does the profile come from?"
>
> **Domain expert:** "Identity & Access computes it from the User's **Groups** and
> **Memberships**."

## Flagged ambiguities

- "Permission" named both administrative grants and fine-grained object access. Resolved:
  **Administrative permission** is owned here, **Object-access permission** is owned by the context
  holding the protected object.
- "Scope" was used interchangeably with permission. Resolved: **Scope** is an Authentication concept
  that gates endpoints, not an authorization decision.
- "Account" was used for **User**. Resolved: use **User**.
- "Service user" and "technical user" suggested that a machine client is a **User** with special
  rights. Resolved: a **Service identity** is not a User; it has no account and passes no User Policy.
