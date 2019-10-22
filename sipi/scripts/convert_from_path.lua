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
    server.log("server.setBuffer() failed: " .. errmsg, server.loglevel.LOG_ERR)
    send_error(500, "buffer could not be set correctly")
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
    server.log("server.fs.is_readable() failed: " .. readable, server.loglevel.LOG_ERR)
    send_error(500, "server.fs.is_readable() failed")
    return
end

if not readable then
    send_error(500, FILE_NOT_READABLE .. sourcePath)
    return
end

-- check for the mimetype of the file
local mime_info
success, mime_info = server.file_mimetype(sourcePath)

if not success then
    server.log("server.file_mimetype() failed: " .. exists, server.loglevel.LOG_ERR)
    send_error(500, "mimetype of file could not be determined")
end

local mime_type = mime_info["mimetype"]

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
        server.log("server.fs.exists() failed: " .. exists, server.loglevel.LOG_ERR)
    end

    if not exists then
        success, errmsg = server.fs.mkdir(projectDir, 511)
        if not success then
            server.log("server.fs.mkdir() failed: " .. errmsg, server.loglevel.LOG_ERR)
            send_error(500, "Project directory could not be created on server")
            return
        end
    end

    local baseName
    success, baseName = server.uuid62()
    if not success then
        server.log("server.uuid62() failed: " .. baseName, server.loglevel.LOG_ERR)
        send_error(500, "unique name could not be created")
        return
    end


    --
    -- create full quality image (jp2)
    --
    local fullImg
    success, fullImg = SipiImage.new(sourcePath)
    if not success then
        server.log("SipiImage.new() failed: " .. fullImg, server.loglevel.LOG_ERR)
        return
    end

    local fullDims
    success, fullDims = fullImg:dims()
    if not success then
        server.log("fullImg:dims() failed: " .. fullDims, server.loglevel.LOG_ERR)
        return
    end

    local fullImgName = baseName .. ".jp2"

    --
    -- create new full quality image file path with sublevels:
    --
    local newFilePath
    success, newFilePath = helper.filename_hash(fullImgName)
    if not success then
        server.sendStatus(500)
        server.log(gaga, server.loglevel.error)
        return false
    end

    success, errmsg = fullImg:write(projectDir .. newFilePath)
    if not success then
        server.log("fullImg:write() failed: " .. errmsg, server.loglevel.LOG_ERR)
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
        server.log("server.fs.exists() failed: " .. exists, server.loglevel.LOG_ERR)
    end

    if not exists then
        success, errmsg = server.fs.mkdir(projectFileDir, 511)
        if not success then
            server.log("server.fs.mkdir() failed: " .. errmsg, server.loglevel.LOG_ERR)
            send_error(500, "Project directory could not be created on server")
            return
        end
    end

    local baseName
    success, baseName = server.uuid62()
    if not success then
        send_error(500, "Couldn't generate uuid62")
        return
    end

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

    local filename = baseName .. "." .. file_info["extension"]
    local filePath = projectFileDir .. filename

    local result
    success, result = server.fs.copyFile(sourcePath, filePath)

    if not success then
        send_error(400, "Couldn't copy file: " .. result)
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
