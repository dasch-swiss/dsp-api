# API Authentication

## Authentication

Authentication is done via the `Authorization` header.
For secured endpoints the value of the header must be `Bearer <token>`,
where `<token>` is a [JWT](https://jwt.io/) token issued by the DSP-API.

The token is a JSON Web Token (JWT) that must contain the following claims:

* `iss` (issuer): The issuer of the token, the DSP-API
* `sub` (subject): The subject of the token: the IRI of the user,
  or the issuer for a token that the DSP-API issues for its own calls to Dsp-Ingest
* `scope` (optional): The routes the token grants access to, format described [below](#authorization-and-scope-format)
* `aud` (audience): The audience of the token, the Dsp-Ingest service specific audience
* `exp` (expiration time): The expiration time of the token, in seconds since epoch
* `iat` (issued at): The time at which the token was issued, in seconds since epoch
* `jti` (JWT ID): A unique identifier for the token

## Authorization and scope format

The `scope` claim is optional.
Its value is a space-delimited string combined from these allowed values:

* `admin` – allows access to any route requiring authorization,
* `write:project:1234` – grants writing permissions for project with the shortcode `1234`.
* `read:project:1234` – grants reading permissions for project with the shortcode `1234`.
* `badvalue` – unrecognized values will be ignored for future-compatibility.

Example `scope` values:

* "" or _empty_
* `write:project:ABCD read:project:8F8F write:project:1A2B`.
