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

-- create a cookie containing the Knora session id

local success, errormsg = server.setBuffer()
if not success then
    server.log("server.setBuffer() failed: " .. errormsg, server.loglevel.LOG_ERR)
    send_error(500, "buffer could not be set correctly")
    return
end

sessionId = server.post['sid']

if sessionId == nil then

    send_error(400, "no sid given")

    return
end

-- set the cookie HttpOnly
local success, errormsg = server.sendHeader("Set-Cookie", "sid=" .. sessionId .. ";HttpOnly")
if not success then
    print(errormsg)
end

-- set content-type to text
-- jQuery in SALSAH expects a content-type
local success, errormsg = server.sendHeader("Content-Type", "text/plain")
if not success then
    print(">>>1 ", errormsg)
end

