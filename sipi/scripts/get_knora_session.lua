-- Copyright © 2015-2019 the contributors (see Contributors.md).
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

-------------------------------------------------------------------------------
-- This function is called from the route to get the Knora session id from the cookie.
-- The cookie is sent to Sipi by the client (HTTP request header).
-- Parameters:
--     'cookie' (string):  cookie from the HTTP request header
--
-- Returns:
--    the Knora session id or `nil` if it could not be found
-------------------------------------------------------------------------------
function get_session_id(cookie)

    if (type(cookie) ~= "string") then
        server.log("parameter 'cookie' for function 'get_session_id' is expected to be a string", server.loglevel.LOG_ERR)
        return nil
    end

    -- tries to extract the Knora session id from the cookie:
    -- gets the digits between "sid=" and the closing ";" (only given in case of several key value pairs)
    -- ";" is expected to separate different key value pairs (https://tools.ietf.org/html/rfc6265#section-4.2.1)
    -- space is also treated as a separator
    -- returns nil if it cannot find the session id (pattern does not match)
    server.log("extracted cookie: " .. cookie, server.loglevel.LOG_DEBUG)
    print("extracted cookie: " .. cookie)
    session_id = string.match(cookie, "KnoraAuthentication=([^%s;]+)")

    return session_id

end
