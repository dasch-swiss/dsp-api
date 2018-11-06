--
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

-- validates JWT tokens

require "send_response"

function get_token()
    if server.request == nil or server.request["token"] == nil then
        send_error(400, "Token missing")
        return nil
    end

    token = server.request["token"]

    success, tokendata = server.decode_jwt(token)

    if not success then
        send_error(401, "Invalid token")
        return nil
    end

    token_expiration = tokendata["exp"]

    if token_expiration == nil then
       send_error(401, "Token has no expiry date")
       return nil
    end

    systime = server.systime()

    if (token_expiration <= systime) then
      send_error(401, "Expired token")
      return nil
    end

    return token
end
