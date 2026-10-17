-- Phase 1 section 3.8. Append-only: grant the application DB user INSERT and SELECT only.
-- No foreign keys on purpose: audit rows must outlive tenants and users.
CREATE TABLE audit_log (
  id UUID PRIMARY KEY,
  tenant_id UUID,
  user_id UUID,
  event_type VARCHAR(50) NOT NULL,
  outcome VARCHAR(10) NOT NULL,
  trace_id VARCHAR(64),
  ip_address VARCHAR(45),
  detail JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_tenant_created ON audit_log (tenant_id, created_at DESC);
CREATE INDEX idx_audit_log_event_created ON audit_log (event_type, created_at DESC);
