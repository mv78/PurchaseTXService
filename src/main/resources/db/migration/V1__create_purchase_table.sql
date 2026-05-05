CREATE TABLE purchase
(
    id                  UUID PRIMARY KEY,
    description         VARCHAR(50)    NOT NULL,
    transaction_date    DATE           NOT NULL,
    purchase_amount_usd DECIMAL(15, 2) NOT NULL,
    created_at          TIMESTAMP      NOT NULL,
    CONSTRAINT chk_amount_positive CHECK (purchase_amount_usd > 0),
    CONSTRAINT chk_description_length CHECK (LENGTH(description) <= 50)
);

CREATE INDEX idx_purchase_transaction_date ON purchase (transaction_date);