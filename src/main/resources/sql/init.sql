-- 创建断路器能耗数据表
CREATE TABLE `breaker_energy_data` (
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