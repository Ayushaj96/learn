package com.example.vasu.aismap;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

import com.example.vasu.aismap.Models.ClusteringItem;
import com.example.vasu.aismap.Models.DirectionJSONParser;
import com.example.vasu.aismap.Models.GetLinePath;
import com.example.vasu.aismap.Models.OwnClusterIconRendered;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        LocationListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private GoogleMap mMap;
    LocationRequest mLocationRequest;
    GoogleApiClient mGoogleApiClient;
    Location mCurrentLocation;

    FloatingActionButton floatingActionButton;
    private static final String TAG = "LocationActivity";
    private static final long INTERVAL = 1000 * 5;
    private static final long FASTEST_INTERVAL = 1000 * 5;

    LatLng position = new LatLng(28.6291027, 77.207133);
    MarkerOptions markerOptionsMyLoc;
    Marker myCurrentLocMarker, mPrevLocMarker;
    Circle mCircle , mPrevCircle;

    MachineDatabase machineDatabase;
    Cursor data ;

    SharedPreferences sharedPreferences ;
    float zoom = 15.0f ;
    float radius = 100.0f ;
    boolean moveMyLocCamera = true ;

    private ClusterManager<ClusteringItem> mClusterManager;

    Polyline pl = null ;

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        Log.d(TAG, "onCreate ...............................");


        machineDatabase = new MachineDatabase(this);
        data = machineDatabase.getListContents();

        sharedPreferences =getApplicationContext().getSharedPreferences("MyPref", MODE_PRIVATE);
        zoom = Float.parseFloat(sharedPreferences.getString("Zoom" , "15.0"));
        radius = Float.parseFloat(sharedPreferences.getString("Radius" , "100.0"));


        createLocationRequest();
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }  



    }

   public void show_machines_on_map(LatLng latLng){
        this.mMap.addMarker(new MarkerOptions().position(latLng).icon(BitmapDescriptorFactory.fromResource(R.drawable.machine)).title("Machine"));

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
            @Override
            public void onCameraIdle() {
                zoom  = mMap.getCameraPosition().zoom;
            }
        });
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 16));
        this.mMap.addMarker(new MarkerOptions().position(position).icon(BitmapDescriptorFactory.fromResource(R.drawable.machine)).title("Machine"));

        /* while (data.moveToNext()) {
            show_machines_on_map(new LatLng(Double.parseDouble(data.getString(2)) , Double.parseDouble(data.getString(1))));
        } */

        mClusterManager = new ClusterManager<ClusteringItem>(this, mMap);

        mMap.setOnMarkerClickListener(mClusterManager);
        mMap.setOnInfoWindowClickListener(mClusterManager);

        mMap.setOnCameraIdleListener(mClusterManager);
        try {
            readItems();
        } catch (JSONException e) {
            Toast.makeText(this, "Problem reading list of markers.", Toast.LENGTH_LONG).show();
        }

        mClusterManager.setOnClusterClickListener(new ClusterManager.OnClusterClickListener<ClusteringItem>() {
            @Override
            public boolean onClusterClick(Cluster<ClusteringItem> cluster) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        cluster.getPosition(), (float) Math.floor(mMap.getCameraPosition().zoom + 1)), 300, null);
                return true;
            }
        });

        mClusterManager.setRenderer(new OwnClusterIconRendered(this.getApplicationContext(), mMap, mClusterManager));

        MarkerInfoWindowAdapter markerInfoWindowAdapter = new MarkerInfoWindowAdapter(getApplicationContext());
        mMap.setInfoWindowAdapter(markerInfoWindowAdapter);

        mClusterManager.setOnClusterItemClickListener(new ClusterManager.OnClusterItemClickListener<ClusteringItem>() {
            @Override
            public boolean onClusterItemClick(ClusteringItem clusteringItem) {

                Toast.makeText(MapsActivity.this, ""+mCurrentLocation.getLatitude(), Toast.LENGTH_SHORT).show();
                LatLng origin = new LatLng(mCurrentLocation.getLatitude(),mCurrentLocation.getLongitude()) ;
                 GetLinePath glp = new GetLinePath() ;
                glp.origin = origin ;
                glp.dest = clusteringItem.getPosition() ;
                glp.getDirectionsUrl();
                if (pl != null){
                    pl.remove();
                }
                pl = mMap.addPolyline(glp.polyOpt);
                return false;
            }
        });

         /* mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                Toast.makeText(MapsActivity.this, "Marker", Toast.LENGTH_SHORT).show();
                return false;
            }
        }); */

    }


    private void readItems() throws JSONException {
        data.moveToFirst() ;
        while (data.moveToNext()){
            double lat = Double.parseDouble(data.getString(2)) ;
            double lng = Double.parseDouble(data.getString(1)) ;
            ClusteringItem offsetItem = new ClusteringItem(lat, lng);
            mClusterManager.addItem(offsetItem);
        }
    }


    @Override
    public void onConnected(Bundle bundle) {
        Log.d(TAG, "onConnected - isConnected ...............: " + mGoogleApiClient.isConnected());
        startLocationUpdates();
    }

    protected void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        PendingResult<Status> pendingResult = LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
        Log.d(TAG, "Location update started ..............: ");
    }


    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
       updateUI();
    }

    private void updateUI() {
        Log.d(TAG, "UI update initiated .............");
        if (null != mCurrentLocation) {

            Toast.makeText(this, ""+mCurrentLocation.getLatitude(), Toast.LENGTH_SHORT).show();

            int strokeColor = 0xffff0000; //red outline
            int shadeColor = 0x44ff0000; //opaque red fill

            Double lat = mCurrentLocation.getLatitude();
            Double lng = mCurrentLocation.getLongitude();

            LatLng ll = new LatLng(lat, lng);

            if (moveMyLocCamera) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ll, zoom));
                moveMyLocCamera = false ;
            }


            markerOptionsMyLoc = new MarkerOptions().position(ll).title("My Location");
            if (mPrevLocMarker != null){
                mPrevLocMarker.remove();
            }
            /*if (mPrevCircle != null){
                mPrevCircle.remove();
            }*/

            myCurrentLocMarker = mMap.addMarker(markerOptionsMyLoc.flat(true).rotation(mCurrentLocation.getBearing()).icon(BitmapDescriptorFactory.fromResource(R.drawable.marker)));
            mPrevLocMarker = myCurrentLocMarker ;
            /*CircleOptions circleOptions = new CircleOptions().center(ll).radius(radius).fillColor(shadeColor).strokeColor(strokeColor).strokeWidth(3);
            mCircle = mMap.addCircle(circleOptions);
            mPrevCircle = mCircle ;
 */
            float[] distance = new float[2] ;
            Location.distanceBetween(mCurrentLocation.getLatitude(),mCurrentLocation.getLongitude(),position.latitude,position.longitude,distance);


        } else {
            Log.d(TAG, "location is null ...............");
        }

    }





}
