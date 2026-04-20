# Local-First Architecture

This document describes the intended architecture for Phone Server as a standalone Android app that turns a phone into a self-managed server environment.

## Product Principle

The phone is the server.

There is no required external control plane. The device owner is the operator. Terminal control, project execution, file management, and hosted processes all live on the phone itself.

There is no account or registration concept in the intended product. The app should be usable immediately after install, subject only to local device permissions and first-run setup.

## First-Run Experience

The intended first-run flow is:

1. Open the app.
2. Accept any required local permissions.
3. Land directly in the local dashboard or terminal.

The app should not require:

- email registration
- login
- device enrollment
- pairing with an external backend
- agent tokens or bearer tokens

## High-Level Modules

### 1. App Shell

Responsible for:

- navigation
- permissions flow
- settings
- terminal UI
- project list
- service status screens

Recommended stack:

- Kotlin
- Jetpack Compose
- Android foreground services for long-running work

The UI should assume a single local owner by default, not a multi-user cloud account model.

### 2. Terminal Runtime

Responsible for:

- creating and maintaining local shell sessions
- preserving shell state between commands
- handling input and output streams
- maintaining working directory and environment state

Expected behavior:

- Linux-style shell experience
- command history
- persistent session state
- stdout and stderr capture
- PTY support when interactive programs need it

### 3. Workspace Manager

Responsible for:

- creating project directories
- cloning Git repositories
- storing build and start commands
- saving runtime environment variables
- managing workspace metadata

Suggested local model:

- `Project`
- `Workspace`
- `EnvironmentVariable`
- `RunProfile`

### 4. Process Supervisor

Responsible for:

- starting project processes
- stopping project processes
- tracking PID or runtime handles where available
- restart policies
- log capture
- process state persistence

Suggested states:

- `STARTING`
- `RUNNING`
- `STOPPING`
- `STOPPED`
- `FAILED`

### 5. Local Persistence

Use local storage instead of server infrastructure.

Current implementation:

- app-private JSON files for workspace catalog and terminal history
- app-private filesystem storage for workspaces

Suggested next step when the data model grows:

- Room or SQLite for structured data
- encrypted storage for secrets if needed

### 6. Networking Layer

Responsible for:

- local port binding
- local service discovery inside the app
- optional LAN exposure if Android and the network allow it
- optional reverse-proxy support later

This should remain secondary to the local runtime. The first goal is terminal and project control on the phone itself.

## Non-Root vs Root

### Non-Root Mode

Reasonable expectations:

- shell access inside app-controlled environment
- Git-based workflows
- package/runtime setup inside userspace
- local HTTP services within Android restrictions

Limitations:

- not a full Linux distribution
- no unrestricted OS-level daemon model
- background execution and networking are constrained by Android

### Rooted Mode

Possible advanced capabilities:

- broader filesystem access
- stronger process management
- deeper networking control
- more VPS-like behavior

Root should be treated as optional advanced support, not as a baseline requirement.

## What To Reuse From The Current Repo

The current Spring Boot code should be treated as a domain reference only.

Reusable ideas:

- project metadata model
- deployment or run-state concepts
- terminal session lifecycle concepts
- validation rules around commands and states

Not reusable as final architecture:

- remote controllers
- JWT and device-token model
- PostgreSQL and Redis assumptions
- control-plane and agent split
- registration, login, and device-enrollment flows

## Current Milestone

The current Android build now covers this baseline milestone:

"Open the app on a phone, start a persistent local shell, run commands, create workspaces, and manage long-running commands locally."

The major remaining terminal/runtime gap is PTY support for truly interactive shell programs.

## Repository Progress

The repository now includes an Android `app/` module with:

- Compose-based local UI
- a more terminal-first Termux-style shell screen
- workspace creation in app-private storage
- a persistent local shell session wrapper
- file-backed workspace metadata and terminal history
- a foreground runtime service
- managed service start, stop, restart, and output preview
- a runtime abstraction layer for Android shell and future Ubuntu userspace backends
- Ubuntu 22.04 scaffold metadata and prepared storage layout
- Ubuntu Base download, checksum verification, and rootfs extraction into app-private storage

That scaffold is the new primary direction. The Spring Boot code remains only as legacy reference.

The next major runtime upgrade is bundling `proot` plus PTY support so the staged Ubuntu 22.04 rootfs can launch as a real userspace shell, followed by Git import flows and stronger persistence for managed services.
