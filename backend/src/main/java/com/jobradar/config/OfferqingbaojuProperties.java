package com.jobradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * offerqingbaoju 校招数据同步配置。
 */
@Component
@ConfigurationProperties(prefix = "offerqingbaoju")
public class OfferqingbaojuProperties {

    /** offerqingbaoju API 基础地址 */
    private String baseUrl = "https://offerqingbaoju.cn";

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

        /** 要同步的 navigation id 列表（如 61=27届秋招），逗号分隔 */
        private List<Integer> navigationIds = List.of(61);

        /** 每页拉取条数。默认 5000：源站第 2 页起需登录，靠大 per_page 一页匿名拉全当前全量（约 2k 条） */
        private int pageSize = 5000;

        /** 页间延迟（毫秒），避免对 offerqingbaoju 造成过大压力 */
        private long pageDelayMs = 300;

        /** 最大翻页数；0 表示跟随接口返回的 total_pages 不设上限 */
        private int maxPages = 0;

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

        public List<Integer> getNavigationIds() {
            return navigationIds;
        }

        public void setNavigationIds(List<Integer> navigationIds) {
            this.navigationIds = navigationIds;
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

        public int getMaxPages() {
            return maxPages;
        }

        public void setMaxPages(int maxPages) {
            this.maxPages = maxPages;
        }
    }
}
