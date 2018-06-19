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

if not authorize_api('admin.sipi.org', 'administrator', config.adminuser) then
    return
end

if server.method == 'GET' then
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
        server.sendStatus(500)
        server.log(jsonstr, server.loglevel.err)
        return false
    end

    server.sendHeader('Content-type', 'application/json')
    server.sendStatus(200)
    server.print(jsonstr)
elseif server.method == 'DELETE' then
    if server.content and server.content_type == 'application/json' then
        local success, todel = server.json_to_table(server.content)
        if not success then
            server.sendStatus(500)
            server.log(todel, server.loglevel.err)
            return false
        end
        for index,canonical in pairs(todel) do
            cache.delete(canonical)
        end
        result = {
            status = 'OK'
        }
        local success, jsonresult = server.table_to_json(result)
        server.sendHeader('Content-type', 'application/json')
        server.sendStatus(200);
        server.print(jsonresult)
    else
        local n = cache.purge()
        result = {
            status = 'OK',
            n = n
        }
        local success, jsonresult = server.table_to_json(result)
        if not success then
            server.sendStatus(500)
            server.log(jsonstr, server.loglevel.err)
            return false
        end
        server.sendHeader('Content-type', 'application/json')
        server.sendStatus(200);
        server.print(jsonresult)
    end
end
