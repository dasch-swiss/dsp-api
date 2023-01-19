-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

require "util"

-------------------------------------------------------------------------------
-- String constants to be returned
-------------------------------------------------------------------------------
TEXT = "text"
IMAGE = "image"
DOCUMENT = "document"
AUDIO = "audio"
VIDEO = "video"
ARCHIVE = "archive"

-------------------------------------------------------------------------------
-- Mimetype constants
-------------------------------------------------------------------------------

local IMAGE_JP2 = "image/jp2"
local IMAGE_JPX = "image/jpx"
local IMAGE_TIFF = "image/tiff"
local IMAGE_PNG = "image/png"
local IMAGE_JPG = "image/jpeg"
local APPLICATION_XML = "application/xml"
local TEXT_XML = "text/xml"
local TEXT_PLAIN = "text/plain"
local AUDIO_MP3 = "audio/mpeg"
local AUDIO_WAV = "audio/wav"
local AUDIO_X_WAV = "audio/x-wav"
local AUDIO_VND_WAVE = "audio/vnd.wave"
local APPLICATION_CSV = "application/csv"
local APPLICATION_PDF = "application/pdf"
local APPLICATION_DOC = "application/msword"
local APPLICATION_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
local APPLICATION_XLS = "application/vnd.ms-excel"
local APPLICATION_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
local APPLICATION_PPT = "application/vnd.ms-powerpoint"
local APPLICATION_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
local APPLICATION_ZIP = "application/zip"
local APPLICATION_TAR = "application/x-tar"
local APPLICATION_GZ = "application/gzip"
local APPLICATION_GZIP = "application/gzip"
local APPLICATION_7Z = "application/x-7z-compressed"
local APPLICATION_TGZ = "application/x-compress"
local APPLICATION_Z = "application/x-compress"
local VIDEO_MP4 = "video/mp4"


local image_mime_types = {
    IMAGE_JP2,
    IMAGE_JPX,
    IMAGE_TIFF,
    IMAGE_PNG,
    IMAGE_JPG
}

local audio_mime_types = {
    AUDIO_MP3,
    AUDIO_WAV,
    AUDIO_X_WAV,
    AUDIO_VND_WAVE
}

local text_mime_types = {
    TEXT_PLAIN,
    APPLICATION_XML,
    TEXT_XML,
    APPLICATION_CSV
}

local document_mime_types = {
    APPLICATION_PDF,
    APPLICATION_DOC,
    APPLICATION_DOCX,
    APPLICATION_XLS,
    APPLICATION_XLSX,
    APPLICATION_PPT,
    APPLICATION_PPTX
}

local archive_mime_types = {
    APPLICATION_TAR,
    APPLICATION_ZIP,
    APPLICATION_GZIP,
    APPLICATION_7Z,
    APPLICATION_TGZ,
    APPLICATION_Z
}

local video_mime_types = {
    VIDEO_MP4
}

local audio_extensions = {
    "mp3",
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
    "doc",
    "docx",
    "xls",
    "xlsx",
    "ppt",
    "pptx"
}

local archive_extensions = {
    "zip",
    "tar",
    "gz",
    "gzip",
    "7z",
    "tgz",
    "z"
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
    if not table.contains(video_extensions, extension) then
        return nil
    else
        return {
            media_type = VIDEO,
            extension = extension
        }
    end
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

function make_archive_file_info(extension)
    if not table.contains(archive_extensions, extension) then
        return nil
    else
        return {
            media_type = ARCHIVE,
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
    elseif table.contains(archive_mime_types, mimetype) then
        return make_archive_file_info(extension)
    else
        -- no supported mediatype could be determined
        return nil
    end
end
