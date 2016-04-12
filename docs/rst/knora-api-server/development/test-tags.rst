.. Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
   Tobias Schweizer, André Kilchenmann, and André Fatton.

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


Test Tags
=========

.. todo:: add example of how to tag a test.

Tags can be used to mark tests, which can then be used to only run tests
with a certain tag, or exclude them.

There is now the **org.knora.webapi.testing.tags.SipiTest** tag (in the
*test* folder), which marks tests that require the Sipi image server.
These tests can be excluded from running with the following command in
sbt:

``test-only * -- -l org.knora.webapi.testing.tags.SipiTest``
