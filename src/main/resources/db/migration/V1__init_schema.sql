CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       full_name VARCHAR(255) NOT NULL,
                       bank_account_number VARCHAR(20) NOT NULL,
                       bank_code VARCHAR(10) NOT NULL,
                       created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE wallets (
                         id BIGSERIAL PRIMARY KEY,
                         user_id BIGINT NOT NULL REFERENCES users(id),
                         balance NUMERIC(15, 2) NOT NULL DEFAULT 0,
                         currency VARCHAR(5) NOT NULL DEFAULT 'NGN',
                         updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE transactions (
                              id BIGSERIAL PRIMARY KEY,
                              user_id BIGINT NOT NULL REFERENCES users(id),
                              type VARCHAR(20) NOT NULL,
                              reference VARCHAR(100) NOT NULL UNIQUE,
                              spotflow_reference VARCHAR(100),
                              amount NUMERIC(15, 2) NOT NULL,
                              status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                              created_at TIMESTAMP NOT NULL DEFAULT now(),
                              updated_at TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO users (full_name, bank_account_number, bank_code)
VALUES ('Test Player', '0123456789', '058');