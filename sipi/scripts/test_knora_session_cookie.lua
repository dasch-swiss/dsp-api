-- Copyright © 2015-2018 the contributors (see Contributors.md).
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

require "send_response"
require "get_knora_session"

success, errmsg = server.setBuffer()

if not success then
    server.log("server.setBuffer() failed: " .. errmsg, server.loglevel.LOG_ERR)
    send_error(500, "buffer could not be set correctly")
    return
end

local test_cookie_headers = {
    {
        header = "sid=2cdbbac3-77d3-454d-8e30-e75e6952a81b",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    },
    {
        header = "sid=2cdbbac3-77d3-454d-8e30-e75e6952a81b ",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    },
    {
        header = "sid=2cdbbac3-77d3-454d-8e30-e75e6952a81b;",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    }, {
        header = "sid=2cdbbac3-77d3-454d-8e30-e75e6952a81b; ",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    }, {
        header = "sid=2cdbbac3-77d3-454d-8e30-e75e6952a81b;other=true;",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    }, {
        header = "sid=2cdbbac3-77d3-454d-8e30-e75e6952a81b; other=true;",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    }, {
        header = "other=true; sid=2cdbbac3-77d3-454d-8e30-e75e6952a81b",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    }, {
        header = "other=true;sid=2cdbbac3-77d3-454d-8e30-e75e6952a81b",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    }, {
        header = "other=true;sid=2cdbbac3-77d3-454d-8e30-e75e6952a81b;other2=true",
        session_id = "2cdbbac3-77d3-454d-8e30-e75e6952a81b"
    }, {
        header = "other=true;sid=2cdbbac3-77d3-454d-8e30-e75e6952a81b; other2=true",
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
