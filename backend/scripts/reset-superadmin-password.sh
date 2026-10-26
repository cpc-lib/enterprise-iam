#!/usr/bin/env bash
# Enterprise IAM - 超级系统管理员 (superadmin) 密码应急重置脚本 (Bash)
# 用法: ./reset-superadmin-password.sh [新密码]

set -euo pipefail

NEW_PASSWORD="${1:-Secret123456789!}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_SCRIPT="${SCRIPT_DIR}/reset-superadmin-password.py"

if [ -f "${PYTHON_SCRIPT}" ]; then
    python3 "${PYTHON_SCRIPT}" --password "${NEW_PASSWORD}"
else
    echo "错误: 未找到重置脚本 ${PYTHON_SCRIPT}" >&2
    exit 1
fi
