# Interaction Between Sipi and DSP-API

## General Remarks

DSP-API and Sipi (Simple Image Presentation Interface) are two
**complementary** software projects. Whereas DSP-API deals with data that
is written to and read from a triplestore (metadata and annotations),
Sipi takes care of storing, converting and serving image files as well
as other types of files such as audio, video, or documents (binary files
it just stores and serves).

DSP-API and Sipi stick to a clear division of responsibility regarding
files: DSP-API knows about the names of files that are attached to
resources as well as some metadata and is capable of creating the URLs
for the client to request them from Sipi, but the whole handling of
files (storing, naming, organization of the internal directory
structure, format conversions, and serving) is taken care of by Sipi.

## Adding Files to DSP

A file is first uploaded to Sipi, then its metadata is submitted to
DSP. The implementation of this procedure is described in
[DSP-API and Sipi](../05-internals/design/api-v2/sipi.md). Instructions for the client are given in
[Creating File Values](../03-endpoints/api-v2/editing-values.md#creating-file-values).

## Retrieving Files from Sipi

### File URLs in API v2

In DSP-API v2, image file URLs are provided in [IIIF](https://iiif.io/) format. In the simple
[ontology schema](../03-endpoints/api-v2/introduction.md#api-schema), a file value is simply
a IIIF URL that can be used to retrieve the file from Sipi.

In the complex schema, a `StillImageFileValue` carries
`knora-api:stillImageFileValueHasIIIFImageService`: the id of the IIIF Image API service
that serves the image. Append `/info.json` to read the sizes, tiles and formats the service
offers to the requesting user, and build IIIF image requests from that id.
The id is built from `sipi.externalBaseUrl`. It matches the `id` in Sipi's `info.json` only
if the reverse proxy in front of Sipi forwards the public host and scheme
(`X-Forwarded-Host` or `Host`, and `X-Forwarded-Proto`).
Knowing the id does not grant access to the image; access is checked on every request.
Placeholder file values, which have no stored image, carry no image service.

`knora-api:stillImageFileValueHasDimX` and `knora-api:stillImageFileValueHasDimY` are the
native size of the stored file, not the size served to a user, which may be smaller.
Take the sizes and region coordinates to request from `info.json`.

A `StillImageExternalFileValue` points at an image served by an external IIIF server.
It has no `stillImageFileValueHasIIIFImageService`: `knora-api:stillImageFileValueHasExternalUrl`
gives the image URL and `knora-api:stillImageFileValueHasIIIFBaseUrl` the server's base URL.
See the `knora-api` ontology for details.

## Authentication of Users with Sipi

File access is restricted to users who have the permission to view the resource that the file is attached to.
In order to check whether a user has the permission to view a resource, Sipi needs to know the user's identity.
The identity is provided by DSP-API in the form of a JWT token.
The token must be provided in the `Authorization` header of the request as a `Bearer` type token.

Any other method of authentication is deprecated.
