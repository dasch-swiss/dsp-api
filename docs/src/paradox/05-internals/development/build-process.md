<!---
Copyright © 2015-2018 the contributors (see Contributors.md).

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

# Build Process

@@toc

## SBT Build Configuration

Knora's complete build definition is defined in `KnoraBuild.sbt`:

@@snip[KnoraBuild.sbt](../../../../../KnoraBuild.sbt) { }


## Building Deployment Packages

### Webapi

### Salsah1


## Building Docker Images

### Webapi

### Salsah1


## Building Documentation

The complete Knora documentation site is built by invoking the following command:

```
$ sbt docs/makeSite
```

The generated documentation can be found under `docs/target/site/`. To preview your
generated site, you can run the following command:

```
$ sbt docs/previewSite
```

which launches a static web server, or:

```
$ sbt docs/previewAuto
```

which launches a dynamic server updating its content at each modification in your source files. Both launch the server
on port `4000` and attempt to connect your browser to `http://localhost:4000/`.

## 3 Publishing

To publish the documentation, you need to be on the `develop` branch inside the `docs` folder and then execute the following
command:

```
$ sbt docs/ghpagesPushSite
```

This command will build all documentation and publish it to the `gh-pages` branch.