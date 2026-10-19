-- Schéma de configuration du plugin identity-headers (valeurs par défaut = besoins de l'énoncé)
local typedefs = require "kong.db.schema.typedefs"

return {
  name = "identity-headers",
  fields = {
    { consumer = typedefs.no_consumer },
    { protocols = typedefs.protocols_http },
    { config = {
        type = "record",
        fields = {
          -- Seuls ces rôles du claim realm_access.roles sont transmis au backend
          { roles_autorises = {
              type = "array",
              elements = { type = "string" },
              default = { "USER", "MODERATEUR", "ADMIN" },
          } },
          -- Bonus : si renseigné, le claim azp doit valoir ce client (token émis pour la SPA)
          { client_attendu = { type = "string", required = false } },
          -- Bonus : secret partagé avec le backend (X-Gateway-Secret). referenceable : la valeur peut être
          -- une référence de vault, ex. "{vault://env/gateway-secret}", pour ne jamais l'écrire dans kong.yml
          { secret_backend = { type = "string", required = false, referenceable = true } },
        },
    } },
  },
}
