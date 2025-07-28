package com.tr.webwhitelist;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class TRWhiteList extends JavaPlugin {
    private HttpServer webServer;
    private String verificationCode;
    private Map<String, String> messages = new HashMap<>();
    private int port;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        // 确保插件目录存在
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        // 保存默认配置文件
        saveDefaultConfig();
        config = getConfig();
        
        // 初始化配置
        reloadConfig();
        
        // 确保资源文件存在（修复资源加载问题）
        ensureResourceFiles();
        
        try {
            startWebServer();
            getLogger().info("Web server started on port " + port);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to start web server", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // 注册命令
        getCommand("trwl-reload").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("trwhitelist.reload")) {
                sender.sendMessage("§cYou don't have permission!");
                return true;
            }
            
            reloadConfig();
            restartWebServer();
            sender.sendMessage("§aConfiguration reloaded and web server restarted!");
            return true;
        });
    }

    @Override
    public void onDisable() {
        stopWebServer();
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        config = getConfig();
        
        // 加载端口
        port = config.getInt("port", 11434);
        
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
        messages.putIfAbsent("index_title", "TR WhiteList Portal");
        messages.putIfAbsent("username_label", "Minecraft Username");
        messages.putIfAbsent("code_label", "Verification Code");
        messages.putIfAbsent("submit_button", "Add to Whitelist");
        
        // 调试：打印加载的消息
        getLogger().info("Loaded messages: " + messages);
    }

    // 确保资源文件存在（修复资源加载问题）
    private void ensureResourceFiles() {
        // 确保配置文件存在
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        
        // 确保HTML文件存在
        File htmlFile = new File(getDataFolder(), "index.html");
        if (!htmlFile.exists()) {
            try (InputStream in = getResource("index.html")) {
                if (in != null) {
                    Files.copy(in, htmlFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    // 如果资源不存在，创建默认HTML
                    createDefaultHtmlFile(htmlFile);
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not save index.html", e);
                createDefaultHtmlFile(htmlFile);
            }
        }
    }
    
    // 创建默认HTML文件
    private void createDefaultHtmlFile(File file) {
        try {
            String defaultHtml = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                    "    <title>${index_title}</title>\n" +
                    "    <style>\n" +
                    "        * {\n" +
                    "            box-sizing: border-box;\n" +
                    "            margin: 0;\n" +
                    "            padding: 0;\n" +
                    "            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;\n" +
                    "        }\n" +
                    "        body {\n" +
                    "            background: linear-gradient(135deg, #1e5799, #207cca);\n" +
                    "            min-height: 100vh;\n" +
                    "            display: flex;\n" +
                    "            justify-content: center;\n" +
                    "            align-items: center;\n" +
                    "            padding: 20px;\n" +
                    "        }\n" +
                    "        .container {\n" +
                    "            background-color: rgba(255, 255, 255, 0.95);\n" +
                    "            border-radius: 12px;\n" +
                    "            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);\n" +
                    "            width: 100%;\n" +
                    "            max-width: 450px;\n" +
                    "            padding: 40px;\n" +
                    "            text-align: center;\n" +
                    "        }\n" +
                    "        h1 {\n" +
                    "            color: #2c3e50;\n" +
                    "            margin-bottom: 30px;\n" +
                    "            font-size: 28px;\n" +
                    "        }\n" +
                    "        .form-group {\n" +
                    "            margin-bottom: 25px;\n" +
                    "            text-align: left;\n" +
                    "        }\n" +
                    "        label {\n" +
                    "            display: block;\n" +
                    "            margin-bottom: 8px;\n" +
                    "            color: #2c3e50;\n" +
                    "            font-weight: 600;\n" +
                    "        }\n" +
                    "        input {\n" +
                    "            width: 100%;\n" +
                    "            padding: 14px;\n" +
                    "            border: 2px solid #e0e0e0;\n" +
                    "            border-radius: 8px;\n" +
                    "            font-size: 16px;\n" +
                    "            transition: border-color 0.3s;\n" +
                    "        }\n" +
                    "        input:focus {\n" +
                    "            border-color: #3498db;\n" +
                    "            outline: none;\n" +
                    "        }\n" +
                    "        button {\n" +
                    "            background: linear-gradient(135deg, #3498db, #2980b9);\n" +
                    "            color: white;\n" +
                    "            border: none;\n" +
                    "            padding: 15px 30px;\n" +
                    "            border-radius: 8px;\n" +
                    "            font-size: 18px;\n" +
                    "            font-weight: 600;\n" +
                    "            cursor: pointer;\n" +
                    "            transition: all 0.3s ease;\n" +
                    "            width: 100%;\n" +
                    "        }\n" +
                    "        button:hover {\n" +
                    "            transform: translateY(-2px);\n" +
                    "            box-shadow: 0 5px 15px rgba(0, 0, 0, 0.1);\n" +
                    "        }\n" +
                    "        .footer {\n" +
                    "            margin-top: 25px;\n" +
                    "            color: #7f8c8d;\n" +
                    "            font-size: 14px;\n" +
                    "        }\n" +
                    "    </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <div class=\"container\">\n" +
                    "        <h1>${index_title}</h1>\n" +
                    "        <form method=\"POST\">\n" +
                    "            <div class=\"form-group\">\n" +
                    "                <label for=\"username\">${username_label}</label>\n" +
                    "                <input type=\"text\" id=\"username\" name=\"username\" required>\n" +
                    "            </div>\n" +
                    "            \n" +
                    "            <div class=\"form-group\">\n" +
                    "                <label for=\"code\">${code_label}</label>\n" +
                    "                <input type=\"password\" id=\"code\" name=\"code\" required>\n" +
                    "            </div>\n" +
                    "            \n" +
                    "            <button type=\"submit\">${submit_button}</button>\n" +
                    "        </form>\n" +
                    "        <div class=\"footer\">\n" +
                    "            TRWhiteList Plugin v1.0\n" +
                    "        </div>\n" +
                    "    </div>\n" +
                    "</body>\n" +
                    "</html>";
            
            Files.write(file.toPath(), defaultHtml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not create default index.html", e);
        }
    }

    private void startWebServer() throws IOException {
        stopWebServer(); // 确保没有运行中的服务器
        
        webServer = HttpServer.create(new InetSocketAddress(port), 0);
        webServer.createContext("/", new WebHandler(this));
        webServer.setExecutor(null); // 使用默认执行器
        webServer.start();
    }
    
    private void restartWebServer() {
        try {
            stopWebServer();
            startWebServer();
            getLogger().info("Web server restarted on port " + port);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to restart web server", e);
        }
    }

    private void stopWebServer() {
        if (webServer != null) {
            webServer.stop(0);
            getLogger().info("Web server stopped");
            webServer = null;
        }
    }

    public void addToWhitelist(String username) {
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                OfflinePlayer player = Bukkit.getOfflinePlayer(username);
                player.setWhitelisted(true);
                Bukkit.reloadWhitelist();
                
                String msg = messages.get("console_success")
                    .replace("{player}", username);
                getLogger().info(msg);
            } catch (Exception e) {
                String msg = messages.get("console_error")
                    .replace("{error}", e.getMessage())
                    .replace("{player}", username);
                getLogger().warning(msg);
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

    // 应用语言设置到HTML内容
    private String applyLanguageSettings(String htmlContent) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return htmlContent;
        }
        
        String result = htmlContent;
        for (Map.Entry<String, String> entry : messages.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, entry.getValue());
        }
        return result;
    }

    static class WebHandler implements HttpHandler {
        private final TRWhiteList plugin;

        public WebHandler(TRWhiteList plugin) {
            this.plugin = plugin;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response;
            int status = 200;
            Map<String, String> messages = plugin.getMessages();
            
            try {
                String method = exchange.getRequestMethod();
                
                if ("POST".equalsIgnoreCase(method)) {
                    // 处理表单提交
                    String formData = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Map<String, String> params = parseFormData(formData);
                    
                    String username = params.getOrDefault("username", "");
                    String code = params.getOrDefault("code", "");
                    
                    if (username.isEmpty() || code.isEmpty()) {
                        response = "<h1>Missing parameters</h1>";
                        status = 400;
                    } else if (code.equals(plugin.getVerificationCode())) {
                        plugin.addToWhitelist(username);
                        response = messages.get("success");
                    } else {
                        response = messages.get("invalid_code");
                        status = 403;
                    }
                } else {
                    // 提供 HTML 页面
                    File htmlFile = plugin.getWebFile("index.html");
                    if (htmlFile.exists()) {
                        String htmlContent = new String(Files.readAllBytes(htmlFile.toPath()), StandardCharsets.UTF_8);
                        
                        // 应用语言设置
                        response = plugin.applyLanguageSettings(htmlContent);
                    } else {
                        // 默认表单
                        String defaultHtml = "<html><body><h1>${index_title}</h1>" +
                                   "<form method='POST'>" +
                                   "<label>${username_label}:</label><input type='text' name='username' required><br>" +
                                   "<label>${code_label}:</label><input type='password' name='code' required><br>" +
                                   "<input type='submit' value='${submit_button}'>" +
                                   "</form></body></html>";
                        
                        // 应用语言设置
                        response = plugin.applyLanguageSettings(defaultHtml);
                    }
                }
                
                // 发送响应
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(status, response.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Web request error", e);
                String error = "Internal server error";
                exchange.sendResponseHeaders(500, error.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(error.getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        private Map<String, String> parseFormData(String formData) {
            Map<String, String> result = new HashMap<>();
            for (String pair : formData.split("&")) {
                String[] entry = pair.split("=");
                if (entry.length == 2) {
                    try {
                        String key = java.net.URLDecoder.decode(entry[0], StandardCharsets.UTF_8);
                        String value = java.net.URLDecoder.decode(entry[1], StandardCharsets.UTF_8);
                        result.put(key, value);
                    } catch (Exception ignored) {}
                }
            }
            return result;
        }
    }
}
