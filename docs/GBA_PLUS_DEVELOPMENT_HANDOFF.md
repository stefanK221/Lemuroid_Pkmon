# GBA Plus 开发交接

更新日期：2026-10-15（北京时间）。本文是状态快照，后续以对应提交的 Actions 和实际测试结果为准。

## 分支与基线

- 后续开发分支：`feature/gba-plus-quick-tools`。
- 首批快捷功能提交：`fd91668113ba76dc961539b7f5f6668dd0bba0c5`。
- 稳定基线：`feature/gba-plus-foundation`，提交 `d00d602039e7e999949ae2cfbe670cab715bd6bf`。
- 用户已反馈稳定版 6V 修改和关闭穿墙效果正常。新快捷功能尚未获得游戏内验证，勿当作稳定版发布。
- 保留稳定分支；不要把未验证的快捷功能直接覆盖到稳定分支。

## 首批已写入代码、待完成验证的功能

1. 无限金钱：运行中持续补充至 999999。
2. 无限大师球：运行中持续补充至 99；已有大师球优先，没有则使用空精灵球格，不覆盖其他物品。
3. 获取指定精灵：备份 SAV 后写入第一个全空的电脑盒位，不替换队伍或已有盒中精灵。默认选择喷火龙（内部编号 6）。

关闭持续功能仅停止补充，不收回已经获得的金钱或物品。开关仅本次游戏会话有效。
赠送精灵目前为 Lv.1、6V、撞击，不自动补图鉴；用户必须先在游戏内保存，操作会重启游戏，未保存的进度会丢失。
其他精灵可使用已列名称或本版本内部编号（1–251、277–411），内部编号不等同全国图鉴编号。

## 当前适配边界

仅对已核验的「漆黑的魅影 5.0EX+BW」ROM SHA-256 启用快捷功能：

`389363bb2c87b8f197f23d67c11e459cc9f46befec75dc57a9e6fdac898eccc2`

Android 端精确哈希校验；核心端另检查标题、存档块指针、队伍数量和精灵球口袋布局。
未知版本应拒绝操作，不允许仅凭文件名套用地址。
ROM 和用户真实存档不得上传到仓库。应用在私有目录 `files/gba_plus_backups` 保存操作前备份。

## 关键代码

- `QuickToolsDialog.kt`：常用功能入口、确认、备份与操作。
- `QuickToolsProfile.kt`：精确 ROM 校验、名称列表、从 ROM 读取名称/招式 PP。
- `EmeraldQuickSavePatcher.kt`：复制后修改电脑空位、精灵加密及节校验和。
- 上述 Kotlin 文件位于 `lemuroid-app/src/main/java/com/swordfish/lemuroid/app/shared/cheats/`。
- `BaseGameActivity.kt`：UI 接入及核心命令传递，位于 shared/game。
- `tools/mgba_quick_tools.h`：逐帧金钱/大师球补充与边界检查。
- `tools/patch_mgba_reset.py`：向固定 mGBA 源码加入快捷功能及原有穿墙关闭修复。
- `tools/test_mgba_quick_tools.c`：运行时资源修改测试。
- `EmeraldQuickSavePatcherTest.kt`：合成存档、24 种排列、只改新记录、满盒及损坏输入测试。

## 构建与验证状态

截至本次核验，提交 fd916681 的：
- [存档编辑回归测试](https://github.com/stefanK221/Lemuroid_Pkmon/actions/runs/38760488022) 已通过。这不等于新 Kotlin 测试或游戏内验证通过。
- [快捷功能单元测试、核心测试及 APK 构建](https://github.com/stefanK221/Lemuroid_Pkmon/actions/runs/38760487692) 仍在运行。应读取最终步骤和日志，不预先宣称成功。
- 本地已验证新补丁脚本能应用于原始固定版本 mGBA；完整 Android 编译以 CI 为准。

固定 mGBA 源码：`c758314a639aa0066e7b65a8341448181b73c804`；NDK：27.2.12479018；JDK：17。
工作流：`.github/workflows/gba-plus-debug.yml`。
Kotlin 测试：`./gradlew :lemuroid-app:testFreeBundleDebugUnitTest`。
核心构建与测试：`bash tools/build_mgba_fixed.sh`。
APK：`./gradlew :lemuroid-app:assembleFreeBundleDebug`。
脚本必须保持 LF；原穿墙关闭回归测试不可移除。

## 下一位开发者的执行顺序

1. 读取上述构建最终状态，失败时定位日志并修复；通过后再生成交付记录。
2. 核实新 APK 包名、版本和签名；安装前备份用户数据。不得卸载或清除数据以绕过签名问题。
3. 用户倾向自己实测：金钱消费后回补、关闭后停止；大师球使用后回补、关闭后停止；喷火龙加入空盒并能取出、保存和重载。
4. 回归已有 6V 及穿墙开关；验证满口袋、满盒、未知 ROM 拒绝、保存后重载、退出重开和开关状态一致性。
5. 留下构建链接、APK SHA-256、真实测试结果；没有合法 ROM 或未进入游戏实测时，不宣称游戏效果已验证。

## 用户后续优先级

完成上述三项后，再安排：
- 性格、特性、技能、等级等属性编辑。
- PP / 招式使用次数不减少。
- 适配具体游戏版本的内置金手指库。
- 已保存金手指直接编辑（不再只能删除重建）。
- 帅气、美丽、可爱等华丽大赛数值上限，优先级较后。

用户未要求联网或多人作弊；本项目当前范围为本地单机模拟器。
