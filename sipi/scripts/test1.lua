--
-- Copyright © 2016 Lukas Rosenthaler, Andrea Bianco, Benjamin Geer,
-- Ivan Subotic, Tobias Schweizer, André Kilchenmann, and André Fatton.
-- This file is part of Sipi.
-- Sipi is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published
-- by the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
-- Sipi is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
-- Additional permission under GNU AGPL version 3 section 7:
-- If you modify this Program, or any covered work, by linking or combining
-- it with Kakadu (or a modified version of that library) or Adobe ICC Color
-- Profiles (or a modified version of that library) or both, containing parts
-- covered by the terms of the Kakadu Software Licence or Adobe Software Licence,
-- or both, the licensors of this Program grant you additional permission
-- to convey the resulting work.
-- You should have received a copy of the GNU Affero General Public
-- License along with Sipi.  If not, see <http://www.gnu.org/licenses/>.
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
