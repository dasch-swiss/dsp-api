-- * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0
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
