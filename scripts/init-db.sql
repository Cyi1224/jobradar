-- ============================================================
-- JobRadar 数据库初始化脚本
-- 用法： mysql -u root -p < scripts/init-db.sql
-- 或：    在 MySQL Workbench 中打开并执行
-- ============================================================

-- 创建数据库（若不存在）
CREATE DATABASE IF NOT EXISTS jobradar
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- 切换到 jobradar 库（后续操作在其上执行）
USE jobradar;

-- 查看建库结果
SELECT CONCAT('数据库 jobradar 已就绪: ', @@character_set_database) AS status;
