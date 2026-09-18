import json
from pathlib import Path

import yt_dlp


def download_audio(url: str, output_dir: str) -> str:
    destination = Path(output_dir)
    destination.mkdir(parents=True, exist_ok=True)
    temporary_dir = destination / ".yt-dlp"
    temporary_dir.mkdir(parents=True, exist_ok=True)
    downloaded_path = None

    def on_progress(data):
        nonlocal downloaded_path
        if data.get("status") == "finished":
            downloaded_path = data.get("filename")

    options = {
        "format": "bestaudio/best",
        "outtmpl": str(temporary_dir / "%(id)s.%(ext)s"),
        "noplaylist": True,
        "quiet": True,
        "retries": 5,
        "fragment_retries": 5,
        "extractor_args": {
            "youtube": {
                "player_client": ["android_vr", "android", "web"]
            }
        },
        "progress_hooks": [on_progress],
    }

    with yt_dlp.YoutubeDL(options) as downloader:
        info = downloader.extract_info(url, download=True)
        if downloaded_path is None:
            downloaded_path = downloader.prepare_filename(info)

    result = {
        "path": downloaded_path,
        "title": info.get("title") or "YouTube Audio",
        "author": info.get("uploader") or info.get("channel") or "YouTube Creator",
        "durationMs": int((info.get("duration") or 0) * 1000),
        "videoId": info.get("id") or "",
    }
    return json.dumps(result)
