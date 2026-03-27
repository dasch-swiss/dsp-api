# Pre-Migration Cleanup: Root User References

## Problem

Some projects have resources where `knora-base:attachedToUser` references the `root` user (username `root`).
The root user must never be imported into another DSP instance. The project migration import rejects any
admin.nq containing the root user.

## Detection

When exporting a project, the export service logs a warning if the root user is referenced:

```
WARNING: Export includes root user '<root-iri>'. Resources referencing root require pre-migration cleanup before import.
```

## Cleanup Procedure

For each affected project, run the following SPARQL UPDATE to reassign resources from the root user to a
designated project user. Replace the placeholders with actual values.

```sparql
PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>

DELETE {
  GRAPH ?g {
    ?resource knora-base:attachedToUser <ROOT_USER_IRI> .
  }
}
INSERT {
  GRAPH ?g {
    ?resource knora-base:attachedToUser <DESIGNATED_USER_IRI> .
  }
}
WHERE {
  GRAPH ?g {
    ?resource knora-base:attachedToUser <ROOT_USER_IRI> .
  }
  FILTER(STRSTARTS(STR(?g), "http://www.knora.org/data/SHORTCODE/"))
}
```

Replace:

- `ROOT_USER_IRI` with the root user's IRI (e.g., `http://rdfh.ch/users/root`)
- `DESIGNATED_USER_IRI` with a project member's IRI who should be the new owner
- `SHORTCODE` with the project's shortcode (e.g., `0110`)

## Notes

- This is a one-time manual step per project.
- Choose a designated user who is an active project member.
- The cleanup modifies the source instance's data — run it before exporting.
- Built-in users (`SystemUser`, `AnonymousUser`) do not need cleanup. Their triples are automatically stripped during import.
