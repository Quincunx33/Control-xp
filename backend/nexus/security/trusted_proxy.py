"""Secure client IP extraction and trusted proxy resolution."""
from __future__ import annotations
import ipaddress
from typing import Optional
from ..config import TRUSTED_PROXIES

def _is_ip_in_networks(ip_str: str, networks: list[str]) -> bool:
    try:
        ip = ipaddress.ip_address(ip_str)
        for net_str in networks:
            try:
                net = ipaddress.ip_network(net_str, strict=False)
                if ip in net:
                    return True
            except ValueError:
                if str(ip) == net_str:
                    return True
        return False
    except ValueError:
        return False

def resolve_client_ip(headers: dict[str, str], peer_ip: Optional[str] = None) -> str:
    """Safely resolve client IP without blindly trusting headers unless from trusted proxy."""
    peer = peer_ip or "127.0.0.1"

    # Only inspect forwarded headers if peer IP is a recognized trusted proxy
    if not _is_ip_in_networks(peer, TRUSTED_PROXIES):
        return peer

    # Check Cloudflare specific header first
    cf_ip = headers.get("cf-connecting-ip") or headers.get("CF-Connecting-IP")
    if cf_ip:
        cf_ip = cf_ip.strip()
        try:
            ipaddress.ip_address(cf_ip)
            return cf_ip
        except ValueError:
            pass

    # Inspect X-Forwarded-For right-to-left
    xff = headers.get("x-forwarded-for") or headers.get("X-Forwarded-For")
    if xff:
        parts = [p.strip() for p in xff.split(",") if p.strip()]
        for candidate in reversed(parts):
            try:
                ipaddress.ip_address(candidate)
                if not _is_ip_in_networks(candidate, TRUSTED_PROXIES):
                    return candidate
            except ValueError:
                continue

    return peer
