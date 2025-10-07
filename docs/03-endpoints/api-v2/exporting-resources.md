# Exporting Resources

The DSP-API provides endpoints for exporting resource data from projects in various formats. This functionality is designed to support research workflows by allowing project members and administrators to extract data for external analysis while respecting access permissions and data governance policies.

## Resource Export Endpoint

### Export Resources by Class

Export resources of a specific class from a project.

```
POST /v2/resources/export
```

#### Request Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Content-Type` | Yes | `application/json` |
| `Authorization` | Yes | Bearer token for authentication |
| `X-Knora-Accept-Project` | Yes | Project IRI (e.g., `http://rdfh.ch/projects/0803`) |

#### Request Body

The request body is a JSON object with the following structure:

```json
{
  "resourceClass": "http://www.knora.org/ontology/0803/incunabula#book",
  "selectedProperties": [
    "http://www.knora.org/ontology/0803/incunabula#title",
    "http://www.knora.org/ontology/0803/incunabula#pubdate"
  ],
  "format": "CSV",
  "language": "en",
  "includeReferenceIris": true
}
```

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `resourceClass` | string | Yes | - | IRI of the resource class to export |
| `selectedProperties` | array of strings | No | `null` | Property IRIs to include in export. If not specified, all properties found on resources will be exported |
| `format` | string | No | `"CSV"` | Export format: `"CSV"` or `"JSON"` |
| `language` | string | No | User's language | Language preference for property labels |
| `includeReferenceIris` | boolean | No | `true` | Whether to include reference IRIs (Resource IRI, Resource Class, Project IRI) in CSV export |

#### Response

The response depends on the requested format:

**For CSV Export:**
- **Content-Type:** `text/csv`
- **Content-Disposition:** `attachment; filename="resources_export_<timestamp>.csv"`
- **Body:** CSV data with user-friendly column headers

**For JSON Export:**
- **Content-Type:** `application/json`
- **Content-Disposition:** `attachment; filename="resources_export_<timestamp>.json"`
- **Body:** JSON array of resource objects

#### Example Requests

**Basic CSV Export with all properties:**
```bash
curl -X POST \
  'https://api.dasch.swiss/v2/resources/export' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <token>' \
  -H 'X-Knora-Accept-Project: http://rdfh.ch/projects/0803' \
  -d '{
    "resourceClass": "http://www.knora.org/ontology/0803/incunabula#book"
  }'
```

**CSV Export with selected properties, no reference IRIs:**
```bash
curl -X POST \
  'https://api.dasch.swiss/v2/resources/export' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <token>' \
  -H 'X-Knora-Accept-Project: http://rdfh.ch/projects/0803' \
  -d '{
    "resourceClass": "http://www.knora.org/ontology/0803/incunabula#book",
    "selectedProperties": [
      "http://www.knora.org/ontology/0803/incunabula#title",
      "http://www.knora.org/ontology/0803/incunabula#pubdate"
    ],
    "includeReferenceIris": false
  }'
```

**JSON Export with German labels:**
```bash
curl -X POST \
  'https://api.dasch.swiss/v2/resources/export' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <token>' \
  -H 'X-Knora-Accept-Project: http://rdfh.ch/projects/0803' \
  -d '{
    "resourceClass": "http://www.knora.org/ontology/0803/incunabula#book",
    "format": "JSON",
    "language": "de"
  }'
```

#### Example CSV Response

When `includeReferenceIris` is `true` (default):
```csv
Resource IRI,Resource Class,Project IRI,Title,Publication Date
http://rdfh.ch/0803/resource1,http://www.knora.org/ontology/0803/incunabula#book,http://rdfh.ch/projects/0803,Liber chronicarum,1493
http://rdfh.ch/0803/resource2,http://www.knora.org/ontology/0803/incunabula#book,http://rdfh.ch/projects/0803,Cosmographia,1544
```

When `includeReferenceIris` is `false`:
```csv
Title,Publication Date
Liber chronicarum,1493
Cosmographia,1544
```

#### Example JSON Response

```json
[
  {
    "resourceIri": "http://rdfh.ch/0803/resource1",
    "resourceClass": "http://www.knora.org/ontology/0803/incunabula#book",
    "projectIri": "http://rdfh.ch/projects/0803",
    "properties": {
      "http://www.knora.org/ontology/0803/incunabula#title": "Liber chronicarum",
      "http://www.knora.org/ontology/0803/incunabula#pubdate": "1493"
    }
  },
  {
    "resourceIri": "http://rdfh.ch/0803/resource2",
    "resourceClass": "http://www.knora.org/ontology/0803/incunabula#book",
    "projectIri": "http://rdfh.ch/projects/0803",
    "properties": {
      "http://www.knora.org/ontology/0803/incunabula#title": "Cosmographia",
      "http://www.knora.org/ontology/0803/incunabula#pubdate": "1544"
    }
  }
]
```

## Permissions and Access Control

The export functionality respects DSP's permission system:

- **Authentication Required:** Users must be authenticated with a valid JWT token
- **Project Membership:** Users must be members of the project they're trying to export from
- **Resource Access:** Only resources that the user has permission to view will be included in the export
- **Property Filtering:** Property values are filtered based on user's access permissions

## Data Processing and Value Extraction

### Property Label Resolution

Property IRIs are automatically resolved to user-friendly labels with multi-language support:

1. **User Language:** First tries to use the user's preferred language
2. **Fallback Language:** Falls back to English if user language not available
3. **Any Available:** Uses any available label if neither user nor fallback language available
4. **Local Name:** Extracts local name from IRI as final fallback (e.g., `http://example.org/ontology#title` → `"title"`)

### Value Type Handling

The export service handles various Knora value types and converts them to human-readable strings:

| Value Type | Conversion |
|------------|------------|
| Text | Plain text content |
| Integer/Decimal | Numeric value |
| Boolean | `true`/`false` |
| Date | Year or year range (e.g., `"1493"` or `"1493 - 1495"`) |
| URI | URI string |
| Color | Color code |
| Hierarchical List | Label or IRI |
| File | Filename with dimensions |
| Link | Target resource IRI |

### Property Discovery

When no `selectedProperties` are specified, the system automatically discovers properties:

1. **From Resources:** Analyzes all loaded resources to find their properties
2. **Class-Based Fallback:** If resources don't have properties loaded, provides common properties based on the resource class
3. **Sorted Order:** Properties are returned in consistent, sorted order

## Error Handling

The API provides detailed error messages for various scenarios:

### Common Error Responses

**400 Bad Request:**
```json
{
  "error": "Invalid resource class IRI 'invalid-iri': Expected valid IRI"
}
```

**400 Bad Request - No Resources Found:**
```json
{
  "error": "No resources found for class 'http://example.org/Book' in project 'http://rdfh.ch/projects/0803'"
}
```

**400 Bad Request - Invalid Property:**
```json
{
  "error": "Invalid property IRI in selected properties: Expected valid property IRI"
}
```

**403 Forbidden:**
```json
{
  "error": "You are not a member of this project"
}
```

## Limitations and Scope

### Current Scope (v1)

- ✅ CSV and JSON export formats
- ✅ Resource class-based exports
- ✅ Property selection and auto-discovery
- ✅ User-friendly property labels
- ✅ Permission-filtered data
- ✅ Reference IRI inclusion/exclusion option

### Out of Scope

- ❌ Media assets and binary files (CSV/JSON only contains references)
- ❌ System-level metadata
- ❌ Real-time data synchronization
- ❌ Bulk exports across multiple projects
- ❌ Custom filtering beyond resource class

### Future Enhancements

The export system is designed to be extensible for future requirements:

- Email packaging for media assets
- Additional export formats
- Advanced filtering options
- Scheduled/batch exports
- External analytics platform integration

## Integration with DSP-App

The export functionality is designed to be integrated into the DSP-App frontend:

- **Class View Integration:** Export button available on resource class views
- **Property Selection UI:** Interactive property picker for customizing exports
- **Progress Feedback:** Support for progress indicators during export processing
- **Download Management:** Direct browser downloads for CSV/JSON, email delivery for assets