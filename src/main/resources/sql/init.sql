-- 创建断路器能耗数据表
CREATE TABLE IF NOT EXISTS `breaker_energy_data` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `breaker_id` varchar(50) NOT NULL COMMENT '开关标识',
  `data_time` datetime NOT NULL COMMENT '数据时标',
  `data_type` int(11) NOT NULL COMMENT '数据类型',
  `data_value` double NOT NULL COMMENT '数据值',
  `city_code` int(11) DEFAULT NULL COMMENT '地市编码',
  `file_time` datetime DEFAULT NULL COMMENT '文件时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_breaker_id` (`breaker_id`),
  KEY `idx_data_type` (`data_type`),
  KEY `idx_file_time` (`file_time`),
  KEY `idx_city_code` (`city_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='断路器能耗数据表';

-- 创建远程文件快捷路径表
CREATE TABLE IF NOT EXISTS `remote_file_bookmark` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `label`       VARCHAR(64)  NOT NULL COMMENT '书签名称',
  `path`        VARCHAR(512) NOT NULL COMMENT '远程路径',
  `server_key`  VARCHAR(128) DEFAULT NULL COMMENT '服务器标识(ip:port:username)，NULL表示全局共享',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_server_key` (`server_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='远程文件快捷路径';

-- 创建服务器连接快捷路径表
CREATE TABLE IF NOT EXISTS `server_connection_bookmark` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `server_key`  VARCHAR(128) NOT NULL COMMENT '服务器标识(ip:port:username)',
  `alias`       VARCHAR(64)  DEFAULT NULL COMMENT '自定义备注名称',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_server_key` (`server_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务器连接快捷路径';