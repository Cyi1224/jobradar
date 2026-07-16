package com.jobradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * offerbiu 校招数据同步配置。
 */
@Component
@ConfigurationProperties(prefix = "offerbiu")
public class OfferbiuProperties {

    /** offerbiu API 基础地址 */
    private String baseUrl = "https://offerbiu.com";

    /** 同步相关配置 */
    private Sync sync = new Sync();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Sync getSync() {
        return sync;
    }

    public void setSync(Sync sync) {
        this.sync = sync;
    }

    public static class Sync {
        /** 是否启用每日同步（dev 默认关，prod 默认开） */
        private boolean enabled = false;

        /** 定时 cron 表达式（默认每天 06:00） */
        private String cron = "0 0 6 * * ?";

        /** 每个招聘季最多拉取页数（只取最新几页做增量，不全量同步） */
        private int maxPages = 10;

        /** 每页拉取条数 */
        private int pageSize = 100;

        /** 页间延迟（毫秒），避免对 offerbiu 造成过大压力 */
        private long pageDelayMs = 200;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public int getMaxPages() {
            return maxPages;
        }

        public void setMaxPages(int maxPages) {
            this.maxPages = maxPages;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public long getPageDelayMs() {
            return pageDelayMs;
        }

        public void setPageDelayMs(long pageDelayMs) {
            this.pageDelayMs = pageDelayMs;
        }
    }
}
