package com.labmanage.module.item.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 物料模块公共配置。
 */
@Configuration
public class ItemConfig {

    /** 物料流程与时间计算共用的 UTC 时钟，测试可替换为固定时钟。 */
    @Bean(name = "itemClock")
    public Clock itemClock() {
        return Clock.systemUTC();
    }
}
