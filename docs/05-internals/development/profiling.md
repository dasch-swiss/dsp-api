<!---
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Profiling Knora

To run Knora with profiling, we first need to build the application. Please run the following from the top `knora` folder:

```
$ sbt webapi/stage
```

## Profiling with [YourKit](http://yourkit.com):

Start `webapi` from the `knora/webapi/target/universal/stage` directory with the following command:

```
$ ./bin/webapi -J-agentpath:/Applications/YourKit-Java-Profiler-2018.04.app/Contents/Resources/bin/mac/libyjpagent.jnilib -J-Xms1G -J-Xmx1G
```


Now start the YourKit Profiler and connect to the `Main` process.
