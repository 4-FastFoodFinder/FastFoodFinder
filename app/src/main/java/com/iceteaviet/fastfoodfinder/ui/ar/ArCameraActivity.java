package com.iceteaviet.fastfoodfinder.ui.ar;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.iceteaviet.fastfoodfinder.App;
import com.iceteaviet.fastfoodfinder.R;
import com.iceteaviet.fastfoodfinder.data.DataManager;
import com.iceteaviet.fastfoodfinder.data.local.poi.model.AugmentedPOI;
import com.iceteaviet.fastfoodfinder.data.remote.store.model.Store;
import com.iceteaviet.fastfoodfinder.ui.base.BaseActivity;
import com.iceteaviet.fastfoodfinder.ui.custom.ar.ARCamera;
import com.iceteaviet.fastfoodfinder.ui.custom.ar.AROverlayView;
import com.iceteaviet.fastfoodfinder.utils.ui.UiUtils;

import java.util.List;

import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ArCameraActivity extends BaseActivity implements SensorEventListener, LocationListener {

    public static final int REQUEST_LOCATION_PERMISSIONS_CODE = 0;
    private final static String TAG = ArCameraActivity.class.getSimpleName();
    private final static int REQUEST_CAMERA_PERMISSIONS_CODE = 11;
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 0; // 10 meters
    private static final long MIN_TIME_BW_UPDATES = 0; //1000 * 60 * 1; // 1 minute
    private static final double RADIUS = 0.005;

    private Location location;
    private SurfaceView surfaceView;
    private FrameLayout cameraContainerLayout;
    private AROverlayView arOverlayView;
    private Camera camera;
    private ARCamera arCamera;
    private TextView tvCurrentLocation;
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private DataManager dataManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        cameraContainerLayout = findViewById(R.id.camera_container_layout);
        surfaceView = findViewById(R.id.surface_view);
        tvCurrentLocation = findViewById(R.id.tv_current_location);
        arOverlayView = new AROverlayView(this);

        dataManager = App.getDataManager();
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestLocationPermission();
        requestCameraPermission();
        registerSensors();
        initAROverlayView();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    protected void onPause() {
        releaseCamera();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        arOverlayView.destroy();
        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrixFromVector = new float[16];
            float[] projectionMatrix = new float[16];
            float[] rotatedProjectionMatrix = new float[16];

            SensorManager.getRotationMatrixFromVector(rotationMatrixFromVector, event.values);

            if (arCamera != null) {
                projectionMatrix = arCamera.getProjectionMatrix();
            }

            Matrix.multiplyMM(rotatedProjectionMatrix, 0, projectionMatrix, 0, rotationMatrixFromVector, 0);
            this.arOverlayView.updateRotatedProjectionMatrix(rotatedProjectionMatrix);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //do nothing
    }

    @Override
    public void onLocationChanged(Location location) {
        dataManager.getLocalStoreDataSource().getStoreInBounds(location.getLatitude() - RADIUS,
                location.getLongitude() - RADIUS,
                location.getLatitude() + RADIUS,
                location.getLongitude() + RADIUS)
                .observeOn(Schedulers.computation())
                .subscribe(new SingleObserver<List<Store>>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onSuccess(List<Store> storeList) {
                        for (int i = 0; i < storeList.size(); i++) {
                            arOverlayView.addArPoint(new AugmentedPOI(storeList.get(i).getTitle(),
                                    Double.valueOf(storeList.get(i).getLat()),
                                    Double.valueOf(storeList.get(i).getLng()),
                                    0,
                                    UiUtils.getStoreLogoDrawableId(storeList.get(i).getType())));
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }
                });

        //Toast.makeText(ArCameraActivity.this, "onLocationChanged", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public int getLayoutId() {
        return R.layout.activity_ar_camera;
    }

    public void requestCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                this.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            this.requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSIONS_CODE);
        } else {
            initARCameraView();
        }
    }

    public void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSIONS_CODE);
        } else {
            initLocationService();
        }
    }

    public void initAROverlayView() {
        if (arOverlayView.getParent() != null) {
            ((ViewGroup) arOverlayView.getParent()).removeView(arOverlayView);
        }
        cameraContainerLayout.addView(arOverlayView);
    }

    public void initARCameraView() {
        reloadSurfaceView();

        if (arCamera == null) {
            arCamera = new ARCamera(this, surfaceView);
        }
        if (arCamera.getParent() != null) {
            ((ViewGroup) arCamera.getParent()).removeView(arCamera);
        }
        cameraContainerLayout.addView(arCamera);
        arCamera.setKeepScreenOn(true);
        initCamera();
    }

    private void initCamera() {
        int numCams = Camera.getNumberOfCameras();
        if (numCams > 0) {
            try {
                camera = Camera.open();
                camera.startPreview();
                arCamera.setCamera(camera);
            } catch (RuntimeException ex) {
                Toast.makeText(this, "Camera not found", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void reloadSurfaceView() {
        if (surfaceView.getParent() != null) {
            ((ViewGroup) surfaceView.getParent()).removeView(surfaceView);
        }

        cameraContainerLayout.addView(surfaceView);
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            arCamera.setCamera(null);
            camera.release();
            camera = null;
        }
    }

    private void registerSensors() {
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_FASTEST);
    }

    private void initLocationService() {
        if (Build.VERSION.SDK_INT >= 23 &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            this.locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);

            // Get GPS and network status
            boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_BW_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                if (locationManager != null) {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    updateLatestLocation();
                }
            }

            if (isGPSEnabled) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        MIN_TIME_BW_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                if (locationManager != null) {
                    location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    updateLatestLocation();
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, ex.getMessage());

        }
    }

    private void updateLatestLocation() {
        if (arOverlayView != null) {
            arOverlayView.updateCurrentLocation(location);
            tvCurrentLocation.setText(String.format("lat: %s \nlon: %s \naltitude: %s \n",
                    location.getLatitude(), location.getLongitude(), location.getAltitude()));
        }
    }
}