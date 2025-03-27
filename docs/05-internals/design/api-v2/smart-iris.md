# Smart IRIs

## Usage

The `SmartIri` trait can be used to parse and validate IRIs, and in
particular for converting [Knora type IRIs](../../../03-endpoints/api-v2/knora-iris.md)
between internal and external schemas. It validates each IRI it parses. To use it,
import the following:

```scala
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.IriConversions._
```

Ensure that an implicit instance of `StringFormatter` is in scope:

```scala
import org.knora.webapi.messages.StringFormatter
implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
```

Then, if you have a string representing an IRI, you can can convert
it to a `SmartIri` like this:

```scala
val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri
````

If the IRI came from a request, use this method to throw a specific
exception if the IRI is invalid:

```scala
val propertyIri: SmartIri = propertyIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid property IRI: <$propertyIriStr>"))
```

You can then use methods such as `SmartIri.isKnoraApiV2EntityIri` and
`SmartIri.getProjectCode` to obtain information about the IRI. To
convert it to another schema, call `SmartIri.toOntologySchema`.
Converting a non-Knora IRI returns the same IRI.

If the IRI represents a Knora internal value class such as
`knora-base:TextValue`, converting it to the `ApiV2Simple` schema will
return the corresponding simplified type, such as `xsd:string`. But this
conversion is not performed in the other direction (external to
internal), since this would require knowledge of the context in which
the IRI is being used.

The performance penalty for using a `SmartIri` instead of a string is
very small. Instances are automatically cached once they are
constructed.

There is no advantage to using `SmartIri` for data IRIs, since they are
not schema-specific (and are not cached). If a data IRI has been
received from a client request, it is better just to validate it using
`StringFormatter.validateAndEscapeIri`, and represent it as an
`org.knora.webapi.IRI` (an alias for `String`).

## Implementation

The smart IRI implementation, `SmartIriImpl`, is nested in the
`StringFormatter` class, because it uses Knora's
hostname, which isn't available until the Akka `ActorSystem` has started.
However, this means that the Scala type of a `SmartIriImpl` instance is
dependent on the instance of `StringFormatter` that constructed it.
Therefore, instances of `SmartIriImpl` created by different instances of
`StringFormatter` can't be compared directly.

There are in fact two instances of `StringFormatter`:

- one returned by `StringFormatter.getGeneralInstance`, which is
  available after Akka has started and has the API server's hostname
  (and can therefore provide `SmartIri` instances capable of parsing
  IRIs containing that hostname). This instance is used throughout the
  DSP-API server.
- one returned by `StringFormatter.getInstanceForConstantOntologies`,
  which is available before Akka has started, and is used only by the
  hard-coded constant `knora-api` ontologies (see
  [Generation of Ontologies in External Schemas](ontology-schemas.md#generation-of-ontologies-in-external-schemas)).

This is the reason for the existence of the `SmartIri` trait, which is a
top-level definition and has its own `equals` and `hashCode` methods.
Instances of `SmartIri` can thus be compared (e.g. to use them as unique
keys in collections), regardless of which instance of `StringFormatter`
created them.
