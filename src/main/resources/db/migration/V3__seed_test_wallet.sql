INSERT INTO wallets (user_id, balance, currency)
SELECT id, 5000.00, 'NGN' FROM users WHERE full_name = 'Test Player';
