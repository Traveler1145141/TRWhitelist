private void createDefaultHtmlFile(File file) {
    try {
        // 使用简单的HTML结构
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
                "            font-family: Arial, sans-serif;\n" +
                "        }\n" +
                "        body {\n" +
                "            background: #1e5799;\n" +
                "            min-height: 100vh;\n" +
                "            display: flex;\n" +
                "            justify-content: center;\n" +
                "            align-items: center;\n" +
                "            padding: 20px;\n" +
                "        }\n" +
                "        .container {\n" +
                "            background-color: rgba(255, 255, 255, 0.95);\n" +
                "            border-radius: 8px;\n" +
                "            box-shadow: 0 5px 15px rgba(0, 0, 0, 0.1);\n" +
                "            width: 100%;\n" +
                "            max-width: 450px;\n" +
                "            padding: 30px;\n" +
                "        }\n" +
                "        h1 {\n" +
                "            color: #2c3e50;\n" +
                "            margin-bottom: 20px;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        .form-group {\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        label {\n" +
                "            display: block;\n" +
                "            margin-bottom: 8px;\n" +
                "            color: #2c3e50;\n" +
                "        }\n" +
                "        input {\n" +
                "            width: 100%;\n" +
                "            padding: 12px;\n" +
                "            border: 1px solid #ddd;\n" +
                "            border-radius: 4px;\n" +
                "        }\n" +
                "        button {\n" +
                "            background: #3498db;\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            padding: 12px 20px;\n" +
                "            border-radius: 4px;\n" +
                "            width: 100%;\n" +
                "            cursor: pointer;\n" +
                "        }\n" +
                "        .footer {\n" +
                "            margin-top: 20px;\n" +
                "            text-align: center;\n" +
                "            color: #7f8c8d;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <h1>${index_title}</h1>\n" +
                "        <form method=\"POST\">\n" +
                "            <div class=\"form-group\">\n" +
                "                <label>${username_label}</label>\n" +
                "                <input type=\"text\" name=\"username\" required>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"form-group\">\n" +
                "                <label>${email_label}</label>\n" +
                "                <input type=\"email\" name=\"email\" required>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"form-group\">\n" +
                "                <label>${code_label}</label>\n" +
                "                <input type=\"password\" name=\"code\" required>\n" +
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
