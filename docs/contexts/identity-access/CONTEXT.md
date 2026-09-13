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
- A User IRI belongs to Identity & Access rather than to a global identifiers target.

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
- **Resources & Values** evaluates its **Object-access permissions** against a **Permission profile**
  using the shared **Permission policy**.
- **Project Migration** and **Operations** read Users, Groups, and permissions through published
  interfaces.

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
