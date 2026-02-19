#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Monthly Mac Usage Report
========================
Generates a report containing:
  1. App usage breakdown from macOS Screen Time (past 30 days)
  2. Installed apps that are unused or rarely used
  3. Disk usage breakdown of user folders

Saves as a styled HTML report and optionally emails it.

Credentials are read from stdin as JSON:
  {
    "credentials": {
      "smtp": {
        "host": "smtp.gmail.com",
        "port": "587",
        "username": "you@gmail.com",
        "password": "app-password"
      }
    }
  }

Pipe from Vault:
  vault kv get -format=json secret/my-scheduler/smtp | jq '{credentials:{smtp:.data.data}}' | python3 demo/my_mac_app_usage.py

Requirements:
  - pip install matplotlib
  - macOS Screen Time enabled
  - Full Disk Access for the running process (Terminal/IDE)
"""

import sqlite3
import subprocess
import os
import sys
import json
import smtplib
import base64
from io import BytesIO
from pathlib import Path
from datetime import datetime, timedelta
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

# ──────────────────────────────────────────────
# CONFIG
# ──────────────────────────────────────────────
EMAIL_TO = "henry.e.brown@icloud.com"
DAYS_BACK = 1
TOP_N_CHART = 15
SAVE_LOCAL = True
REPORT_DIR = os.path.dirname(os.path.abspath(__file__))

KNOWLEDGE_DB = os.path.expanduser(
    "~/Library/Application Support/Knowledge/knowledgeC.db"
)
MAC_EPOCH_OFFSET = 978307200

_BUNDLE_NAME_CACHE = {}


# ──────────────────────────────────────────────
# STDIN CREDENTIAL LOADING
# ──────────────────────────────────────────────
def load_credentials_from_stdin():
    """
    Reads JSON credentials from stdin.
    Returns the smtp credential dict or None if stdin is empty / not piped.
    """
    if sys.stdin.isatty():
        return None

    try:
        payload = json.load(sys.stdin)
        return payload.get("credentials", {}).get("smtp")
    except (json.JSONDecodeError, KeyError) as e:
        print(f"WARNING: Failed to parse stdin credentials: {e}")
        return None


# ──────────────────────────────────────────────
# PLIST HELPERS
# ──────────────────────────────────────────────
def _read_plist_key(app_path, key):
    try:
        result = subprocess.run(
            ["defaults", "read", str(app_path / "Contents/Info"), key],
            capture_output=True, text=True, timeout=5
        )
        if result.returncode == 0:
            return result.stdout.strip()
    except Exception:
        pass
    return None


def bundle_to_name(bundle_id):
    if bundle_id in _BUNDLE_NAME_CACHE:
        return _BUNDLE_NAME_CACHE[bundle_id]
    parts = bundle_id.split(".")
    return parts[-1] if parts else bundle_id


def format_duration(seconds):
    if not seconds or seconds < 0:
        return "0m"
    hours = int(seconds // 3600)
    minutes = int((seconds % 3600) // 60)
    if hours > 0:
        return f"{hours}h {minutes}m"
    return f"{minutes}m"


def format_size(size_mb):
    if size_mb >= 1024:
        return f"{size_mb / 1024:.1f} GB"
    return f"{size_mb} MB"


# ──────────────────────────────────────────────
# SCREEN TIME USAGE
# ──────────────────────────────────────────────
def query_screen_time(days_back):
    if not Path(KNOWLEDGE_DB).exists():
        print("WARNING: Screen Time database not found. Is Screen Time enabled?")
        return {}

    conn = sqlite3.connect(f"file:{KNOWLEDGE_DB}?mode=ro", uri=True)

    cutoff = datetime.now() - timedelta(days=days_back)
    cutoff_mac = cutoff.timestamp() - MAC_EPOCH_OFFSET

    sql = """
          SELECT
              ZOBJECT.ZVALUESTRING AS app_bundle,
              SUM(ZOBJECT.ZENDDATE - ZOBJECT.ZSTARTDATE) AS total_seconds
          FROM ZOBJECT
          WHERE ZSTREAMNAME = '/app/usage'
            AND ZSTARTDATE > ?
            AND ZVALUESTRING IS NOT NULL
          GROUP BY ZOBJECT.ZVALUESTRING
          ORDER BY total_seconds DESC
          """

    cursor = conn.execute(sql, (cutoff_mac,))
    results = {row[0]: row[1] for row in cursor.fetchall()}
    conn.close()
    return results


# ──────────────────────────────────────────────
# INSTALLED APPS SCAN
# ──────────────────────────────────────────────
def get_installed_apps():
    apps = {}
    search_dirs = [
        Path("/Applications"),
        Path.home() / "Applications",
        ]

    for search_dir in search_dirs:
        if not search_dir.exists():
            continue
        for app_path in search_dir.rglob("*.app"):
            parents = [p.suffix for p in app_path.parents]
            if ".app" in parents:
                continue

            name = app_path.stem
            bundle_id = _read_plist_key(app_path, "CFBundleIdentifier")
            display_name = (
                    _read_plist_key(app_path, "CFBundleDisplayName")
                    or _read_plist_key(app_path, "CFBundleName")
                    or name
            )
            size_mb = get_app_size_mb(app_path)

            if bundle_id:
                _BUNDLE_NAME_CACHE[bundle_id] = display_name

            apps[bundle_id or name] = {
                "name": display_name,
                "path": str(app_path),
                "bundle_id": bundle_id,
                "size_mb": size_mb,
            }

    return apps


def get_app_size_mb(app_path):
    try:
        result = subprocess.run(
            ["du", "-sm", str(app_path)],
            capture_output=True, text=True, timeout=10
        )
        if result.returncode == 0:
            return int(result.stdout.split()[0])
    except Exception:
        pass
    return 0


def find_unused_apps(installed_apps, usage_data):
    unused = []

    for key, app_info in installed_apps.items():
        bundle_id = app_info.get("bundle_id") or key
        usage_seconds = usage_data.get(bundle_id, 0)

        if usage_seconds == 0:
            for st_bundle, seconds in usage_data.items():
                if app_info["name"].lower() in st_bundle.lower():
                    usage_seconds = seconds
                    break

        if usage_seconds < 300:
            unused.append({
                "name": app_info["name"],
                "size_mb": app_info["size_mb"],
                "usage": format_duration(usage_seconds),
                "path": app_info["path"],
            })

    unused.sort(key=lambda x: x["size_mb"], reverse=True)
    return unused


# ──────────────────────────────────────────────
# DISK USAGE
# ──────────────────────────────────────────────
def get_disk_usage():
    """Scan user folders and return size + file count for each."""
    home = Path.home()

    scan_folders = [
        "Desktop", "Documents", "Downloads", "Movies", "Music",
        "Pictures", "Developer", "Projects", "Library/Caches",
    ]

    results = []
    total_files = 0
    total_mb = 0

    for folder_name in scan_folders:
        folder = home / folder_name
        if not folder.exists():
            continue

        try:
            du_result = subprocess.run(
                ["du", "-sm", str(folder)],
                capture_output=True, text=True, timeout=30
            )
            size_mb = int(du_result.stdout.split()[0]) if du_result.returncode == 0 else 0
        except Exception:
            size_mb = 0

        try:
            find_result = subprocess.run(
                ["find", str(folder), "-type", "f"],
                capture_output=True, text=True, timeout=30
            )
            file_count = len(find_result.stdout.strip().split("\n")) if find_result.returncode == 0 and find_result.stdout.strip() else 0
        except Exception:
            file_count = 0

        if size_mb > 0:
            results.append({
                "name": folder_name,
                "path": str(folder),
                "size_mb": size_mb,
                "file_count": file_count,
            })
            total_files += file_count
            total_mb += size_mb

    big_folders = [r for r in results if r["size_mb"] > 1024]
    subfolder_breakdown = {}

    for folder_info in big_folders:
        folder = Path(folder_info["path"])
        subs = []
        try:
            for child in sorted(folder.iterdir()):
                if child.name.startswith("."):
                    continue
                if not child.is_dir():
                    continue
                try:
                    du_result = subprocess.run(
                        ["du", "-sm", str(child)],
                        capture_output=True, text=True, timeout=15
                    )
                    sub_mb = int(du_result.stdout.split()[0]) if du_result.returncode == 0 else 0
                except Exception:
                    sub_mb = 0

                if sub_mb >= 100:
                    subs.append({"name": child.name, "size_mb": sub_mb})
        except PermissionError:
            pass

        if subs:
            subs.sort(key=lambda x: x["size_mb"], reverse=True)
            subfolder_breakdown[folder_info["name"]] = subs[:10]

    results.sort(key=lambda x: x["size_mb"], reverse=True)

    return {
        "folders": results,
        "subfolders": subfolder_breakdown,
        "total_files": total_files,
        "total_mb": total_mb,
    }


# ──────────────────────────────────────────────
# SYSTEM DISK OVERVIEW
# ──────────────────────────────────────────────
def get_system_disk_overview():
    """Get overall disk capacity and breakdown of where space is used."""
    overview = {
        "total_gb": 0,
        "used_gb": 0,
        "free_gb": 0,
        "pct_used": 0,
        "categories": [],
    }

    try:
        result = subprocess.run(
            ["diskutil", "info", "/"],
            capture_output=True, text=True, timeout=10
        )
        if result.returncode == 0:
            for line in result.stdout.split("\n"):
                line = line.strip()
                if "Container Total Space" in line:
                    parts = line.split(":")[-1].strip().split()
                    overview["total_gb"] = round(float(parts[0]))
                elif "Volume Available Space" in line or "Container Free Space" in line:
                    parts = line.split(":")[-1].strip().split()
                    overview["free_gb"] = round(float(parts[0]))
    except Exception:
        pass

    if overview["total_gb"] == 0:
        try:
            result = subprocess.run(
                ["df", "-g", "/"],
                capture_output=True, text=True, timeout=10
            )
            if result.returncode == 0:
                lines = result.stdout.strip().split("\n")
                if len(lines) >= 2:
                    parts = lines[1].split()
                    overview["total_gb"] = int(parts[1])
                    overview["free_gb"] = int(parts[3])
        except Exception:
            pass

    overview["used_gb"] = overview["total_gb"] - overview["free_gb"]
    overview["pct_used"] = round(overview["used_gb"] / overview["total_gb"] * 100) if overview["total_gb"] > 0 else 0

    scan_paths = [
        ("/Applications", "Applications"),
        ("/Library", "System Library"),
        (os.path.expanduser("~/Library/Caches"), "Caches"),
        (os.path.expanduser("~/Library/Application Support"), "App Data"),
        (os.path.expanduser("~/Library/Developer"), "Developer (Xcode/Simulators)"),
        (os.path.expanduser("~/Library/Containers"), "App Containers"),
        (os.path.expanduser("~/Library/Group Containers"), "App Group Data"),
        ("/opt/homebrew", "Homebrew"),
        ("/usr/local", "Homebrew (Intel)"),
        (os.path.expanduser("~/.docker"), "Docker"),
        (os.path.expanduser("~/.gradle"), "Gradle Cache"),
        (os.path.expanduser("~/.m2"), "Maven Cache"),
        (os.path.expanduser("~/.npm"), "npm Cache"),
        (os.path.expanduser("~/.sdkman"), "SDKMAN"),
        (os.path.expanduser("~/.cargo"), "Rust/Cargo"),
        (os.path.expanduser("~/Library/Caches/com.apple.dt.Xcode"), "Xcode Cache"),
    ]

    for path, label in scan_paths:
        if not os.path.exists(path):
            continue

        try:
            result = subprocess.run(
                ["du", "-sg", path],
                capture_output=True, text=True, timeout=30
            )
            if result.returncode == 0:
                size_gb = int(result.stdout.split()[0])
                if size_gb >= 1:
                    overview["categories"].append({
                        "name": label,
                        "path": path,
                        "size_gb": size_gb,
                    })
        except Exception:
            pass

    overview["categories"].sort(key=lambda x: x["size_gb"], reverse=True)
    return overview


# ──────────────────────────────────────────────
# LOGIN ITEMS (startup apps)
# ──────────────────────────────────────────────
def get_login_items():
    """Get apps that launch at startup via multiple sources."""
    items = []
    seen = set()

    try:
        result = subprocess.run(
            ["osascript", "-e",
             'tell application "System Events" to get the name of every login item'],
            capture_output=True, text=True, timeout=10
        )
        if result.returncode == 0 and result.stdout.strip():
            for name in result.stdout.strip().split(", "):
                name = name.strip()
                if name and name not in seen:
                    items.append({"name": name, "source": "Login Items"})
                    seen.add(name)
    except Exception:
        pass

    launch_agent_dirs = [
        Path.home() / "Library/LaunchAgents",
        Path("/Library/LaunchAgents"),
        ]

    for agent_dir in launch_agent_dirs:
        if not agent_dir.exists():
            continue
        for plist in agent_dir.glob("*.plist"):
            name = plist.stem
            parts = name.split(".")
            if len(parts) >= 3:
                display = " ".join(parts[1:]).replace("-", " ").title()
            else:
                display = name.replace("-", " ").replace("_", " ").title()

            # trim long names
            if display > display[:40]:
                display = display[:40] + "..."


            if display not in seen:
                source = "User Agent" if "home" in str(agent_dir).lower() else "System Agent"
                items.append({"name": display, "source": source})
                seen.add(display)

    return items


# ──────────────────────────────────────────────
# GIT ACTIVITY
# ──────────────────────────────────────────────
def get_git_activity(days_back):
    """Find all git repos and summarise commit activity."""
    repos = []
    total_commits = 0
    daily_counts = {}

    home = str(Path.home())
    print(f"      Searching for repos in {home}")
    skip_dirs = {".docker", ".gradle", ".m2", ".npm", ".cargo", ".sdkman",
                 "node_modules", ".Trash", "Library", ".cache", ".local"}

    try:
        result = subprocess.run(
            ["find", home, "-name", ".git", "-type", "d", "-maxdepth", "5"],
            capture_output=True, text=True, timeout=30
        )
        git_dirs = [d for d in result.stdout.strip().split("\n") if d]
        print(f"      Found {len(git_dirs)} .git directories (find exit code: {result.returncode})")
        if not git_dirs:
            return {"repos": [], "total_commits": 0, "daily_counts": {}}
    except Exception as e:
        print(f"      Failed to scan: {e}")
        return {"repos": [], "total_commits": 0, "daily_counts": {}}

    since_date = (datetime.now() - timedelta(days=days_back)).strftime("%Y-%m-%d")

    git_user = ""
    try:
        user_result = subprocess.run(
            ["git", "config", "user.name"],
            capture_output=True, text=True, timeout=5
        )
        if user_result.returncode == 0:
            git_user = user_result.stdout.strip()
            print(f"      Filtering commits by author: {git_user}")
    except Exception:
        pass

    if not git_user:
        print("      WARNING: No git user.name configured, skipping git activity")
        return {"repos": [], "total_commits": 0, "daily_counts": {}}

    for git_dir in git_dirs:
        repo_path = str(Path(git_dir).parent)

        if any(skip in repo_path for skip in skip_dirs):
            continue

        try:
            log_cmd = [
                "git", "-C", repo_path, "log",
                f"--since={since_date}",
                "--author-date-order",
                "--format=%ad",
                "--date=short",
                f"--author={git_user}",
            ]

            log_result = subprocess.run(
                log_cmd, capture_output=True, text=True, timeout=10
            )
            if log_result.returncode != 0 or not log_result.stdout.strip():
                continue

            dates = log_result.stdout.strip().split("\n")
            commit_count = len(dates)

            if commit_count == 0:
                continue

            repo_name = Path(repo_path).name

            for d in dates:
                daily_counts[d] = daily_counts.get(d, 0) + 1

            repos.append({
                "name": repo_name,
                "path": repo_path,
                "commits": commit_count,
            })

            total_commits += commit_count
            print(f"      {repo_name}: {commit_count} commits")

        except Exception:
            continue

    git_email = ""
    try:
        email_result = subprocess.run(
            ["git", "config", "user.email"],
            capture_output=True, text=True, timeout=5
        )
        if email_result.returncode == 0:
            git_email = email_result.stdout.strip()
    except Exception:
        pass

    repos.sort(key=lambda x: x["commits"], reverse=True)

    return {
        "repos": repos,
        "total_commits": total_commits,
        "daily_counts": daily_counts,
        "git_user": git_user,
        "git_email": git_email,
    }


# ──────────────────────────────────────────────
# CHART
# ──────────────────────────────────────────────
def generate_chart(usage_data):
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        import matplotlib.ticker as ticker
    except ImportError:
        print("WARNING: matplotlib not installed. Skipping chart.")
        return None

    sorted_usage = sorted(usage_data.items(), key=lambda x: x[1], reverse=True)[:TOP_N_CHART]

    names = [bundle_to_name(b) for b, _ in reversed(sorted_usage)]
    hours = [s / 3600 for _, s in reversed(sorted_usage)]

    bg_color = "#fafbfc"
    card_bg = "#ffffff"
    text_color = "#24292f"
    muted_color = "#656d76"
    bar_color = "#2f81f7"
    grid_color = "#d1d9e0"

    fig, ax = plt.subplots(figsize=(11, max(5.5, len(names) * 0.5)))
    fig.set_facecolor(bg_color)
    ax.set_facecolor(card_bg)

    bars = ax.barh(
        range(len(names)), hours,
        color=bar_color, edgecolor=card_bg, linewidth=0.5,
        height=0.55
    )

    for bar, h in zip(bars, hours):
        label = f"{h:.1f}h" if h >= 1 else f"{h * 60:.0f}m"
        ax.text(
            bar.get_width() + 0.15, bar.get_y() + bar.get_height() / 2,
            label, va="center", fontsize=8.5, color=muted_color,
            fontfamily="monospace"
        )

    ax.set_yticks(range(len(names)))
    ax.set_yticklabels(names, fontsize=10, color=text_color, fontfamily="monospace")
    ax.set_xlabel("Hours", fontsize=10, color=muted_color, fontfamily="monospace", labelpad=10)

    ax.set_title(
        f"Top {len(sorted_usage)} Apps  |  Past {DAYS_BACK} Days",
        fontsize=13, fontweight="600", color=text_color,
        fontfamily="monospace", pad=15, loc="left"
    )

    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.spines["bottom"].set_color(grid_color)
    ax.spines["left"].set_color(grid_color)
    ax.tick_params(axis="x", colors=muted_color, labelsize=9)
    ax.xaxis.set_major_formatter(ticker.FormatStrFormatter("%.0f"))
    ax.grid(axis="x", color=grid_color, linewidth=0.4, alpha=0.6)

    plt.tight_layout()

    buf = BytesIO()
    fig.savefig(buf, format="png", dpi=150, bbox_inches="tight", facecolor=bg_color)
    plt.close(fig)
    buf.seek(0)

    return base64.b64encode(buf.read()).decode("utf-8")


# ──────────────────────────────────────────────
# HTML REPORT
# ──────────────────────────────────────────────
def build_html_report(usage_data, unused_apps, chart_b64, disk_usage, login_items, system_disk, git_activity):
    now = datetime.now()
    month_name = (now - timedelta(days=1)).strftime("%B %Y")
    total_seconds = sum(usage_data.values())
    total_apps = len(usage_data)

    total_unused_mb = sum(a["size_mb"] for a in unused_apps)
    total_unused_gb = total_unused_mb / 1024

    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <style>
        @import url('https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@300;400;500;600;700&display=swap');

        * {{ margin: 0; padding: 0; box-sizing: border-box; }}

        body {{
            font-family: 'JetBrains Mono', 'SF Mono', 'Fira Code', monospace;
            background: #f0f2f5;
            color: #24292f;
            max-width: 800px;
            margin: 0 auto;
            padding: 40px 24px;
            line-height: 1.6;
            font-size: 13px;
        }}

        .card {{
            background: #ffffff;
            border: 1px solid #d1d9e0;
            border-radius: 10px;
            padding: 24px;
            margin-bottom: 16px;
        }}

        .header {{
            padding-bottom: 16px;
        }}

        .header h1 {{
            font-size: 20px;
            font-weight: 700;
            color: #24292f;
        }}

        .header .subtitle {{
            font-size: 11px;
            color: #656d76;
            margin-top: 4px;
        }}

        .stats-row {{
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 12px;
            margin-top: 16px;
            margin-bottom: 16px;
        }}

        .stat {{
            background: #ffffff;
            border: 1px solid #d1d9e0;
            border-radius: 10px;
            padding: 16px;
            text-align: center;
        }}

        .stat .value {{
            font-size: 22px;
            font-weight: 700;
            color: #2f81f7;
        }}

        .stat .value.warn {{ color: #bf8700; }}
        .stat .value.danger {{ color: #cf222e; }}

        .stat .label {{
            font-size: 9px;
            color: #656d76;
            text-transform: uppercase;
            letter-spacing: 1.5px;
            margin-top: 4px;
        }}

        h2 {{
            font-size: 12px;
            font-weight: 600;
            color: #24292f;
            text-transform: uppercase;
            letter-spacing: 1.5px;
            margin-bottom: 16px;
        }}

        .chart-wrap {{
            text-align: center;
        }}

        .chart-wrap img {{
            width: 100%;
            max-width: 760px;
            border-radius: 6px;
        }}

        table {{
            width: 100%;
            border-collapse: collapse;
            font-size: 12px;
        }}

        thead th {{
            color: #656d76;
            font-weight: 500;
            font-size: 10px;
            text-transform: uppercase;
            letter-spacing: 1px;
            padding: 8px 10px;
            text-align: left;
            border-bottom: 2px solid #d1d9e0;
        }}

        thead th.right {{ text-align: right; }}

        tbody td {{
            padding: 7px 10px;
            border-bottom: 1px solid #eef1f4;
        }}

        tbody td.right {{ text-align: right; }}

        tbody tr:hover td {{ background: #f6f8fa; }}

        .rank {{ color: #b0b8c1; font-weight: 400; }}
        .app-name {{ color: #24292f; font-weight: 500; }}
        .time {{ color: #2f81f7; font-weight: 500; }}
        .pct {{ color: #656d76; }}

        .mini-bar-track {{
            display: inline-block;
            width: 80px;
            height: 4px;
            background: #eef1f4;
            border-radius: 2px;
            vertical-align: middle;
            overflow: hidden;
        }}

        .mini-bar-fill {{
            display: block;
            height: 100%;
            background: #2f81f7;
            border-radius: 2px;
        }}

        .mini-bar-fill.disk {{ background: #8250df; }}

        .unused-summary {{
            font-size: 12px;
            color: #656d76;
            margin-bottom: 16px;
            padding: 12px 14px;
            background: #fff5f5;
            border-left: 3px solid #cf222e;
            border-radius: 0 8px 8px 0;
        }}

        .unused-summary strong {{ color: #cf222e; }}
        .unused-size {{ color: #cf222e; font-weight: 600; }}

        .login-summary {{
            font-size: 12px;
            color: #656d76;
            margin-bottom: 16px;
            padding: 12px 14px;
            background: #fff8ee;
            border-left: 3px solid #bf8700;
            border-radius: 0 8px 8px 0;
        }}

        .login-summary strong {{ color: #bf8700; }}

        .source-tag {{
            display: inline-block;
            font-size: 9px;
            padding: 2px 6px;
            border-radius: 4px;
            background: #eef1f4;
            color: #656d76;
            letter-spacing: 0.5px;
            text-transform: uppercase;
        }}

        .disk-size {{ color: #8250df; font-weight: 600; }}
        .file-count {{ color: #656d76; }}

        .subfolder-row td {{
            padding-left: 30px !important;
            color: #656d76;
            font-size: 11px;
        }}

        .subfolder-row .app-name {{
            color: #656d76;
            font-weight: 400;
        }}

        .disk-bar-outer {{
            width: 100%;
            height: 28px;
            background: #eef1f4;
            border-radius: 6px;
            overflow: hidden;
            margin: 12px 0 8px 0;
        }}

        .disk-bar-inner {{
            height: 100%;
            border-radius: 6px 0 0 6px;
            transition: width 0.3s;
        }}

        .disk-bar-labels {{
            display: flex;
            justify-content: space-between;
            font-size: 11px;
            color: #656d76;
            margin-bottom: 16px;
        }}

        .disk-bar-labels strong {{ color: #24292f; }}

        .category-size {{ color: #2f81f7; font-weight: 600; }}

        .git-icon {{
            display: inline-block;
            width: 20px;
            height: 20px;
            margin-right: 6px;
            vertical-align: -4px;
        }}

        .git-tiles {{
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
        }}

        .git-tile {{
            background: #f6fef9;
            border: 1px solid #b4dfca;
            border-radius: 8px;
            padding: 12px 16px;
            display: flex;
            align-items: center;
            gap: 10px;
            min-width: 180px;
            flex: 1 1 calc(50% - 5px);
            max-width: calc(50% - 5px);
        }}

        .git-tile-icon {{
            flex-shrink: 0;
            opacity: 0.45;
        }}

        .git-tile-name {{
            font-weight: 500;
            color: #24292f;
            font-size: 12px;
        }}

        .git-tile-commits {{
            font-size: 12px;
            font-weight: 600;
            color: #1a7f37;
            margin-left: auto;
            white-space: nowrap;
        }}

        .commit-count {{ color: #1a7f37; font-weight: 600; }}

        .footer {{
            text-align: center;
            margin-top: 24px;
            font-size: 10px;
            color: #b0b8c1;
            letter-spacing: 1px;
        }}
    </style>
</head>
<body>

    <div class="card header" style="margin-bottom: 16px;">
        <h1>Monthly Mac Report</h1>
        <div class="subtitle">{month_name} &mdash; generated {now.strftime('%Y-%m-%d %H:%M')}</div>
    </div>

    <div class="stats-row">
        <div class="stat">
            <div class="value">{format_duration(total_seconds)}</div>
            <div class="label">Screen Time</div>
        </div>
        <div class="stat">
            <div class="value">{total_apps}</div>
            <div class="label">Apps Used</div>
        </div>
        <div class="stat">
            <div class="value{' danger' if len(unused_apps) > 20 else ' warn' if len(unused_apps) > 10 else ''}">{len(unused_apps)}</div>
            <div class="label">Unused Apps</div>
        </div>
        <div class="stat">
            <div class="value{' danger' if total_unused_gb > 10 else ' warn' if total_unused_gb > 5 else ''}">{total_unused_gb:.1f} GB</div>
            <div class="label">Reclaimable</div>
        </div>
    </div>
"""

    # git activity — tile layout near the top
    if git_activity["repos"]:
        git_svg = '<svg class="git-icon" viewBox="0 0 92 92" xmlns="http://www.w3.org/2000/svg"><path fill="#1a7f37" d="M90.156 41.965 50.036 1.848a5.918 5.918 0 0 0-8.372 0l-8.328 8.332 10.566 10.566a7.03 7.03 0 0 1 7.23 1.684 7.034 7.034 0 0 1 1.669 7.277l10.187 10.184a7.028 7.028 0 0 1 7.278 1.672 7.04 7.04 0 0 1 0 9.957 7.05 7.05 0 0 1-9.965 0 7.044 7.044 0 0 1-1.528-7.66l-9.5-9.497V59.36a7.04 7.04 0 0 1 1.86 11.29 7.04 7.04 0 0 1-9.957 0 7.04 7.04 0 0 1 0-9.958 7.06 7.06 0 0 1 2.304-1.539V33.926a7.049 7.049 0 0 1-3.82-9.234L29.242 14.272 1.73 41.777a5.925 5.925 0 0 0 0 8.371L41.852 90.27a5.925 5.925 0 0 0 8.37 0l39.934-39.934a5.925 5.925 0 0 0 0-8.371"/></svg>'

        git_user_name = git_activity.get("git_user", "")
        git_user_email = git_activity.get("git_email", "")
        user_line = ""
        if git_user_name or git_user_email:
            parts = []
            if git_user_name:
                parts.append(git_user_name)
            if git_user_email:
                parts.append(f"&lt;{git_user_email}&gt;")
            user_line = f'<div style="font-size: 11px; color: #656d76; margin-bottom: 12px;">{" ".join(parts)}</div>'

        html += f"""
    <div class="card">
        <h2>{git_svg} Git Activity</h2>
        {user_line}
        <div style="font-size: 12px; color: #656d76; margin-bottom: 16px;">
            <strong style="color: #1a7f37;">{git_activity['total_commits']}</strong> commits across
            <strong style="color: #1a7f37;">{len(git_activity['repos'])}</strong> repos in the past {DAYS_BACK} days.
        </div>
        <div class="git-tiles">
"""
        for repo in git_activity["repos"][:15]:
            html += f"""            <div class="git-tile">
                <svg class="git-tile-icon" width="16" height="16" viewBox="0 0 16 16" fill="#1a7f37"><path d="M2 2.5A2.5 2.5 0 0 1 4.5 0h8.75a.75.75 0 0 1 .75.75v12.5a.75.75 0 0 1-.75.75h-2.5a.75.75 0 0 1 0-1.5h1.75v-2h-8a1 1 0 0 0-.714 1.7.75.75 0 1 1-1.072 1.05A2.495 2.495 0 0 1 2 11.5Zm10.5-1h-8a1 1 0 0 0-1 1v6.708A2.486 2.486 0 0 1 4.5 9h8ZM5 12.25a.25.25 0 0 1 .25-.25h3.5a.25.25 0 0 1 .25.25v3.25a.25.25 0 0 1-.4.2l-1.45-1.087a.249.249 0 0 0-.3 0L5.4 15.7a.25.25 0 0 1-.4-.2Z"/></svg>
                <span class="git-tile-name">{repo['name']}</span>
                <span class="git-tile-commits">{repo['commits']} commits</span>
            </div>
"""

        html += """        </div>
    </div>
"""

    # chart
    if chart_b64:
        html += f"""
    <div class="card">
        <h2>Usage Breakdown</h2>
        <div class="chart-wrap">
            <img src="data:image/png;base64,{chart_b64}" alt="App Usage Chart" />
        </div>
    </div>
"""

    # unused apps
    if unused_apps:
        html += f"""
    <div class="card">
        <h2>Unused / Rarely Used</h2>
        <div class="unused-summary">
            <strong>{len(unused_apps)}</strong> apps with less than 5 min usage in {DAYS_BACK} days.
            Removing them frees up <strong>{total_unused_gb:.1f} GB</strong>.
        </div>
        <table>
            <thead>
                <tr><th>App</th><th class="right">Size</th><th class="right">Usage</th></tr>
            </thead>
            <tbody>
"""
        for app in unused_apps[:25]:
            size_str = format_size(app['size_mb'])
            html += f"""                <tr>
                    <td class="app-name">{app['name']}</td>
                    <td class="right unused-size">{size_str}</td>
                    <td class="right pct">{app['usage']}</td>
                </tr>
"""

        if len(unused_apps) > 25:
            html += f"""                <tr>
                    <td style="color: #656d76;">... and {len(unused_apps) - 25} more</td>
                    <td></td>
                    <td></td>
                </tr>
"""

        html += """            </tbody>
        </table>
    </div>
"""

    # disk usage
    if disk_usage["folders"]:
        total_disk_gb = disk_usage["total_mb"] / 1024
        max_mb = disk_usage["folders"][0]["size_mb"] if disk_usage["folders"] else 1

        html += f"""
    <div class="card">
        <h2>Your Files</h2>
        <div class="unused-summary" style="background: #f5f0ff; border-left-color: #8250df;">
            <strong style="color: #8250df;">{disk_usage['total_files']:,}</strong> files across user folders,
            totalling <strong style="color: #8250df;">{total_disk_gb:.1f} GB</strong>.
        </div>
        <table>
            <thead>
                <tr>
                    <th>Folder</th>
                    <th class="right">Size</th>
                    <th class="right">Files</th>
                    <th></th>
                </tr>
            </thead>
            <tbody>
"""

        for folder in disk_usage["folders"]:
            bar_pct = (folder["size_mb"] / max_mb * 100) if max_mb > 0 else 0

            html += f"""                <tr>
                    <td class="app-name">{folder['name']}</td>
                    <td class="right disk-size">{format_size(folder['size_mb'])}</td>
                    <td class="right file-count">{folder['file_count']:,}</td>
                    <td><span class="mini-bar-track"><span class="mini-bar-fill disk" style="width: {bar_pct:.0f}%;"></span></span></td>
                </tr>
"""
            if folder["name"] in disk_usage["subfolders"]:
                for sub in disk_usage["subfolders"][folder["name"]]:
                    sub_bar = (sub["size_mb"] / max_mb * 100) if max_mb > 0 else 0
                    html += f"""                <tr class="subfolder-row">
                    <td class="app-name">&lfloor; {sub['name']}</td>
                    <td class="right" style="color: #8250df; opacity: 0.7;">{format_size(sub['size_mb'])}</td>
                    <td></td>
                    <td><span class="mini-bar-track"><span class="mini-bar-fill disk" style="width: {sub_bar:.0f}%; opacity: 0.5;"></span></span></td>
                </tr>
"""

        html += """            </tbody>
        </table>
    </div>
"""

    # system disk overview
    if system_disk["total_gb"] > 0:
        pct = system_disk["pct_used"]
        bar_color = "#cf222e" if pct > 90 else "#bf8700" if pct > 75 else "#2f81f7"

        html += f"""
    <div class="card">
        <h2>System Storage</h2>
        <div class="disk-bar-outer">
            <div class="disk-bar-inner" style="width: {pct}%; background: {bar_color};"></div>
        </div>
        <div class="disk-bar-labels">
            <span><strong>{system_disk['used_gb']} GB</strong> used</span>
            <span><strong>{system_disk['free_gb']} GB</strong> free of {system_disk['total_gb']} GB</span>
        </div>
"""

        if system_disk["categories"]:
            max_gb = system_disk["categories"][0]["size_gb"]
            html += """        <table>
            <thead>
                <tr>
                    <th>Category</th>
                    <th class="right">Size</th>
                    <th></th>
                </tr>
            </thead>
            <tbody>
"""
            for cat in system_disk["categories"]:
                bar_pct = (cat["size_gb"] / max_gb * 100) if max_gb > 0 else 0
                html += f"""                <tr>
                    <td class="app-name">{cat['name']}</td>
                    <td class="right category-size">{cat['size_gb']} GB</td>
                    <td><span class="mini-bar-track"><span class="mini-bar-fill" style="width: {bar_pct:.0f}%;"></span></span></td>
                </tr>
"""

            accounted = sum(c["size_gb"] for c in system_disk["categories"])
            unaccounted = system_disk["used_gb"] - accounted
            if unaccounted > 1:
                html += f"""                <tr>
                    <td style="color: #656d76;">macOS + System Data</td>
                    <td class="right" style="color: #656d76;">~{unaccounted} GB</td>
                    <td></td>
                </tr>
"""

            html += """            </tbody>
        </table>
"""

        html += """    </div>
"""

    # login items
    if login_items:
        html += f"""
    <div class="card">
        <h2>Startup Items</h2>
        <div class="login-summary">
            <strong>{len(login_items)}</strong> apps and services launch when you log in.
            Each one slows your boot and runs in the background.
        </div>
        <table>
            <thead>
                <tr><th>Name</th><th class="right">Source</th></tr>
            </thead>
            <tbody>
"""
        for item in login_items:
            html += f"""                <tr>
                    <td class="app-name">{item['name']}</td>
                    <td class="right"><span class="source-tag">{item['source']}</span></td>
                </tr>
"""

        html += """            </tbody>
        </table>
    </div>
"""

    html += f"""
    <div class="footer">
        my-task-scheduler &middot; report v1.0
    </div>

</body>
</html>"""

    return html


# ──────────────────────────────────────────────
# SEND EMAIL
# ──────────────────────────────────────────────
def send_email(html_body, subject, smtp_creds):
    """
    Sends the report as an HTML email using credentials from stdin.

    :param html_body: the HTML report content
    :param subject: email subject line
    :param smtp_creds: dict with host, port, username, password
    """
    msg = MIMEMultipart("alternative")
    msg["Subject"] = subject
    msg["From"] = smtp_creds["username"]
    msg["To"] = EMAIL_TO

    plain = "Your monthly Mac usage report is ready. View in an HTML-capable email client."
    msg.attach(MIMEText(plain, "plain"))
    msg.attach(MIMEText(html_body, "html"))

    try:
        with smtplib.SMTP(smtp_creds["host"], int(smtp_creds["port"])) as server:
            server.starttls()
            server.login(smtp_creds["username"], smtp_creds["password"])
            server.send_message(msg)
        print(f"Email sent to {EMAIL_TO}")
        return True
    except Exception as e:
        print(f"ERROR: Failed to send email: {e}", file=sys.stderr)
        return False


# ──────────────────────────────────────────────
# MAIN
# ──────────────────────────────────────────────
def main():
    print("Monthly Mac Usage Report")
    print(f"Period: past {DAYS_BACK} days\n")

    # load credentials from stdin (must happen before any other stdin reads)
    smtp_creds = load_credentials_from_stdin()
    if smtp_creds:
        print("[✓] SMTP credentials loaded from stdin")
    else:
        print("[–] No stdin credentials — email sending disabled")

    # 1. scan installed apps
    print("[1/8] Scanning installed apps...")
    installed_apps = get_installed_apps()
    print(f"      {len(installed_apps)} apps found, {len(_BUNDLE_NAME_CACHE)} names cached")

    # 2. screen time
    print("[2/8] Querying Screen Time...")
    usage_data = query_screen_time(DAYS_BACK)
    print(f"      {len(usage_data)} apps with usage data")

    # 3. unused apps
    print("[3/8] Finding unused apps...")
    unused_apps = find_unused_apps(installed_apps, usage_data)
    print(f"      {len(unused_apps)} unused/rarely used")

    # 4. git activity
    print("[4/8] Scanning git repos...")
    git_activity = get_git_activity(DAYS_BACK)
    print(f"      {git_activity['total_commits']} commits across {len(git_activity['repos'])} repos")

    # 5. disk usage
    print("[5/8] Scanning disk usage...")
    disk_usage = get_disk_usage()
    print(f"      {len(disk_usage['folders'])} folders, {disk_usage['total_files']:,} files, {disk_usage['total_mb'] / 1024:.1f} GB")

    # 6. system disk overview
    print("[6/8] Checking system storage...")
    system_disk = get_system_disk_overview()
    print(f"      {system_disk['used_gb']} GB used / {system_disk['free_gb']} GB free of {system_disk['total_gb']} GB")
    print(f"      {len(system_disk['categories'])} categories found")

    # 7. login items
    print("[7/8] Checking startup items...")
    login_items = get_login_items()
    print(f"      {len(login_items)} startup items")

    # 8. chart
    print("[8/8] Generating chart...")
    chart_b64 = generate_chart(usage_data)

    # build report
    print("\nBuilding report...")
    month_name = (datetime.now() - timedelta(days=1)).strftime("%B %Y")
    subject = f"Mac Report - {month_name}"
    html = build_html_report(usage_data, unused_apps, chart_b64, disk_usage, login_items, system_disk, git_activity)

    # save locally
    if SAVE_LOCAL:
        os.makedirs(REPORT_DIR, exist_ok=True)
        filename = f"{REPORT_DIR}/mac-report-{datetime.now().strftime('%Y-%m-%d-%H%M%S')}.html"
        with open(filename, "w", encoding="utf-8") as f:
            f.write(html)
        print(f"Report saved: {filename}")

    # send email if credentials were provided via stdin
    if smtp_creds:
        print("Sending email...")
        send_email(html, subject, smtp_creds)
    else:
        print("Skipping email — no credentials provided")

    print("Done")


if __name__ == "__main__":
    main()