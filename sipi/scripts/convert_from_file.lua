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

-- Knora GUI-case: Sipi has already saved the file that is supposed to be converted
-- the file was saved to: config.imgroot .. '/tmp/' (route make_thumbnail)

require "send_response"

local success, errmsg = server.setBuffer()
if not success then
    send_error(500, "server.setBuffer() failed: " .. errmsg)
    return
end

if server.post == nil then
    send_error(400, PARAMETERS_INCORRECT .. " (post)")
    return
end

--
-- check if the project directory is available, otherwise create it.
--

local prefix = server.post['prefix']

if prefix == nil then
    send_error(400, PARAMETERS_INCORRECT .. " (prefix)")
    return
end

local projectDir = config.imgroot .. '/' .. prefix .. '/'

local exists
success, exists = server.fs.exists(projectDir)

if not success then
    send_error(500, "server.fs.exists() failed: " .. exists)
    return
end

if not exists then
    local error_msg
    success, error_msg = server.fs.mkdir(storage_dir, 511)

    if not success then
        send_error(500, "server.fs.mkdir() failed: " .. error_msg)
        return
    end
end

local originalFilename = server.post['originalFilename']
local originalMimeType = server.post['originalMimeType']
local filename = server.post['filename']

-- check if all the expected params are set
if originalFilename == nil then
    send_error(400, PARAMETERS_INCORRECT .. " (originalFilename)")
    return
end

if originalMimeType == nil then
    send_error(400, PARAMETERS_INCORRECT .. " (originalMimeType)")
    return
end

if filename == nil then
    send_error(400, PARAMETERS_INCORRECT .. " (filename)")
    return
end

-- file with name given in param "filename" has been saved by make_thumbnail.lua beforehand
local tmpDir = config.imgroot .. '/tmp/'

local hashed_filename
success, hashed_filename = helper.filename_hash(filename)

if not success then
    send_error(500, "helper.filename_hash() failed: " .. hashed_filename)
    return
end

local sourcePath = tmpDir .. hashed_filename

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

-- all params are set

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

local submitted_mimetype
success, submitted_mimetype = server.parse_mimetype(originalMimeType)

if not success then
    send_error(400, "Couldn't parse mimetype: " .. originalMimeType)
    return
end

local check
success, check = fullImg:mimetype_consistency(submitted_mimetype.mimetype, originalFilename)

if not success then
    send_error(500, "convert_from_file.lua: fullImg:mimetype_consistency() failed: " .. check)
    return
end

-- if check returns false, the user's input is invalid
if not check then
    send_error(400, MIMETYPES_INCONSISTENCY)
    return
end

local fullImgName = baseName .. ".jp2"

--
-- create new full quality image file path with sublevels:
--
local newFilePath
success, newFilePath = helper.filename_hash(fullImgName);
if not success then
    send_error(500, "helper.filename_hash() failed: " .. newFilePath)
    return
end

local fullDims
success, fullDims = fullImg:dims()
if not success then
    send_error(500, "fullImg:dims() failed: " .. fullDims)
    return
end

fullImg:write(projectDir .. newFilePath)

-- create thumbnail (jpg)
local thumbImg
success, thumbImg = SipiImage.new(sourcePath, { size = config.thumb_size })
if not success then
    send_error(500, "SipiImage.new() failed: " .. thumbImg)
    return
end

local thumbDims
success, thumbDims = thumbImg:dims()
if not success then
    send_error(500, "thumbImg:dims() failed: " .. thumbDims)
    return
end

--
-- delete tmp file
--
success, errmsg = server.fs.unlink(sourcePath)
if not success then
    send_error(500, "server.fs.unlink() failed: " .. errmsg)
    return
end

result = {
    status = 0,
    mimetype_full = "image/jp2",
    filename_full = fullImgName,
    nx_full = fullDims.nx,
    ny_full = fullDims.ny,
    original_mimetype = originalMimeType,
    original_filename = originalFilename,
    file_type = "image"
}

send_success(result)
