# Maintenance Endpoint

Every maintenance endpoint requires a `SystemAdmin`. This page describes the endpoints that start background
maintenance jobs.

## Backfill `valueHasXml`

`POST /admin/maintenance/projects/{shortcode}/backfill-value-has-xml` writes `knora-base:valueHasXml` on every
formatted text value of one project that has standoff but no stored XML. The scope includes the current version, all
previous versions and deleted values in the project data graph. The XML is the same canonical XML that the API writes
when a client creates or updates a formatted text value. The backfill runs in the background on the API instance that
receives the request.

Run it once per project after an upgrade, and again after a project is copied in as raw RDF. The request has no body.

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  https://api.example.org/admin/maintenance/projects/0001/backfill-value-has-xml
```

| Situation | Status |
| --- | --- |
| The backfill started | `202` |
| Not a `SystemAdmin` | `403` |
| No project has this shortcode | `404` |
| A backfill already runs on this API instance, for any project | `409` |

The response does not wait for the run. There is no status endpoint: the progress and the result go to the log.

### Safety

- The backfill only adds `valueHasXml`. It never changes or removes an existing value, and it does not change
  `lastModificationDate`.
- The INSERT skips a value that gained `valueHasXml` after the run selected it, and a value that was erased.
- A run that stops for any reason can start again. The next run selects only the values that still lack the XML.
- An API instance runs one backfill at a time. Two instances can run at the same time without risk, because each
  INSERT is conditional.

### How a run works

The run selects all candidate values of the project one time and keeps their IRIs in memory, about 100 bytes for each
IRI. Then it processes them in batches of 50: for each batch, it loads the standoff, renders the XML and writes it. The
run pauses for one second between batches. If the candidate query fails with a timeout, increase
`app.triplestore.maintenance-timeout` (default `120 seconds`).

### Log lines

Every line carries the annotation `shortcode`. The counts are cumulative: `found` is the number of values processed,
`rendered` the number of values sent to the INSERT, and `failed` the number of values that could not be rendered.

| Level | Line |
| --- | --- |
| `INFO` | `valueHasXml backfill started (candidates=1234)`, after the candidate query |
| `INFO` | `valueHasXml backfill progress: found=50 rendered=49 failed=1`, one per batch |
| `WARN` | `valueHasXml backfill: <value IRI> failed: <error class or reason>`, one per failed value |
| `INFO` | `valueHasXml backfill finished: found=… rendered=… failed=0` |
| `ERROR` | The same `finished` line when `failed > 0` |
| `ERROR` | `valueHasXml backfill failed with <error class>`, when a triplestore call fails or the run is interrupted |

The log lines never contain text, XML or error messages, because standoff and mapping errors can repeat user text.

### When a run ends

- `finished` with `failed=0`: the project is complete. A second run reports `found=0`.
- `finished` with `failed > 0`: every other value has its XML. Look at the `WARN` lines for the failed values. A second
  run reports `found` equal to this `failed`.
- `failed with …`: a triplestore call failed, for example a timeout. The last `progress` line shows the partial
  counts. Run again when the triplestore is healthy.
- `409`, or the API restarted during a run: wait until the running backfill ends, or start it again after the
  restart. A restart stops a run without a log line.

### Re-run after a renderer change

The stored XML is the output of `StandoffTagUtilV2.convertStandoffTagV2ToXML`. A change to that output does not
update stored values. After such a change, remove the stored `valueHasXml` of the affected values and run the
backfill again.
