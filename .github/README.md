# EzChestShopReborn

---

[![Discord](https://img.shields.io/discord/1302627666007953559?label=Discord&color=blue)](https://discord.gg/invite/gjV6BgKxFV)
[![GitHub Release](https://img.shields.io/github/v/release/nouish/EzChestShop?label=version)](https://github.com/nouish/EzChestShop/releases/latest)
[![bStats Servers](https://img.shields.io/bstats/servers/23732)](https://bstats.org/plugin/bukkit/EzChestShopReborn/23732)
[![bStats Players](https://img.shields.io/bstats/players/23732)](https://bstats.org/plugin/bukkit/EzChestShopReborn/23732)
[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/nouish/EzChestShop/main.yml)](https://github.com/nouish/EzChestShop/actions/workflows/main.yml)
[![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/nouish/EzChestShop/total)](https://github.com/nouish/EzChestShop/releases/latest)

EzChestShopReborn is a fork from the [EzChestShop](https://github.com/ItzAmirreza/EzChestShop) plugin originally authored by [ItzAmirreza](https://github.com/ItzAmirreza).

This fork was created to support Minecraft 1.21, on request from the [Torrent SMP](https://www.torrentsmp.com/) server.

As of October 27th 2024 the original EzChestShop plugin is no longer actively maintained and offers no support for Minecraft versions beyond 1.20.4.

If you use EzChestShopReborn, please don't ask questions in the upstream channels. Instead, join the [Discord](https://discord.gg/invite/gjV6BgKxFV) for this project.

## Download

You can find the most recent version for download [here](https://github.com/nouish/EzChestShop/releases/latest) on GitHub, or join [Discord](https://discord.gg/invite/gjV6BgKxFV) to chat.


## Support

This plugin is built for the [Paper platform](https://papermc.io/).

If you need help or need to report an issue, please [open a new issue here](https://github.com/nouish/EzChestShop/issues/new/choose).

### Supported Minecraft versions

This is a full list of Minecraft versions supported by EzChestShopReborn:

| Minecraft version |    | Status                                               |
|-------------------|----|------------------------------------------------------|
| Minecraft 1.21.4  | ✅ |                                                      |
| Minecraft 1.21.3  | ✅ |                                                      |
| Minecraft 1.21.2  | ✅ |                                                      |
| Minecraft 1.21.1  | ✅ |                                                      |
| Minecraft 1.21    | ✅ |                                                      |

## Contribute

Feel free to contribute through Pull Requests.

Please understand that features that may require a lot of work to maintain in future versions will be rejected. As such, it is a good idea to discuss changes before spending a lot of time for a feature that would be rejected.

Please follow the existing code style of the project.


### Build instructions

Build with Apache Maven:

```shell
mvn clean package
```

If you have trouble with this, see the prerequisites below first, and refer to the [GitHub Actions configuration](workflows/main.yml) for a working example of building this plugin from the ground up.

### Prerequisites 

* Java 21 or later
* Apache Maven 3.9.9 or later
* You must build targeted Spigot versions [Spigots BuildTools](https://www.spigotmc.org/wiki/buildtools/). This will change to Paperweight in the future.

## License

```text
GNU General Public License (GPLv3)
```

EzChestShopReborn is licensed under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html), inherited from [Bukkit](https://hub.spigotmc.org/stash/projects/SPIGOT/repos/bukkit/browse/LICENCE.txt).

The forked version of EzChestShop was mislabeled as [MIT](https://github.com/nouish/EzChestShop/commit/0adc3d64f647f47ec0aa4151244a8b3e12f7a491), which conflicts with the GPL 3.0.

On October 27th 2024, EzChestShop moved to the GNU Affero General Public License for future releases ([commit](https://github.com/ItzAmirreza/EzChestShop/commit/d2a786a33be11be8f4a6c2cbbfeaf7ef6974da2d)).
