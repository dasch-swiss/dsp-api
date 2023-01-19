-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

require "send_response"
require "util"

--- Parses and validates a JSON web token from Knora. Sends an HTTP error
-- if the token is missing or invalid.
-- @return a table representing the token.
function get_knora_token()
    local token = get_token()

    if token == nil then
        return nil
    end

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
    
    token_issuer = webapi_hostname .. ':' .. webapi_port
    server.log("token_issuer: " .. token_issuer, server.loglevel.LOG_DEBUG)
    if token["iss"] ~= token_issuer then
        server.log("Invalid token issuer: " .. token_issuer .. " . Expected: " .. token["iss"], server.loglevel.LOG_DEBUG)
        send_error(401, "Invalid token. The token was not issued by the same server that sent the request.")
        return nil
    end

    return token
end

--- Parses and validates a JSON web token. Sends an HTTP error if the token is
-- missing or invalid.
-- @return a table representing the token.
function get_token()
    if server.request == nil or server.request["token"] == nil then
        send_error(400, "Token missing")
        return nil
    end

    local token_str = server.request["token"]
    local success, token = server.decode_jwt(token_str)

    if not success then
        send_error(401, "Invalid token")
        return nil
    end

    local expiration_date = token["exp"]

    if expiration_date == nil then
        send_error(401, "Token has no expiry date")
        return nil
    end

    local systime = server.systime()

    if (expiration_date <= systime) then
        send_error(401, "Expired token")
        return nil
    end

    local audience = token["aud"]

    if audience == nil or not table.contains(audience, "Sipi") then
        send_error(401, "Sipi not in token audience")
        return nil
    end

    return token
end
