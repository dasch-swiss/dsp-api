-- * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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

    if token["iss"] ~= "Knora" then
        send_error(401, "Not a Knora token")
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
