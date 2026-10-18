-- identity-headers : transforme le JWT validé par le plugin jwt en headers d'identité pour le backend.
-- Le backend ne voit jamais le token : il ne reçoit que X_USER_ID et X_ROLE_ID.
local jwt_decoder = require "kong.plugins.jwt.jwt_parser"

local IdentityHeaders = {
  -- Après jwt (1450), avant rate-limiting (910) qui compte par X_USER_ID
  PRIORITY = 1000,
  VERSION = "1.0.0",
}

local function refuser(message)
  return kong.response.exit(401, { message = message })
end

function IdentityHeaders:access(conf)
  -- 1. Anti-usurpation : on ne fait jamais confiance aux headers d'identité venus du client
  --    (Nginx ignore déjà les headers contenant "_", on les supprime quand même explicitement)
  kong.service.request.clear_header("X_USER_ID")
  kong.service.request.clear_header("X_ROLE_ID")

  -- 2. Token déjà vérifié (signature, iss, exp) par le plugin jwt, qui le dépose ici
  local token = kong.ctx.shared.authenticated_jwt_token
  if not token then
    return refuser("Unauthorized")
  end
  local jwt, err = jwt_decoder:new(token)
  if err then
    return refuser("Bad token")
  end
  local claims = jwt.claims

  if type(claims.sub) ~= "string" or claims.sub == "" then
    return refuser("Token sans sujet")
  end

  -- Bonus : le token doit avoir été émis pour le client attendu (claim azp)
  if conf.client_attendu and claims.azp ~= conf.client_attendu then
    return refuser("Token non émis pour ce client")
  end

  -- 3. Rôles du realm limités à la liste autorisée, dans l'ordre du token (ex. "MODERATEUR,USER")
  local autorises = {}
  for _, role in ipairs(conf.roles_autorises) do
    autorises[role] = true
  end
  local roles = {}
  local realm_access = claims.realm_access
  if type(realm_access) == "table" and type(realm_access.roles) == "table" then
    for _, role in ipairs(realm_access.roles) do
      if autorises[role] then
        roles[#roles + 1] = role
      end
    end
  end

  kong.service.request.set_header("X_USER_ID", claims.sub)
  if #roles > 0 then
    kong.service.request.set_header("X_ROLE_ID", table.concat(roles, ","))
  end

  -- 4. Le token s'arrête à la gateway
  kong.service.request.clear_header("Authorization")
end

return IdentityHeaders
