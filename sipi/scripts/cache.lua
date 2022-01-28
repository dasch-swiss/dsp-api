-- * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

if not authorize_api('admin.sipi.org', 'administrator', config.adminuser) then
    return
end

if server.method == 'GET' then
    local flist

    if server.get and (server.get.sort == 'atasc') then
        flist = cache.filelist('AT_ASC')
    elseif server.get and (server.get.sort == 'atdesc') then
        flist = cache.filelist('AT_DESC')
    elseif server.get and (server.get.sort == 'fsasc') then
        flist = cache.filelist('FS_ASC')
    elseif server.get and (server.get.sort == 'fsdesc') then
        flist = cache.filelist('FS_DESC')
    else
        flist = cache.filelist('AT_ASC')
    end

    local success, jsonstr = server.table_to_json(flist)
    if not success then
        send_error(500, jsonstr)
        return false
    end

    server.sendHeader('Content-type', 'application/json')
    server.sendStatus(200)
    server.print(jsonstr)
elseif server.method == 'DELETE' then
    if server.content and server.content_type == 'application/json' then
        local success, todel = server.json_to_table(server.content)
        if not success then
            send_error(500, todel)
            return false
        end

        for index, canonical in pairs(todel) do
            cache.delete(canonical)
        end

        result = {
            status = 'OK'
        }

        local jsonresult
        success, jsonresult = server.table_to_json(result)
        server.sendHeader('Content-type', 'application/json')
        server.sendStatus(200)
        server.print(jsonresult)
    else
        local n = cache.purge()

        result = {
            status = 'OK',
            n = n
        }

        local success, jsonresult = server.table_to_json(result)
        if not success then
            send_error(500, jsonresult)
            return false
        end

        server.sendHeader('Content-type', 'application/json')
        server.sendStatus(200)
        server.print(jsonresult)
    end
end
