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

# The Sipi Media Server

Sipi is a high-performance media server written in C++, for serving and
converting binary media files such as images and video. Sipi can
efficiently convert between many different formats on demand, preserving
embedded metadata, and implements the [International Image
Interoperability Framework (IIIF)](http://iiif.io/). Knora is designed
to use Sipi for converting and serving media files.

@@toc { depth=1 }

@@@ index

* [Setting Up Sipi for Knora](setup-sipi-for-knora.md)
* [Interaction Between Sipi and Knora](sipi-and-knora.md)

@@@
