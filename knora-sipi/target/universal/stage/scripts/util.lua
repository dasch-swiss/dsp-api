-- Copyright © 2015-2019 the contributors (see Contributors.md).
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

--- Checks whether a table contains a value.
-- @param table the table to be checked.
-- @param search_value the value to look for.
-- @param a boolean indicating whether the table contains the value.
function table.contains(table, search_value)
  for _, value in pairs(table) do
    if value == search_value then
      return true
    end
  end

  return false
end

--- Return the external protocol
-- We need to be able to run behind a proxy and to configure this easily.
-- Allows to set SIPI_EXTERNAL_PROTOCOL environment variable and use its value.
--
function get_external_protocol()
  local external_protocol = os.getenv("SIPI_EXTERNAL_PROTOCOL")
  if external_protocol == nil then
    external_protocol = "http"
  end
  return external_protocol
end


--- Returns the external hostname
-- We need to be able to run behind a proxy and to configure this easily.
-- Allows to set SIPI_EXTERNAL_HOSTNAME environment variable and use its value.
--
function get_external_hostname()
  local external_hostname = os.getenv("SIPI_EXTERNAL_HOSTNAME")
  if external_hostname == nil then
    external_hostname = config.hostname
  end
  return external_hostname
end

--- Returns the external port
-- We need to be able to run behind a proxy and to configure this easily.
-- Allows to set SIPI_EXTERNAL_PORT environment variable and use its value.
--
function get_external_port()
  local external_port = os.getenv("SIPI_EXTERNAL_PORT")
  if external_port == nil then
    external_port = config.port
  end
  return external_port
end
