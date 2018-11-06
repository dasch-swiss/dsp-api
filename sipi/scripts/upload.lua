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

-- upload script for binary files (currently only images) from Knora
--

require "send_response"
require "jwt"
require "clean_tempdir"

token = get_token()

if token == nil then
  return false
end

myimg = {}
newfilename = {}
iiifurls = {}

for imgindex, imgparam in pairs(server.uploads) do

    --
    -- check if tmporary directory is available under image root, if not, create it
    --
    local tmpdir = config.imgroot .. '/tmp/'
    local success, exists = server.fs.exists(tmpdir)

    if not success then
        server.sendStatus(500)
        server.log(exists, server.loglevel.LOG_ERR)
        return false
    end

    if not exists then
        local success, errmsg = server.fs.mkdir(tmpdir, 511)
        if not success then
            server.sendStatus(500)
            server.log(errmsg, server.loglevel.LOG_ERR)
            return false
        end
    end

    --
    -- copy the file to a safe place
    --
    local success, uuid62 = server.uuid62()
    if not success then
        server.sendStatus(500)
        server.log(uuid62, server.loglevel.LOG_ERR)
        return false
    end

    local tmppath = tmpdir .. uuid62
    local success, errmsg = server.copyTmpfile(imgindex, tmppath)

    if not success then
        server.sendStatus(500)
        server.log(errmsg, server.loglevel.LOG_ERR)
        return false
    end

    --
    -- create a new Lua image object. This reads the image into an
    -- internal in-memory representation independent of the original
    -- image format.
    --
    local success, tmpimgref = SipiImage.new(tmppath, {original = imgparam["origname"], hash = "sha256"})

    if not success then
        server.sendStatus(500)
        server.log(tmpimgref, server.loglevel.LOG_ERR)
        return false
    end

    myimg[imgindex] = tmpimgref

    local filename = imgparam["origname"]
    newfilename[imgindex] = tmppath .. '.jp2'

    if server.secure then
        protocol = 'https://'
    else
        protocol = 'http://'
    end

    iiifurls[uuid62 .. ".jp2"] = protocol .. server.host .. '/tmp/' .. uuid62 .. '.jp2'

    local success, newfilepath = helper.filename_hash(newfilename[imgindex])

    if not success then
        server.sendStatus(500)
        server.log(newfilepath, server.loglevel.LOG_ERR)
        return false
    end

    local fullfilepath = config.imgroot .. '/tmp/' .. newfilepath

    local status, errmsg = myimg[imgindex]:write(fullfilepath)
    if not status then
        server.print('Error converting image to j2k: ', filename, ' ** ', errmsg)
    end

    local success, errmsg = server.fs.unlink(tmppath)

    if not success then
        server.sendStatus(500)
        server.log(errmsg, server.loglevel.LOG_ERR)
        return false
    end
end

-- Clean up old temporary files.
--
-- No need to handle errors from clean_tempdir here, because it logs them
-- itself, and in any case we don't want it to stop us from processing
-- the request.
clean_tempdir()

send_success(iiifurls)
