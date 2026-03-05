# QU Micro-services Cluster — CSC340 Project 1

**Team:** Aurelien Buisine, Reed Scampoli, Wilson Chen, Jacob Batson

---

## Overview

A distributed microservices cluster where a central server routes client task requests to a dynamic pool of worker Service Nodes (SNs). The browser frontend communicates via HTTP to an HTTP Gateway, which bridges to the internal custom TCP/UDP protocol.

```
Browser ──HTTP──▶ HTTPGateway ──TCP──▶ DoormanListener ──TCP──▶ Service Node
                                              ▲
                                Service Nodes (UDP heartbeats)
```

### Services

| # | Service | Node Class |
|---|---------|-----------|
| 1 | N-Body Gravitational Stepper | `Services.NBody.NBodyNode` |
| 2 | Base64 Encode / Decode | `Services.Base64.Base64Node` |
| 3 | Compression / Decompression | `Services.Compression.CompressionNode` |
| 4 | CSV Stats | `Services.CSVStats.CSVStatsNode` |
| 5 | Image to ASCII | `Services.ImageToAscii.ImageToAsciiNode` |

### Ports

| Port | Protocol | Purpose |
|------|----------|---------|
| 5050 | HTTP | HTTPGateway — browser-facing |
| 5101 | TCP | DoormanListener — client connections |
| 5102 | TCP | Service Node listener (all SNs, each on own host) |
| 6001 | UDP | HeartbeatMonitor — receives SN heartbeats |

---

## Running Locally

### Prerequisites
- Java 17+
- All commands run from the project root directory

### Step 1 — Compile

```bash
mkdir -p out

# Core server
javac -d out Source/*.java

# Service nodes
javac -cp out -d out Services/Base64/*.java
javac -cp out -d out Services/NBody/*.java
javac -cp out -d out Services/Compression/*.java
javac -cp out -d out Services/CSVStats/*.java
javac -cp out -d out Services/ImageToAscii/*.java
```

### Step 2 — Start the Server (Terminal 1)

```bash
java -cp out -Dfrontend.dir=Frontend Source.Server
```

The frontend is now accessible at **http://localhost:5050**

### Step 3 — Start Service Nodes (one terminal each)

Run whichever services you want available:

```bash
# Terminal 2
java -cp out Services.NBody.NBodyNode

# Terminal 3
java -cp out Services.Base64.Base64Node

# Terminal 4
java -cp out Services.Compression.CompressionNode

# Terminal 5
java -cp out Services.CSVStats.CSVStatsNode

# Terminal 6
java -cp out Services.ImageToAscii.ImageToAsciiNode
```

Each node sends a UDP heartbeat to the server every 5 seconds. Once registered, it appears as **Online** on the home page within 10 seconds.

> **Note:** If running nodes on a different machine than the server, pass the server IP:
> ```bash
> java -cp out -Dserver.host=<SERVER_IP> Services.Base64.Base64Node
> ```

### Step 4 — Use the Frontend

Open **http://localhost:5050** in a browser. Service cards show **Online** (green) or **Offline** (red) based on live heartbeat status, updated every 10 seconds.

> **Local testing only:** Change the `GATEWAY` constant in `Frontend/src/index.js` from the AWS IP to `http://localhost:5050` before testing locally, then revert before deploying.

---

## Protocol

### Client → Server (TCP :5101)

1. Client connects to DoormanListener
2. Server immediately sends the available service list:
   ```
   AVAILABLE_SERVICES:BASE64_ENCODE_DECODE,CSV_STATS,...\n
   ```
3. Client sends JSON payload and signals EOF (closes write end):
   ```json
   { "service": 2, "filename": "file.txt", "base64": "<base64-encoded data>" }
   ```
4. Server routes to the appropriate live SN, forwards payload
5. SN processes and responds — server streams result back to client
6. Connection closes

> A client may also connect, read the service list, and close without sending a payload (status-only query — used by the home page).

### Service Node → Server (UDP :6001)

Each SN sends a heartbeat packet every 5 seconds:
```
<nodeId>,<tcpPort>,<serviceName>
```
Example: `2,5102,BASE64_ENCODE_DECODE`

The server marks a node **dead** if no heartbeat is received for **60 seconds**. Dead nodes that resume heartbeats are automatically re-added as available (recovery).

### Server → Service Node (TCP :5102)

The Pipe thread connects to the SN, forwards the full JSON payload, signals EOF, then reads all response bytes and streams them back to the original client.

---

## Architecture

### Server Components

| Class | Role |
|-------|------|
| `Server` | Main entry point — starts all server threads |
| `DoormanListener` | Accepts TCP client connections on :5101, spawns a `Pipe` per client |
| `Pipe` | Client thread — sends service list, routes request to SN, returns result |
| `HeartbeatMonitor` | UDP listener on :6001 — receives heartbeats, sweeps dead nodes every 10s |
| `NodeRegistry` | Thread-safe map of all known nodes and their alive status |
| `HTTPGateway` | HTTP server on :5050 — serves frontend files and bridges HTTP to TCP |

### Service Node Base (`Node.java`)

All SNs extend `Node`, which provides:
- **HeartbeatPulse** — daemon thread sending UDP heartbeats to the server
- **ServiceListener** — TCP server on :5102 accepting connections from `Pipe`
- **Worker pool** — handles multiple concurrent requests per node

Subclasses implement one of two patterns:
- **Pattern A** — override `handleRequest(byte[] payload)` — pure Java, in-process
- **Pattern B** — override `getExecutorCommand()` — spawns a subprocess (Python, C, etc.)

---

## AWS Deployment

The live server is at **http://54.225.145.40:5050**

Each component runs as a systemd service on its own EC2 instance. To override the frontend directory or server host at launch:

```bash
# Server
java -cp microservices.jar -Dfrontend.dir=/home/ec2-user/Frontend Source.Server

# Service node pointing to server's private IP
java -cp microservices.jar -Dserver.host=10.0.x.x Services.Base64.Base64Node
```
