package skinsrestorer.bungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import org.bstats.bungeecord.Metrics;
import skinsrestorer.bungee.commands.AdminCommands;
import skinsrestorer.bungee.commands.PlayerCommands;
import skinsrestorer.bungee.listeners.LoginListener;
import skinsrestorer.shared.storage.Config;
import skinsrestorer.shared.storage.Locale;
import skinsrestorer.shared.storage.SkinStorage;
import skinsrestorer.shared.utils.C;
import skinsrestorer.shared.utils.MojangAPI;
import skinsrestorer.shared.utils.MojangAPI.SkinRequestException;
import skinsrestorer.shared.utils.MySQL;
import skinsrestorer.shared.utils.updater.bungee.SpigetUpdate;
import skinsrestorer.shared.utils.updater.core.UpdateCallback;
import skinsrestorer.shared.utils.updater.core.VersionComparator;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SkinsRestorer extends Plugin {

    private static SkinsRestorer instance;
    CommandSender con = null;
    private MySQL mysql;
    private boolean multibungee;
    private ExecutorService exe;
    private boolean outdated;

    public static SkinsRestorer getInstance() {
        return instance;
    }

    @SuppressWarnings("deprecation")
    public void log(String msg) {
        con.sendMessage(C.c("&e[&2SkinsRestorer&e] &r" + msg));
    }

    public String checkVersion() {
        try {
            HttpsURLConnection con = (HttpsURLConnection) new URL("https://api.spigotmc.org/legacy/update.php?resource=2124")
                    .openConnection();
            con.setDoOutput(true);
            con.setRequestMethod("GET");
            String version = new BufferedReader(new InputStreamReader(con.getInputStream())).readLine();
            if (version.length() <= 13)
                return version;
        } catch (Exception ex) {
            ex.printStackTrace();
            log("&cFailed to check for an update on spigot.");
        }
        return getVersion();
    }

    public ExecutorService getExecutor() {
        return exe;
    }

    public MySQL getMySQL() {
        return mysql;
    }

    public String getVersion() {
        return getDescription().getVersion();
    }

    public boolean isMultiBungee() {
        return multibungee;
    }

    public boolean isOutdated() {
        return outdated;
    }

    @Override
    public void onDisable() {
        exe.shutdown();
    }

    @SuppressWarnings("unused")
    @Override
    public void onEnable() {
        Metrics metrics = new Metrics(this);
        SpigetUpdate updater = new SpigetUpdate(this, 2124);
        updater.setVersionComparator(VersionComparator.EQUAL);
        updater.setVersionComparator(VersionComparator.SEM_VER_BETA);

        instance = this;
        Config.load(getResourceAsStream("config.yml"));
        Locale.load();
        exe = Executors.newCachedThreadPool();
        con = getProxy().getConsole();

        if (Config.USE_MYSQL)
            SkinStorage.init(mysql = new MySQL(Config.MYSQL_HOST, Config.MYSQL_PORT, Config.MYSQL_DATABASE,
                    Config.MYSQL_USERNAME, Config.MYSQL_PASSWORD));
        else
            SkinStorage.init(getDataFolder());

        getProxy().getPluginManager().registerListener(this, new LoginListener());
        getProxy().getPluginManager().registerCommand(this, new AdminCommands());
        getProxy().getPluginManager().registerCommand(this, new PlayerCommands());
        getProxy().registerChannel("SkinsRestorer");
        SkinApplier.init();

        multibungee = Config.MULTIBUNGEE_ENABLED
                || ProxyServer.getInstance().getPluginManager().getPlugin("RedisBungee") != null;

        exe.submit(new Runnable() {

            @Override
            public void run() {
                if (Config.UPDATER_ENABLED)
                    updater.checkForUpdate(new UpdateCallback() {
                        @Override
                        public void updateAvailable(String newVersion, String downloadUrl) {
                            log("----------------------------------------------");
                            log("    +===============+");
                            log("    | SkinsRestorer |");
                            log("    +===============+");
                            log("----------------------------------------------");
                            log("    Current version: " + getVersion());
                            log("    A new version is available! Downloading it now...");
                            log("----------------------------------------------");
                            }
                        }

                        @Override
                        public void upToDate() {
                            log("----------------------------------------------");
                            log("    +===============+");
                            log("    | SkinsRestorer |");
                            log("    +===============+");
                            log("----------------------------------------------");
                            log("    Current version: " + getVersion());
                            log("    This is the latest version!");
                            log("----------------------------------------------");
                        };

                if (Config.DEFAULT_SKINS_ENABLED)
                    for (String skin : Config.DEFAULT_SKINS)
                        try {
                            SkinStorage.setSkinData(skin, MojangAPI.getSkinProperty(MojangAPI.getUUID(skin)));
                        } catch (SkinRequestException e) {
                            if (SkinStorage.getSkinData(skin) == null)
                                log(ChatColor.RED + "Default Skin '" + skin + "' request error:" + e.getReason());
                        }
            }

        });

    }
}