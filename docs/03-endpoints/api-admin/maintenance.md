# Maintenance Endpoint

Every maintenance endpoint requires a `SystemAdmin`. This page describes the endpoints that start background
maintenance jobs.

## Backfill `valueHasXml`

`POST /admin/maintenance/backfill-value-has-xml` writes `knora-base:valueHasXml` on every formatted text value that
has standoff but no stored XML, in every project on the server. The scope includes the current version, all previous
versions and deleted values in each project data graph. The XML is the same canonical XML that the API writes
when a client creates or updates a formatted text value. The backfill runs in the background on the API instance that
receives the request.

Run it once after an upgrade, and again after a project is copied in as raw RDF. The request has no body.

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  https://api.example.org/admin/maintenance/backfill-value-has-xml
```

| Situation | Status |
| --- | --- |
| The backfill started | `202` |
| Not a `SystemAdmin` | `403` |
| A backfill already runs on this API instance | `409` |

The response does not wait for the run. There is no status endpoint: the progress and the result go to the log.

### Safety

- The backfill only adds `valueHasXml`. It never changes or removes an existing value, and it does not change
  `lastModificationDate`.
- The INSERT skips a value that gained `valueHasXml` after the run selected it, and a value that was erased.
- A run that stops for any reason can start again. The next run selects only the values that still lack the XML.
- An API instance runs one backfill at a time. Two instances can run at the same time without risk, because each
  INSERT is conditional.

### How a run works

The run processes the projects one at a time. For each project, it selects all candidate values one time and keeps
their IRIs in memory, about 100 bytes for each IRI. A project without candidates costs only this query. Then it processes them in batches of 50: for each batch, it loads the standoff, renders the XML and writes it. The
run pauses for one second between batches. If the candidate query fails with a timeout, increase
`app.triplestore.maintenance-timeout` (default `120 seconds`).

### Log lines

Every line of a project carries the annotation `shortcode`. The counts of a project are cumulative: `found` is the number of values processed,
`rendered` the number of values sent to the INSERT, and `failed` the number of values that could not be rendered.

| Level | Line |
| --- | --- |
| `INFO` | `valueHasXml backfill started (candidates=1234)`, after the candidate query |
| `INFO` | `valueHasXml backfill progress: found=50 rendered=49 failed=1`, one per batch |
| `WARN` | `valueHasXml backfill: <value IRI> failed: <error class or reason>`, one per failed value |
| `INFO` | `valueHasXml backfill finished: found=… rendered=… failed=0` |
| `ERROR` | The same `finished` line when `failed > 0` |
| `ERROR` | `valueHasXml backfill failed with <error class>`, when a triplestore call fails or the run is interrupted |
| `INFO` | `valueHasXml backfill of all projects finished: projects=70 projectsFailed=0 found=… rendered=… failed=0` |
| `ERROR` | The same summary line when `projectsFailed > 0` or `failed > 0` |

The log lines never contain text, XML or error messages, because standoff and mapping errors can repeat user text.

### When a run ends

The summary line sums the counts of all projects. `projectsFailed` is the number of projects that logged
`failed with …`. The lines below describe the end of one project.

- `finished` with `failed=0`: the project is complete. A second run reports `found=0`.
- `finished` with `failed > 0`: every other value has its XML. Look at the `WARN` lines for the failed values. A second
  run reports `found` equal to this `failed`.
- `failed with …`: a triplestore call failed, for example a timeout. The last `progress` line shows the partial
  counts. The run continues with the next project. Run again when the triplestore is healthy.
- `409`, or the API restarted during a run: wait until the running backfill ends, or start it again after the
  restart. A restart stops the run without a log line, and the run does not continue with the next project.

### Re-run after a renderer change

The stored XML is the output of `StandoffTagUtilV2.convertStandoffTagV2ToXML`. A change to that output does not
update stored values. After such a change, remove the stored `valueHasXml` of the affected values and run the
backfill again.

## Check standoff link reference counts

`POST /admin/maintenance/check-standoff-link-ref-counts` compares the stored `knora-base:valueHasRefCount` of every
standoff link value with the number of current text values that link to the target. The check runs on every project of the
server, in the background, on the API instance that receives the request. It only logs. It never writes. The request has
no body.

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  https://api.example.org/admin/maintenance/check-standoff-link-ref-counts
```

| Situation | Status |
| --- | --- |
| The check started | `202` |
| Not a `SystemAdmin` | `403` |
| A check or a repair already runs on this API instance | `409` |

The response does not wait for the run. There is no status endpoint: the result goes to the log.

### Log lines of the check

Every line of a project carries the annotation `shortcode`.

| Level | Line |
| --- | --- |
| `INFO` | `standoff link reference count check requested: user=<user IRI>`, written by the request, without annotation |
| `INFO` | `standoff link reference count check finished: mismatches=0` |
| `WARN` | `standoff link reference count check finished: mismatches=3`, followed by one mismatch line per row |
| `WARN` | `standoff link reference count mismatch: source=<resource IRI> target=<resource IRI> stored=<count or none> expected=<count> directLink=<true or false>` |
| `ERROR` | `standoff link reference count check failed with <error class>`, when a triplestore call fails |
| `INFO` | `standoff link reference count check of all projects finished: projects=70 projectsFailed=0 mismatches=0` |
| `WARN` | The same summary line when `mismatches > 0` |
| `ERROR` | The same summary line when `projectsFailed > 0` |

A project whose check fails does not stop the next project.

## Repair standoff link reference counts

`POST /admin/maintenance/repair-standoff-link-ref-counts` repairs the mismatches that the check reports, in one project.
The repair runs in the background on the API instance that receives the request. It handles one mismatch at a time. It
sets the stored count to the expected count, and it inserts or deletes the direct link where the count requires it.

The body is JSON.

| Field | Type | Meaning |
| --- | --- | --- |
| `projectShortcode` | string, required | The project to repair |
| `dryRun` | boolean, optional | Default `true`. A dry run reads and logs the planned repairs. It writes nothing. |

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"projectShortcode": "0001", "dryRun": true}' \
  https://api.example.org/admin/maintenance/repair-standoff-link-ref-counts
```

| Situation | Status |
| --- | --- |
| The repair started | `202` |
| No body, a missing or invalid `projectShortcode`, or an unknown project | `400` |
| Not a `SystemAdmin` | `403` |
| A check or a repair already runs on this API instance | `409` |

The check and the repair share one guard. An API instance runs one of them at a time.

### Safety of the repair

- A real repair plans and writes each row under the per-resource lock of the source, the lock that value writes take.
- A real repair writes a row only if the stored count still equals the count that the plan used and the source is
  still a live resource. Otherwise it skips the row as `changed since the check`. A deleted source is logged this way.
- The repair leaves the `lastModificationDate` of the source unchanged.
- A deleted target that still exists is repaired. Only a missing target is skipped, as `TargetMissing`.
- The repair assumes one current link value per source and target, as the check does.
- If the confirming query after the update fails, the row is logged as `failed`, although the update may have been
  written. Run the check again to see the state.
- A mismatch that the repair cannot resolve is skipped with a reason: `TargetMissing`, `StoredCountMissing` or
  `NothingToRepair`.
- A row that fails does not stop the next row.

### Log lines of the repair

Every line of a project carries the annotation `shortcode`. Each request writes one audit line before the run starts,
naming the requesting administrator.

| Level | Line |
| --- | --- |
| `INFO` | `standoff link reference count repair requested: user=<user IRI> shortcode=<shortcode> dryRun=<true or false>` |
| `INFO` | `standoff link reference count repair planned: source=… target=… oldCount=<count or none> newCount=… directLink=<insert, delete or none>`, one per row of a dry run |
| `INFO` | `standoff link reference count repair applied: source=… target=… oldCount=… newCount=… directLink=…`, one per row of a real run |
| `WARN` | `standoff link reference count repair skipped: source=… target=… reason=<reason>` |
| `WARN` | `standoff link reference count repair skipped: changed since the check: source=… target=…` |
| `ERROR` | `standoff link reference count repair failed: source=… target=… error=<error class>` |
| `INFO` | `standoff link reference count repair remaining mismatches=<n>`, a real run only, after a new check |
| `INFO` | `standoff link reference count repair finished: dryRun=… planned=… applied=… skipped=…[ (<reason>=<n>, …)] stale=… failed=… remaining=<n or n/a>` |
| `ERROR` | `standoff link reference count repair failed with <error class>`, when a triplestore call fails |

### Logs hold no text content

The check and the repair log resource IRIs, counts and error classes. They never log text, XML or error messages,
because these can repeat user text.

### Rollout order

1. Finish the `valueHasXml` backfill on all projects. No project may log `failed > 0`.
2. Run the check on stage and on prod. Read the mismatch lines.
3. Run a dry-run repair for each project with mismatches. Review the planned lines and the `skipped` counts.
4. Run the real repair for the project, with `"dryRun": false`.
5. Run the check again. Expect `mismatches=0`, except for the skipped rows.
6. Turn on the [`serve-stored-xml`](../../04-publishing-deployment/configuration.md#serve-stored-xml) flag on stage.
   Compare a sample of 20 resources with formatted text, with the flag off and on, after canonicalization.
7. After one week of edits on stage, run the check again. A new mismatch blocks prod: turn the flag off on stage.
8. Turn the flag on for prod. Run the check again after one week, with the same rule.
