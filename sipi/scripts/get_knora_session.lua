-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

basexx = require("basexx")

-------------------------------------------------------------------------------
-- This function is called from the route to get the Knora session id from the cookie.
-- The cookie is sent to Sipi by the client (HTTP request header).
-- Parameters:
--     'cookie' (string):  cookie from the HTTP request header
--
-- Returns:
--    the Knora session id or `nil` if it could not be found
-------------------------------------------------------------------------------
function get_session_id(cookie)

    if (type(cookie) ~= "string") then
        server.log("parameter 'cookie' for function 'get_session_id' is expected to be a string", server.loglevel.LOG_ERR)
        return nil
    end

    -- name of the cokie depends on the environment defined as host:port combination
    -- this combination is "mangled" using base32 and appended to "KnoraAuthentication"
    -- to get the correct cokie, we need to calculate first the mangled host-port combination
    local webapi_hostname = os.getenv("KNORA_WEBAPI_KNORA_API_EXTERNAL_HOST")
    local webapi_port = os.getenv("KNORA_WEBAPI_KNORA_API_EXTERNAL_PORT")
    if webapi_hostname == nil then
        send_error(500, "KNORA_WEBAPI_KNORA_API_EXTERNAL_HOST not set")
        return nil
    end
    if webapi_port == nil then
        send_error(500, "KNORA_WEBAPI_KNORA_API_EXTERNAL_PORT not set")
        return nil
    end

    host_port = webapi_hostname .. ':' .. webapi_port
    server.log("host_port: " .. host_port, server.loglevel.LOG_DEBUG)

    host_port_base32 = basexx.to_base32Custom(host_port)
    server.log("host_port_base32: " .. host_port_base32, server.loglevel.LOG_DEBUG)






    -- tries to extract the Knora session id from the cookie:
    -- gets the digits between "sid=" and the closing ";" (only given in case of several key value pairs)
    -- ";" is expected to separate different key value pairs (https://tools.ietf.org/html/rfc6265#section-4.2.1)
    -- space is also treated as a separator
    -- returns nil if it cannot find the session id (pattern does not match)
    server.log("extracted cookie: " .. cookie, server.loglevel.LOG_DEBUG)
    local session_id = string.match(cookie, "KnoraAuthentication" .. host_port_base32 .. "=([^%s;]+)")
    if session_id == nil then
        server.log("no session_id could be extracted from cookie: " .. cookie, server.loglevel.LOG_DEBUG)
    else
        server.log("extracted session_id: " .. session_id, server.loglevel.LOG_DEBUG)
    end

    local session = {}
    session["id"] = session_id
    session["name"] = "KnoraAuthentication" .. host_port_base32

    return session

end
