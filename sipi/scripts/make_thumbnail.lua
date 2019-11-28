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

-- Knora GUI-case: create a thumbnail

require "send_response"
require "util"

success, errormsg = server.setBuffer()
if not success then
    return -1
end

--
-- check if temporary directory is available, if not, create it.
--
local tmpDir = config.imgroot .. '/tmp/'

local success, exists = server.fs.exists(tmpDir)
if not success then
    send_error(500, "server.fs.exists() failed: " .. exists)
    return
end

if not exists then
    local result
    success, result = server.fs.mkdir(tmpDir, 511)
    if not success then
        send_error(500, "server.fs.mkdir() failed: " .. result)
        return
    end
end

--
-- check if thumbs directory is available, if not, create it.
--
local thumbsDir = config.imgroot .. '/thumbs/'

success, exists = server.fs.exists(thumbsDir)
if not success then
    send_error(500, "server.fs.exists() failed: " .. exists)
    return
end
if not exists then
    local result
    success, result = server.fs.mkdir(thumbsDir, 511)
    if not success then
        send_error(500, "server.fs.mkdir() failed: " .. result)
        return
    end
end

--
-- check if something was uploaded
--
if server.uploads == nil then
    send_error(400, "no image uploaded")
    return
end

for imgindex, imgparam in pairs(server.uploads) do

    --
    -- copy the uploaded file (from config.tmpdir) to tmpDir so we have access to it in later requests
    --

    -- create tmp name
    local tmpName
    success, tmpName = server.uuid62()
    if not success then
        send_error(500, "server.uuid62() failed: " .. tmpName)
        return
    end

    local hashed_tmpName
    success, hashed_tmpName = helper.filename_hash(tmpName)

    if not success then
        send_error(500, "helper.filename_hash() failed: " .. hashed_tmpName)
        return
    end

    local tmpPath =  tmpDir .. hashed_tmpName

    local result
    success, result = server.copyTmpfile(imgindex, tmpPath)
    if not success then
        send_error(500, "server.copyTmpfile() failed: " .. result)
        return
    end


    --
    -- create a thumnail sized SipiImage
    --
    local thumbImg
    success, thumbImg = SipiImage.new(tmpPath, {size = config.thumb_size})
    if not success then
        send_error(500, "SipiImage.new() failed: " .. thumbImg)
        return
    end

    local filename = imgparam["origname"]
    local submitted_mimetype
    success, submitted_mimetype = server.parse_mimetype(imgparam["mimetype"])

    if not success then
        send_error(400, "Couldn't parse mimetype: " .. imgparam["mimetype"])
        return
    end

    local check
    success, check = thumbImg:mimetype_consistency(submitted_mimetype.mimetype, filename)
    if not success then
        send_error(500, "make_thumbnail.lua: thumbImg:mimetype_consistency() failed: " .. check)
        return
    end

    --
    -- if check returns false, the user's input is invalid
    --

    if not check then
        send_error(400, MIMETYPES_INCONSISTENCY)
        return -1
    end

    --
    -- get the dimensions
    --
    local dims
    success, dims = thumbImg:dims()
    if not success then
        send_error(500, "thumbImg:dims() failed: " .. dims)
        return
    end


    --
    -- write the thumbnail file
    --
    local thumbName = tmpName .. ".jpg"
    local thumbPath = thumbsDir .. thumbName

    server.log("thumbnail path: " .. thumbPath, server.loglevel.LOG_DEBUG)

    success, result = thumbImg:write(thumbPath)
    if not success then
        send_error(500, "thumbImg:write() failed: " .. result)
        return
    end

    -- #snip_marker
    server.log("make_thumbnail - external_protocol: " .. get_external_protocol(), server.loglevel.LOG_DEBUG)

    server.log("make_thumbnail - external_hostname: " .. get_external_hostname(), server.loglevel.LOG_DEBUG)

    server.log("make_thumbnail - external_port: " .. get_external_port(), server.loglevel.LOG_DEBUG)

    answer = {
        nx_thumb = dims.nx,
        ny_thumb = dims.ny,
        mimetype_thumb = 'image/jpeg',
        preview_path =  get_external_protocol() .. "://" .. get_external_hostname() .. ":" .. get_external_port() .."/thumbs/" .. thumbName .. "/full/full/0/default.jpg",
        filename = tmpName, -- make this a IIIF URL
        original_mimetype = submitted_mimetype.mimetype,
        original_filename = filename,
        file_type = 'IMAGE'
    }
    -- #snip_marker

end

send_success(answer)
