package com.wesan.antispam;

import java.io.*;
import java.util.Properties;

// read properties file
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

    public int getDeadline() throws NumberFormatException {
        return Integer.parseInt(properties.getProperty("deadline"));
    }
}
