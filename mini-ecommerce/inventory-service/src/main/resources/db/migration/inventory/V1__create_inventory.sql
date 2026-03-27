CREATE TABLE inventory (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id UUID        NOT NULL UNIQUE,
    quantity   INTEGER     NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
