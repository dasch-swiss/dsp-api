# ADR-0006 Replace Akka with Pekko


## Status

Accepted

## Context

On 7. September 2022 Lightbend announced a 
[license change](https://www.lightbend.com/blog/why-we-are-changing-the-license-for-akka) for the Akka project, 
the TL;DR being that you will need a commercial license to use future versions of Akka (2.7+) in production 
if you exceed a certain revenue threshold.

*For now*, we have staid on Akka 2.6, the current latest version that is still available under the original license. 
Historically Akka has been incredibly stable, and combined with our limited use of features, 
we did not expect this to be a problem.

However, the [last update of Akka 2.6 is announced to be in September 2023](https://www.lightbend.com/akka/license-faq).

> **Will critical vulnerabilities and bugs be patched in 2.6.x?**
> Yes, critical security updates and critical bugs will be patched in Akka v2.6.x 
  under the current Apache 2 license until September of 2023.

As a result, we will not receive further updates and we will never get support for Scala 3 for Akka.

## Proposal

[Apache Pekko](https://pekko.apache.org/) is based on the latest version of Akka in the v2.6.x series. 
It is currently an incubator project in the ASF. 
All Akka modules currently in use in the dsp-api are already released and ported to 
[pekko](https://pekko.apache.org/modules.html): 
[https://mvnrepository.com/artifact/org.apache.pekko](https://mvnrepository.com/artifact/org.apache.pekko)

The latest stable version [1.0.1](https://pekko.apache.org/docs/pekko/current/release-notes/index.html#1-0-1) 
is compatible with Akka v2.6.x series and meant to be a plug in replacement.

Scala 3.3.0 is the minimum Scala 3 version supported. Scala 2.12 and 2.13 are still supported.

[The migration guide](https://pekko.apache.org/docs/pekko/current/project/migration-guides.html)

Our current migration to another http server implementation is currently on hold, 
but we might want to switch to Pekko so that we could receive security updates and bugfixes.

The proof of concept implementation has been shared in the pull request 
[here](https://github.com/dasch-swiss/dsp-api/pull/2848), 
allowing for further testing and validation of the proposed switch to Pekko.

## Decision

We replace Akka and Akka/Http with Apache Pekko.
