# Discord ↔ Minecraft Bridge (NeoForge)

An open-source **Discord ↔ Minecraft bridge mod** built on **NeoForge**, allowing real-time chat syncing, command execution, and account linking between Discord and Minecraft servers.
Designed for public servers, modpacks, and paid environments — while ensuring all forks remain open-source under **AGPLv3**.

---

## ✨ Features

- 💬 **Two-way chat bridge**  
  Sync messages between Minecraft chat and Discord channels in real time.

- 🛠 **Admin command support**  
  Run Minecraft commands from Discord (permission-based).

- 🔗 **Account linking**  
  Link Discord accounts to Minecraft players.

- 🗄 **SQL database support**  
  Persistent storage for linked accounts (MySQL / MariaDB / SQLite).

- 🪝 **Discord webhooks**  
  Optional webhook support for rich message formatting.

- ✏️ **Editable event messages**  
  Fully customizable join/leave, death, and system messages.

---

## 🔐 Permissions & Security

- Role-based access for Discord commands
- Server-side permission checks
- No client-side exploits
- Designed for production servers

---

## 📦 Installation

1. Install **NeoForge** for your Minecraft version  
2. Drop the mod `.jar` into your server’s `mods` folder  
3. Start the server to generate the config files  
4. Configure:
   - Discord bot token
   - Channel IDs
   - Database connection (if desired)
5. Restart the server

---

## ⚙️ Configuration

Configuration files allow you to customize:
- Discord channels
- Command permissions
- Database type & credentials
- Event message formats
- Webhook usage

Detailed configuration examples are provided in the `config/` directory.

---

## 📜 License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPLv3)**.

- ✔ Commercial use is allowed (including paid servers and mod platforms)
- ✔ Forks and modifications **must remain open-source**
- ❌ Closed-source redistribution is not permitted

See the `LICENSE` file for full terms.

---

## 🤝 Contributing

Contributions are welcome!

By contributing, you agree that your work will be licensed under **AGPLv3**.

Please:
- Keep code clean and documented
- Follow existing project structure
- Open an issue before large changes

---

## 🧠 Why AGPLv3?

This license ensures:
- The community benefits from improvements
- Hosted or paid services cannot hide modifications
- Long-term project sustainability

---

## 📫 Support & Issues

- Use **GitHub Issues** for bug reports and feature requests
- Include logs and reproduction steps when possible

---

## ⭐ Acknowledgements

Built for the Minecraft modding community with a focus on openness, security, and maintainability.
