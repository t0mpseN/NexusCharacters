<div align="center">
  <img src="src/main/resources/assets/nexuscharacters/icon-round.png" alt="NexusCharactersIcon" width="360">
  <br/>

  <img width="517" height="139" alt="4" src="https://github.com/user-attachments/assets/3fd576f0-d44f-4811-bf29-f85756534a85" />
  <br/>
  
  [![Release](https://img.shields.io/github/v/release/t0mpseN/NexusCharacters?label=Latest%20Release&color=blue)](#)
  [![ModLoader](https://img.shields.io/badge/ModLoader-Fabric-lightgrey)](#)
</div>

> [!NOTE]
> 🚧 **WORK IN PROGRESS:** Nexus is currently in active, early development. As a solo passion project, please expect to encounter some bugs, rough edges, and incomplete features. Your patience and feedback are incredibly appreciated as I continue to refine the mod!

<br/>

<details open>
  <summary>📚 Table of Contents</summary>

- [✨ Core Features](#-core-features)
  - [Character Creation](#character-creation)
  - [Cross-World Progression](#cross-world-progression)
  - [Hardcore Characters](#hardcore-characters)
- [🚀 Extended Capabilities](#-extended-capabilities)
  - [Deep Mod Compatibility](#deep-mod-compatibility)
  - [Multiplayer Support](#multiplayer-support)
  - [Legacy Data Migration](#legacy-data-migration)
  - [Autosave Interval Command (Server/LAN only)](#autosave-interval-command)
- [🛠️ Installation & Setup](#installation-setup)
- [🤝 Feedback and Support](#-feedback-and-support)
- [📄 License](#-license)

</details>

<br/>

Welcome to **Nexus Characters**! This mod fundamentally changes how you experience Minecraft by shifting the focus from world-bound progression to character-bound progression.

Instead of starting from scratch every time you create a new world, Nexus Characters allows you to seamlessly carry your inventory, stats, and identity across all your single-player saves and multiplayer servers. Build your character, explore infinite worlds, and never lose your hard-earned progress again.

<br/>

# ✨ Core Features

## Character Creation
Create unique identities for your different playthroughs. When making a new character, you have complete control over their identity and playstyle:

- **Custom Naming:** Give your character a unique identity.
- **Skin Syncing:** Simply input an existing Mojang username, and your character will automatically adopt that skin.
- **Bound Gamemodes:** Gamemodes (Survival, Creative, Adventure) are tied to the character, rather than the world.

![Character Creation](demo/character_creation.gif)

<br/>

## Cross-World Progression
Your character's journey doesn't end when you leave a world. Everything that defines your character travels with them:

- Health and hunger stats
- Experience levels (XP)
- Full inventory and equipped armor
- Advancements and statistics

![Cross-World Progression](demo/concept.gif)

<br/>

## Hardcore Characters

Love the thrill of Hardcore but hate losing a beautiful world you spent hours building? Nexus Characters introduces Hardcore Characters.

- If your Hardcore Character dies, they are permanently erased from your character list.
- **The World Survives:** The world they died in remains perfectly intact. You can roll a brand-new character, travel to that same world, and find your fallen character's base (or avenge them!).

![Hardcore Characters](demo/hardcore_death.gif)

<br/>

# 🚀 Extended Capabilities

## Deep Mod Compatibility

Nexus Characters doesn't just save vanilla data. It integrates deeply with your favorite mods to ensure that external progression is saved directly to your character. For example, if you are playing with Cobblemon, your party of Pokémon and your PC boxes will travel with your character across dimensions and worlds!

## Multiplayer Support

Take your characters online! Nexus Characters is fully compatible with dedicated servers. Server owners can install the mod to allow players to bring their cross-world characters into the community, or restrict servers to specific character types.

## Legacy Data Migration
If you are adding Nexus Characters to an existing world where you already have progress, you can easily inherit your old data!

- **How to Inherit:** Create a new character and set the **Character Name** to perfectly match your **Minecraft Username** (the account that originally played in that world). 
- **Security:** You can only inherit your *own* legacy data. For example, if you are `Player1` and your friend is `Player2`, you cannot create a character named `Player2` to steal their progress. 

> [!WARNING]
> **Important:** When you do this, your character's current progress will be **completely overwritten** by the legacy data from that world. If you want to enter a legacy world but keep your cross-world character's existing progress, ensure your character is named **differently** from your Minecraft username!

## <a id="autosave-interval-command"></a>Autosave Interval Command (Server/LAN only)
If you are hosting a Server or a LAN world, you can manage how often non-vanilla data is synced to players:

- `/nexus saveinterval <time in seconds>` *(Default: 30s)*
  Alters the time between autosaves for data coming from external mods (e.g., catching a Pokémon or updating your Pokédex). 

> [!IMPORTANT]
> **Syncing Delay:** Even if you define the interval to `1 second`, it still takes time for the server to send the data to the client, and for the client to save it properly to disk. Because of this, **you should wait 1 to 2 minutes before disconnecting** after doing something important related to mods to avoid losing progress! Also, the shorter the interval, the heavier will be server load.

<img width="1899" height="989" alt="image" src="https://github.com/user-attachments/assets/91e822a7-daa5-41cd-a0c7-88031097ae70" />

> Character Model Showcase in RPG Origins modpack


<br/>

# <a id="installation-setup"></a>🛠️ Installation & Setup
> [!IMPORTANT]
> **Note:** Make sure you are using Fabric as your mod loader and Minecraft 1.21.1.
1. Download the latest version of **Nexus Characters** from the [releases](https://github.com/t0mpseN/NexusCharacters/releases) page
2. Drop the `.jar` file into your Minecraft `mods` folder (do the same for servers)
3. Launch the game and access the new **"Characters"** menu from the title screen

<br/>

# 🤝 Feedback and Support

Found a bug or have a feature request? Please open an issue on the [GitHub Issues tab](https://github.com/t0mpseN/NexusCharacters/issues) or send me an [email](mailto:pedrotbandel@gmail.com). I am always looking for ways to improve the user experience! Also if you wish to support my work, I'll happily take a coffee!
<div align=center>
  <p>
    <a href="mailto:pedrotbandel@gmail.com"><img src="https://img.shields.io/badge/pedrotbandel@gmail.com-EA4335?style=flat-square&logo=gmail&logoColor=white" /></a>
    &nbsp;
    <a href="https://discordapp.com/users/239538765356466176"><img src="https://img.shields.io/badge/t0mpseN-5865F2?style=flat-square&logo=discord&logoColor=white" /></a>
  </p>

  <a href="https://ko-fi.com/pedrotompsen" target="_blank">
    <img width="240" height="50" alt="image" src="https://storage.ko-fi.com/cdn/brandasset/v2/support_me_on_kofi_dark.png" />
  </a>
</div>

<br/>

# 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
