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

-- Knora GUI-case: create a thumbnail

require "send_response"

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
    send_error(500, "Internal server error: " .. exists)
    return -1
end
if not exists then
    local success, result = server.fs.mkdir(tmpDir, 511)
    if not success then
        local errorMsg = "Could not create tmpDir: " .. tmpDir .. " , result: " .. result
        send_error(500, errorMsg)
        server.log(errorMsg, server.loglevel.LOG_ERR)
        return -1
    end
end

--
-- check if thumbs directory is available, if not, create it.
--
local thumbsDir = config.imgroot .. '/thumbs/'
local success, exists = server.fs.exists(thumbsDir)
if not success then
    send_error(500, "Internal server error: " .. exists)
    return -1
end
if not exists then
    local success, result = server.fs.mkdir(thumbsDir, 511)
    if not success then
        local errorMsg = "Could not create thumbsDir: " .. thumbsDir .. " , result: " .. result
        send_error(500, errorMsg)
        server.log(errorMsg, server.loglevel.LOG_ERR)
        return -1
    end
end

--
-- check if something was uploaded
--
if server.uploads == nil then
    send_error(400, "no image uploaded")
    return -1
end

for imgindex, imgparam in pairs(server.uploads) do

    --
    -- copy the uploaded file (from config.tmpdir) to tmpDir so we have access to it in later requests
    --

    -- create tmp name
    local success, tmpName = server.uuid62()
    if not success then
        send_error(500, "Couldn't generate uuid62!")
        return -1
    end

    local tmpPath =  tmpDir .. tmpName

    local success, result = server.copyTmpfile(imgindex, tmpPath)
    if not success then
        local errorMsg = "Couldn't copy uploaded file to tmp path: " .. tmpPath .. ", result: " .. result
        send_error(500, errorMsg)
        server.log(errorMsg, server.loglevel.LOG_ERR)
        return -1
    end


    --
    -- create a thumnail sized SipiImage
    --
    local success, thumbImg = SipiImage.new(tmpPath, {size = config.thumb_size})
    if not success then
        local errorMsg = "Couldn't create thumbnail for path: " .. tmpPath  .. ", result: " .. tostring(thumbImg)
        send_error(500, errorMsg)
        server.log(errorMsg, server.loglevel.LOG_ERR)
        return -1
    end

    local filename = imgparam["origname"]
    local success, submitted_mimetype = server.parse_mimetype(imgparam["mimetype"])

    if not success then
        send_error(400, "Couldn't parse mimetype: " .. imgparam["mimetype"])
        return -1
    end

    local success, check = thumbImg:mimetype_consistency(submitted_mimetype.mimetype, filename)
    if not success then
        send_error(500, "Couldn't check mimteype consistency: " .. check)
        return -1
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
    local success, dims = thumbImg:dims()
    if not success then
        send_error(500, "Couldn't get image dimensions: " .. dims)
        return -1
    end


    --
    -- write the thumbnail file
    --
    thumbName = tmpName .. ".jpg"
    thumbPath = thumbsDir .. thumbName

    server.log("thumbnail path: " .. thumbPath, server.loglevel.LOG_DEBUG)

    local success, result = thumbImg:write(thumbPath)
    if not success then
        local errorMsg = "Couldn't create thumbnail for path: " .. tostring(thumbPath) .. ", result: " .. tostring(result)
        send_error(500, errorMsg)
        server.log(errorMsg , server.loglevel.LOG_ERR)
        return -1
    end

    answer = {
        nx_thumb = dims.nx,
        ny_thumb = dims.ny,
        mimetype_thumb = 'image/jpeg',
        preview_path = "http://" .. config.hostname .. ":" .. config.port .."/thumbs/" .. thumbName .. "/full/full/0/default.jpg",
        filename = tmpName, -- make this a IIIF URL
        original_mimetype = submitted_mimetype.mimetype,
        original_filename = filename,
        file_type = 'IMAGE'
    }

end

send_success(answer)
