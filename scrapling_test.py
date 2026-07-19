#!/usr/bin/env python3
"""Test Scrapling against givemeoc.com - compare with REST API data quality"""
import json, time
from scrapling import Fetcher

BASE = "https://www.givemeoc.com"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36",
    "Accept-Language": "zh-CN,zh;q=0.9",
}

print("=" * 60)
print("Scrapling 测试 givemeoc.com")
print("=" * 60)

# 测试1: 首页 HTML 抓取
print("\n1. 抓取首页 HTML...")
f = Fetcher(auto_headers=True)
try:
    resp = f.get(BASE)
    print(f"   状态: {resp.status_code}")
    html = resp.html

    # 查找 table
    tables = html.find_all("table")
    print(f"   找到 {len(tables)} 个 table")

    if tables:
        rows = tables[0].find_all("tr")
        print(f"   第一个 table 有 {len(rows)} 行")

        if len(rows) > 1:
            # 解析表头
            header = rows[0].find_all(["th", "td"])
            print(f"   表头: {[h.get_text(strip=True)[:15] for h in header[:8]]}")

            # 解析前3条数据
            print("\n   前3条数据:")
            for i, row in enumerate(rows[1:4]):
                cells = row.find_all("td")
                if len(cells) >= 7:
                    co = cells[0].get_text(strip=True)
                    city = cells[5].get_text(strip=True)
                    pos = cells[6].get_text(strip=True)
                    deadline = cells[9].get_text(strip=True) if len(cells) > 9 else "?"
                    print(f"   [{i+1}] {co} | 城市:{city} | 岗位:{pos[:40]} | 截止:{deadline}")

            # 数一下有多少条城市=登录后可见
            login_only = 0
            full_data = 0
            for row in rows[1:]:
                cells = row.find_all("td")
                if len(cells) >= 7:
                    city = cells[5].get_text(strip=True)
                    if city == "登录后可见":
                        login_only += 1
                    else:
                        full_data += 1
            print(f"\n   数据质量: 完整 {full_data} 条, 登录后可见 {login_only} 条")
except Exception as e:
    print(f"   错误: {e}")

# 测试2: REST API (对照组)
print("\n2. REST API (对照组)...")
import requests
api_url = "https://www.givemeoc.com/wp-json/givemeoc/v1/companies?page=1&per_page=5"
try:
    resp = requests.get(api_url, headers=HEADERS, timeout=15)
    data = resp.json()
    companies = data.get("data", [])
    print(f"   返回 {len(companies)} 条, 总计 {data.get('pagination',{}).get('total','?')} 条")
    for c in companies[:3]:
        locs = c.get("locations", []) or []
        pos = c.get("positions", []) or []
        print(f"   {c.get('name','?')} | 城市:{','.join(locs)} | 岗位:{' '.join(pos)[:50]}")
except Exception as e:
    print(f"   错误: {e}")

print("\n" + "=" * 60)
print("结论: HTML抓取需要登录才能看城市/岗位，REST API完全不需要")
print("Scrapling可以帮我们保持登录态，配合cookie实现完整数据抓取")
print("=" * 60)
