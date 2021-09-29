<!---
Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)

This file is part of DSP — DaSCH Service Platform.

DSP is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

DSP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with DSP. If not, see <http://www.gnu.org/licenses/>.
-->

# The DSP APIs

The DSP APIs include:

* The DSP API versions [1](api-v1/index.md) and [2](api-v2/index.md), which is intended to be used by
  virtual research environments and other clients for querying and updating
  data.
* The DSP [Admin API](api-admin/index.md), which is intended to be used only by the
  [DSP-APP](https://github.com/dasch-swiss/dsp-app) user interface, for
  administering projects that use Knora as well as Knora itself.
* The DSP [Util API](api-util/index.md), which is intended to be used for information retrieval
  about the DSP-stack itself.

DSP API v2 and the admin API support [Feature Toggles](feature-toggles.md).
