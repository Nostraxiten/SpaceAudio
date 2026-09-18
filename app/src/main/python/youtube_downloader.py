import json
from pathlib import Path

import yt_dlp


def _build_options(temporary_dir: Path, player_clients: list, on_progress) -> dict:
    """Build yt-dlp options for a given player client list."""
    return {
        # Best audio-only in m4a/aac, fallback to any best audio
        "format": "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best",
        "outtmpl": str(temporary_dir / "%(id)s.%(ext)s"),
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "retries": 3,
        "fragment_retries": 3,
        # Disable the SABR/PoToken streaming that causes 403 on modern clients
        "extractor_args": {
            "youtube": {
                "player_client": player_clients,
                # Skip webpage parsing to avoid bot-detection triggers
                "player_skip": ["webpage", "configs"],
            }
        },
        "http_headers": {
            "User-Agent": (
                "com.google.ios.youtube/19.45.4 "
                "(iPhone16,2; U; CPU iPhone OS 18_1_0 like Mac OS X)"
            ),
            "X-YouTube-Client-Name": "5",
            "X-YouTube-Client-Version": "19.45.4",
        },
        "progress_hooks": [on_progress],
    }


def download_audio(url: str, output_dir: str) -> str:
    destination = Path(output_dir)
    destination.mkdir(parents=True, exist_ok=True)
    temporary_dir = destination / ".yt-dlp"
    temporary_dir.mkdir(parents=True, exist_ok=True)
    downloaded_path = None
    info = None

    def on_progress(data):
        nonlocal downloaded_path
        if data.get("status") == "finished":
            downloaded_path = data.get("filename")

    # Cascade of player clients — ordered by 403-bypass effectiveness
    # iOS client gets direct m4a without SABR/PoToken bot challenge
    client_strategies = [
        ["ios"],                            # iOS client: direct m4a, no bot challenge
        ["mweb"],                           # Mobile web: lighter restrictions
        ["ios", "mweb"],                    # Combined
        ["web_creator", "android_creator"], # Creator-tier clients
        ["android_vr", "android"],          # Legacy Android as last resort
    ]

    last_error = None
    for clients in client_strategies:
        try:
            opts = _build_options(temporary_dir, clients, on_progress)
            downloaded_path = None
            with yt_dlp.YoutubeDL(opts) as downloader:
                info = downloader.extract_info(url, download=True)
                if downloaded_path is None and info is not None:
                    downloaded_path = downloader.prepare_filename(info)
            if downloaded_path and Path(downloaded_path).is_file() and Path(downloaded_path).stat().st_size > 0:
                break  # Successful download — stop trying strategies
        except Exception as e:
            last_error = e
            downloaded_path = None
            info = None

    if not downloaded_path or not Path(downloaded_path).is_file():
        raise RuntimeError(
            f"All yt-dlp client strategies failed for {url}. "
            f"Last error: {last_error}"
        )

    result = {
        "path": str(downloaded_path),
        "title": (info or {}).get("title") or "YouTube Audio",
        "author": (info or {}).get("uploader") or (info or {}).get("channel") or "YouTube Creator",
        "durationMs": int(((info or {}).get("duration") or 0) * 1000),
        "videoId": (info or {}).get("id") or "",
    }
    return json.dumps(result)
