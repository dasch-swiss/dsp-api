# Files Endpoint

## Endpoint Overview

| Scope  | Route                                 | Operation | Description                                                                   |
| ------ | ------------------------------------- | --------- | ----------------------------------------------------------------------------- |
| assets | `/admin/files/{shortcode}/{filename}` | `GET`     | [get the access decision for an asset](#get-the-access-decision-for-an-asset) |

## Get the Access Decision for an Asset

Permissions: No permissions required. A bearer token identifies the caller; without one the caller is anonymous.

Request definition: `GET /admin/files/{shortcode}/{filename}`

Description: Returns what the caller may receive of a stored asset, given its internal filename.

The `{shortcode}` segment is not authoritative and does not affect the result: an internal filename is a
globally-unique asset ID, so the asset is identified by the filename alone. The segment is retained only for
URL compatibility.

Example request:

```bash
curl --request GET --url http://localhost:3333/admin/files/0803/incunabula_0000003328.jp2
```

Example response:

```json
{
  "derivative": "clamped",
  "original": "withhold",
  "size": "!128,128"
}
```

Errors:

- `404 Not Found` if no file value carries that filename.

### Two channels

The response carries two independent channels, consumed by two different services:

| Field        | Answers                                                      | Read by                |
| ------------ | ------------------------------------------------------------ | ---------------------- |
| `original`   | what the caller may receive of the original file as uploaded | dsp-ingest             |
| `derivative` | what the caller may receive of the derivative                | Sipi's pre-flight hook |

**The two channels must not be collapsed: neither consumer may read the other's field.** They answer different
questions, and neither is derivable from the other. An asset served as a size-capped still image, for example,
answers `clamped` on one channel and `withhold` on the other.

### `original`

| Value      | Meaning                                      |
| ---------- | -------------------------------------------- |
| `grant`    | the caller may receive the original file     |
| `withhold` | the caller may not receive the original file |

### `derivative`

| Value     | Meaning                                                                      |
| --------- | ---------------------------------------------------------------------------- |
| `full`    | served without restriction                                                   |
| `clamped` | a raster still image served through the IIIF pipeline, capped or watermarked |
| `stream`  | consumable in place, never handed over as a file                             |
| `denied`  | not served at all                                                            |

`clamped` is accompanied by **exactly one** of:

- `size`: an IIIF size string, for example `!128,128` or `pct:50`
- `watermark`: a boolean, always `true`

No other `derivative` value carries either field.

The clamp comes from the project's
[restricted view settings](projects.md#get-restricted-view-settings); a project that has none configured
inherits the platform default `!128,128`.
