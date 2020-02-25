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

# Generating Client Test Data

@@toc

## Requirements

Generate test requests and responses for Knora's routes, to be used in testing
client code without the need for a running Knora instance.
  
## Implementation

A class for each Knora API extends the `ClientApi` trait.
A `ClientApi` contains one or more `KnoraRoute` implementations that extend
`ClientEndpoint`. Each endpoint provides functions that return generated
client test data.

The route `ClientApiRoute` returns a Zip file containing generated test data.
returning source code in a Zip file.

## Usage

The following route returns a Zip file containing generated client test
data:

```
HTTP GET to http://host/clientapitest
```
