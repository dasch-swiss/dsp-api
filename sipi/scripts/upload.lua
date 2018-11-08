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
-- Upload route for binary files (currently only images) to be used with Knora.
--

require "get_mediatype"
require "send_response"
require "jwt"
require "clean_tempdir"

-- Check for a JSON Web Token from Knora.

local token = get_knora_token()

if token == nil then
  return
end

local myimg = {}
local newfilename = {}
local iiifurls = {}

-- Process the uploaded files.

for imgindex, imgparam in pairs(server.uploads) do
    -- Create the temporary directory if necessary.

    local tmpdir = config.imgroot .. '/tmp/'
    local success, exists = server.fs.exists(tmpdir)

    if not success then
        send_error(500, exists)
        return
    end

    if not exists then
        local success, errmsg = server.fs.mkdir(tmpdir, 511)
        if not success then
            send_error(500, errmsg)
            return
        end
    end

    -- Save the uploaded file to the temporary directory, using a random
    -- filename.

    local success, uuid62 = server.uuid62()

    if not success then
        send_error(500, uuid62)
        return
    end

    local tmppath = tmpdir .. uuid62
    local success, errmsg = server.copyTmpfile(imgindex, tmppath)

    if not success then
        send_error(500, errmsg)
        return
    end

    server.log("upload.lua: copied upload to " .. tmppath, server.loglevel.LOG_DEBUG)

    -- Create a new Lua image object. This reads the image into an
    -- internal in-memory representation independent of the original
    -- image format.
    local success, tmpimgref = SipiImage.new(tmppath, {original = imgparam["origname"], hash = "sha256"})

    if not success then
        send_error(500, tmpimgref)
        return
    end

    myimg[imgindex] = tmpimgref

    -- Remember the file's original name=.
    local filename = imgparam["origname"]
    newfilename[imgindex] = uuid62 .. '.jp2'

    if server.secure then
        protocol = 'https://'
    else
        protocol = 'http://'
    end

    -- Create a IIIF URL for the converted file.
    iiifurls[uuid62 .. ".jp2"] = protocol .. server.host .. '/tmp/' .. uuid62 .. '.jp2'

    -- Convert the image to JPEG 2000 format, saving it in the temporary directory.

    local success, hashedFilename = helper.filename_hash(newfilename[imgindex])

    if not success then
        send_error(500, newfilepath)
        return
    end

    local fullfilepath = config.imgroot .. '/tmp/' .. hashedFilename

    local status, errmsg = myimg[imgindex]:write(fullfilepath)

    server.log("upload.lua: wrote JPEG 2000 file to " .. fullfilepath, server.loglevel.LOG_DEBUG)

    if not status then
        send_error(500, errmsg)
        return
    end

    -- Delete the original file.

    local success, errmsg = server.fs.unlink(tmppath)

    if not success then
        send_error(500, errmsg)
        return
    end
end

-- Clean up old temporary files.
clean_tempdir()

send_success(iiifurls)
