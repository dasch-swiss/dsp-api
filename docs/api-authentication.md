# API Authentication

### Authentication

Authentication is done via the `Authorization` header.
For secured endpoints the value of the header must be `Bearer <token>`,
where `<token>` is a [JWT](https://jwt.io/) token issued by the dsp-api.

The token is a JSON Web Token (JWT) that must contain the following claims:

* `iss` (issuer): The issuer of the token, the dsp-api
* `sub` (subject): The subject of the token, the user's or system id
* `aud` (audience): The audience of the token, the Dsp-Ingest service specific audience
* `exp` (expiration time): The expiration time of the token, in seconds since epoch
* `iat` (issued at): The time at which the token was issued, in seconds since epoch
* `jti` (JWT ID): A unique identifier for the token

# Permissions and Authorization

Currently, no further authorization is done, but this will change in the future.