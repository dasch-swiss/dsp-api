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
require "get_mediatype"

success, errmsg = server.setBuffer()
if not success then
    server.log("server.setBuffer() failed: " .. errmsg, server.loglevel.LOG_ERR)
    send_error(500, "buffer could not be set correctly")
    return
end

if server.post == nil then
    send_error(400, PARAMETERS_INCORRECT)

    return
end

originalFilename = server.post['originalfilename']
originalMimetype = server.post['originalmimetype']
sourcePath = server.post['source']

-- check if all the expected params are set
if originalFilename == nil or originalMimetype == nil or sourcePath == nil then
    send_error(400, PARAMETERS_INCORRECT)
    return
end

-- all params are set

-- check if source is readable

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
success, real_mimetype = server.file_mimetype(sourcePath)

if not success then
    server.log("server.file_mimetype() failed: " .. exists, server.loglevel.LOG_ERR)
    send_error(500, "mimetype of file could not be determined")
end

-- handle the file depending on its media type (image, text file)
mediatype = get_mediatype(real_mimetype.mimetype)

-- in case of an unsupported mimetype, the function returns false
if not mediatype then
    send_error(400, "Mimetype '" .. real_mimetype.mimetype .. "' is not supported")
    return
end

-- depending on the media type, decide what to do
if mediatype == IMAGE then

    -- it is an image

    --
    -- check if knora directory is available, if not, create it
    --
    knoraDir = config.imgroot .. '/knora/'
    success, exists = server.fs.exists(knoraDir)
    if not success then
        server.log("server.fs.exists() failed: " .. exists, server.loglevel.LOG_ERR)
    end

    if not exists then
        success, errmsg = server.fs.mkdir(knoraDir, 511)
        if not success then
            server.log("server.fs.mkdir() failed: " .. errmsg, server.loglevel.LOG_ERR)
            send_error(500, "Knora directory could not be created on server")
            return
        end
    end

    success, baseName = server.uuid62()
    if not success then
        server.log("server.uuid62() failed: " .. baseName, server.loglevel.LOG_ERR)
        send_error(500, "unique name could not be created")
        return
    end


    --
    -- create full quality image (jp2)
    --
    success, fullImg = SipiImage.new(sourcePath)
    if not success then
        server.log("SipiImage.new() failed: " .. fullImg, server.loglevel.LOG_ERR)
        return
    end

    success, check = fullImg:mimetype_consistency(originalMimetype, originalFilename)
    if not success then
        server.log("fullImg:mimetype_consistency() failed: " .. check, server.loglevel.LOG_ERR)
        return
    end

    -- if check returns false, the user's input is invalid
    if not check then

        send_error(400, MIMETYPES_INCONSISTENCY)

        return
    end

    success, fullDims = fullImg:dims()
    if not success then
        server.log("fullImg:dims() failed: " .. fullDIms, server.loglevel.LOG_ERR)
        return
    end

    fullImgName = baseName .. '.jpx'

    --
    -- create new full quality image file path with sublevels:
    --
    success, newFilePath = helper.filename_hash(fullImgName);
    if not success then
        server.sendStatus(500)
        server.log(gaga, server.loglevel.error)
        return false
    end

    success, errmsg = fullImg:write(knoraDir .. newFilePath)
    if not success then
        server.log("fullImg:write() failed: " .. errmsg, server.loglevel.LOG_ERR)
        return
    end

    --
    -- create thumbnail (jpg)
    --
    success, thumbImg = SipiImage.new(sourcePath, { size = config.thumb_size })
    if not success then
        server.log("SipiImage.new failed: " .. thumbImg, server.loglevel.LOG_ERR)
        return
    end

    success, thumbDims = thumbImg:dims()
    if not success then
        server.log("thumbImg:dims() failed: " .. thumbDims, server.loglevel.LOG_ERR)
        return
    end

    thumbImgName = baseName .. '.jpg'

    --
    -- create new thumnail image file path with sublevels:
    --
    success, newThumbPath = helper.filename_hash(thumbImgName);
    if not success then
        server.sendStatus(500)
        server.log(gaga, server.loglevel.error)
        return false
    end


    success, errmsg = thumbImg:write(knoraDir .. newThumbPath)
    if not success then
        server.log("thumbImg:write failed: " .. errmsg, server.loglevel.LOG_ERR)
        return
    end

    result = {
        mimetype_full = "image/jp2",
        filename_full = fullImgName,
        nx_full = fullDims.nx,
        ny_full = fullDims.ny,
        mimetype_thumb = "image/jpeg",
        filename_thumb = thumbImgName,
        nx_thumb = thumbDims.nx,
        ny_thumb = thumbDims.ny,
        original_mimetype = originalMimetype,
        original_filename = originalFilename,
        file_type = IMAGE
    }

    send_success(result)

elseif mediatype == TEXT then

    -- it is a text file

    --
    -- check if knora directory is available, if not, create it
    --
    fileDir = config.docroot .. '/knora/'
    success, exists = server.fs.exists(fileDir)
    if not success then
        server.log("server.fs.exists() failed: " .. exists, server.loglevel.LOG_ERR)
    end

    if not exists then
        success, errmsg = server.fs.mkdir(fileDir, 511)
        if not success then
            server.log("server.fs.mkdir() failed: " .. errmsg, server.loglevel.LOG_ERR)
            send_error(500, "Knora directory could not be created on server")
            return
        end
    end

    local success, filename = server.uuid62()
    if not success then
        send_error(500, "Couldn't generate uuid62")
        return -1
    end

    -- check file extension
    if not check_file_extension(real_mimetype.mimetype, originalFilename) then
        send_error(400, MIMETYPES_INCONSISTENCY)
        return
    end

    -- check that the submitted mimetype is the same as the real mimetype of the file

    local success, submitted_mimetype = server.parse_mimetype(originalMimetype)

    if not success then
        send_error(400, "Couldn't parse mimetype: " .. originalMimetype)
        return -1
    end

    if (real_mimetype.mimetype ~= submitted_mimetype.mimetype) then
        send_error(400, MIMETYPES_INCONSISTENCY)
        return
    end

    local filePath = fileDir .. filename

    local success, result = server.fs.copyFile(sourcePath, filePath)
    if not success then
        send_error(400, "Couldn't copy file: " .. result)
        return -1
    end

    result = {
        mimetype = submitted_mimetype.mimetype,
        charset = submitted_mimetype.charset,
        file_type = TEXT,
        filename = filename,
        original_mimetype = originalMimetype,
        original_filename = originalFilename
    }

    send_success(result)

end
