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

# SPARQL Query Design

@@toc

## Querying Past Value Versions

Value versions are a linked list, starting with the current version. Each value points to
the previous version via `knora-base:previousValue`. The resource points only to the current
version.

Past value versions are queried in `getResourcePropertiesAndValues.scala.txt`, which can
take a timestamp argument. Given the current value version, we must find the most recent
past version that existed at the target date.

First, we get the set of previous values that were created on or before the target
date:

```
?currentValue knora-base:previousValue* ?valueObject .
?valueObject knora-base:valueCreationDate ?valueObjectCreationDate .
FILTER(?valueObjectCreationDate <= "@versionDate"^^xsd:dateTime)
```

The resulting versions are now possible values of `?valueObject`. Next, out of this set
of versions, we exclude all versions except for the most recent one. We do this by checking,
for each `?valueObject`, whether there is another version, `?otherValueObject`, that is more
recent and was also created before the target date. If such a version exists, we exclude
the one we are looking at.

```
FILTER NOT EXISTS {
    ?currentValue knora-base:previousValue* ?otherValueObject .
    ?otherValueObject knora-base:valueCreationDate ?otherValueObjectCreationDate .

    FILTER(
        (?otherValueObjectCreationDate <= "@versionDate"^^xsd:dateTime) &&
        (?otherValueObjectCreationDate > ?valueObjectCreationDate)
    )
}
```

This excludes all past versions except the one we are interested in.
