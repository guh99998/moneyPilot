-- Pop-up de primeiro contato (spec 027): preferência "Mostrar ao iniciar", marcada por padrão.
ALTER TABLE onboarding ADD COLUMN show_on_start BOOLEAN NOT NULL DEFAULT TRUE;
