-- FCRC 平台数据库与账号初始化。
-- 官方 MySQL 镜像会先根据 MYSQL_DATABASE/MYSQL_USER 创建库和用户；
-- 这里再显式声明一次，保证脚本可脱离 Compose 单独执行。

SET NAMES utf8mb4;
SET time_zone = '+08:00';

CREATE DATABASE IF NOT EXISTS fcrc
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'fcrc'@'%' IDENTIFIED BY 'fcrc_pass';
GRANT ALL PRIVILEGES ON fcrc.* TO 'fcrc'@'%';
FLUSH PRIVILEGES;

USE fcrc;
