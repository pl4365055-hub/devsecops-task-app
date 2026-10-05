#!/bin/bash
set -e  # 任何命令失败则停止

echo "=== Deploying Task App ==="
echo "Image tag: ${IMAGE_TAG}"

# 1. 登录 Registry
echo "${REGISTRY_PASSWORD}" | docker login ${REGISTRY} -u "${REGISTRY_USERNAME}" --password-stdin

# 2. 拉取最新镜像
docker compose -f docker-compose.prod.yml pull

# 3. 零停机重启（先启动新容器，再停止旧容器）
docker compose -f docker-compose.prod.yml up -d --remove-orphans

# 4. 等待健康检查
echo "Waiting for services to be healthy..."
sleep 10

# 5. 验证
if curl -sf http://localhost:8080/actuator/health > /dev/null; then
    echo "✅ Backend is healthy"
else
    echo "❌ Backend health check failed"
    docker compose -f docker-compose.prod.yml logs backend
    exit 1
fi

# 6. 清理旧镜像
docker image prune -f

echo "=== Deployment complete ==="
