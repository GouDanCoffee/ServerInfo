package com.smallaswater.serverinfo;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.event.server.QueryRegenerateEvent;
import cn.nukkit.event.server.ServerStopEvent;
import cn.nukkit.form.response.FormResponseSimple;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;
import com.smallaswater.serverinfo.network.UpdateServerInfoRunnable;
import com.smallaswater.serverinfo.servers.ServerInfo;
import com.smallaswater.serverinfo.utils.RsNpcXVariable;
import com.smallaswater.serverinfo.utils.TipsVariable;
import com.smallaswater.serverinfo.windows.CreateWindow;
import lombok.Getter;
import tip.utils.Api;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author SmallasWater
 * Create on 2021/7/13 15:23
 * Package com.smallaswater.serverinfo
 */
public class ServerInfoMainClass extends PluginBase implements Listener {

    private static ServerInfoMainClass instance;

    private Config language;

    @Getter
    private ArrayList<ServerInfo> serverInfos = new ArrayList<>();

    public static final ThreadPoolExecutor THREAD_POOL = (ThreadPoolExecutor) Executors.newCachedThreadPool();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();
        loadServer();
        language = new Config(this.getDataFolder()+"/language.yml",Config.YAML);
        this.getLogger().info("服务器信息加载完成");
        THREAD_POOL.execute(new UpdateServerInfoRunnable());

        this.getServer().getPluginManager().registerEvents(instance, instance);

        //注册TIPS变量
        try {
            Api.registerVariables("serverInfo", TipsVariable.class);
        } catch (Exception ignored) {

        }
        //注册RsNPCX变量
        try {
            Class.forName("com.smallaswater.npc.variable.VariableManage");
            com.smallaswater.npc.variable.VariableManage.addVariable("ServerInfoVariable", RsNpcXVariable.class);
        } catch (Exception ignored) {

        }



    }

    public Config getLanguage() {
        return language;
    }

    public int getAllPlayerSize(){
        int maxOnline = 0;
        for (ServerInfo info : ServerInfoMainClass.getInstance().getServerInfos()) {
            if(info.onLine()) {
                maxOnline += info.getPlayer();
            }
        }
        return maxOnline;
    }

    public void call(String callback,String[] data){
        for(ServerInfo info: serverInfos){
            if(info.getCallback().equals(callback)){
                info.update(data);
            }
        }

    }

    private void loadServer(){
        this.serverInfos.clear();
        for(Map map:getConfig().getMapList("server-info")){
            ServerInfo info = new ServerInfo(map.get("name").toString(),map.get("ip").toString(),Integer.parseInt(map.get("port").toString()));
            serverInfos.add(info);
            this.getLogger().info("加载服务器 "+info.getCallback()+" 完成");
        }
    }

    public static ServerInfoMainClass getInstance() {
        return instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(args.length > 0){
            if("reload".equalsIgnoreCase(args[0]) && sender.isOp()){
                saveDefaultConfig();
                reloadConfig();
                loadServer();
                sender.sendMessage("配置文件重载完成");
                return true;
            }
        }
        if(sender instanceof Player){
            CreateWindow.showMenu((Player) sender);
            return true;
        }
        return false;
    }

    @EventHandler
    public void onQueryRegenerateEvent(QueryRegenerateEvent event) {
        if (getConfig().getBoolean("sync-player", false)) {
            event.setPlayerCount(Server.getInstance().getOnlinePlayers().size() + getAllPlayerSize());
        }
    }

    @EventHandler
    public void onWindow(PlayerFormRespondedEvent event){
        if(event.wasClosed() || event.getResponse() == null){
            return;
        }
        if(event.getFormID() == CreateWindow.MENU){
            ServerInfo info = getServerInfos().get(
                    ((FormResponseSimple)event.getResponse()).getClickedButtonId());
            if(info.onLine()){
                Server.getInstance().broadcastMessage(TextFormat.colorize('&',language.getString("player-transfer-text","").replace("{server}",info.getCallback()))
                .replace("{name}",event.getPlayer().getName()));
                event.getPlayer().transfer(new InetSocketAddress(info.getIp(),info.getPort()));
            }else{
                event.getPlayer().sendMessage(TextFormat.colorize('&',language.getString("player-transfer-off","")));
            }
        }
    }

    @EventHandler
    public void onServerStop(ServerStopEvent event) {
        if (!this.getConfig().getBoolean("ServerCloseTransfer.enable") ||
                this.getServer().getOnlinePlayers().isEmpty()) {
            return;
        }

        for (Player player : this.getServer().getOnlinePlayers().values()) {
            player.sendTitle(
                    this.getConfig().getString("ServerCloseTransfer.showTitle.title"),
                    this.getConfig().getString("ServerCloseTransfer.showTitle.subTitle"),
                    10,
                    100,
                    20
            );
        }
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (Player player : this.getServer().getOnlinePlayers().values()) {
            player.transfer(
                    new InetSocketAddress(
                            this.getConfig().getString("ServerCloseTransfer.ip"),
                            this.getConfig().getInt("ServerCloseTransfer.port")
                    )
            );
        }
        try {
            //让服务器发送完数据包再关闭
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
