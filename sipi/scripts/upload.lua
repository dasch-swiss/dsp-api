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

function table.contains(table, element)
  for _, value in pairs(table) do
    if value == element then
      return true
    end
  end
  return false
end


if server.request == nil or server.request["token"] == nil then
    send_error(400, "token missing")
    return -1
end

token = server.request["token"]

success, tokendata = server.decode_jwt(token)
if not success then
    send_error(401, "Token not valid")
    return -1
end

myimg = {}
newfilename = {}
iiifurls = {}

for imgindex,imgparam in pairs(server.uploads) do

    --
    -- check if tmporary directory is available under image root, if not, create it
    --
    tmpdir = config.imgroot .. '/tmp/'
    local success, exists = server.fs.exists(tmpdir)
    if not success then
        server.sendStatus(500)
        server.log(exists, server.loglevel.error)
        return false
    end
    if not exists then
        local success, errmsg = server.fs.mkdir(tmpdir, 511)
        if not success then
            server.sendStatus(500)
            server.log(errmsg, server.loglevel.error)
            return false
        end
    end

    --
    -- copy the file to a safe place
    --
    local success, uuid62 = server.uuid62()
    if not success then
        server.sendStatus(500)
        server.log(uuid62, server.loglevel.error)
        return false
    end
    tmppath =  tmpdir .. uuid62
    local success, errmsg = server.copyTmpfile(imgindex, tmppath)
    if not success then
        server.sendStatus(500)
        server.log(errmsg, server.loglevel.error)
        return false
    end

    --
    -- create a new Lua image object. This reads the image into an
    -- internal in-memory representation independent of the original
    -- image format.
    --
    success, tmpimgref = SipiImage.new(tmppath, {original = imgparam["origname"], hash = "sha256"})
    if not success then
        server.sendStatus(500)
        server.log(gaga, server.loglevel.error)
        return false
    end

    myimg[imgindex] = tmpimgref

    filename = imgparam["origname"]
    n1, n2 = string.find(filename, '.', 1, true)
    newfilename[imgindex] = tmppath .. '.jp2'

    if server.secure then
        protocol = 'https://'
    else
        protocol = 'http://'
    end
    iiifurls[uuid62 .. ".jp2"] = protocol .. server.host .. '/tmp/' .. uuid62 .. '.jp2'

    success, newfilepath = helper.filename_hash(newfilename[imgindex]);
    if not success then
        server.sendStatus(500)
        server.log(gaga, server.loglevel.error)
        return false
    end

    fullfilepath = config.imgroot .. '/tmp/' .. newfilepath

    local status, errmsg = myimg[imgindex]:write(fullfilepath)
    if not status then
        server.print('Error converting image to j2k: ', filename, ' ** ', errmsg)
    end

    success, errmsg = server.fs.unlink(tmppath)
    if not success then
        server.sendStatus(500)
        server.log(errmsg, server.loglevel.error)
        return false
    end

end


send_success(iiifurls)
