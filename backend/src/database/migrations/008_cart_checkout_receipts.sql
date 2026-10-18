CREATE TABLE day2_cart_checkouts (
 order_id text PRIMARY KEY REFERENCES orders(order_id),
 user_id text NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
