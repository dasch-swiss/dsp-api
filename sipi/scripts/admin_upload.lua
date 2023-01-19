-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

-- upload script for project logos (images), user profile avatar, and project specific icons used in salsah admin
--
require "send_response"

success, errormsg = server.setBuffer()
if not success then
    return -1
end

files = {}
newfilename = {}

for findex,fparam in pairs(server.uploads) do

    --
    -- check if admin directory is available under server root, if not, create it
    --
    admindir = config.docroot .. '/admin/'
    local success, exists = server.fs.exists(admindir)
    if not success then
        send_error(500, "server.fs.exists() failed: " .. exists)
        return false
    end
    if not exists then
        local success, errmsg = server.fs.mkdir(admindir, 511)
        if not success then
            send_error(500, "server.fs.mkdir() failed: " .. errmsg)
            return false
        end
    end

    --
    -- copy the file to admin directory
    --
    success, uuid62 = server.uuid62()
    origname = fparam["origname"]:gsub("%s+", "-")

    adminpath =  admindir .. uuid62 .. '-' .. origname
    local errmsg
    success, errmsg = server.copyTmpfile(findex, adminpath)
    if not success then
        send_error(500, "Couldn't upload file: " .. errmsg)
        return false
    else
        files[findex] = uuid62 .. '-' .. origname
    end
end

answer = {
    files = files
}

send_success(answer)
