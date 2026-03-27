CREATE TABLE orders (
    id          UUID           NOT NULL PRIMARY KEY,
    product_id  UUID           NOT NULL,
    quantity    INTEGER        NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    total_price NUMERIC(10, 2) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE    NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE    NOT NULL
);
