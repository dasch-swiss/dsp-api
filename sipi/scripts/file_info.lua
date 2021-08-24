-- Copyright © 2015-2021 the contributors (see Contributors.md).
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

require "util"

-------------------------------------------------------------------------------
-- String constants to be returned
-------------------------------------------------------------------------------
TEXT = "text"
IMAGE = "image"
DOCUMENT = "document"
AUDIO = "audio"
VIDEO = "video"

-------------------------------------------------------------------------------
-- Mimetype constants
-------------------------------------------------------------------------------

local IMAGE_JP2 = "image/jp2"
local IMAGE_TIFF = "image/tiff"
local IMAGE_PNG = "image/png"
local IMAGE_JPG = "image/jpeg"
local APPLICATION_XML = "application/xml"
local TEXT_XML = "text/xml"
local TEXT_PLAIN = "text/plain"
local AUDIO_MP3 = "audio/mpeg"
local AUDIO_MP4 = "audio/mp4"
local AUDIO_WAV = "audio/wav"
local AUDIO_X_WAV = "audio/x-wav"
local AUDIO_VND_WAVE = "audio/vnd.wave"
local APPLICATION_PDF = "application/pdf"
local APPLICATION_DOC = "application/msword"
local APPLICATION_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
local APPLICATION_XLS = "application/vnd.ms-excel"
local APPLICATION_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
local APPLICATION_PPT = "application/vnd.ms-powerpoint"
local APPLICATION_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
local APPLICATION_ZIP = "application/zip"
local APPLICATION_TAR = "application/x-tar"
local APPLICATION_ISO = "application/x-iso9660-image"
local APPLICATION_GZIP = "application/gzip"
local VIDEO_MP4 = "video/mp4"

local image_mime_types = {
    IMAGE_JP2,
    IMAGE_TIFF,
    IMAGE_PNG,
    IMAGE_JPG
}

local audio_mime_types = {
    AUDIO_MP3,
    AUDIO_MP4,
    AUDIO_WAV,
    AUDIO_X_WAV,
    AUDIO_VND_WAVE
}

local text_mime_types = {
    TEXT_PLAIN,
    APPLICATION_XML,
    TEXT_XML
}

local document_mime_types = {
    APPLICATION_PDF,
    APPLICATION_TAR,
    APPLICATION_ZIP,
    APPLICATION_ISO,
    APPLICATION_GZIP,
    APPLICATION_DOC,
    APPLICATION_DOCX,
    APPLICATION_XLS,
    APPLICATION_XLSX,
    APPLICATION_PPT,
    APPLICATION_PPTX
}

local video_mime_types = {
    VIDEO_MP4
}

local audio_extensions = {
    "mp3",
    "mp4",
    "wav"
}

local text_extensions = {
    "xml",
    "xsl",
    "xsd",
    "txt",
    "csv"
}

local document_extensions = {
    "pdf",
    "zip",
    "tar",
    "iso",
    "gz",
    "doc",
    "docx",
    "xls",
    "xlsx",
    "ppt",
    "pptx"
}

local video_extensions = {
    "mp4"
}

function make_image_file_info(extension)
    return {
        media_type = IMAGE,
        extension = extension
    }
end

function make_audio_file_info(extension)
    if not table.contains(audio_extensions, extension) then
        return nil
    else
        return {
            media_type = AUDIO,
            extension = extension
        }
    end
end

function make_video_file_info(extension)
    return {
        media_type = VIDEO,
        extension = extension
    }
end

function make_text_file_info(extension)
    if not table.contains(text_extensions, extension) then
        return nil
    else
        return {
            media_type = TEXT,
            extension = extension
        }
    end
end

function make_document_file_info(extension)
    if not table.contains(document_extensions, extension) then
        return nil
    else
        return {
            media_type = DOCUMENT,
            extension = extension
        }
    end
end

-------------------------------------------------------------------------------
-- Determines the media type and file extension of a file.
-- Parameters:
-- "filename" (string): the name of the file.
-- "mimetype" (string): the mimetype of the file.
--
-- Returns:
-- a table containing "media_type" and "extension", or false if no supported media type was found.
-------------------------------------------------------------------------------
function get_file_info(filename, mimetype)

    local extension = filename:match("^.+%.([^.]+)$")

    if extension == nil then
        return nil
    elseif table.contains(image_mime_types, mimetype) then
        return make_image_file_info(extension)
    elseif table.contains(audio_mime_types, mimetype) then
        return make_audio_file_info(extension)
    elseif table.contains(video_mime_types, mimetype) then
        return make_video_file_info(extension)
    elseif table.contains(text_mime_types, mimetype) then
        return make_text_file_info(extension)
    elseif table.contains(document_mime_types, mimetype) then
        return make_document_file_info(extension)
    else
        -- no supported mediatype could be determined
        return nil
    end
end
