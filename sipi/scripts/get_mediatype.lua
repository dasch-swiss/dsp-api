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
-------------------------------------------------------------------------------
-- String constants to be returned
-------------------------------------------------------------------------------
TEXT = "text"
IMAGE = "image"

-------------------------------------------------------------------------------
-- Mimetype constants
-------------------------------------------------------------------------------

APPLICATION_XML = "application/xml"
TEXT_XML = "text/xml"
TEXT_PLAIN = "text/plain"

-------------------------------------------------------------------------------
-- This function is called from the route to determine the media type (image, text file) of a given file.
-- Parameters:
-- 'mimetype' (string):  the mimetype of the file.
--
-- Returns:
-- the media type of the file or false in case no supported type could be determined.
-------------------------------------------------------------------------------
function get_mediatype(mimetype)

    if mimetype == APPLICATION_XML or mimetype == TEXT_XML or mimetype == TEXT_PLAIN then
        return TEXT

    elseif mimetype == "image/jp2" or mimetype == "image/tiff" or mimetype == "image/png" or mimetype == "image/jpeg" then

        return IMAGE

        -- TODO: implement video and audio

    else

        -- no supported mediatype could be determined
        return false
    end
end

-------------------------------------------------------------------------------
-- This function is called from the route to check the file extension of the given filename.
-- Parameters:
-- 'mimetype' (string):  the mimetype of the file.
-- `filename` (string): the name of the file excluding the file extension.
--
-- Returns:
-- a boolean indicating whether the file extension is correct or not.
-------------------------------------------------------------------------------
function check_file_extension(mimetype, filename)

    if (mimetype == APPLICATION_XML or mimetype == TEXT_XML) then
        local ext = string.sub(filename, -4)

        -- valid extensions are: xml, xsl (XSLT), and .xsd (XML Schema)
        return ext == ".xml" or ext == ".xsl" or ext == ".xsd"
    elseif (mimetype == TEXT_PLAIN) then
        local ext = string.sub(filename, -4)

        return ext == ".txt"
    end
end
