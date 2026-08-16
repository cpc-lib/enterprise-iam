# SPEC 15 — Dynamic Field Permission Engine

## V1.0 Frozen Baseline

READ/WRITE/MASK/HIDDEN；捕获 submitted fields，区分缺失与显式 null。
支持嵌套路径、数组路径规范化、Jackson Response Filter、Mask SPI、MyBatis UpdateColumnGuard、导入导出权限。
