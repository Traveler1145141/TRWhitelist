package com.tr.webwhitelist;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class TRWhiteList extends JavaPlugin {
    private HttpServer webServer;
    private String verificationCode;
    private Map<String, String> messages = new HashMap<>();
    private int port;
    private FileConfiguration config;
    private FileConfiguration emailConfig;
    private File emailFile;
    private Set<String> registeredEmails = new HashSet<>();
    private List<String> allowedEmailSuffixes = new ArrayList<>();

    @Override
    public void onEnable() {
        // 确保插件目录存在
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        // 初始化配置文件
        saveDefaultConfig();
        config = getConfig();
        
        // 初始化邮箱配置文件
        emailFile = new File(getDataFolder(), "emails.yml");
        if (!emailFile.exists()) {
            saveResource("emails.yml", false);
        }
        emailConfig = YamlConfiguration.loadConfiguration(emailFile);
        
        // 初始化配置
        reloadConfig();
        
        // 确保资源文件存在
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
        
        // 注册清理邮箱命令
        getCommand("trwl-clear-emails").setExecutor((sender, command, label, args) -> {
            if (!sender.hasPermission("trwhitelist.admin")) {
                sender.sendMessage("§cYou don't have permission!");
                return true;
            }
            
            registeredEmails.clear();
            saveEmailConfig();
            sender.sendMessage("§aEmail registry cleared!");
            return true;
        });
    }

    @Override
    public void onDisable() {
        stopWebServer();
        saveEmailConfig();
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        config = getConfig();
        
        // 加载端口
        port = config.getInt("port", 11434);
        
        // 加载验证码
        verificationCode = config.getString("verification-code", "default");
        
        // 加载允许的邮箱后缀
        allowedEmailSuffixes = config.getStringList("allowed-email-suffixes");
        
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
        messages.putIfAbsent("email_required", "<h1 style='color:red'>Email is required!</h1>");
        messages.putIfAbsent("invalid_email", "<h1 style='color:red'>Invalid email format!</h1>");
        messages.putIfAbsent("email_suffix_not_allowed", "<h1 style='color:red'>Email suffix not allowed! Allowed: {suffixes}</h1>");
        messages.putIfAbsent("email_already_registered", "<h1 style='color:red'>This email is already registered!</h1>");
        messages.putIfAbsent("console_success", "Added {player} to whitelist");
        messages.putIfAbsent("console_error", "Error: {error}");
        messages.putIfAbsent("index_title", "TR WhiteList Portal");
        messages.putIfAbsent("username_label", "Minecraft Username");
        messages.putIfAbsent("email_label", "Email Address");
        messages.putIfAbsent("code_label", "Verification Code");
        messages.putIfAbsent("submit_button", "Add to Whitelist");
        
        // 加载已注册邮箱
        loadEmailConfig();
    }

    // 确保资源文件存在
    private void ensureResourceFiles() {
        // 确保配置文件存在
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        
        // 确保邮箱配置文件存在
        if (!emailFile.exists()) {
            saveResource("emails.yml", false);
        }
        
        // 确保HTML文件存在
        File htmlFile = new File(getDataFolder(), "index.html");
        if (!htmlFile.exists()) {
            try (InputStream in = getResource("index.html")) {
                if (in != null) {
                    Files.copy(in, htmlFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
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
                    "        .info {\n" +
                    "            margin-top: 15px;\n" +
                    "            font-size: 14px;\n" +
                    "            color: #7f8c8d;\n" +
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
                    "                <label for=\"email\">${email_label}</label>\n" +
                    "                <input type=\"email\" id=\"email\" name=\"email\" required>\n" +
                    "            </div>\n" +
                    "            \n" +
                    "            <div class=\"form-group\">\n" +
                    "                <label for=\"code\">${code_label}</label>\n" +
                    "                <input type=\"password\" id=\"code\" name=\"code\" required>\n" +
                    "            </div>\n" +
                    "            \n" +
                    "            <button type=\"submit\">${submit_button}</button>\n" +
                    "        </form>\n" +
                    "        <div class=\"info\">\n" +
                    "            Only emails with allowed suffixes can be registered.\n" +
                    "        </div>\n" +
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

    // 加载邮箱配置
    private void loadEmailConfig() {
        registeredEmails.clear();
        if (emailConfig.isConfigurationSection("registered")) {
            ConfigurationSection section = emailConfig.getConfigurationSection("registered");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    registeredEmails.add(key.toLowerCase());
                }
            }
        }
        getLogger().info("Loaded " + registeredEmails.size() + " registered emails");
    }
    
    // 保存邮箱配置
    private void saveEmailConfig() {
        try {
            emailConfig.set("registered", null); // 清除旧数据
            
            for (String email : registeredEmails) {
                emailConfig.set("registered." + email, true);
            }
            
            emailConfig.save(emailFile);
            getLogger().info("Saved " + registeredEmails.size() + " registered emails");
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save emails.yml", e);
        }
    }

    // 添加白名单并记录邮箱
    public void addToWhitelist(String username, String email) {
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                // 获取玩家对象
                OfflinePlayer player = Bukkit.getOfflinePlayer(username);
                
                // 检查玩家是否已经存在
                if (player.isWhitelisted()) {
                    getLogger().info("Player " + username + " is already whitelisted");
                    return;
                }
                
                // 添加玩家到白名单
                player.setWhitelisted(true);
                
                // 记录邮箱
                String normalizedEmail = email.toLowerCase();
                if (!registeredEmails.contains(normalizedEmail)) {
                    registeredEmails.add(normalizedEmail);
                    saveEmailConfig();
                }
                
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

    // 验证邮箱格式
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        
        // 简单的邮箱格式验证
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        return Pattern.compile(emailRegex).matcher(email).matches();
    }
    
    // 验证邮箱后缀是否允许
    private boolean isEmailSuffixAllowed(String email) {
        if (allowedEmailSuffixes.isEmpty()) {
            return true; // 如果没有限制，则允许所有
        }
        
        for (String suffix : allowedEmailSuffixes) {
            if (email.toLowerCase().endsWith(suffix.toLowerCase())) {
                return true;
            }
        }
        return false;
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
                    String email = params.getOrDefault("email", "");
                    String code = params.getOrDefault("code", "");
                    
                    // 验证必填字段
                    if (username.isEmpty() || email.isEmpty() || code.isEmpty()) {
                        response = "<h1>Missing parameters</h1>";
                        status = 400;
                    } 
                    // 验证邮箱格式
                    else if (!plugin.isValidEmail(email)) {
                        response = messages.get("invalid_email");
                        status = 400;
                    }
                    // 验证邮箱后缀
                    else if (!plugin.isEmailSuffixAllowed(email)) {
                        String suffixes = String.join(", ", plugin.allowedEmailSuffixes);
                        response = messages.get("email_suffix_not_allowed")
                                .replace("{suffixes}", suffixes);
                        status = 403;
                    }
                    // 验证邮箱是否已注册
                    else if (plugin.registeredEmails.contains(email.toLowerCase())) {
                        response = messages.get("email_already_registered");
                        status = 403;
                    }
                    // 验证验证码
                    else if (!code.equals(plugin.getVerificationCode())) {
                        response = messages.get("invalid_code");
                        status = 403;
                    }
                    // 所有验证通过
                    else {
                        plugin.addToWhitelist(username, email);
                        response = messages.get("success");
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
                                   "<label>${email_label}:</label><input type='email' name='email' required><br>" +
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
