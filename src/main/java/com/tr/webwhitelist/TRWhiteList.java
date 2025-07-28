package com.tr.webwhitelist;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class TRWhiteList extends JavaPlugin {
    private int port = 11434;
    private WebServer webServer;
    private String verificationCode;
    private Map<String, String> messages = new HashMap<>();
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        
        // 初始化配置
        reloadConfig();
        
        // 创建插件数据目录
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        // 确保资源文件存在
        saveResource("index.html", false);
        
        try {
            webServer = new WebServer(this, port);
            webServer.start();
            getLogger().info("Web server started on port " + port);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to start web server", e);
            getServer().getPluginManager().disablePlugin(this);
        }
        
        // 注册命令
        getCommand("trwl-reload").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("trwhitelist.reload")) {
                sender.sendMessage("§cYou don't have permission!");
                return true;
            }
            
            reloadConfig();
            sender.sendMessage("§aConfiguration reloaded!");
            return true;
        });
    }

    @Override
    public void onDisable() {
        if (webServer != null) {
            webServer.stop();
            getLogger().info("Web server stopped");
        }
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        config = getConfig();
        
        // 加载验证码
        verificationCode = config.getString("verification-code", "default");
        
        // 加载消息
        messages.clear();
        if (config.isConfigurationSection("messages")) {
            config.getConfigurationSection("messages").getKeys(false).forEach(key -> {
                messages.put(key, config.getString("messages." + key, ""));
            });
        }
        
        // 设置默认消息
        messages.putIfAbsent("success", "<h1 style='color:green'>Success! Player added.</h1>");
        messages.putIfAbsent("invalid_code", "<h1 style='color:red'>Invalid code!</h1>");
        messages.putIfAbsent("console_success", "Added {player} to whitelist");
        messages.putIfAbsent("console_error", "Error: {error}");
    }

    public void addToWhitelist(String username) {
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                OfflinePlayer player = Bukkit.getOfflinePlayer(username);
                player.setWhitelisted(true);
                Bukkit.reloadWhitelist();
                getLogger().info(messages.get("console_success")
                              .replace("{player}", username));
            } catch (Exception e) {
                getLogger().warning(messages.get("console_error")
                             .replace("{error}", e.getMessage()));
            }
        });
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public Map<String, String> getMessages() {
        return messages;
    }

    public File getWebFile(String name) {
        return new File(getDataFolder(), name);
    }

    // 内置的简单 HTTP 服务器
    public static class WebServer implements com.sun.net.httpserver.HttpHandler {
        private com.sun.net.httpserver.HttpServer server;
        private final TRWhiteList plugin;

        public WebServer(TRWhiteList plugin, int port) throws IOException {
            this.plugin = plugin;
            server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(port), 0);
            server.createContext("/", this);
        }

        public void start() {
            server.start();
        }

        public void stop() {
            server.stop(0);
        }

        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            String response;
            int status = 200;
            Map<String, String> messages = plugin.getMessages();
            
            try {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    // 处理表单提交
                    String formData = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                    Map<String, String> params = parseFormData(formData);
                    
                    String username = params.getOrDefault("username", "");
                    String code = params.getOrDefault("code", "");
                    
                    if (code.equals(plugin.getVerificationCode())) {
                        plugin.addToWhitelist(username);
                        response = messages.get("success");
                    } else {
                        response = messages.get("invalid_code");
                        status = 403;
                    }
                } else {
                    // 提供 HTML 页面
                    Path path = plugin.getWebFile("index.html").toPath();
                    if (Files.exists(path)) {
                        byte[] htmlBytes = Files.readAllBytes(path);
                        response = new String(htmlBytes, "UTF-8");
                    } else {
                        // 默认表单
                        response = "<html><body><h1>" + messages.getOrDefault("index_title", "TR WhiteList") + "</h1>" +
                                   "<form method='POST'>" +
                                   "<label>" + messages.getOrDefault("username_label", "Username") + 
                                   ":</label><input type='text' name='username'><br>" +
                                   "<label>" + messages.getOrDefault("code_label", "Code") + 
                                   ":</label><input type='password' name='code'><br>" +
                                   "<input type='submit' value='" + messages.getOrDefault("submit_button", "Submit") + "'>" +
                                   "</form></body></html>";
                    }
                }
                
                // 发送响应
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(status, response.getBytes("UTF-8").length);
                try (java.io.OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes("UTF-8"));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Web request error", e);
                String error = "Internal server error";
                exchange.sendResponseHeaders(500, error.length());
                try (java.io.OutputStream os = exchange.getResponseBody()) {
                    os.write(error.getBytes("UTF-8"));
                }
            }
        }

        private Map<String, String> parseFormData(String formData) {
            Map<String, String> result = new HashMap<>();
            for (String pair : formData.split("&")) {
                String[] entry = pair.split("=");
                if (entry.length == 2) {
                    try {
                        String key = java.net.URLDecoder.decode(entry[0], "UTF-8");
                        String value = java.net.URLDecoder.decode(entry[1], "UTF-8");
                        result.put(key, value);
                    } catch (Exception ignored) {}
                }
            }
            return result;
        }
    }
}
