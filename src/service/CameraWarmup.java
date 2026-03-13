package service;

import com.github.sarxos.webcam.Webcam;

import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CameraWarmup {

    private static final AtomicBoolean WARMING = new AtomicBoolean(false);
    private static final AtomicBoolean WARMED  = new AtomicBoolean(false);

    private CameraWarmup() {}

    public static void warmAsync() {
        if (WARMED.get()) return;
        if (!WARMING.compareAndSet(false, true)) return;

        Thread t = new Thread(() -> {
            Webcam webcam = null;
            try {
                webcam = Webcam.getDefault();
                if (webcam == null) return;

                webcam.setViewSize(new Dimension(640, 480));
                webcam.open(true);

                for (int i = 0; i < 3; i++) {
                    webcam.getImage();
                    try { Thread.sleep(40); } catch (InterruptedException ignored) {}
                }

                WARMED.set(true);
            } catch (Throwable ignored) {
            } finally {
                try { if (webcam != null) webcam.close(); } catch (Throwable ignored) {}
                WARMING.set(false);
            }
        }, "camera-warmup");

        t.setDaemon(true);
        t.start();
    }

    public static boolean isWarmed() {
        return WARMED.get();
    }
}
