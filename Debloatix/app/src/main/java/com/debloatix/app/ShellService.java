package com.debloatix.app;

import android.content.Context;
import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class ShellService extends IShellService.Stub {

    public ShellService() {}

    public ShellService(Context context) {}

    @Override
    public String exec(String command) throws RemoteException {
        if (command == null || command.length() > 2048) {
            return "EXIT=126\nCommande invalide";
        }
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", command});
            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                return "EXIT=124\nDélai dépassé";
            }

            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) out.append(line).append('\n');
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) out.append(line).append('\n');
            }
            return "EXIT=" + process.exitValue() + "\n" + out.toString().trim();
        } catch (Throwable t) {
            return "EXIT=125\n" + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
        }
    }
}
