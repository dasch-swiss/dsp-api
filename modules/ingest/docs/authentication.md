# API Authentication

### Authentication

Authentication is done via the `Authorization` header.
For secured endpoints the value of the header must be `Bearer <token>`,
where `<token>` is a [JWT](https://jwt.io/) token issued by the DSP-API.

The token is a JSON Web Token (JWT) that must contain the following claims:

* `iss` (issuer): The issuer of the token, the DSP-API
* `sub` (subject): The subject of the token, grants access to certain routes, format described [below](#authorization-and-subject-format).
* `aud` (audience): The audience of the token, the Dsp-Ingest service specific audience
* `exp` (expiration time): The expiration time of the token, in seconds since epoch
* `iat` (issued at): The time at which the token was issued, in seconds since epoch
* `jti` (JWT ID): A unique identifier for the token

# Authorization and subject format

Subject should be either empty or contain an object of form `{"scope": "admin"}`
where the value should contain space-delimited string combined from allowed values:

* `admin` – allows access to any route requiring authorization,
* `write:project:1234` – grants writing permissions for project with the shortcode `1234`.
* `read:project:1234` – grants reading permissions for project with the shortcode `1234`.
* `badvalue` – unrecognized values will be ignored for future-compatibility.

Example subject contents:
* "" or _empty_
* `write:project:ABCD read:project:8F8F write:project:1A2B`.
