package com.airoom.secureagent.capture;

import com.airoom.secureagent.log.HttpLogger;
import com.airoom.secureagent.log.LogManager;

import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class ProcessMonitor {

    // 마지막 감지 시각 저장
    private static final Map<String, Long> lastDetectedMap = new HashMap<>();
    private static final long DETECTION_INTERVAL_MS = 10_000; // 10초 중복 방지

    // 감지 대상: 주요 캡처/녹화 도구
    private static final Map<String, String> suspiciousProcesses = Map.ofEntries(
            entry("SnippingTool.exe", "윈도우 캡처 도구"),
            entry("ALCapture.exe", "알캡처"),
            entry("Snipaste.exe", "스닙페이스트"),
            entry("obs64.exe", "OBS Studio"),
            entry("obs32.exe", "OBS Studio (32bit)"),
            entry("bdcam.exe", "반디캠"),
            entry("GomCam.exe", "곰캠"),
            entry("CamtasiaStudio.exe", "캠타시아"),
            entry("GameBar.exe", "Xbox Game Bar"),
            entry("BroadcastDVRServer.exe", "Windows 녹화 서버"),
            entry("GameBarFT.exe", "Xbox GameBar FT"),
            entry("ScreenToGif.exe", "스크린투GIF"),
            entry("ShareX.exe", "ShareX"),
            entry("NVIDIA Share.exe", "NVIDIA 녹화 툴")
    );

    private static final SystemInfo systemInfo = new SystemInfo();
    private static final OperatingSystem os = systemInfo.getOperatingSystem();

    public static void detect() {
        try {
            // 1. tasklist 기반 감지
            Process process = Runtime.getRuntime().exec("tasklist");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                for (Map.Entry<String, String> entry : suspiciousProcesses.entrySet()) {
                    String proc = entry.getKey();
                    String appName = entry.getValue();

                    if (line.contains(proc)) {
                        long now = System.currentTimeMillis();
                        Long lastDetected = lastDetectedMap.get(proc);

                        // NVIDIA Share는 CPU 사용률로 필터링
                        if (proc.equals("NVIDIA Share.exe")) {
                            List<OSProcess> processes = os.getProcesses();

                            Optional<OSProcess> targetProcess = processes.stream()
                                    .filter(p -> {
                                        String path = p.getPath();
                                        return path != null && path.toLowerCase().endsWith("\\" + proc.toLowerCase());
                                    })
                                    .findFirst();

                            if (targetProcess.isPresent()) {
                                double cpu = targetProcess.get().getProcessCpuLoadCumulative() * 100;
                                if (cpu < 2.0) {
                                    continue; // idle 상태이면 무시
                                }
                            } else {
                                continue; // 감지 못함
                            }
                        }

                        if (lastDetected == null || (now - lastDetected > DETECTION_INTERVAL_MS)) {
                            String log = "[프로세스 감지] " + appName + " 실행 중 (" + proc + ")";
                            System.out.println("[SecureAgent] " + log);

                            LogManager.writeLog(log);
                            HttpLogger.sendLog(log);

                            lastDetectedMap.put(proc, now);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Map.Entry<String, String> entry(String key, String value) {
        return new AbstractMap.SimpleEntry<>(key, value);
    }
}
