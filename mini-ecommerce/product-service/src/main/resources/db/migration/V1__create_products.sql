CREATE TABLE products (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       NUMERIC(10, 2) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

INSERT INTO products (id, name, description, price, created_at, updated_at) VALUES
    (gen_random_uuid(), 'Laptop Pro 15',   'High-performance laptop', 4999.99, now(), now()),
    (gen_random_uuid(), 'Wireless Mouse',  'Ergonomic wireless mouse', 129.99, now(), now()),
    (gen_random_uuid(), 'Mechanical Keyboard', 'RGB mechanical keyboard', 349.99, now(), now());
