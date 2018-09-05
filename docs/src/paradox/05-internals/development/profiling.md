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

# Profiling Knora

To run Knora with profiling, we first need to build the application. Please run the following from the `webapi` folder:

```
$ sbt stage
```

## Profiling with [YourKit](http://yourkit.com):

Start `webapi` from the `knora/webapi/target/universal/stage` directory with the following command:

```
$ ./bin/webapi -J-agentpath:/Applications/YourKit-Java-Profiler-2018.04.app/Contents/Resources/bin/mac/libyjpagent.jnilib -J-Xms1G -J-Xmx1G
```


Now start the YourKit Profiler and connect to the `Main` process.