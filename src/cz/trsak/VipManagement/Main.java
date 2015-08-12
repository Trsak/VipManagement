package cz.trsak.VipManagement;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.util.Random;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main extends JavaPlugin implements Listener {
    static String JDBCuser;
    static String JDBCpass;
    static String JDBCurl;
    static Connection connection;
    static Statement st;
    static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static Random rnd = new Random();
    final TimeReader timeReader =
            new TimeReader()
                    .addUnit("y", 31536000l)
                    .addUnit("m", 2592000l)
                    .addUnit("d", 86400l)
                    .addUnit("h", 3600l)
                    .addUnit("m", 60l)
                    .addUnit("s", 1l);

    public Main() {
    }

    @Override
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("[VipManagement] Loading VipManagement v" + this.getDescription().getVersion() + ".");
        this.getConfig().options().copyDefaults(true);
        this.saveConfig();

        JDBCuser = this.getConfig().getString("user");
        JDBCpass = this.getConfig().getString("pass");
        JDBCurl = this.getConfig().getString("db");

        try {
            this.connection = DriverManager.getConnection(JDBCurl, JDBCuser,JDBCpass);

            getLogger().info("[VipManagement] Connected to MySQL server!");

            this.st = connection.createStatement();
            st.execute("CREATE TABLE IF NOT EXISTS `" + this.getConfig().getString("prefix") + "codes` (`code` VARCHAR(10) PRIMARY KEY, `group` VARCHAR(20), `time` VARCHAR(40));");
            st.execute("CREATE TABLE IF NOT EXISTS `" + this.getConfig().getString("prefix") + "players` (`player` VARCHAR(20) PRIMARY KEY, `group` VARCHAR(20), `time` VARCHAR(40));");
        } catch (SQLException e) {
            Plugin p = Bukkit.getPluginManager().getPlugin("VipManagement");
            Bukkit.getPluginManager().disablePlugin(p);

            getLogger().info("[VipManagement] Couldn't connect to MySQL database, disabling plugin.");
            getLogger().info("[VipManagement] SQLException: " + e.getMessage());
            getLogger().info("[VipManagement] SQLState: " + e.getSQLState());
        }

        Integer updateTime = this.getConfig().getInt("updateTime")*1200;

        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                getLogger().info("[VipManagement] Checking online players..");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    VipCheck(p);
                }
            }
        }, 0L, updateTime);
    }

    @Override
    public void onDisable(){
        getLogger().info("[VipManagement] Disabling VipManagement v" + this.getDescription().getVersion() + ".");
        try {
            if(connection!=null && connection.isClosed()){
                connection.close();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        VipCheck(player);
    }

    public void VipCheck(Player player) {
        try {
            String name = player.getName();

            PreparedStatement prepSQL = connection.prepareStatement("SELECT  *, COUNT(*) AS total FROM `" + this.getConfig().getString("prefix") + "players` WHERE `player`=? LIMIT 1");
            prepSQL.setString(1, name);
            ResultSet rs = prepSQL.executeQuery();

            rs.next();
            if (rs.getInt("total") != 0) {
                if(System.currentTimeMillis() >= rs.getLong("time") && rs.getLong("time") != 0) {
                    removeVIP(rs.getString("group"), rs.getString("player"));
                }
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    String randomString(int len)
    {
        StringBuilder sb = new StringBuilder( len );
        for( int i = 0; i < len; i++ )
            sb.append( AB.charAt( rnd.nextInt(AB.length()) ) );
        return sb.toString();
    }

    public void setVIP(String group, String player, Long time) {
        Long timeSet = 0l;

        if (time != 0) {
            timeSet =  System.currentTimeMillis() + (time*1000);
        }

        try {
            st.execute("INSERT INTO `" + this.getConfig().getString("prefix") + "players` (`player`, `group`, `time`) VALUES ('" + player + "', '" + group + "', '" + timeSet + "');");
        } catch(Exception e){
        e.printStackTrace();
        }

        if (this.getConfig().getBoolean("ActiveMessageSend")) {
            Player p = Bukkit.getPlayer(player);
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("ActiveMessage")).replaceAll("%group%", group).replaceAll("%player%", player));
        }

        if (this.getConfig().getBoolean("PublicActiveMessageSend")) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("PublicActiveMessage")).replaceAll("%group%", group).replaceAll("%player%", player));
        }

        List<String> commands = getConfig().getStringList("CommandsActive");
        execute(commands, group, player);
    }

    public void removeVIP(String group, String player) {
        try {
            PreparedStatement prepSQL2 = connection.prepareStatement("DELETE FROM `" + this.getConfig().getString("prefix") + "players` WHERE `player`=?");
            prepSQL2.setString(1, player);
            prepSQL2.executeUpdate();
        } catch(Exception e){
            e.printStackTrace();
        }

        if (this.getConfig().getBoolean("ExpireMessageSend")) {
            Player p = Bukkit.getPlayer(player);
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("ExpireMessage")).replaceAll("%group%", group).replaceAll("%player%", player));
        }

        if (this.getConfig().getBoolean("PublicExpireMessageSend")) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("PublicExpireMessage")).replaceAll("%group%", group).replaceAll("%player%", player));
        }

        List<String> commands = getConfig().getStringList("CommandsDeactive");
        execute(commands, group, player);
    }

    public void execute(List<String> commands, String group, String player) {
        for(String command : commands) {
            command = command.replaceAll("%player%", player);
            command = command.replaceAll("%group%", group);
            command = ChatColor.translateAlternateColorCodes('&', command);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        VipCheck((Player) sender);

        try {
            if (label.equalsIgnoreCase("vip") && args.length == 0) {
                if (sender.hasPermission("vip.info")||sender.isOp()||sender.hasPermission("vip.admin") || sender.hasPermission("vip.user")||sender.hasPermission("vip.*")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("VIPInfo")));
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("NoPermission")));
                }
            } else if (label.equalsIgnoreCase("vip") && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("vip.reload")||sender.isOp()||sender.hasPermission("vip.admin")||sender.hasPermission("vip.*")) {
                    this.getConfig();
                    this.reloadConfig();
                    this.saveConfig();
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("Reload")));
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("NoPermission")));
                }
            } else if (label.equalsIgnoreCase("vip") && args[0].equalsIgnoreCase("gencode")) {
                if (sender.hasPermission("vip.gencode")||sender.isOp()||sender.hasPermission("vip.admin")||sender.hasPermission("vip.*")) {
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("BadCmdUsage").replaceAll("%cmd_usage%", "/vip gencode <group> <time>")));
                    } else {
                        String code = randomString(10);
                        String Time = "";
                        Long TimeFinal = 0l;

                        if (args[2] != "0") {
                            Integer TimeArgs = args.length;
                            for (int i=2; i<TimeArgs; i++){
                                Time += args[i] + " ";
                            }

                            TimeFinal = timeReader.parse(Time);
                        }

                        st.execute("INSERT INTO `" + this.getConfig().getString("prefix") + "codes` (`code`, `group`, `time`) VALUES ('" + code + "', '" + args[1] + "', '" + TimeFinal + "');");

                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("CodeGenerated").replaceAll("%code%", code)));
                    }
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("NoPermission")));
                }
            } else if (label.equalsIgnoreCase("vip") && args[0].equalsIgnoreCase("codes")) {
                if (sender.hasPermission("vip.codes")||sender.isOp()||sender.hasPermission("vip.admin")||sender.hasPermission("vip.*")) {
                    String query = "SELECT * FROM `" + this.getConfig().getString("prefix") + "codes`";
                    ResultSet rs = st.executeQuery(query);

                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("CodesList")));
                    while (rs.next()) {
                        String code = rs.getString("code");
                        String group = rs.getString("group");
                        Long seconds = rs.getLong("time");

                        String Duration = "";

                        if (seconds != 0) {
                            int day = (int) TimeUnit.SECONDS.toDays(seconds);
                            long hours = TimeUnit.SECONDS.toHours(seconds) - (day * 24);
                            long minute = TimeUnit.SECONDS.toMinutes(seconds) - (TimeUnit.SECONDS.toHours(seconds) * 60);
                            long second = TimeUnit.SECONDS.toSeconds(seconds) - (TimeUnit.SECONDS.toMinutes(seconds) * 60);

                            if (day != 0) {
                                Duration += day + " " + this.getConfig().getString("Days") + " ";
                            }
                            if (hours != 0) {
                                Duration += hours + " " + this.getConfig().getString("Hours") + " ";
                            }
                            if (minute != 0) {
                                Duration += minute + " " + this.getConfig().getString("Minutes") + " ";
                            }
                            if (second != 0) {
                                Duration += second + " " + this.getConfig().getString("Seconds");
                            }
                        }
                        else {
                            Duration += this.getConfig().getString("Permanent");
                        }

                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("CodeInfo").replaceAll("%code%", code).replaceAll("%group%", group).replaceAll("%duration%", Duration)));
                    }
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("NoPermission")));
                }
            } else if (label.equalsIgnoreCase("vip") && args[0].equalsIgnoreCase("code")) {
                if (sender.hasPermission("vip.code")||sender.isOp()||sender.hasPermission("vip.admin")||sender.hasPermission("vip.user")||sender.hasPermission("vip.*")) {
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("BadCmdUsage").replaceAll("%cmd_usage%", "/vip code <code>")));
                    }
                    else {
                        PreparedStatement prepSQL = connection.prepareStatement("SELECT  *, COUNT(*) AS total FROM `" + this.getConfig().getString("prefix") + "codes` WHERE `code`=? LIMIT 1");
                        prepSQL.setString (1, args[1]);
                        ResultSet rs = prepSQL.executeQuery();

                        rs.next();
                        if (rs.getInt("total") == 0) {
                            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("CodeNotExist").replaceAll("%code%", args[1])));
                        }
                        else {
                            setVIP(rs.getString("group"), sender.getName(), rs.getLong("time"));

                            PreparedStatement prepSQL2 = connection.prepareStatement("DELETE FROM `" + this.getConfig().getString("prefix") + "codes` WHERE `code`=?");
                            prepSQL2.setString(1, args[1]);
                            prepSQL2.executeUpdate();
                        }
                    }
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("NoPermission")));
                }
            } else if (label.equalsIgnoreCase("vip") && args[0].equalsIgnoreCase("time")) {
                if (sender.hasPermission("vip.time")||sender.isOp()||sender.hasPermission("vip.admin")||sender.hasPermission("vip.user")||sender.hasPermission("vip.*")) {
                    PreparedStatement prepSQL = connection.prepareStatement("SELECT  *, COUNT(*) AS total FROM `" + this.getConfig().getString("prefix") + "players` WHERE `player`=? LIMIT 1");
                    prepSQL.setString(1, sender.getName());
                    ResultSet rs = prepSQL.executeQuery();

                    rs.next();
                    if (rs.getInt("total") == 0) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("NotVIP")));
                    }
                    else if (rs.getLong("time") == 0) {
                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("VIPTimePermanent")));
                    }
                    else {
                        Long time = rs.getLong("time");
                        time = time - System.currentTimeMillis();

                        Long seconds = time/1000;

                        int day = (int)TimeUnit.SECONDS.toDays(seconds);
                        long hours = TimeUnit.SECONDS.toHours(seconds) - (day *24);
                        long minute = TimeUnit.SECONDS.toMinutes(seconds) - (TimeUnit.SECONDS.toHours(seconds)* 60);
                        long second = TimeUnit.SECONDS.toSeconds(seconds) - (TimeUnit.SECONDS.toMinutes(seconds) *60);

                        String Duration = "";

                        if (day != 0) {
                            Duration += day + " " + this.getConfig().getString("Days") + " ";
                        }
                        if (hours != 0) {
                            Duration += hours + " "  + this.getConfig().getString("Hours") + " ";
                        }
                        if (minute != 0) {
                            Duration += minute + " "  + this.getConfig().getString("Minutes") + " ";
                        }
                        if (second != 0) {
                            Duration += second + " "  + this.getConfig().getString("Seconds");
                        }

                        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("VIPTime")).replaceAll("%time%", Duration));
                    }

                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', this.getConfig().getString("NoPermission")));
                }
            }
        } catch (SQLException e) {
            getLogger().info("[VipManagement] MySQL error!");
            getLogger().info("[VipManagement] SQLException: " + e.getMessage());
            getLogger().info("[VipManagement] SQLState: " + e.getSQLState());
        }

        return false;
    }
}