# Third-Party Dependencies

Third party libraries are managed by SBT.

## Defining Dependencies in `Dependencies.scala`

Within the `build.sbt` file, the `Dependencies` package is referenced, which is located in `project/Dependencies.scala`.
All third party dependencies need to be declared there.

### Referencing a third party library

There is an object `Dependencies` where each library should be declared in a `val`.

```scala
val akkaHttpCors = "ch.megard" %% "akka-http-cors" % "1.0.0"
```

The first string corresponds to the group/organization in the library's maven artefact,
the second string corresponds to the artefact ID and the third string defines the version.

The strings are combined with `%` or `%%` operators, the latter fixing the dependency to the specified scala-version.

It is also possible to use variables in these definitions, e.g. if multiple dependencies share a version number:

```scala
val ZioVersion = "2.0.0-RC2"
val zio = "dev.zio" %% "zio" % ZioVersion
val zioTest = "dev.zio" %% "zio-test" % ZioVersion
```

### Assigning the dependencies to a specific subproject

For each SBT project, there is one `Seq` in the `Dependencies` object.
In order to make use of the declared dependencies, they must be referred to in the `Seq` of the respective subproject.

```scala
val webapiLibraryDependencies = Seq(
    akkaActor,
    akkaHttp,
    akkaSlf4j % Runtime,
    akkaHttpTestkit % Test,
    ...
)
```

By default, the dependencies will be scoped to compile time. But it's possible to override this to `Runtime` or `Test`.


## Docker Image Versions

The required Docker image versions of `Sipi` and `Fuseki` are also defined in the `Dependencies.scala` file.
