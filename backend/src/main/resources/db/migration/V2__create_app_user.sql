-- Phase 1 section 3.2. Role list and tenant rule are PROPOSED, pending approval.
CREATE TABLE app_user (
  id UUID PRIMARY KEY,
  tenant_id UUID REFERENCES tenant(id),
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(30) NOT NULL CHECK (role IN ('SUPER_ADMIN', 'TENANT_ADMIN', 'OPERATIONS_USER')),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
  failed_login_count INT NOT NULL DEFAULT 0,
  locked_until TIMESTAMPTZ,
  last_login_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- tenant_id may be NULL only for the platform super user (pending approval)
  CONSTRAINT ck_app_user_tenant_required CHECK (role = 'SUPER_ADMIN' OR tenant_id IS NOT NULL)
);

CREATE UNIQUE INDEX uq_app_user_email_lower ON app_user (LOWER(email));
CREATE INDEX idx_app_user_tenant_role ON app_user (tenant_id, role);
