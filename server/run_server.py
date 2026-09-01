"""一键启动脚本：python run_server.py

会顺便把本机所有局域网 IP 打印出来，方便直接抄到手机 App 的设置页。
"""

from __future__ import annotations

import socket

import uvicorn

from app import config


def lan_addresses() -> list[str]:
    """列出本机可能的局域网地址。"""
    found: set[str] = set()
    try:
        # 不会真的发包，只是让系统选出默认出口网卡的地址
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(("223.5.5.5", 80))
            found.add(sock.getsockname()[0])
    except OSError:
        pass
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            addr = info[4][0]
            if not addr.startswith("127."):
                found.add(addr)
    except OSError:
        pass
    return sorted(found)


def main() -> None:
    print("=" * 62)
    print(f"  推理引擎     : {config.ENGINE}")
    print(f"  演示场景     : {config.DEMO_SCENARIO}")
    print(f"  契约版本     : {config.SCHEMA_VERSION}")
    print(f"  监听地址     : {config.HOST}:{config.PORT}")
    addrs = lan_addresses()
    if addrs:
        print("  手机 App 里填下面任意一个（不要填 localhost）：")
        for addr in addrs:
            print(f"      http://{addr}:{config.PORT}")
    else:
        print("  未检测到局域网地址，请手动执行 ipconfig 查看")
    print("=" * 62)

    uvicorn.run(
        "app.main:app",
        host=config.HOST,
        port=config.PORT,
        reload=False,
        log_level="info",
    )


if __name__ == "__main__":
    main()
