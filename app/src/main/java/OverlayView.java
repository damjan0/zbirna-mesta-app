package com.pjevic.damjan.artest3;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


import com.pjevic.damjan.artest3.ArtutActivity;

import java.io.IOException;

public class OverlayView extends View implements SensorEventListener,
        LocationListener {

    public static final String DEBUG_TAG = "OverlayView Log";

    private final Context context;
    private Handler handler;

    //public static double LAT = 46.62804147820471;     //krizisce lipovci
    //public static double LON = 16.227163978541967;
    public static double LAT = 0.0;
    public static double LON = 0.0;

    String accelData = "Accelerometer Data";
    String compassData = "Compass Data";
    String gyroData = "Gyro Data";

    private LocationManager locationManager = null;
    private SensorManager sensors = null;

    private Location lastLocation;
    private float[] lastAccelerometer;
    private float[] lastCompass;

    private float verticalFOV;
    private float horizontalFOV;

    private boolean isAccelAvailable;
    private boolean isCompassAvailable;
    private boolean isGyroAvailable;
    private Sensor accelSensor;
    private Sensor compassSensor;
    private Sensor gyroSensor;

    private TextPaint contentPaint;
    private TextPaint contentPaint2;

    private Paint targetPaint;

    private String html;
    public double[] pointGPS;

    private static Location mPOI = new Location("manual");
    static {
        mPOI.setLatitude(LAT);
        mPOI.setLongitude(LON);
        mPOI.setAltitude(180);
    }

    public boolean noNet = false;

    public OverlayView(Context context) {
        super(context);
        this.context = context;
        this.handler = new Handler();
        locationManager = (LocationManager) context
                .getSystemService(Context.LOCATION_SERVICE);

        sensors = (SensorManager) context
                .getSystemService(Context.SENSOR_SERVICE);
        accelSensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        compassSensor = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyroSensor = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        //POBERI DOL Z NETA LON,LAT
        html = "https://pjevic.com/ar/koordinateZbirno.html";
        if (checkNet(context)) {
            new pojdiNaNet().execute(); //vklopi jsoup
        } else {
            noNet = true;
        }

        startSensors();
        startGPS();

        // get some camera parameters
        Camera camera = Camera.open();
        Camera.Parameters params = camera.getParameters();
        verticalFOV = params.getVerticalViewAngle();
        horizontalFOV = params.getHorizontalViewAngle();
        camera.release();

        // paint for text
        contentPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        contentPaint.setTextAlign(Align.LEFT);
        contentPaint.setTextSize(35);
        contentPaint.setColor(Color.RED);

        // paint for whereToLook
        contentPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        contentPaint2.setTextAlign(Align.CENTER);
        contentPaint2.setTextSize(75);
        contentPaint2.setColor(Color.GREEN);

        // paint for target

        targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetPaint.setColor(Color.GREEN);

    }

    public class pojdiNaNet extends AsyncTask<Void,Void,Void> {

        //ka ven pobere
        @Override
        protected Void doInBackground(Void... voids) {
            //nalozi seznam prevodov za ono crko
            dolPotegniGPS();
            return null;
        }

        //PREVERI ali med prevodi se najde nasa beseda
        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            //preveri ujemannje
            makeNewGPS(); //to napravi ko se ono prej izvede
        }
    }

    private StringBuilder bilder;

    public void dolPotegniGPS(){
        try {
            Log.d("MyApp","connected to jsoup");
            Document doc = Jsoup.connect(html).get();   //pobere html
            Elements prevodi = doc.select("p"); //pobere samo prevode dol
            bilder = new StringBuilder();               //stringbilder keroga urejam

            for (Element prevod : prevodi){             //idi skozi vse prevode
                bilder.append(prevod.text()).append("\n");      //za on string napravi enter za vsakin prevodon
            }
            pointGPS = new double[2];      //samo za primerjati  - nucamo samo prekmurski del

            //vstavi vse elements v tabelo stringov namesto elements:
            int i=0;
            for (Element prevod : prevodi){
                String vrsticaBesede = prevod.text();       //npr. "ka - kaj"  -- trbej še razdeliti na prekmurski in slovenski del
                pointGPS[i] = Double.parseDouble(vrsticaBesede);
                Log.d("MyApp","tu notri smo");
                Log.d("MyApp",vrsticaBesede);
                i++;
            }

        } catch (IOException e) {e.printStackTrace();}
    }

    public void makeNewGPS(){
        LAT = pointGPS[0];
        LON = pointGPS[1];
        mPOI.setLatitude(LAT);
        mPOI.setLongitude(LON);
        Log.d("MyApp",String.valueOf(LON));
    }

    private void startSensors() {
        isAccelAvailable = sensors.registerListener(this, accelSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        isCompassAvailable = sensors.registerListener(this, compassSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
        isGyroAvailable = sensors.registerListener(this, gyroSensor,
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void startGPS() {
        Criteria criteria = new Criteria();
        // criteria.setAccuracy(Criteria.ACCURACY_FINE);
        // while we want fine accuracy, it's unlikely to work indoors where we
        // do our testing. :)
        criteria.setAccuracy(Criteria.NO_REQUIREMENT);
        criteria.setPowerRequirement(Criteria.NO_REQUIREMENT);

        String best = locationManager.getBestProvider(criteria, true);

        Log.v(DEBUG_TAG, "Best provider: " + best);


        //if permission is granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(best, 50, 0, this);
        }

    }


    public double calculateDistance(){
        double lon1 = mPOI.getLongitude();
        double lat1 = mPOI.getLatitude();
        double lon2 = lastLocation.getLongitude();
        double lat2 = lastLocation.getLatitude();
        double theta = lon1 - lon2;
        double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
        dist = Math.acos(dist);
        dist = Math.toDegrees(dist);
        dist = dist * 60 * 1.1515;
        dist = dist * 1.609344; //to km
        dist = dist*1000; //to m
        dist = (double)Math.round(dist* 100.0) / 100.0;
        return dist;
    }


    @Override
    protected void onDraw(Canvas canvas) {
        //Log.d(DEBUG_TAG, "onDraw");
        super.onDraw(canvas);

        // Draw something fixed (for now) over the camera view
        float curBearingTomPOI = 0.0f;

        /**
         *  Tekst za čez overlay
         */
        StringBuilder text = new StringBuilder();
        StringBuilder whereToLook = new StringBuilder();
        //text.append(accelData).append("\n");
        //text.append(compassData).append("\n");
        //text.append(gyroData).append("\n");

        if(noNet) {
            text.append(String.format("Ni internetne povezave...")).append("\n");
        }
        if (lastLocation == null) text.append(String.format("Cakam na GPS signal....")).append("\n");

        if (lastLocation != null && !noNet) {
            text.append(
                    String.format("GPS: (%.5f, %.5f)",
                            lastLocation.getLatitude(),
                            lastLocation.getLongitude())).append("\n");

            curBearingTomPOI = lastLocation.bearingTo(mPOI);

            text.append(String.format("Azimut nase tocke: %.3f", curBearingTomPOI))
                    .append("\n");
            text.append(String.format("Razdalja do tocke: %.2f m", calculateDistance()))
                    .append("\n");

        }

        /**
         *  Dobi orientacio telefona in naš azimut iz senzora
         */
        // compute rotation matrix
        float rotation[] = new float[9];
        float identity[] = new float[9];
        if (lastAccelerometer != null && lastCompass != null) {
            boolean gotRotation = SensorManager.getRotationMatrix(rotation,
                    identity, lastAccelerometer, lastCompass);
            if (gotRotation) {
                float cameraRotation[] = new float[9];
                // remap such that the camera is pointing straight down the Y
                // axis
                SensorManager.remapCoordinateSystem(rotation,
                        SensorManager.AXIS_X, SensorManager.AXIS_Z,
                        cameraRotation);

                // orientation vector
                float orientation[] = new float[3];
                SensorManager.getOrientation(cameraRotation, orientation);

               // text.append(
               //         String.format("Orientation (%.3f, %.3f, %.3f)",
               //                 Math.toDegrees(orientation[0]), Math.toDegrees(orientation[1]), Math.toDegrees(orientation[2])))
               //         .append("\n");
                text.append(
                        String.format("Naš azimuth: %.3f", Math.toDegrees(orientation[0]))).append("\n");
                //orientation[0] = levo, desno (za stopinje trbej še dati v Math.toDegrees)
                //orientation[1] = gor, dol
                //orientation[2] = rotacija

                //GLEDATI LEVO ALI DESNO
                //our azimuth
                float tempOurAzi;
                if (orientation[0]<0){
                    tempOurAzi = (float)Math.toDegrees(orientation[0])+360;
                }else{
                    tempOurAzi = (float)Math.toDegrees(orientation[0]);
                }
                //text.append(String.format("tempOurAzi: %.3f", tempOurAzi)).append("\n");
                //point azimuth
                float tempPointAZI;
                if (curBearingTomPOI<0){
                    tempPointAZI = curBearingTomPOI+360;
                }else{
                    tempPointAZI = curBearingTomPOI;
                }
                //text.append(String.format("tempPointAZI: %.3f", tempPointAZI)).append("\n");

                float kelkoaNanFali = 360-tempOurAzi;
                tempOurAzi = 0;    //zdaj bi moglo biti 360
                tempPointAZI += kelkoaNanFali;
                //text.append(String.format("tempOurAzi: %.3f", tempOurAzi)).append("\n");
                //text.append(String.format("tempPointAZI: %.3f", tempPointAZI)).append("\n");

                if(tempPointAZI>=360) tempPointAZI = tempPointAZI % 360;
                //text.append(String.format("tempPointAZI: %.3f", tempPointAZI)).append("\n");

                if (tempPointAZI>180){
                    if (lastLocation != null && !noNet)  whereToLook.append(String.format("LEVO"))
                            .append("\n");
                }else{
                    if (lastLocation != null && !noNet) whereToLook.append(String.format("DESNO"))
                            .append("\n");
                }



                //"keep device leveled"
                if (Math.abs(Math.toDegrees(orientation[2])) >= 25 || Math.abs(Math.toDegrees(orientation[1])) >= 40) {
                    text.append(String.format("Keep device leveled..."))
                            .append("\n");
                }
                //text.append(String.format("%.3f",Math.abs(Math.toDegrees(orientation[1]))))
                  //      .append("\n");

                    /**
                     *   DRAW EVERYTHING
                     */
                // draw horizon line (a nice sanity check piece) and the target (if it's on the screen)
                canvas.save();
                // use roll for screen rotation
                //canvas.rotate((float)(0.0f- Math.toDegrees(orientation[2])));

                // Translate, but normalize for the FOV of the camera -- basically, pixels per degree, times degrees == pixels
                float dx = (float) ( (canvas.getWidth()/ horizontalFOV) * (Math.toDegrees(orientation[0])-curBearingTomPOI));
                float dy = (float) ( (canvas.getHeight()/ verticalFOV) * Math.toDegrees(orientation[1])) ;  //za gor, dol
                //canvas.translate(0.0f, 0.0f-dy);  //za gor, dol


                // now translate the dx
                canvas.translate(0.0f-dx, 0.0f);
                // draw our point -- we've rotated and translated this to the right spot already

                if (lastLocation != null && !noNet) {
                    canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, 80.0f, targetPaint);

                    ImageView imageView = new ImageView(context);
                    Drawable drawable  = getResources().getDrawable(R.mipmap.group);
                    imageView.setImageDrawable(drawable);
                    imageView.setX(canvas.getWidth() / 2);
                    imageView.setY(canvas.getHeight() / 2);

                }
                canvas.restore();

            }
        }

        canvas.save();
        canvas.translate(15.0f, 15.0f);
        StaticLayout textBox = new StaticLayout(text.toString(), contentPaint,
                500, Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
        textBox.draw(canvas);
        canvas.drawText(whereToLook.toString(), canvas.getWidth()/2, canvas.getHeight() + ((contentPaint2.descent() + contentPaint2.ascent()))  , contentPaint2);
        canvas.restore();
    }

    public void onAccuracyChanged(Sensor arg0, int arg1) {
        Log.d(DEBUG_TAG, "onAccuracyChanged");

    }

    //LOW-PASS filter da ikona ne skače vse posedi
    static final float ALPHA = 0.15f; // if ALPHA = 1 OR 0, no filter applies.
    protected float[] lowPass( float[] input, float[] output ) {
        if ( output == null ) return input;
        for ( int i=0; i<input.length; i++ ) {
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }
        return output;
    }

    public void onSensorChanged(SensorEvent event) {
        // Log.d(DEBUG_TAG, "onSensorChanged");

        StringBuilder msg = new StringBuilder(event.sensor.getName())
                .append(" ");
        for (float value : event.values) {
            msg.append("[").append(String.format("%.3f", value)).append("]");
        }

        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                lastAccelerometer = lowPass(event.values.clone(), lastAccelerometer);
                accelData = msg.toString();
                break;
            case Sensor.TYPE_GYROSCOPE:
                gyroData = msg.toString();
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                lastCompass = lowPass(event.values.clone(),lastCompass);
                compassData = msg.toString();
                break;
        }

        this.invalidate();
    }

    public void onLocationChanged(Location location) {
        // store it off for use when we need it
        lastLocation = location;
    }

    public void onProviderDisabled(String provider) {
        // ...
    }

    public void onProviderEnabled(String provider) {
        // ...
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
        // ...
    }

    // this is not an override
    public void onPause() {
        locationManager.removeUpdates(this);
        sensors.unregisterListener(this);
    }

    // this is not an override
    public void onResume() {
        startSensors();
        startGPS();
    }

    public boolean checkNet(Context ctx){
        ConnectivityManager connectivityManager = (ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).getState() == NetworkInfo.State.CONNECTED ||
                connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState() == NetworkInfo.State.CONNECTED) {
            //we are connected to a network
            return true;
        }
        else {
            return false;
        }
    }
}
