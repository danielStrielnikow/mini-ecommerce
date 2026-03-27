CREATE TABLE products (
    id          UUID           NOT NULL PRIMARY KEY,
    name        VARCHAR(255)   NOT NULL,
    description TEXT,
    price       NUMERIC(10, 2) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE    NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE    NOT NULL
);

INSERT INTO products (id, name, description, price, created_at, updated_at) VALUES
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Laptop Pro 15',      'High-performance laptop',  4999.99, now(), now()),
    ('b1ffcd88-8d1a-4fa9-cc7e-7cc0ce491b22', 'Wireless Mouse',      'Ergonomic wireless mouse',  129.99, now(), now()),
    ('c2aade77-7e2b-4ba0-dd8f-8dd1df502c33', 'Mechanical Keyboard', 'RGB mechanical keyboard',   349.99, now(), now());
