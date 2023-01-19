-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

if not authorize_api('admin.sipi.org', 'administrator', config.adminuser) then
    return
end

result = {
    status = 'OK'
}
local success, jsonresult = server.table_to_json(result)
server.sendHeader('Content-type', 'application/json')
server.sendStatus(200)
server.print(jsonresult)
server.shutdown()
