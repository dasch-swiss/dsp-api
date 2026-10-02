# Maintenance Endpoint

Maintenance actions run in the background on the API instance that receives the request. All of them require a
`SystemAdmin`.

## Backfill `valueHasXml`

`POST /admin/maintenance/projects/{shortcode}/backfill-value-has-xml` writes `knora-base:valueHasXml` on every
formatted text value of one project that has standoff but no stored XML. The scope includes the current version, all
previous versions and deleted values in the project data graph. The XML is the same canonical XML that the API writes
when a client creates or updates a formatted text value.

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
- The INSERT skips a value that gained `valueHasXml` after the batch was selected, and a value that was erased.
- A run that stops for any reason can start again. The next run selects only the values that still lack the XML.
- An API instance runs one backfill at a time. Two instances can run at the same time without risk, because each
  INSERT is conditional.

### Configuration

The run selects a batch, loads its standoff, renders the XML and writes it. The next batch starts no earlier than one
`batch-interval` after the start of the previous batch.

| Key under `app.value-has-xml-backfill` | Environment variable | Default |
| --- | --- | --- |
| `batch-size`: values per batch | `KNORA_WEBAPI_VALUE_HAS_XML_BACKFILL_BATCH_SIZE` | `50` |
| `batch-interval`: minimum gap between batch starts | `KNORA_WEBAPI_VALUE_HAS_XML_BACKFILL_BATCH_INTERVAL` | `1 second` |
| `max-failures`: failed values at which the run stops | `KNORA_WEBAPI_VALUE_HAS_XML_BACKFILL_MAX_FAILURES` | `1000` |

Each candidate query scans the project data graph. For a large project, increase `batch-size`, not the request rate.

### Log lines

Every line carries the annotation `shortcode`. The counts are cumulative: `found` is the number of values selected,
`rendered` the number of values sent to the INSERT, and `failed` the number of values that could not be rendered.

| Level | Line |
| --- | --- |
| `INFO` | `valueHasXml backfill started (batchSize=50)` |
| `INFO` | `valueHasXml backfill progress: found=50 rendered=49 failed=1 stoppedEarly=false`, one per batch |
| `WARN` | `valueHasXml backfill: <value IRI> failed: <error class or reason>`, one per failed value |
| `INFO` | `valueHasXml backfill finished: found=… rendered=… failed=0 stoppedEarly=false stop=NoCandidates` |
| `ERROR` | The same `finished` line when `failed > 0` |
| `ERROR` | `valueHasXml backfill failed with <error class>: <partial counts>`, when a triplestore call fails |
| `WARN` | `valueHasXml backfill interrupted: <partial counts>` |

The log lines never contain text, XML or error messages, because standoff and mapping errors can repeat user text.

### When a run ends

- `stop=NoCandidates`, `failed=0`: the project is complete. A second run reports `found=0`.
- `stop=NoCandidates`, `failed > 0`: every other value has its XML. Look at the `WARN` lines for the failed values. A
  second run reports `found` equal to this `failed`.
- `stop=MaxFailures` (`stoppedEarly=true`): too many values failed. Find the cause in the `WARN` lines, then run
  again.
- `stop=Stalled` (`stoppedEarly=true`): the INSERT wrote nothing for a batch, so the same values came back. Check the
  triplestore, then run again.
- `failed with …`: a triplestore call failed, for example a timeout. Run again when the triplestore is healthy.
- `409`, or the API restarted during a run: wait until the running backfill ends, or start it again after the
  restart. A restart stops a run without the `interrupted` line.

### Trace

The run is one span `value_has_xml_backfill`, with the stage spans `value_has_xml_backfill.select`, `.load`,
`.render` and `.write` per batch. The root span carries the shortcode and the final counts as attributes. Its
parent is the HTTP span of the `POST`, which ends before the run.

### Re-run after a renderer change

The stored XML is the output of `StandoffTagUtilV2.convertStandoffTagV2ToXML`. A change to that output does not
update stored values. After such a change, remove the stored `valueHasXml` of the affected values and run the
backfill again.
