-- * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
-- * SPDX-License-Identifier: Apache-2.0

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
