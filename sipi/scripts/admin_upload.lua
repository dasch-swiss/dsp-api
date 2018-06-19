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
-- it with Kakadu (or a modified version of that library), containing parts
-- covered by the terms of the Kakadu Software Licence, the licensors of this
-- Program grant you additional permission to convey the resulting work.
-- See the GNU Affero General Public License for more details.
-- You should have received a copy of the GNU Affero General Public
-- License along with Sipi.  If not, see <http://www.gnu.org/licenses/>.

-- upload script for project logos (images), user profile avatar, and project specific icons used in salsah admin
--
require "send_response"

success, errormsg = server.setBuffer()
if not success then
    return -1
end

files = {}
newfilename = {}

for findex,fparam in pairs(server.uploads) do

    --
    -- check if admin directory is available under server root, if not, create it
    --
    admindir = config.docroot .. '/admin/'
    local success, exists = server.fs.exists(admindir)
    if not success then
        server.log("server.fs.exists() failed: " .. exists, server.loglevel.LOG_ERR)
        send_error(500, "Internal server error")
        return false
    end
    if not exists then
        local success, errmsg = server.fs.mkdir(admindir, 511)
        if not success then
            server.log("server.fs.mkdir() failed: " .. errmsg, server.loglevel.LOG_ERR)
            send_error(500, "Admin directory could not be created on server")
            return false
        end
    end

    --
    -- copy the file to admin directory
    --
    success, uuid62 = server.uuid62()
    origname = fparam["origname"]:gsub("%s+", "-")

    adminpath =  admindir .. uuid62 .. '-' .. origname
    local success, errmsg = server.copyTmpfile(findex, adminpath)
    if not success then
        server.log(errmsg, server.loglevel.error)
        send_error(500, "Couldn't upload file: " .. result)
        return false
    else
        files[findex] = uuid62 .. '-' .. origname
    end
end

answer = {
    files = files
}

send_success(answer)
