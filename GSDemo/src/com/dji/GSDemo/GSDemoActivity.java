package com.dji.GSDemo;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import dji.midware.data.manager.P3.ServiceManager;
import dji.sdk.api.DJIDrone;
import dji.sdk.api.DJIError;
import dji.sdk.api.DJIDroneTypeDef.DJIDroneType;
import dji.sdk.api.GroundStation.DJIGroundStationTask;
import dji.sdk.api.GroundStation.DJIGroundStationTypeDef.DJIGroundStationFinishAction;
import dji.sdk.api.GroundStation.DJIGroundStationTypeDef.DJIGroundStationMovingMode;
import dji.sdk.api.GroundStation.DJIGroundStationTypeDef.GroundStationResult;
import dji.sdk.api.GroundStation.DJIGroundStationWaypoint;
import dji.sdk.api.MainController.DJIMainControllerSystemState;
import dji.sdk.api.MainController.DJIMainControllerTypeDef.DJIMcIocType;
import dji.sdk.interfaces.DJIGerneralListener;
import dji.sdk.interfaces.DJIGroundStationExecuteCallBack;
import dji.sdk.interfaces.DJIMcIocTypeCallBack;
import dji.sdk.interfaces.DJIMcuUpdateStateCallBack;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;



public class GSDemoActivity extends FragmentActivity implements OnClickListener, OnMapClickListener, OnMapReadyCallback{
	protected static final String TAG = "GSDemoActivity";
	
	private GoogleMap aMap;
	
	private Button locate, add, clear;
	private Button config, upload, start, stop;
	private ToggleButton tb;
	
	private DJIMcuUpdateStateCallBack mMcuUpdateStateCallBack = null;
	
	private boolean isAdd = false;
	
	private double droneLocationLat, droneLocationLng;
	private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
	private Marker droneMarker = null;
	private DJIGroundStationTask mGroundStationTask = null;
	
	private float altitude = 100.0f;
	private boolean repeatGSTask = false;
	private float speedGSTask;
	private DJIGroundStationFinishAction actionAfterFinishTask;
	private DJIGroundStationMovingMode heading;
	
	private int DroneCode;
	private final int SHOWDIALOG = 1;
	private final int SHOWTOAST = 2;
	
	private Timer mTimer;
	private TimerTask mTask;
	
	private Handler handler = new Handler(new Handler.Callback() {
        
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case SHOWDIALOG:
                    showMessage(getString(R.string.demo_activation_message_title),(String)msg.obj); 
                    break;
                case SHOWTOAST:
                    setResultToToast((String)msg.obj);
                    break;
                default:
                    break;
            }
            return false;
        }
    });
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gsdemo);
        
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        
        mapFragment.getMapAsync(this);
        
        locate = (Button) findViewById(R.id.locate);
        add = (Button) findViewById(R.id.add);
        clear = (Button) findViewById(R.id.clear);
        config = (Button) findViewById(R.id.config);
        upload = (Button) findViewById(R.id.upload);
        start = (Button) findViewById(R.id.start);
        stop = (Button) findViewById(R.id.stop);
        tb = (ToggleButton) findViewById(R.id.tb);
        
        locate.setOnClickListener(this);
        add.setOnClickListener(this);
        clear.setOnClickListener(this);
        config.setOnClickListener(this);
        upload.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);
        
        tb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					// Use the satellite map
					aMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
				}else{
					//Use the normal map
					aMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
				}
				
			}
		});
        
        DroneCode = 1; // Initiate Inspire 1's SDK in function onInitSDK
        mGroundStationTask = new DJIGroundStationTask(); // Initiate an object for GroundStationTask
        
        onInitSDK(DroneCode);  // Initiate the SDK for Insprie 1
        DJIDrone.connectToDrone(); // Connect to Drone
        
        new Thread(){
            public void run() {
                try {
                    DJIDrone.checkPermission(getApplicationContext(), new DJIGerneralListener() {
                        
                        @Override
                        public void onGetPermissionResult(int result) {
                            // TODO Auto-generated method stub
                            if (result == 0) {
                                handler.sendMessage(handler.obtainMessage(SHOWDIALOG, DJIError.getCheckPermissionErrorDescription(result)));

                                updateDroneLocation(); // Obtain the drone's lat and lng from MCU.
                            } else {
                                handler.sendMessage(handler.obtainMessage(SHOWDIALOG, getString(R.string.demo_activation_error)+DJIError.getCheckPermissionErrorDescription(result)+"\n"+getString(R.string.demo_activation_error_code)+result));
                        
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
          
        mTimer = new Timer();
        class Task extends TimerTask {
            //int times = 1;

            @Override
            public void run() 
            {
                //Log.d(TAG ,"==========>Task Run In!");
                updateDroneLocation(); 
            }

        };
        mTask = new Task();
        
    }
    

    
    private void setUpMap() {
        aMap.setOnMapClickListener(this);// add the listener for click for amap object 

    }
    
    // Function for initiating SDKs for the drone according to the drone type.
    private void onInitSDK(int type){
        switch(type){
            case 0 : {
                DJIDrone.initWithType(this.getApplicationContext(),DJIDroneType.DJIDrone_Vision);
                break;
            }
            case 1 : {
                DJIDrone.initWithType(this.getApplicationContext(),DJIDroneType.DJIDrone_Inspire1);
                break;
            }
            case 2 : {
                DJIDrone.initWithType(this.getApplicationContext(),DJIDroneType.DJIDrone_Phantom3_Advanced);
                break;
            }
            case 3 : {
                DJIDrone.initWithType(this.getApplicationContext(),DJIDroneType.DJIDrone_M100);
                break;
            }
            default : {
                break;
            }
        }
        
    }
      
    // Update the drone location based on states from MCU.
    private void updateDroneLocation(){
        // Set the McuUpdateSateCallBack
        mMcuUpdateStateCallBack = new DJIMcuUpdateStateCallBack(){

            @Override
            public void onResult(DJIMainControllerSystemState state) {
            	droneLocationLat = state.droneLocationLatitude;
            	droneLocationLng = state.droneLocationLongitude;
            	Log.e(TAG, "drone lat "+state.droneLocationLatitude);
            	Log.e(TAG, "drone lat "+state.homeLocationLatitude);
            	Log.e(TAG, "drone lat "+state.droneLocationLongitude);
            	Log.e(TAG, "drone lat "+state.homeLocationLongitude);
            }     
        };
        Log.e(TAG,"setMcuUpdateState");
        DJIDrone.getDjiMC().setMcuUpdateStateCallBack(mMcuUpdateStateCallBack);
        
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));
        
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }
             
                droneMarker = aMap.addMarker(markerOptions);
            }
          });
    }
   
    
    @Override
    public void onMapClick(LatLng point) {
    	if (isAdd == true){
    		markWaypoint(point);
    		DJIGroundStationWaypoint mDJIGroundStationWaypoint = new DJIGroundStationWaypoint(point.latitude, point.longitude);
    		mGroundStationTask.addWaypoint(mDJIGroundStationWaypoint);
    		//Add waypoints to Waypoint arraylist;
    	}else{
    		// Do not add waypoint;
    	}
    		
    }
    
    private void markWaypoint(LatLng point){
    	//Create MarkerOptions object
    	MarkerOptions markerOptions = new MarkerOptions();
    	markerOptions.position(point);
    	markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
    	Marker marker = aMap.addMarker(markerOptions);
    	mMarkers.put(mMarkers.size(),marker);
    }
    
    @Override
    public void onClick(View v) {
        // TODO Auto-generated method stub
        switch (v.getId()) {
            case R.id.locate:{
                updateDroneLocation();
                cameraUpdate(); // Locate the drone's place                         	
                break;
            }
            case R.id.add:{
                enableDisableAdd(); 
                break;
            }
            case R.id.clear:{
                runOnUiThread(new Runnable(){
                    @Override
                    public void run() {
                        aMap.clear();
                    }
                    
                });
                mGroundStationTask.RemoveAllWaypoint(); // Remove all the waypoints added to the task
                break;
            }
            case R.id.config:{
                showSettingDialog();
                break;
            }
            case R.id.upload:{
                uploadGroundStationTask();
                break;
            }
            case R.id.start:{
                startGroundStationTask();
                mTimer.schedule(mTask, 0, 1000);
                break;
            }
            case R.id.stop:{
                stopGroundStationTask();
                mTimer.cancel();
                break;
            }
            default:
                break;
        }
    }
    
    private void cameraUpdate(){
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        CameraUpdate cu = CameraUpdateFactory.newLatLng(pos);
        aMap.moveCamera(cu);
        
    }
    
    private void enableDisableAdd(){
        if (isAdd == false) {
            isAdd = true; // the switch for enabling or disabling adding waypoint function
            add.setText("Exit");
        }else{
            isAdd = false;
            add.setText("Add");
        }
    }
    
    private void showSettingDialog(){
        LinearLayout wayPointSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);

        final TextView wpAltitude_TV = (TextView) wayPointSettings.findViewById(R.id.altitude);
        final Switch repeatEnable_SW = (Switch) wayPointSettings.findViewById(R.id.repeat);
        RadioGroup speed_RG = (RadioGroup) wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = (RadioGroup) wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup heading_RG = (RadioGroup) wayPointSettings.findViewById(R.id.heading);
        
        repeatEnable_SW.setChecked(repeatGSTask);
        repeatEnable_SW.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener(){

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // TODO Auto-generated method stub
                if (isChecked){
                    repeatGSTask = true;
                    repeatEnable_SW.setChecked(true);
                } else {
                    repeatGSTask = false;
                    repeatEnable_SW.setChecked(false);
                }
            }
            
        });
        
        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // TODO Auto-generated method stub
                if (checkedId == R.id.lowSpeed){
                    speedGSTask = 1.0f;
                } else if (checkedId == R.id.MidSpeed){
                    speedGSTask = 3.0f;
                } else if (checkedId == R.id.HighSpeed){
                    speedGSTask = 5.0f;
                }
            }
            
        });
        
        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // TODO Auto-generated method stub
                if (checkedId == R.id.finishNone){
                    actionAfterFinishTask = DJIGroundStationFinishAction.None;
                } else if (checkedId == R.id.finishGoHome){
                    actionAfterFinishTask = DJIGroundStationFinishAction.Go_Home;
                } else if (checkedId == R.id.finishLanding){
                    actionAfterFinishTask = DJIGroundStationFinishAction.Land;
                } else if (checkedId == R.id.finishToFirst){
                    actionAfterFinishTask = DJIGroundStationFinishAction.Back_To_First_Way_Point;
                }
            }
        });
        
        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // TODO Auto-generated method stub
                if (checkedId == R.id.headingNext){
                    heading = DJIGroundStationMovingMode.GSHeadingTowardNextWaypoint;
                } else if (checkedId == R.id.headingInitDirec){
                    heading = DJIGroundStationMovingMode.GSHeadingUsingInitialDirection;
                } else if (checkedId == R.id.headingRC){
                    heading = DJIGroundStationMovingMode.GSHeadingControlByRemoteController;
                } else if (checkedId == R.id.headingWP){
                    heading = DJIGroundStationMovingMode.GSHeadingUsingWaypointHeading;
                }
            }
        });
        
        
        new AlertDialog.Builder(this)
        .setTitle("")
        .setView(wayPointSettings)
        .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface dialog, int id) {
                altitude = Integer.parseInt(wpAltitude_TV.getText().toString());
                Log.e(TAG,"altitude "+altitude);
                Log.e(TAG,"repeat "+repeatGSTask);
                Log.e(TAG,"speed "+speedGSTask);
                Log.e(TAG, "actionAfterFinishTask "+actionAfterFinishTask);
                Log.e(TAG, "heading "+heading);
                configGroundStationTask();
            }
            
        })
        .setNegativeButton("Cancel", new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
            
        })
        .create()
        .show();
    }
    
    private void configGroundStationTask(){
        mGroundStationTask.isLoop = repeatGSTask;
        mGroundStationTask.finishAction=actionAfterFinishTask;
        mGroundStationTask.movingMode = heading;
        for (int i=0; i<mGroundStationTask.wayPointCount; i++){
            mGroundStationTask.getWaypointAtIndex(i).speed = speedGSTask;
            mGroundStationTask.getWaypointAtIndex(i).altitude = altitude;
        }
    }
    
    private void uploadGroundStationTask(){
        DJIDrone.getDjiGroundStation().openGroundStation(new DJIGroundStationExecuteCallBack(){

            @Override
            public void onResult(GroundStationResult result) {
                // TODO Auto-generated method stub
                String ResultsString = "return code =" + result.toString();
                handler.sendMessage(handler.obtainMessage(SHOWTOAST, ResultsString));
                
                if (result == GroundStationResult.GS_Result_Success) {
                    DJIDrone.getDjiGroundStation().uploadGroundStationTask(mGroundStationTask, new DJIGroundStationExecuteCallBack(){
    
                        @Override
                        public void onResult(GroundStationResult result) {
                            // TODO Auto-generated method stub
                            String ResultsString = "return code =" + result.toString();
                            handler.sendMessage(handler.obtainMessage(SHOWTOAST, ResultsString));
                        }
                        
                    });
                }
            }
            
        });
    }
    
    private void startGroundStationTask(){
//        DJIDrone.getDjiMainController().getAircraftIocType(new DJIMcIocTypeCallBack(){
//
//            @Override
//            public void onResult(DJIMcIocType result) {
//                // TODO Auto-generated method stub
//                String ResultsString = "return code =" + result.toString();
//                handler.sendMessage(handler.obtainMessage(SHOWTOAST, ResultsString));
//            }
//            
//        });
        DJIDrone.getDjiGroundStation().startGroundStationTask(new DJIGroundStationExecuteCallBack(){

            @Override
            public void onResult(GroundStationResult result) {
                // TODO Auto-generated method stub
                String ResultsString = "return code =" + result.toString();
                handler.sendMessage(handler.obtainMessage(SHOWTOAST, ResultsString));
            }
        });
    }
    
    private void stopGroundStationTask(){
        DJIDrone.getDjiGroundStation().pauseGroundStationTask(new DJIGroundStationExecuteCallBack(){

            @Override
            public void onResult(GroundStationResult result) {
                // TODO Auto-generated method stub
                String ResultsString = "return code =" + result.toString();
                handler.sendMessage(handler.obtainMessage(SHOWTOAST, ResultsString));
                
                DJIDrone.getDjiGroundStation().closeGroundStation(new DJIGroundStationExecuteCallBack(){

                    @Override
                    public void onResult(GroundStationResult result) {
                        // TODO Auto-generated method stub
                        String ResultsString = "return code =" + result.toString();
                        handler.sendMessage(handler.obtainMessage(SHOWTOAST, ResultsString));
                    }

                });
            }
        });
        mGroundStationTask.RemoveAllWaypoint();
    }

    
    public void showMessage(String title, String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }
    
    private void setResultToToast(String result){
        Toast.makeText(GSDemoActivity.this, result, Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onResume(){
        super.onResume();
        ServiceManager.getInstance().pauseService(false);
        Log.e(TAG, "startUpdateTimer");
        DJIDrone.getDjiMC().startUpdateTimer(1000); // Start the update timer for MC to update info
    }
    
    @Override
    protected void onPause(){
        super.onPause();        
        ServiceManager.getInstance().pauseService(true);
        Log.e(TAG, "stopUpdateTimer");
        DJIDrone.getDjiMC().stopUpdateTimer(); // Stop the update timer for MC to update info
    }
    
    @Override
    protected void onSaveInstanceState (Bundle outState) {
        super.onSaveInstanceState(outState);
    }
    
    @Override
    protected void onDestroy(){
        super.onDestroy();
        Process.killProcess(Process.myPid());
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        // TODO Auto-generated method stub
        // Initializing Amap object
        if (aMap == null) {
            aMap = googleMap;
            setUpMap();
        }
        
        LatLng Shenzhen = new LatLng(22.5500, 114.1000);
        googleMap.addMarker(new MarkerOptions().position(Shenzhen).title("Marker in Shenzhen"));
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(Shenzhen));
    }

}
