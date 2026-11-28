---
title: "A defect in tapir server logic skips the headers an endpoint interceptor adds"
date: 2026-11-28
category: runtime-errors
component: webapi-app
module: modules/webapi/src/main/scala/org/knora/webapi/core/DspApiServer.scala
problem_type: runtime-error
severity: high
symptoms:
  - "A client streaming a large upload hangs until its own read timeout after a 500"
  - "The 500 for a defect has no `Connection: close`; a typed failure on the same route has it"
root_cause: "ztapir turns a defect into a failed Task, and tapir's exception interceptor, outside the endpoint interceptors, builds that 500 without the response mapping of onDecodeSuccess."
tags: [tapir, zio-http, interceptor, exception-handler, connection-close, streaming]
issue: "DEV-8106"
---

# A defect in tapir server logic skips the headers an endpoint interceptor adds

## Problem

A route that streams its request body (`autoRead=false` in zio-http) must answer with `Connection: close` when it
fails before it reads the body, or the client keeps sending and waits for its read timeout. The rule lived in an
endpoint interceptor's `onDecodeSuccess`, which maps the response of the delegate. A typed failure got the header. A
defect did not.

## Root Cause

ztapir turns a defect (any `Throwable`) into a failed `Task`. The exception interceptor sits outside the endpoint
interceptors and builds the 500 itself, so `onDecodeSuccess` never sees a response to map.

## Solution

Install an exception handler in the server options that is tapir's `DefaultExceptionHandler` (same status and
body) plus `Connection: close` under the same rule, and keep the rule in one function both paths call
(`DspApiServer.closesOnUnreadBodyExceptionHandler`, `closeConnectionIfBodyUnread`).

Catching the failure inside `onDecodeSuccess` and building the 500 there was rejected: it duplicates the exception
handler's response and bypasses tapir's server log of the exception.

## Prevention

- Test both a typed failure and a defect on a real server with a body larger than the aggregation threshold, over a
  raw socket (`EarlyErrorStreamingSpec`). A stub interpreter does not run the exception interceptor the same way.
- Anything an interceptor must add to every response also needs the exception path.
