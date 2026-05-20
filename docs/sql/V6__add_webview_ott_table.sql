CREATE TABLE IF NOT EXISTS sys_user_ott (
    id CHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ott_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);