-- * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

require "env"
require "send_response"
require "strings"
require "util"

--- Extracts a JSON web token from the HTTP request: header ('Authorization' and 'Cookie') or query param 'token'.
-- Decodes token and validates claims exp, aud, iss.
-- Sends an HTTP error if the token is missing or invalid.
-- @return the raw jwt token string, or nil if the token is invalid.
--         or sends an error in the following cases:
--             * 400 if the token is missing
--             * 401 if the token is invalid
function auth_get_jwt_raw()
   local token = _token()
   return token.raw
end

function _token()
    local jwt_raw = _get_jwt_string_from_header_params_or_cookie()
    local decoded = _decode_jwt(jwt_raw)
    if decoded == nil then
        return { raw = nil, decoded = nil }
    else
        return { raw = jwt_raw, decoded = decoded }
    end
end

--- Extracts a JSON web token from the HTTP request: header ('Authorization' and 'Cookie') or query param 'token'.
-- Decodes token and validates claims exp, aud, iss.
-- Sends an HTTP error if the token is missing or invalid.
-- @return the result of server.decode_jwt token, or nil if the token is invalid.
--         or sends an error in the following cases:
--             * 400 if the token is missing
--             * 401 if the token is invalid
function auth_get_jwt_decoded()
    local token = _token()
    return token.decoded
end

function _decode_jwt(token_str)
    -- decode token
    server.log("authentication: decoding jwt token", server.loglevel.LOG_DEBUG)
    local success, decoded_token = server.decode_jwt(token_str)
    if not success then
        send_error(401, "Invalid token, unable to decode jwt.")
        return nil
    end

    -- check expiration date of token
    local expiration_date = decoded_token["exp"]
    if expiration_date == nil then
        send_error(401, "Invalid 'exp' (expiration date) in token, token has no expiry date.")
        return nil
    end
    local systime = server.systime()
    if (expiration_date <= systime) then
        send_error(401, "Invalid 'exp' (expiration date) in token, token is expired.")
        return nil
    end

    -- check audience of token
    local audience = decoded_token["aud"]
    local expected_audience = "Sipi"
    if audience == nil or not table.contains(audience, expected_audience) then
        send_error(401, "Invalid 'aud' (audience) in token, expected: " .. expected_audience .. ".")
        return nil
    end

    -- check issuer of token
    local token_issuer = env_dsp_api_host_port()
    if decoded_token["iss"] ~= token_issuer then
        send_error(401, "Invalid 'iss' (issuer) in token, expected: " .. token_issuer .. ".")
        return nil
    end
    return decoded_token
end

--- Extract the JSON web token from the HTTP request header or query parameter.
function _get_jwt_string_from_header_params_or_cookie()
    local from_header = _get_jwt_token_from_auth_header()
    if from_header ~= nil then
        server.log("authentication: token found in authorization header", server.loglevel.LOG_DEBUG)
        return from_header
    end

    local from_query_param = _get_jwt_token_from_query_param()
    if from_query_param ~= nil then
        server.log("authentication: token found in query param", server.loglevel.LOG_DEBUG)
        return from_query_param
    end

    local from_cookie = _get_jwt_token_from_cookie()
    if from_cookie ~= nil then
        server.log("authentication: token found in cookie header", server.loglevel.LOG_DEBUG)
        return from_cookie
    end

    server.log("authentication: no token found in request", server.loglevel.LOG_DEBUG)
    send_error(400, "Token missing")
    return nil
end

--- Extract the "Bearer" JSON web token from the HTTP request "Authorization" header.
-- @return the token
--         or nil if the header is missing
--         or nil if the header value is not a "Bearer" token.
function _get_jwt_token_from_auth_header()
    server.log("authentication: checking for jwt token in authorization header", server.loglevel.LOG_DEBUG)
    local auth_header = _get_auth_header()
    local bearer_prefix = "Bearer "
    if str_starts_with(auth_header, bearer_prefix) then
        return str_strip_prefix(auth_header, bearer_prefix)
    else
        return nil
    end
end

--- Extract the "Authorization" header from the HTTP request.
-- @return the header value or nil if the header is missing.
function _get_auth_header()
    return _get_header("authorization")
end

--- Extract the "Cookie" header from the HTTP request.
-- @return the header value or nil if the header is missing.
function _get_cookie_header()
    return _get_header("cookie")
end

--- Extract a header from the HTTP request.
-- @param key the header key
-- @return the header value or nil if the header is missing.
function _get_header(key)
    if server.header ~= nil and server.header[key] ~= nil then
        return server.header[key]
    else
        return nil
    end
end

--- Extract the JSON web token from the HTTP request query parameter "token".
-- @return the header value or nil if the header is missing.
function _get_jwt_token_from_query_param()
    server.log("authentication: checking for jwt token in query param token", server.loglevel.LOG_DEBUG)
    if server.request ~= nil then
        return server.request["token"]
    else
        return nil
    end
end

--- Extracts the jwt token from the cookie.
-- @return jwt token or nil if the cookie is missing or invalid.
function _get_jwt_token_from_cookie()
    server.log("authentication: checking for jwt token in cookie header", server.loglevel.LOG_DEBUG)
    local cookie_header_value = _get_cookie_header()
    if cookie_header_value == nil then
        return nil
    end
    local cookie_name = env_knora_authentication_cookie_name()
    local jwt_token = str_strip_prefix(cookie_header_value, cookie_name .. "=")
    return jwt_token
end
