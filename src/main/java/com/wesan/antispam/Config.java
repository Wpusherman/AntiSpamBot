package com.wesan.antispam;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private final Properties properties = new Properties();

    public Config() throws IOException {
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            properties.load(fis);
        }
    }

    public String getToken() {
        return properties.getProperty("token");
    }
}
