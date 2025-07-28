package com.tr.webwhitelist;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TRWhiteList extends JavaPlugin {
    private Server webServer;
    private String verificationCode;
    private Map<String, String> messages = new HashMap<>();
    private Path dataFolder;
    private Path htmlPath;

    @Override
    public void onEnable() {
        dataFolder = getDataFolder().toPath();
        htmlPath = dataFolder.resolve("index.html");
        
        // 确保必要的目录和文件存在
        try {
            if (!Files.exists(dataFolder)) Files.createDirectories(dataFolder);
            if (!Files.exists(dataFolder.resolve("config.yml"))) saveResource("config.yml", false);
            if (!Files.exists(htmlPath)) saveResource("index.html", false);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not create plugin files", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        reloadConfigData();
        startWebServer();
    }

    @Override
    public void onDisable() {
        stopWebServer();
    }

    public void reloadConfigData() {
        reloadConfig();
        verificationCode = getConfig().getString("verification-code", "defaultCode");
        
        // 安全加载消息配置
        if (getConfig().isConfigurationSection("messages")) {
            for (String key : getConfig().getConfigurationSection("messages").getKeys(false)) {
                messages.put(key, getConfig().getString("messages." + key, ""));
            }
        }
        
        // 设置默认消息以防配置缺失
        messages.putIfAbsent("success", "<h1>Player added successfully!</h1>");
        messages.putIfAbsent("invalid_code", "<h1>Invalid verification code</h1>");
        messages.putIfAbsent("console_success", "Added {player} to whitelist");
        messages.putIfAbsent("console_error", "Error adding player: {error}");
        messages.putIfAbsent("index_title", "TR WhiteList Portal");
        messages.putIfAbsent("username_label", "Minecraft Username");
        messages.putIfAbsent("code_label", "Verification Code");
        messages.putIfAbsent("submit_button", "Add to Whitelist");
    }

    private void startWebServer() {
        new Thread(() -> {
            try {
                webServer = new Server(11434);
                webServer.setHandler(new WebHandler());
                webServer.start();
                webServer.join();
                getLogger().info("Web server started on port 11434");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Web server error", e);
            }
        }, "TRWhiteList-WebServer").start();
    }

    private void stopWebServer() {
        if (webServer != null && webServer.isStarted()) {
            try {
                webServer.stop();
                getLogger().info("Web server stopped");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to stop web server", e);
            }
        }
    }

    class WebHandler extends AbstractHandler {
        private final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(.*?)\\}");
        
        @Override
        public void handle(String target, Request baseRequest, 
                          HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
            
            baseRequest.setHandled(true);
            response.setContentType("text/html;charset=utf-8");
            
            try {
                if ("POST".equalsIgnoreCase(request.getMethod())) {
                    handlePostRequest(request, response);
                } else {
                    serveIndexPage(response);
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Error handling web request", e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().println("<h1>Internal Server Error</h1>");
            }
        }
        
        private void handlePostRequest(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
            
            String username = request.getParameter("username");
            String enteredCode = request.getParameter("code");
            
            if (enteredCode == null || username == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().println("<h1>Missing parameters</h1>");
                return;
            }
            
            if (enteredCode.equals(verificationCode)) {
                addToWhitelist(username);
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().println(messages.get("success"));
            } else {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().println(messages.get("invalid_code"));
            }
        }
        
        private void serveIndexPage(HttpServletResponse response) throws IOException {
            if (Files.exists(htmlPath)) {
                String htmlContent = new String(Files.readAllBytes(htmlPath), StandardCharsets.UTF_8);
                
                // 替换占位符
                Matcher matcher = PLACEHOLDER_PATTERN.matcher(htmlContent);
                StringBuffer result = new StringBuffer();
                while (matcher.find()) {
                    String placeholder = matcher.group(1);
                    String replacement = messages.getOrDefault(placeholder, "");
                    matcher.appendReplacement(result, replacement);
                }
                matcher.appendTail(result);
                
                response.getWriter().print(result.toString());
            } else {
                // 应急回退表单
                String form = "<html><body><h1>${index_title}</h1>" +
                    "<form method='POST'>" +
                    "${username_label}: <input type='text' name='username' required><br>" +
                    "${code_label}: <input type='password' name='code' required><br>" +
                    "<input type='submit' value='${submit_button}'>" +
                    "</form></body></html>";
                
                response.getWriter().print(form.replace("${index_title}", messages.get("index_title"))
                    .replace("${username_label}", messages.get("username_label"))
                    .replace("${code_label}", messages.get("code_label"))
                    .replace("${submit_button}", messages.get("submit_button")));
            }
        }
    }

    private void addToWhitelist(String username) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(username);
                    player.setWhitelisted(true);
                    Bukkit.reloadWhitelist();
                    
                    String msg = messages.get("console_success")
                        .replace("{player}", username)
                        .replace("{error}", "");
                    getLogger().info(msg);
                } catch (Exception e) {
                    String msg = messages.get("console_error")
                        .replace("{player}", username)
                        .replace("{error}", e.getMessage());
                    getLogger().warning(msg);
                }
            }
        }.runTask(this);
    }
}
