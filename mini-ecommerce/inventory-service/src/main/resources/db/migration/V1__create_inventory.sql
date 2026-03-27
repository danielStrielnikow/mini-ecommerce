CREATE TABLE inventory (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id UUID        NOT NULL UNIQUE,
    quantity   INTEGER     NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Sample stock data matching products from product-service
INSERT INTO inventory (product_id, quantity, version, created_at, updated_at)
SELECT p.id, 100, 0, now(), now()
FROM (VALUES
    (gen_random_uuid()),
    (gen_random_uuid()),
    (gen_random_uuid())
) AS p(id);
