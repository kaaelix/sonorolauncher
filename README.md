# Sonoro

Wuthering Waves 3.8 launcher for Android.

## Connect Server

Choose `Connect Server`, enter a server IP or proxy link, and tap `Start`.
In-game, open the server list and pick `Local Server`.

## Local Server

Choose `Local Server` (`Solo` or `Co-op`), grant Shizuku or Root and storage access, and tap `Start`.
Downloads server files, `assets.db`, and Rust if missing, then starts the server on your phone.
In-game, open the server list and pick `Local Server`.

## Requirements

- Android 8.0+ (`arm64-v8a`)
- Shizuku or Root (`su`)

## Ports

- `5500/tcp` login / config
- `8080/tcp` proxy
- `13100/udp` game (KCP)

## Build

```sh
./build.sh
```
