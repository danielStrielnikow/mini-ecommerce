CREATE TABLE inventory (
    id         UUID        NOT NULL PRIMARY KEY,
    product_id UUID        NOT NULL UNIQUE,
    quantity   INTEGER     NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
