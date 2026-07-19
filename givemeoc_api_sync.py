#!/usr/bin/env python3
"""givemeoc REST API sync - runs daily at 08:00 via crontab"""
import os, json, time, logging, datetime, requests

DIR = "/home/ubuntu/jobradar_scripts"
STATE_FILE = os.path.join(DIR, "jobs_api_state.json")
LOG_FILE = os.path.join(DIR, "sync_api.log")
TOKEN = "please-set-JOBRADAR_SYNC_TOKEN-in-production"
API_URL = "https://www.givemeoc.com/wp-json/givemeoc/v1/companies"
PUSH_URL = "http://localhost:8080/api/jobs/sync"
H = {"User-Agent": "Mozilla/5.0"}
PH = {"X-Sync-Token": TOKEN, "Content-Type": "application/json", "User-Agent": "Mozilla/5.0"}

def setup_logging():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s",
                        datefmt="%Y-%m-%d %H:%M:%S",
                        handlers=[logging.FileHandler(LOG_FILE, encoding="utf-8")])

def load_state():
    if not os.path.exists(STATE_FILE):
        return {}
    with open(STATE_FILE, encoding="utf-8") as f:
        data = json.load(f)
    result = {}
    for r in data:
        key = (r.get("co",""), r.get("positions",""), r.get("recruitType",""), r.get("city",""), r.get("deadline",""))
        result[key] = True
    return result

def map_record(c):
    locs = c.get("locations", []) or []
    positions_list = c.get("positions", []) or []
    pos = " ".join(positions_list)
    if len(pos) > 2000:
        pos = pos[:1997] + "..."
    if not pos or pos.strip() == "":
        return None
    target = (c.get("target_candidates") or "").strip()
    if target:
        target = target + "届"
        target = target.replace("届届", "届")
    # 规范化城市格式
    city = ",".join(locs).strip()
    city = city.replace(", ", ",").replace(" ,", ",")
    if not city or city in ("登录后可见", ""):
        return None

    # 规范化行业
    industry = (c.get("industry") or "").strip()
    if not industry:
        industry = "其他"

    # 规范化类型
    co_type = (c.get("type") or "").strip()
    recruit_type = (c.get("recruitment_type") or "").strip()
    if not recruit_type:
        return None

    return {
        "co": (c.get("name") or "").strip(),
        "coType": co_type,
        "industry": industry,
        "recruitType": recruit_type,
        "target": target,
        "city": city,
        "positions": pos,
        "updatedAt": (c.get("update_time") or "")[:10],
        "deadline": (c.get("deadline") or "招满为止").strip(),
        "applyUrl": "",
        "announceUrl": "",
        "note": "",
    }

def fetch_detail(cid):
    try:
        resp = requests.get(API_URL + "/" + str(cid), headers=H, timeout=10)
        if resp.status_code == 200:
            d = resp.json()
            links = d.get("related_links", []) or []
            return {
                "applyUrl": links[0] if links else "",
                "announceUrl": (d.get("recruitment_notice") or "").strip(),
            }
    except:
        pass
    return {}

def push_batch(records):
    try:
        resp = requests.post(PUSH_URL, json=records, headers=PH, timeout=120)
        if resp.status_code == 200:
            return resp.json()
        logging.error("push failed HTTP {}".format(resp.status_code))
    except Exception as e:
        logging.error("push error: {}".format(e))
    return {"inserted": 0, "skipped": 0}

def main():
    setup_logging()
    logging.info("=" * 50)
    logging.info("givemeoc API sync {}".format(datetime.datetime.now().strftime("%Y-%m-%d %H:%M")))

    state = load_state()
    logging.info("state has {} records".format(len(state)))

    new_records = []
    for page in range(1, 70):
        try:
            resp = requests.get(API_URL, params={"page": page, "per_page": 100}, headers=H, timeout=30)
            resp.raise_for_status()
            companies = resp.json().get("data", [])
            if not companies:
                break
            page_new = 0
            for c in companies:
                rec = map_record(c)
                if rec is None:
                    continue
                key = (rec["co"], rec["positions"], rec["recruitType"], rec["city"], rec["deadline"])
                if key not in state:
                    new_records.append((rec, c["id"]))
                    page_new += 1
            logging.info("page {}: {} companies, {} new".format(page, len(companies), page_new))
            if page_new == 0 and page > 5:
                break
        except Exception as e:
            logging.warning("page {} failed: {}".format(page, e))
            break
        time.sleep(0.3)

    logging.info("candidates: {} new records".format(len(new_records)))

    if new_records:
        for i, (rec, cid) in enumerate(new_records):
            if i % 5 == 0:
                logging.info("fetching details {}/{}...".format(i+1, len(new_records)))
            detail = fetch_detail(cid)
            rec["applyUrl"] = detail.get("applyUrl", "")
            rec["announceUrl"] = detail.get("announceUrl", "")
            time.sleep(0.1)

        total_in = 0
        total_skip = 0
        for i in range(0, len(new_records), 500):
            batch = [r for r, _ in new_records[i:i+500]]
            r = push_batch(batch)
            total_in += r.get("inserted", 0)
            total_skip += r.get("skipped", 0)
            logging.info("batch {}: +{} -{}".format(i//500+1, r.get("inserted",0), r.get("skipped",0)))
            time.sleep(0.5)

        for rec, _ in new_records:
            state[(rec["co"], rec["positions"], rec["recruitType"], rec["city"], rec["deadline"])] = True
        with open(STATE_FILE, "w", encoding="utf-8") as f:
            json.dump([dict(zip(["co","positions","recruitType","city","deadline"], k)) for k in state], f, ensure_ascii=False)
        print("done: +{} inserted, -{} skipped".format(total_in, total_skip))
    else:
        print("no new records")

    logging.info("sync complete")

if __name__ == "__main__":
    main()
