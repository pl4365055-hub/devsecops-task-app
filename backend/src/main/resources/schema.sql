CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tasks (
    id SERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    assignee_id INTEGER REFERENCES users(id),
    created_by INTEGER REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 初始使用者（密碼均為 BCrypt 加密後的 "password"）
INSERT INTO users (username, password, role) VALUES
    ('admin', '$2a$10$qgnZePe6crvsk7pHF6F9ie1smsC02slQe3ih6HMpLXwJyD/1/ubi.', 'ADMIN'),
    ('user', '$2a$10$qgnZePe6crvsk7pHF6F9ie1smsC02slQe3ih6HMpLXwJyD/1/ubi.', 'USER')
ON CONFLICT (username) DO NOTHING;

INSERT INTO tasks (title, description, status, priority, assignee_id, created_by)
SELECT '更新依賴安全掃描', '檢查並更新專案依賴套件。', 'IN_PROGRESS', 'HIGH', assignee.id, creator.id
FROM users assignee, users creator
WHERE assignee.username = 'user' AND creator.username = 'admin'
    AND NOT EXISTS (SELECT 1 FROM tasks WHERE title = '更新依賴安全掃描');

INSERT INTO tasks (title, description, status, priority, assignee_id, created_by)
SELECT '整理部署文件', '補充 Docker Compose 開發環境說明。', 'PENDING', 'MEDIUM', assignee.id, creator.id
FROM users assignee, users creator
WHERE assignee.username = 'user' AND creator.username = 'admin'
    AND NOT EXISTS (SELECT 1 FROM tasks WHERE title = '整理部署文件');

INSERT INTO tasks (title, description, status, priority, assignee_id, created_by)
SELECT '驗證生產環境備份', '確認備份流程與還原步驟。', 'COMPLETED', 'HIGH', assignee.id, creator.id
FROM users assignee, users creator
WHERE assignee.username = 'admin' AND creator.username = 'admin'
    AND NOT EXISTS (SELECT 1 FROM tasks WHERE title = '驗證生產環境備份');
