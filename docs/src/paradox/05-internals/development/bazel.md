<!---
Copyright © 2015-2019 the contributors (see Contributors.md).

This file is part of Knora.

Knora is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Knora is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
-->

# Bazel (Experimental)

The following section discusses on how to build and run tests for Knora-API
with [Bazel](https://bazel.build).

## Prerequisites

See the instruction for installing Bazel: https://docs.dasch.swiss/developers/

## Commands

Build `webapi`:

```
# build webapi
$ bazel build //webapi

# run api (webapi)
$ bazel run //:api

# run all tests
$ bazel test //...
```

## Build Structure

The Bazel build is defined in a number of files:
  - WORKSPACE - here are external dependencies defined
  - BUILD - there are a number of BUILD files throughout the directory structure
    where each represents a separate package responsible for everything underneath.
  - *.bzl - custom extensions loaded and used in BUILD files

For a more detailed discussion, please see the [Concepts and Terminology](https://docs.bazel.build/versions/master/build-ref.html)
section in the Bazel documentation.

### Directory Structure

```
WORKSPACE - external dependencies
.bazel.rc - config
|__ docs - build definitios and sources
|__ images - build definitions for container images
|__ knora-ontologies - build definitions and source
|__ salsah1 - build definitions and source
|__ sipi - build definitions and source
|__ third_party - build definitions for third party maven dependencies
|__ tools - build definitions 
   |__ build_rules - bazel config
   |__ buildstamp - stamping (generation of BuildInfo.scala)
|__ travis - travis secrets
|__ upgrade - build definitions and sources
|__ webapi - build definitions and sources
```
