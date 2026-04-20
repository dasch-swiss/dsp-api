# Value Types

A Value Object is an object that measures, quantifies, or describes a concept in your domain model. 
Validated, type-safe wrappers for primitive values exist. 
For example `StringValue` guarantees that its content passed validation at construction time, 
so downstream code can use it without re-checking.
Use value types to make the domain model type-safe.

## Core hierarchy

```text
Value[A]            — base trait, provides `def value: A` and `toString` delegating to `value`
├── StringValue     — type alias for Value[String]
├── IntValue        — type alias for Value[Int]
└── BooleanValue    — type alias for Value[Boolean]
```

Defined in `webapi/src/main/scala/org/knora/webapi/slice/common/ValueTypes.scala`.

## Defining a StringValue

A minimal definition has two parts — a case class with a private constructor and a
companion extending `StringValueCompanion`:

```scala
final case class Shortcode private (override val value: String) extends StringValue

object Shortcode extends StringValueCompanion[Shortcode] {
  private val shortcodeRegex: Regex = "^\\p{XDigit}{4}$".r

  def from(value: String): Either[String, Shortcode] = value match {
    case v if shortcodeRegex.matches(v.toUpperCase) => Right(Shortcode(v.toUpperCase()))
    case _ => Left(s"Shortcode is invalid: $value")
  }
}
```

What you get for free:

| Method       | Source        | Behaviour                                                  |
|--------------|---------------|------------------------------------------------------------|
| `from`       | You implement | Validates and returns `Either[String, A]`                  |
| `unsafeFrom` | `WithFrom`    | Calls `from`, throws `IllegalArgumentException` on `Left`  |
| `toString`   | `Value[A]`    | Returns `value.toString` — the raw string                  |

The private constructor ensures every instance went through `from` (or `unsafeFrom`).

## Extra parsed fields

When the raw string contains structure worth exposing, parse it once in `from` and store
the parts as additional fields:

```scala
final case class ResourceIri private (
  override val value: String,
  shortcode: Shortcode,
  resourceId: ResourceId,
) extends StringValue

object ResourceIri extends StringValueCompanion[ResourceIri] {
  private val ResourceIriRegex: Regex =
    """^http://rdfh\.ch/(\p{XDigit}{4})/([A-Za-z0-9_-]+)$""".r

  def from(value: String): Either[String, ResourceIri] = value match {
    case ResourceIriRegex(sc, id) =>
      Right(ResourceIri(value, Shortcode.unsafeFrom(sc), ResourceId.unsafeFrom(id)))
    case _ => Left(s"<$value> is not a Knora resource IRI")
  }
}
```

The `unsafeFrom` calls inside `from` are safe because the regex already guarantees the
sub-parts are valid.

## Validation combinators

`StringValueCompanion` provides reusable checks that can be composed with
`fromValidations`:

```scala
object Description extends StringValueCompanion[Description] {
  def from(value: String): Either[String, Description] =
    fromValidations("Description", Description.apply, value)(nonEmpty, maxLength(500), noLineBreaks)
}
```

Available combinators: `nonEmpty`, `noLineBreaks`, `maxLength(n)`, `isUri`, `absoluteUri`.

## String interpolation

Because `Value[A].toString` returns `value.toString`, a `StringValue` can be dropped
directly into string interpolation without calling `.value`:

```scala
val resourceIri: ResourceIri = ...
val valueId: ValueId = ...

// toString is called implicitly — these are equivalent:
s"$resourceIri/values/$valueId"
s"${resourceIri.value}/values/${valueId.value}"
uri"/v3/projects/$project/"
```

This is particularly useful in URI, IRI construction and SPARQL query building where IRIs are
composed from validated parts:

```scala
def makeNew(resourceIri: ResourceIri): ValueIri = {
  val id = ValueId.makeNew
  unsafeFrom(s"$resourceIri/values/$id")
}
```
