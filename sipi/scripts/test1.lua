-- Copyright © 2015-2018 the contributors (see Contributors.md).
--
-- This file is part of Knora.
--
-- Knora is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published
-- by the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- Knora is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU Affero General Public License for more details.
--
-- You should have received a copy of the GNU Affero General Public
-- License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
--
server.setBuffer()

server.sendHeader("Content-Type", "text/html")
server.print("<html>")
server.print("<head><title>LUA TEST-1</title></head>")
server.print("<body><h1>LUA TEST-1</h1>")
server.print("<table>")
server.print("<tr><th>Field</th><th>Value</th></tr>")
for k,v in pairs(server.header) do
    server.print("<tr><td>", k, "</td><td>", v, "</td></tr>")
end
server.print("</table>")
server.print("<hr/>")
server.print("URI: ", server.uri)
server.print("</body>")
server.print("</html>")
