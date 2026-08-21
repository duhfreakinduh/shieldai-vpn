# ShieldAI VPN — real WireGuard Android client

ShieldAI v0.1 is an Android VPN client with a WebView dashboard and the official embeddable WireGuard Android tunnel library. The connect button controls a real Android `VpnService` through WireGuard's `GoBackend`; the RX/TX values shown in the dashboard are live WireGuard tunnel statistics.

## What is real in v0.1

- Real Android VPN permission flow.
- Real WireGuard tunnel using `com.wireguard.android:tunnel:1.0.20260102`.
- Full-tunnel IPv4 + IPv6 routes when your server config uses `0.0.0.0/0, ::/0`.
- Live transmitted/received byte counters from the WireGuard backend.
- Standard WireGuard client configuration parser.
- Ubuntu/Debian WireGuard exit-node installer.

There are deliberately no fake "AI speed boost", malware-blocking, latency, or threat-monitor claims in this version.

## The important hosting limitation

GitHub and Hugging Face are useful for source code, CI, the dashboard, and optional APIs, but they are not the WireGuard exit node. A VPN exit node must be a machine that can accept WireGuard UDP traffic and route packets to the Internet.

For a $0 recurring-cost personal setup, run the included server script on an existing Linux machine/router at home, or on a cloud VM that you independently have at no cost. If the home server is behind a router, forward UDP 51820 to it.

## 1. Set up the exit node

On Ubuntu or Debian, copy `server/install-server.sh` to the server and run:

```bash
sudo bash install-server.sh YOUR_PUBLIC_IP_OR_DNS
```

Example:

```bash
sudo bash install-server.sh vpn.example.com
```

The script installs WireGuard, enables forwarding/NAT, starts `wg0`, generates one client, writes `/root/shieldai-client.conf`, and prints a QR code.

**Do not commit that generated client configuration or any WireGuard private key to GitHub.**

## 2. Build the Android APK

The included GitHub Actions workflow runs:

```bash
gradle assembleDebug
```

and uploads `app-debug.apk` as the `ShieldAI-debug-apk` workflow artifact.

You can also build locally with Java 17 and Android SDK 35:

```bash
gradle assembleDebug
```

## 3. Install and connect

1. Install the debug APK on your Android phone.
2. Open ShieldAI.
3. Tap **VPN Settings**.
4. Paste `/root/shieldai-client.conf` from your server.
5. Save it.
6. Tap the large power button.
7. Accept Android's system VPN permission dialog.
8. Open a browser and check your public IP. It should now be the exit node's public IP.

## Security notes

- The v0.1 app saves the WireGuard client config in Android app-private SharedPreferences. It is not committed or transmitted to GitHub/Hugging Face. A production version should encrypt key material with Android Keystore.
- Protect the server and keep the OS updated.
- Do not expose SSH with password authentication to the public Internet.
- Use Android's Always-on VPN / Block connections without VPN options if you want lockdown behavior.
- This repository intentionally contains no server or client private keys.

## Hugging Face role

A Hugging Face Space can host a web dashboard/control plane, but should not be treated as the WireGuard UDP exit node. ShieldAI can add a Space later for server-health summaries or AI-assisted server selection without putting VPN private keys there.

## License

Project code in this starter is provided for personal development. WireGuard's Android tunnel library is separately licensed under Apache-2.0.
