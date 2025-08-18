# GitTorrent

# Combining the power of Git and torrent technology for Web3

### Description

- The project utilizes JGit for all Git operations and offers built-in features such as code formatting, metadata removal, and commit history rewriting, making it a highly customizable and decentralized approach to version control
- This solution integrates various tools such as Linguist for language detection, secret scanning, and automatic repository seeding through torrent peers
- By using `.gittorlng`, `.gittorrls`, and `.gittorfmt` XML configuration files, users can optimize their Git operations and maintain privacy while ensuring code quality

---

## NOTICE

- Please read through this `README.md` to better understand the project's source code and setup instructions
- Also, make sure to review the contents of the `License/` directory
- Your attention to these details is appreciated — enjoy exploring the project!

---

## Problem Statement

- Managing Git repositories in a traditional centralized way exposes potential privacy and security risks
- Existing solutions don't offer seamless integration with torrent-based distribution, nor do they have built-in utilities for security scanning and commit history management
- This project solves these issues by combining Git operations with decentralized seeding and integrated tools for code formatting, secret detection, and metadata removal

---

## Project Goals

### Decentralized Git Repositories

- Enable self-hosted, decentralized Git repositories that rely on torrent technology for seeding and downloading

### Secure Code Management

- Integrate secret scanning, code formatting, and metadata removal into the Git workflow, improving privacy and security

### Seamless Integration with JGit

- Provide a robust, lightweight solution for Git operations using JGit, enabling compatibility with existing Git workflows while adding extra features for security and privacy

---

## Tools, Materials & Resources

### JGit

- JGit is used for all Git operations, enabling easy and efficient interaction with Git repositories in a Java-based environment

### Torrent Technology

- Peer-to-peer file sharing and seeding technology borrowed from torrents allows decentralized distribution and seeding of repositories

### Linguist

- Linguist is used for language detection and code analysis, helping identify the languages used in the repository and contributing to automated code formatting

---

## Design Decision

### Torrent-Based Seeding for Repository Distribution

- Repositories will be distributed via torrent technology, enabling peer-to-peer sharing without relying on centralized hosting

### Integration with JGit

- The decision to integrate JGit was made to maintain a lightweight, Java-centric solution for Git operations, which also allows flexibility for custom features and configurations

### XML-Based Configuration

- Using `.gittorlng`, `.gittorrls`, and `.gittorfmt` XML configuration files allows users to easily define and customize features like language detection, secrets scanning, and code formatting

---

## Features

### Peer-to-Peer Seeding

- Repositories are only accessible when the built-in torrent client is actively seeding, ensuring secure and decentralized distribution

### Secret Scanning and Code Quality

- The `.gittorrls` file scans the repository for secrets, while `.gittorfmt` enables automatic code formatting to ensure consistency and readability

### Chain Cloning

- The `.gittor` file enables chaining multiple Git clones, linking repositories together for efficient multi-repo management

---

## Block Diagram

```plaintext
                                     ┌───────────────────┐
       Node.js System Diagram        │    Event Queue    │        ┌───────────────────────────────────────────────┐
                                     │                   ├── → ───┼──────┐                                        │
   ┌───────────────────┐             │ ╔═══════════════╗ │        │      │               LIB UV                   │
   │                   │             │ ║   Callback    ║ ├─── ← ──┼───┐  │          Asynchronous I/O              │
   │    Application    │             │ ╚═══════════════╝ │        │   ↓  ↑             C Library                  │
   │                   │             └──────┬──────┬─────┘        │   │  │ Event Loop            Worker Threads   │
   └───┬────────────┬──┘                    │      │              │  ┌┴──┴────────────┐Blocking┌────────────────┐ │
       │            │                       ↓      ↑              │  │╔══════════════╗├── → ───┤╔══════════════╗│ │
       ↓ JavaScript ↑                       │      │              │  │║              ║│        │║   Process    ║│ │
       │            │               ┌───────┴──────┴──────┐       │  │╚══════════════╝├─── ← ──┤╚══════════════╝│ │
┌──────┴────────────┴──────┐        │    C++ Bindings     │       │  └────────────────┘Callback└────────────────┘ │
│                          ├── → ───┤      Node API       │       └───────────────────────────────────────────────┘
│   V8 JavaScript Engine   │        │  ╓───────────────╖  │
│                          ├─── ← ──┤  ║ OS Operations ║  │
└──────────────────────────┘        │  ╙───────────────╜  │
                                    └─────────────────────┘
Chars: ─ │ ┌ ┐ └ ┘ ├ ┤ ┬ ┴ ┼ ═ ║ ╔ ╗ ╚ ╝ ╠ ╣ ╦ ╩ ╬ ← → ↑ ↓ ↗ ↘ ↙ ↖ ↔ ↕ ╓ ╙ ╖ ╜ ╒ ╘ ╕ ╛

```

---

## Functional Overview

- When a repo is pushed, the built-in torrent client will seed the repo, allowing others to access it via the torrent protocol
- Code formatting is applied according to the rules specified in `.gittorfmt`, ensuring code consistency across contributors
- The `.gittorrls` file automatically scans for sensitive information in the repository and flags it for removal or modification
- Using the `.gittor` file, users can clone multiple repositories in sequence, making it easier to work with related repositories

---

## Challenges & Solutions

### Torrent-Based Access Control

- Implemented a mechanism where seeding status is checked before granting access to the repo

### Code Formatting and Metadata Removal

- The `.gittorfmt` and `.gittorrls` files automate this process by enforcing formatting rules and scanning for secrets before pushing to the repository

---

## Lessons Learned

### Torrent Technology and Git Integration

- Integrating torrent technology with Git provided a scalable, decentralized way to share repositories but required careful handling of data consistency and peer synchronization

### Security and Automation

- Implementing security features like secret scanning and metadata removal as part of the repository workflow greatly improved repository integrity and developer confidence

---

## Project Structure

```plaintext
root/
├── License/
│   ├── LICENSE.md
│   │
│   └── NOTICE.md
│
├── .gitattributes
│
├── .gitignore
│
├── README.md
│
├── gittor/
│   ├── file_1
│   │
│   ├── file_2
│   │
│   └── file_3
│
└── gui/
    ├── subfolder_1/
    │   └── file_1
    │
    ├── subfolder_2/
    │   └── sub-subfolder_1/
    │       └── file_1
    │
    ├── file_1
    │
    ├── file_2
    │
    └── file_3

```

---

## Future Enhancements

- Adding support for more advanced torrent client features like encryption and peer prioritization
- Extending support for additional platforms beyond the initial Java-based implementation
- Enhance the secret scanning engine to support more types of secrets and custom rules
- Improve the handling of chained clones, allowing more complex relationships between repositories
