/*
 * @author     ucchy
 * @license    GPLv3
 * @copyright  Copyright ucchy 2013
 */
package com.github.ucchyocean.et;

import java.io.File;
import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * @author ucchy
 * 経験値タイマー
 */
public class ExpTimer extends JavaPlugin implements Listener {

    private static ExpTimer instance;
    private TimerTask runnable;
    private BukkitTask task;

    protected static ETConfigData config;
    protected static HashMap<String, ETConfigData> configs;

    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable() {

        instance = this;

        // コンフィグのロード
        reloadConfigData();

        // メッセージの初期化
        Messages.initialize();
    }

    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onDisable()
     */
    @Override
    public void onDisable() {

        // タスクが残ったままなら、強制終了しておく。
        if ( runnable != null ) {
            getLogger().warning("タイマーが残ったままです。強制終了します。");
            getServer().getScheduler().cancelTask(task.getTaskId());
            runnable = null;
        }
    }

    /**
     * @see org.bukkit.plugin.java.JavaPlugin#onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean onCommand(
            CommandSender sender, Command command, String label, String[] args) {

        // 引数がない場合は実行しない
        if ( args.length <= 0 ) {
            return false;
        }

        if ( args[0].equalsIgnoreCase("start") ) {
            // タイマーをスタートする

            if ( runnable == null ) {

                if ( args.length == 1 ) {
                    config = configs.get("default").clone();
                } else if ( args.length >= 2 ) {
                    if ( args[1].matches("^[0-9]+$") ) {
                        config = configs.get("default").clone();
                        config.seconds = Integer.parseInt(args[1]);
                        if ( args.length >= 3 && args[2].matches("^[0-9]+$")) {
                            config.readySeconds = Integer.parseInt(args[2]);
                        }
                    } else {
                        if ( !configs.containsKey(args[1]) ) {
                            sender.sendMessage(ChatColor.RED +
                                    String.format("設定 %s が見つかりません！", args[1]));
                            return true;
                        }
                        config = configs.get(args[1]).clone();
                    }
                }

                runnable = new TimerTask(this, config.readySeconds, config.seconds);
                task = getServer().getScheduler().runTaskTimer(this, runnable, 20, 20);
                sender.sendMessage(ChatColor.GRAY + "タイマーを新規に開始しました。");
                return true;

            } else {
                runnable.startFromPause();
                sender.sendMessage(ChatColor.GRAY + "タイマーを再開しました。");
                return true;
            }

        } else if ( args[0].equalsIgnoreCase("pause") ) {
            // タイマーを一時停止する

            if ( runnable == null ) {
                sender.sendMessage(ChatColor.RED + "タイマーが開始されていません！");
                return true;
            }

            runnable.pause();
            sender.sendMessage(ChatColor.GRAY + "タイマーを一時停止しました。");
            return true;

        } else if ( args[0].equalsIgnoreCase("end") ) {
            // タイマーを強制終了する

            if ( runnable == null ) {
                sender.sendMessage(ChatColor.RED + "タイマーが開始されていません！");
                return true;
            }

            endTask();
            if ( config.useExpBar )
                setExpLevel(0, 1);
            sender.sendMessage(ChatColor.GRAY + "タイマーを強制停止しました。");
            return true;

        } else if ( args[0].equalsIgnoreCase("status") ) {
            // ステータスを参照する

            String stat;
            if ( runnable == null ) {
                stat = "タイマー停止中";
            } else {
                stat = runnable.getStatus();
            }

            sender.sendMessage(ChatColor.GRAY + "----- ExpTimer information -----");
            sender.sendMessage(ChatColor.GRAY + "現在の状況：" +
                    ChatColor.WHITE + stat);
            sender.sendMessage(ChatColor.GRAY + "開始時に実行するコマンド：");
            for ( String com : config.commandsOnStart ) {
                sender.sendMessage(ChatColor.WHITE + "  " + com);
            }
            sender.sendMessage(ChatColor.GRAY + "終了時に実行するコマンド：");
            for ( String com : config.commandsOnEnd ) {
                sender.sendMessage(ChatColor.WHITE + "  " + com);
            }
            sender.sendMessage(ChatColor.GRAY + "--------------------------------");

            return true;

        } else if ( args[0].equalsIgnoreCase("reload") ) {
            // config.yml をリロードする

            reloadConfigData();
            sender.sendMessage(ChatColor.GRAY + "config.yml をリロードしました。");
            return true;
        }

        return false;
    }

    /**
     * config.yml を再読み込みする
     */
    protected void reloadConfigData() {

        saveDefaultConfig();
        reloadConfig();
        FileConfiguration config = getConfig();
        configs = new HashMap<String, ETConfigData>();

        // デフォルトのデータ読み込み
        ConfigurationSection section = config.getConfigurationSection("default");
        ETConfigData defaultData = ETConfigData.loadFromSection(section, null);
        configs.put("default", defaultData);

        // デフォルト以外のデータ読み込み
        for ( String key : config.getKeys(false) ) {
            if ( key.equals("default") ) {
                continue;
            }
            section = config.getConfigurationSection(key);
            ETConfigData data = ETConfigData.loadFromSection(section, defaultData);
            configs.put(key, data);
        }
    }

    /**
     * 全プレイヤーの経験値レベルを設定する
     * @param level 設定するレベル
     */
    protected static void setExpLevel(int level, int max) {

        float progress = (float)level / (float)max;
        if ( progress > 1.0f ) {
            progress = 1.0f;
        } else if ( progress < 0.0f ) {
            progress = 0.0f;
        }
        if ( level > 24000 ) {
            level = 24000;
        } else if ( level < 0 ) {
            level = 0;
        }

        Player[] players = instance.getServer().getOnlinePlayers();
        for ( Player player : players ) {
            player.setLevel(level);
            player.setExp(progress);
        }
    }

    /**
     * 現在実行中のタスクを終了する
     */
    protected void endTask() {

        if ( runnable != null ) {
            getServer().getScheduler().cancelTask(task.getTaskId());
            runnable = null;
            task = null;
        }
    }

    /**
     * プラグインのデータフォルダを返す
     * @return プラグインのデータフォルダ
     */
    protected static File getConfigFolder() {
        return instance.getDataFolder();
    }

    /**
     * プラグインのJarファイル自体を示すFileオブジェクトを返す
     * @return プラグインのJarファイル
     */
    protected static File getPluginJarFile() {
        return instance.getFile();
    }

    /**
     * プレイヤー死亡時に呼び出されるメソッド
     * @param event
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {

        // タイマー起動中の死亡は、経験値を落とさない
        if ( runnable != null )
            event.setDroppedExp(0);
    }
}
