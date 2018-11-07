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

--
-- Parses and validates JSON web tokens.
--

require "send_response"
require "util"

--- Parses and validates a JSON web token from Knora. Sends an HTTP error
-- if the token is missing or invalid.
-- @return a table representing the token.
function get_knora_token()
    local token = get_token()

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
    server.log("got token: " .. token_str, server.loglevel.DEBUG)

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
