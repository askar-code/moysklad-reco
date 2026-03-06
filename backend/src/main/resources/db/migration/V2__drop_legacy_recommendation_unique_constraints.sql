DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT con.conname
    INTO constraint_name
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace nsp ON nsp.oid = con.connamespace
    WHERE nsp.nspname = 'public'
      AND rel.relname = 'base_product_stats'
      AND con.contype = 'u'
      AND pg_get_constraintdef(con.oid) = 'UNIQUE (account_id, base_product_id)';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE public.base_product_stats DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT con.conname
    INTO constraint_name
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    JOIN pg_namespace nsp ON nsp.oid = con.connamespace
    WHERE nsp.nspname = 'public'
      AND rel.relname = 'recommendation_pairs'
      AND con.contype = 'u'
      AND pg_get_constraintdef(con.oid) = 'UNIQUE (account_id, base_product_id, recommended_product_id)';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE public.recommendation_pairs DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;
