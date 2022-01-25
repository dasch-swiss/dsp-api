-- * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

require "send_response"
require "get_knora_session"

success, errmsg = server.setBuffer()

if not success then
    send_error(500, "server.setBuffer() failed: " .. errmsg)
    return
end

local test_cookie_headers = {
    {
        header = "KnoraAuthentication=2cdbbac3-77d3-454d-8e30-e75e6952a81b",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    },
    {
        header = "KnoraAuthentication=2cdbbac3-77d3-454d-8e30-e75e6952a81b ",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    },
    {
        header = "KnoraAuthentication=2cdbbac3-77d3-454d-8e30-e75e6952a81b;",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    },
    {
        header = "KnoraAuthentication=2cdbbac3-77d3-454d-8e30-e75e6952a81b; ",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    },
    {
        header = "KnoraAuthentication=2cdbbac3-77d3-454d-8e30-e75e6952a81b;other=true;",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    },
    {
        header = "KnoraAuthentication=2cdbbac3-77d3-454d-8e30-e75e6952a81b; other=true;",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    },
    {
        header = "other=true; KnoraAuthentication=2cdbbac3-77d3-454d-8e30-e75e6952a81b",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    },
    {
        header = "other=true;KnoraAuthentication=2cdbbac3-77d3-454d-8e30-e75e6952a81b",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    },
    {
        header = "other=true;KnoraAuthentication=2cdbbac3-77d3-454d-8e30-e75e6952a81b;other2=true",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    },
    {
        header = "other=true;KnoraAuthentication=2cdbbac3-77d3-454d-8e30-e75e6952a81b; other2=true",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    }
}

for i, cookie_item in ipairs(test_cookie_headers) do

    local session_id = get_session_id(cookie_item.header)

    if session_id == nil or session_id ~= cookie_item.session_id then
        send_error(400, "Knora session id could not be parsed correctly from cookie: " .. cookie_item.header)
        return -1
    end
end

result = {
    result = "ok"
}

send_success(result)
