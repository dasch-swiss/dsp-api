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

-- destroy the cookie containing the Knora session id

local success, errormsg = server.setBuffer()
if not success then
    server.log("server.setBuffer() failed: " .. errormsg, server.loglevel.LOG_ERR)
    send_error(500, "buffer could not be set correctly")
    return
end

-- invalidate cookie
local success, errormsg = server.sendHeader("Set-Cookie", "KnoraToken=deleted;Expires=Thu, 01 Jan 1970 00:00:00 GMT")
if not success then
    print(errormsg)
end

-- set content-type to text
-- jQuery in SALSAH expects a content-type
local success, errormsg = server.sendHeader("Content-Type", "text/plain")
if not success then
    print(">>>1 ", errormsg)
end
