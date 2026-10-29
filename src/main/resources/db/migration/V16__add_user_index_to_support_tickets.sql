CREATE INDEX idx_support_tickets_user_created ON support_tickets (user_id, created_at DESC);
