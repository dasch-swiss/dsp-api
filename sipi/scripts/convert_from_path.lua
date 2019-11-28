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

-- handles the Knora non GUI-case: Knora uploaded a file to sourcePath

require "send_response"
require "file_info"

local success, errmsg = server.setBuffer()
if not success then
    send_error(500, "server.setBuffer() failed: " .. errmsg)
    return
end

if server.post == nil then
    send_error(400, PARAMETERS_INCORRECT)
    return
end

local originalFilename = server.post['originalFilename']
local originalMimeType = server.post['originalMimeType']
local sourcePath = server.post['source']
local prefix = server.post['prefix']

-- check if all the expected params are set
if originalFilename == nil or originalMimeType == nil or sourcePath == nil or prefix == nil then
    send_error(400, PARAMETERS_INCORRECT)
    return
end

-- all params are set

-- check if source is readable

local readable
success, readable = server.fs.is_readable(sourcePath)
if not success then
    send_error(500, "server.fs.is_readable() failed: " .. readable)
    return
end

if not readable then
    send_error(400, FILE_NOT_READABLE .. sourcePath)
    return
end

-- check for the mimetype of the file
local mime_info
success, mime_info = server.file_mimetype(sourcePath)

if not success then
    send_error(500, "server.file_mimetype() failed: " .. mime_info)
    return
end

local mime_type = mime_info["mimetype"]

-- check that the submitted mimetype is the same as the real mimetype of the file

local submitted_mimetype
success, submitted_mimetype = server.parse_mimetype(originalMimeType)

if not success then
    send_error(400, "Couldn't parse mimetype: " .. originalMimeType)
    return
end

if (mime_type ~= submitted_mimetype.mimetype) then
    send_error(400, MIMETYPES_INCONSISTENCY)
    return
end

-- handle the file depending on its media type (image, text file)
local file_info = get_file_info(originalFilename, mime_type)

-- in case of an unsupported mimetype, the function returns false
if not file_info then
    send_error(400, "Mimetype '" .. mime_type .. "' is not supported")
    return
end

local media_type = file_info["media_type"]

-- depending on the media type, decide what to do
if media_type == IMAGE then

    -- it is an image

    --
    -- check if project directory is available, if not, create it
    --

    local projectDir = config.imgroot .. '/' .. prefix .. '/'

    local exists
    success, exists = server.fs.exists(projectDir)
    if not success then
        send_error(500, "server.fs.exists() failed: " .. exists)
        return
    end

    if not exists then
        success, errmsg = server.fs.mkdir(projectDir, 511)
        if not success then
            send_error(500, "server.fs.mkdir() failed: " .. errmsg)
            return
        end
    end

    local baseName
    success, baseName = server.uuid62()
    if not success then
        send_error(500, "server.uuid62() failed: " .. baseName)
        return
    end


    --
    -- create full quality image (jp2)
    --
    local fullImg
    success, fullImg = SipiImage.new(sourcePath)
    if not success then
        send_error(500, "SipiImage.new() failed: " .. fullImg)
        return
    end

    local check
    success, check = fullImg:mimetype_consistency(submitted_mimetype.mimetype, originalFilename)

    if not success then
        send_error(500, "convert_from_path.lua: fullImg:mimetype_consistency() failed: " .. check)
        return
    end

    -- if check returns false, the user's input is invalid
    if not check then
        send_error(400, MIMETYPES_INCONSISTENCY)
        return
    end

    local fullDims
    success, fullDims = fullImg:dims()
    if not success then
        send_error(500, "fullImg:dims() failed: " .. fullDims)
        return
    end

    local fullImgName = baseName .. ".jp2"

    --
    -- create new full quality image file path with sublevels:
    --
    local newFilePath
    success, newFilePath = helper.filename_hash(fullImgName)
    if not success then
        send_error(500, "helper.filename_hash: " .. newFilePath)
        return
    end

    success, errmsg = fullImg:write(projectDir .. newFilePath)
    if not success then
        send_error(500, "fullImg:write() failed: " .. errmsg)
        return
    end

    result = {
        mimetype_full = "image/jp2",
        filename_full = fullImgName,
        nx_full = fullDims.nx,
        ny_full = fullDims.ny,
        original_mimetype = originalMimeType,
        original_filename = originalFilename,
        file_type = IMAGE
    }

    send_success(result)

elseif media_type == TEXT then

    -- it's a text file

    --
    -- check if project directory is available, if not, create it
    --
    local projectFileDir = config.imgroot .. '/' .. prefix .. '/'
    local exists
    success, exists = server.fs.exists(projectFileDir)
    if not success then
        send_error(500, "server.fs.exists() failed: " .. exists)
        return
    end

    if not exists then
        success, errmsg = server.fs.mkdir(projectFileDir, 511)
        if not success then
            send_error(500, "server.fs.mkdir() failed: " .. errmsg)
            return
        end
    end

    local baseName
    success, baseName = server.uuid62()
    if not success then
        send_error(500, "server.uuid62() failed: " .. baseName)
        return
    end

    local filename = baseName .. "." .. file_info["extension"]
    local filePath = projectFileDir .. filename

    local result
    success, result = server.fs.copyFile(sourcePath, filePath)
    if not success then
        send_error(500, "server.fs.copyFile() failed: " .. result)
        return
    end

    server.log("Copied " .. sourcePath .. " to " .. filePath, server.loglevel.LOG_DEBUG)

    result = {
        mimetype = submitted_mimetype.mimetype,
        charset = submitted_mimetype.charset,
        file_type = TEXT,
        filename = filename,
        original_mimetype = originalMimeType,
        original_filename = originalFilename
    }

    send_success(result)

else
    send_error(400, "Unsupported mimetype: " .. mime_type)
end
