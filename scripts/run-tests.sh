#!/usr/bin/env bash
# ============================================================
# JeePay 测试一键执行脚本
#
# 作用：
#   1. 校验 Docker 基础设施（MySQL/Redis/RocketMQ）已就绪
#   2. 幂等加载 docs/test/prepare-*.sql 测试数据
#   3. 跑全部集成测试（jeepay-payment + jeepay-merchant，共 84 用例）
#
# 前置：docker compose up -d mysql redis rocketmq-namesrv rocketmq-broker
# 用法：bash scripts/run-tests.sh
# ============================================================
set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m'

MYSQL_CONTAINER="jeepay-mysql"
MYSQL_DB="jeepaydb"
MYSQL_ROOT_PASS="rootroot"
SEED_DIR="docs/test"

# ── 1. 校验 Docker 容器 ──
echo -e "${YELLOW}[1/3] 校验 Docker 基础设施容器...${NC}"
for name in "$MYSQL_CONTAINER" "jeepay-redis" "jeepay-rocketmq-namesrv" "jeepay-rocketmq-broker"; do
  if ! docker ps --format '{{.Names}}' | grep -qx "$name"; then
    echo -e "${RED}[失败] 容器 $name 未运行。${NC}"
    echo -e "${RED}请先执行：docker compose up -d mysql redis rocketmq-namesrv rocketmq-broker${NC}"
    exit 1
  fi
done
echo -e "${GREEN}基础容器就绪（MySQL/Redis/RocketMQ）${NC}"

# ── 2. 幂等加载测试数据 ──
echo -e "${YELLOW}[2/3] 加载测试 seed 数据（INSERT IGNORE，可重复执行）...${NC}"
for sql in "$SEED_DIR"/prepare-*.sql; do
  echo "  > $(basename "$sql")"
  docker exec -i "$MYSQL_CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASS" "$MYSQL_DB" < "$sql"
done
echo -e "${GREEN}seed 数据加载完成${NC}"

# ── 3. 跑测试 ──
echo -e "${YELLOW}[3/3] 运行集成测试...${NC}"
mvn -B -Dsurefire.failIfNoSpecifiedTests=false -pl jeepay-payment,jeepay-merchant -am test

echo -e "${GREEN}全部测试执行完成${NC}"
