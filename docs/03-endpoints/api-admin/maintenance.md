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

The response does not wait for the run. There is no status endpoint: the progress and the result go to the log and to
the trace.

### Safety

- The backfill only adds `valueHasXml`. It never changes or removes an existing value, and it does not change
  `lastModificationDate`.
- The INSERT skips a value that gained `valueHasXml` after the run selected it, and a value that was erased.
- A run that stops for any reason can start again. The next run selects only the values that still lack the XML.
- An API instance runs one backfill at a time. Two instances can run at the same time without risk, because each
  INSERT is conditional.

### Configuration

The run selects all candidate values of the project one time. Then it processes them in batches: for each batch, it
loads the standoff, renders the XML and writes it. The next batch starts no earlier than one `batch-interval` after the
start of the previous batch.

| Key under `app.value-has-xml-backfill` | Environment variable | Default |
| --- | --- | --- |
| `batch-size`: values per batch | `KNORA_WEBAPI_VALUE_HAS_XML_BACKFILL_BATCH_SIZE` | `50` |
| `batch-interval`: minimum gap between batch starts | `KNORA_WEBAPI_VALUE_HAS_XML_BACKFILL_BATCH_INTERVAL` | `1 second` |
| `max-failures`: failed values at which the run stops | `KNORA_WEBAPI_VALUE_HAS_XML_BACKFILL_MAX_FAILURES` | `1000` |

The candidate query scans the project data graph one time per run. The run keeps the candidate IRIs in memory, about
100 bytes for each IRI. If the candidate query fails with a timeout, increase `app.triplestore.maintenance-timeout`
(default `120 seconds`).

### Log lines

Every line carries the annotation `shortcode`. The counts are cumulative: `found` is the number of values processed,
`rendered` the number of values sent to the INSERT, and `failed` the number of values that could not be rendered.

| Level | Line |
| --- | --- |
| `INFO` | `valueHasXml backfill started (batchSize=50, candidates=1234)`, after the candidate query |
| `INFO` | `valueHasXml backfill progress: found=50 rendered=49 failed=1 stoppedEarly=false`, one per batch |
| `WARN` | `valueHasXml backfill: <value IRI> failed: <error class or reason>`, one per failed value |
| `INFO` | `valueHasXml backfill finished: found=… rendered=… failed=0 stoppedEarly=false stop=NoCandidates` |
| `ERROR` | The same `finished` line when `failed > 0` |
| `ERROR` | `valueHasXml backfill failed with <error class>: <partial counts>`, when a triplestore call fails |
| `WARN` | `valueHasXml backfill interrupted: <partial counts>` |

When the candidate query fails, the `failed with` line is the only line of the run.

The log lines never contain text, XML or error messages, because standoff and mapping errors can repeat user text.

### When a run ends

- `stop=NoCandidates`, `failed=0`: the project is complete. A second run reports `found=0`.
- `stop=NoCandidates`, `failed > 0`: every other value has its XML. Look at the `WARN` lines for the failed values. A
  second run reports `found` equal to this `failed`.
- `stop=MaxFailures` (`stoppedEarly=true`): too many values failed. Find the cause in the `WARN` lines, then run
  again.
- `failed with …`: a triplestore call failed, for example a timeout. Run again when the triplestore is healthy.
- `409`, or the API restarted during a run: wait until the running backfill ends, or start it again after the
  restart. A restart stops a run without the `interrupted` line.

### Trace

The run is one span `value_has_xml_backfill`, with one child span `value_has_xml_backfill.select` for the candidate
query. Its parent is the HTTP span of the `POST`, which ends before the run. The run span carries these attributes,
all prefixed `value_has_xml_backfill.`:

- `shortcode`, from the start of the run.
- `found`, `rendered`, `failed` and `stopped_early`, on every end of the run. After a failure or an interruption, they
  are the partial counts.
- `stop_reason` (`NoCandidates` or `MaxFailures`), when the run ends normally.
- `exit_reason=interrupted`, when the run is interrupted.

Each batch is a separate trace: a root span `value_has_xml_backfill.batch` with a span link to the run span, and the
stage spans `.load`, `.render` and `.write`. A batch span carries `shortcode` and the `found`, `rendered` and `failed`
counts of that batch. Thus a trace does not grow with the size of the project.

A run that ends with `failed > 0` keeps the span status `UNSET`. Find such runs with the `failed` attribute or with the
`ERROR` `finished` line, not with the span status.

### Re-run after a renderer change

The stored XML is the output of `StandoffTagUtilV2.convertStandoffTagV2ToXML`. A change to that output does not
update stored values. After such a change, remove the stored `valueHasXml` of the affected values and run the
backfill again.
