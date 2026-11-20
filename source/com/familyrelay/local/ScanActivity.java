/*
 * Decompiled with CFR 0.152.
 */
package com.familyrelay.local;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.familyrelay.local.ContactCard;
import com.familyrelay.local.UiProtection;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScanActivity
extends Activity
implements SurfaceHolder.Callback {
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final AtomicBoolean decoding = new AtomicBoolean(false);
    private SurfaceView preview;
    private Camera camera;
    private boolean surfaceReady;
    private boolean resumed;
    private boolean found;
    private long lastFrame;
    private TextView hint;

    public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout((Context)this);
        root.setBackgroundColor(-16777216);
        this.preview = new SurfaceView((Context)this);
        this.preview.getHolder().addCallback((SurfaceHolder.Callback)this);
        root.addView((View)this.preview, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
        this.hint = new TextView((Context)this);
        this.hint.setText((CharSequence)"Scan an Improm contact QR\n\nKeep the entire QR in view.\nScanning runs entirely offline.");
        this.hint.setTextColor(-1);
        this.hint.setTextSize(18.0f);
        this.hint.setGravity(17);
        this.hint.setPadding(24, 48, 24, 48);
        this.hint.setBackgroundColor(-1291845632);
        FrameLayout.LayoutParams instructions = new FrameLayout.LayoutParams(-1, -2, 80);
        instructions.bottomMargin = 48;
        root.addView((View)this.hint, (ViewGroup.LayoutParams)instructions);
        this.setContentView((View)root);
        UiProtection.protect(this.getWindow());
        if (this.checkSelfPermission("android.permission.CAMERA") != 0) {
            this.requestPermissions(new String[]{"android.permission.CAMERA"}, 1);
        }
    }

    protected void onResume() {
        super.onResume();
        this.resumed = true;
        this.openCamera();
    }

    protected void onPause() {
        this.resumed = false;
        this.closeCamera();
        super.onPause();
    }

    protected void onDestroy() {
        this.decoder.shutdown();
        super.onDestroy();
    }

    public void surfaceCreated(SurfaceHolder holder) {
        this.surfaceReady = true;
        this.openCamera();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        this.surfaceReady = false;
        this.closeCamera();
    }

    private void openCamera() {
        if (!this.resumed || !this.surfaceReady || this.camera != null || this.found || this.checkSelfPermission("android.permission.CAMERA") != 0) {
            return;
        }
        try {
            int cameraId = -1;
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int id = 0; id < Camera.getNumberOfCameras(); ++id) {
                Camera.getCameraInfo((int)id, (Camera.CameraInfo)info);
                if (info.facing != 0) continue;
                cameraId = id;
                break;
            }
            if (cameraId < 0) {
                this.cameraError("No rear camera was found. Use Paste contact QR text in People instead.");
                return;
            }
            this.camera = Camera.open((int)cameraId);
            Camera.Parameters parameters = this.camera.getParameters();
            Camera.Size best = parameters.getSupportedPreviewSizes().stream().min(Comparator.comparingInt(size -> Math.abs(size.width * size.height - 307200))).orElseThrow(() -> new IllegalStateException("No camera preview size available."));
            parameters.setPreviewSize(best.width, best.height);
            parameters.setPreviewFormat(17);
            List modes = parameters.getSupportedFocusModes();
            if (modes != null && modes.contains("continuous-picture")) {
                parameters.setFocusMode("continuous-picture");
            }
            this.camera.setParameters(parameters);
            Camera.getCameraInfo((int)cameraId, (Camera.CameraInfo)info);
            int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
            int degrees = switch (rotation) {
                case 0 -> 0;
                case 1 -> 90;
                case 2 -> 180;
                case 3 -> 270;
                default -> 0;
            };
            this.camera.setDisplayOrientation((info.orientation - degrees + 360) % 360);
            this.camera.setPreviewDisplay(this.preview.getHolder());
            this.camera.setPreviewCallbackWithBuffer((data, source) -> this.decode(data, source, best.width, best.height));
            this.camera.addCallbackBuffer(new byte[best.width * best.height * 3 / 2]);
            this.camera.addCallbackBuffer(new byte[best.width * best.height * 3 / 2]);
            this.camera.startPreview();
        }
        catch (IOException | RuntimeException e) {
            Log.e((String)"Improm", (String)"Contact QR camera failed. Check camera availability and permission.");
            this.closeCamera();
            this.cameraError("Could not open the camera. Check camera permission or use Paste contact QR text.");
        }
    }

    private void decode(byte[] data, Camera source, int width, int height) {
        long now = SystemClock.elapsedRealtime();
        if (this.found || now - this.lastFrame < 250L || !this.decoding.compareAndSet(false, true)) {
            if (this.camera == source) {
                source.addCallbackBuffer(data);
            }
            return;
        }
        this.lastFrame = now;
        this.decoder.execute(() -> {
            MultiFormatReader reader = new MultiFormatReader();
            HashMap<DecodeHintType, Object> hints = new HashMap<DecodeHintType, Object>();
            hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
            hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            reader.setHints(hints);
            try {
                PlanarYUVLuminanceSource image = new PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false);
                Result result = reader.decodeWithState(new BinaryBitmap((Binarizer)new HybridBinarizer((LuminanceSource)image)));
                String value = result.getText();
                ContactCard.fromQr(value);
                this.runOnUiThread(() -> {
                    if (!this.found && this.resumed) {
                        this.found = true;
                        this.setResult(-1, new Intent().putExtra("qr", value));
                        this.finish();
                    }
                });
            }
            catch (ReaderException image) {
            }
            catch (IllegalArgumentException e) {
                this.runOnUiThread(() -> this.hint.setText((CharSequence)"This is not an Improm contact QR.\nAsk them to open People & your QR."));
            }
            finally {
                reader.reset();
                this.decoding.set(false);
                this.runOnUiThread(() -> {
                    if (this.camera == source && this.resumed && !this.found) {
                        source.addCallbackBuffer(data);
                    }
                });
            }
        });
    }

    private void closeCamera() {
        if (this.camera != null) {
            this.camera.setPreviewCallbackWithBuffer(null);
            this.camera.stopPreview();
            this.camera.release();
            this.camera = null;
        }
    }

    private void cameraError(String message) {
        if (!this.isFinishing()) {
            UiProtection.show(new AlertDialog.Builder((Context)this).setTitle((CharSequence)"QR scanner").setMessage((CharSequence)message).setPositiveButton((CharSequence)"Back", (dialog, which) -> this.finish()).setOnCancelListener(dialog -> this.finish()).create());
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (results.length > 0 && results[0] == 0) {
            this.openCamera();
        } else {
            this.cameraError("Camera permission is needed to scan a QR. You can paste QR text instead.");
        }
    }
}

