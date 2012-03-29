package com.bafflesoft.locationcache.viewer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bafflesoft.locationcache.viewer.ShellCommand.CommandResult;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Projection;

public class MainActivity extends MapActivity {
	private static final String PREF_SHOWED_SU_WARNING  = "SHOWED_SU_WARNING";
	private static final String PREF_SHOWED_ICS_WARNING = "SHOWED_ICS_WARNING";

	private static final int THRESHOLD_HEATMAP = 150;	
	
	public class NoRootAccessException extends Exception {	
		private static final long serialVersionUID = 1L;
		public NoRootAccessException(String message) {
			super(message);			
		}
	}

	public class RunCommandException extends Exception {	
		private static final long serialVersionUID = 1L;
		public RunCommandException(String message) {
			super(message);			
		}
	}

	private static final int MENU_ITEM_CELL    = 2;
	private static final int MENU_ITEM_WIFI    = 1;
	private static final int MENU_ITEM_ZOOM    = 3;
	private static final int MENU_ITEM_HEATMAP = 4;
	private static final int MENU_ITEM_ABOUT   = 6;
	private static final int MENU_ITEM_REPLAY  = 5;
	private static final int MENU_ITEM_EXPORT  = 7;
	private static final int MENU_ITEM_RELOAD  = 8;

	private static final String FOLDER_CACHE        = "/data/data/com.google.android.location/files/";
	private static final String LOCATION_CACHE_CELL = FOLDER_CACHE + "cache.cell";
	private static final String LOCATION_CACHE_WIFI = FOLDER_CACHE + "cache.wifi";
	
	// pulled from http://developer.android.com/reference/android/os/Build.VERSION_CODES.html#GINGERBREAD
	private static final int BUILD_VERSION_CODE_GINGERBREAD_MR1 = 10;
	
	private Markers markersWifi = null;
	private Markers markersCell = null;

	private SortedSet<LocationInformation> pointsAll = null;
	
	private long lastLoad = 0;
	
	private boolean firstLoad = true;
	private boolean noRoot    = true;
	
	private ProgressDialog dialog = null;
	private LoadDataTask   task   = null;
	
	public class LoadDataTask extends AsyncTask<Void, String, Boolean> {
		private List<LocationInformation> pointsCell = null;
		private List<LocationInformation> pointsWifi = null;
		
		private Exception error = null;
		
		@Override
		protected Boolean doInBackground(Void... params) {
			try {
				publishProgress("Loading Cell Tower Locations");
				pointsCell = loadPoints(LOCATION_CACHE_CELL, "Cellphone Tower");
				publishProgress("Loading Wireless Access Point Locations");
				pointsWifi = loadPoints(LOCATION_CACHE_WIFI, "Wireless Access Point");
				publishProgress("Drawing");
				
				drawLocations(pointsCell, pointsWifi);
				
				return Boolean.TRUE;
				
			} catch ( Exception ex ) {
				this.error = ex;
				return Boolean.FALSE;
			}
		}
		
		@Override
		protected void onCancelled() {
			super.onCancelled();
			
			try {
				if ( dialog != null ) {
					dialog.dismiss();
					dialog = null;
				}
			} catch ( Exception ex ) { } // ignore the "View not attached to a window manager" exception

			task = null;
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);

			try {
				if ( dialog != null ) {
					dialog.dismiss();
					dialog = null;
				}
			} catch ( Exception ex ) { } // ignore the "View not attached to a window manager" exception

			task = null;
			
			if ( !result || error != null )
			{
				if ( error instanceof NoRootAccessException ) {
					MainActivity.this.showError("Root Access Required", error);
				} else if ( error instanceof RunCommandException ) {
					MainActivity.this.showError("Error Reading Location Cache", error);
				} else {
					showError("Error Reading Location Cache", error);
				}
			}
			else
			{
				if ( pointsCell == null || pointsWifi == null ) {
					StringBuilder message = new StringBuilder();
					message.append("Location data cache found.\n\n");
					message.append("Unable to load location data for:\n\n");
					if ( pointsCell == null ) {
						message.append("     Cell Locations\n\n");
					}
					if ( pointsWifi == null ) {
						message.append("     Wifi Locations\n\n");
					}
					message.append("This data is only stored if you have enabled the 'Wireless Networks' options under Settings -> Locations\n\nOtherwise, Android does not cache this data.");
					
					if ( isRunningUnsupportedVersion() ) {
						message.append("\n\nYou are running an unsupported version of Android (newer than Gingerbread MR1 - 2.3.3). It is possible your phone does not store a location cache.");
					}
					
					Builder builder = new AlertDialog.Builder(MainActivity.this).setTitle("Location Data Does Not Exist")
					.setMessage(message)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setOnCancelListener(new DialogInterface.OnCancelListener() {
						public void onCancel(DialogInterface dialog) { finish(); }
					});
					
					if ( pointsCell == null && pointsWifi == null ) {
						builder.setPositiveButton("Close Application", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) { finish(); }
						});
					} else {
						builder.setPositiveButton("Show What's Available", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) { }
						});
					}
					
					builder.show();							
				}
				
				lastLoad = System.currentTimeMillis();
				MainActivity.this.pointsAll = new TreeSet<LocationInformation>();
				if ( pointsCell != null && pointsCell.size() > 0 ) {
					MainActivity.this.pointsAll.addAll(pointsCell);
				}
				if ( pointsWifi != null && pointsWifi.size() > 0 ) {
					MainActivity.this.pointsAll.addAll(pointsWifi);
				}
				
				updateLabels();
				
				zoomToVisibleMarkers();				
			}
		}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			dialog = ProgressDialog.show(MainActivity.this, "Loading Cached Location Data", "");
			dialog.setIcon(0);
		}
		
		@Override
		protected void onProgressUpdate(String... values) {
			super.onProgressUpdate(values);
			
			dialog.setMessage(values[0]);
		}
				
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		if ( Util.isDebugBuild(this) ) {
//			MapView mapView = (MapView)findViewById(R.id.mapview);
//			ViewGroup group = (ViewGroup) mapView.getParent();
//			group.removeView(mapView);
//			MapView newMap = new MapView(this, "0xvPRibgTaSB3fZfRRPq0g7tVgTceOdToKSgaVg");
//			group.addView(newMap);
		} else {
		
		}
		
		
		showICSWarning();
	}
	
	private void showSUWarning() {
		SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);

		boolean showedSUWarning = prefs.getBoolean(PREF_SHOWED_SU_WARNING, false);
		
		if ( !showedSUWarning ) {		
			new AlertDialog.Builder(this).setTitle("Root Access Required")
			.setMessage("This application requests root in order to read files which are normally not visible to an application.\n\n" + 
			"The files are only READ, and nothing else, but if you are not comofrtable with giving this application root access, then please click \"Quit Now\" or deny root access.\n\n" +
			"If you are curious about what this program will be doing, full source code is available on Github.")
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					finish();
				}
			})
			.setNegativeButton("Quit Now", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					finish();
				}
			})
			.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) { 
					setPreferenceFlag(PREF_SHOWED_SU_WARNING, true);
					finishStartup();
				}
			})
			.show();
		} else {
			finishStartup();
		}
	}

	private void showICSWarning() {
		SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);

		boolean showedICSWarning  = prefs.getBoolean(PREF_SHOWED_ICS_WARNING, false);
		
		if ( isRunningUnsupportedVersion() && !showedICSWarning ) { 
			new AlertDialog.Builder(this).setTitle("Unsupported Android Version")
			.setMessage("You are running a version of Android later than the last known working version (Gingerbread MR1 - 2.3.3).\n\n" + 
			"It looks like the location cache files are no longer stored in your version of Android.\n\n" + 
			"There is no harm or danger in running this program, but it will likely not find any location cache files to view.\n\n" +
			"I am sorry if this program cannot be of use to you, but be happy you (maybe) have no cache files tracking you!")
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					finish();
				}
			})
			.setNegativeButton("Quit Now", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					finish();
				}
			})
			.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) { 
					setPreferenceFlag(PREF_SHOWED_ICS_WARNING, true);
					showSUWarning();
				}
			})
			.show();
			
		} else {
			showSUWarning();
		}
	}
	
	private boolean isRunningUnsupportedVersion() {
		return Build.VERSION.SDK_INT > BUILD_VERSION_CODE_GINGERBREAD_MR1;
	}

	private void finishStartup()
	{
		setPreferenceFlag(PREF_SHOWED_SU_WARNING, true);
		
		try {
			ShellCommand cmd = new ShellCommand();
			if ( !cmd.canSU(true) ) {
				noRoot = true;
				throw new NoRootAccessException(String.valueOf(cmd.result.stderr));
			} else {
				noRoot = false;
			}
		} catch ( NoRootAccessException ex ) {
			showError("Root Access Required", ex);
		}
		
		initOverlays();
	}
	
	private void setPreferenceFlag(String pref, boolean value)
	{
		SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
		Editor prefEdit = prefs.edit();
		prefEdit.putBoolean(pref, true);
		prefEdit.commit();
	}
	
	private void showError(String title, Throwable ex)
	{	
		StringBuilder message = null;
		if ( ex instanceof NoRootAccessException ) {
			message = new StringBuilder("Unable to get root access.\n\nThis application needs root to function.\n\nMore information: ");
		} else if ( ex instanceof RunCommandException ) {
			message = new StringBuilder("Unable to access cache files.\n\nMake sure you enabled root access.\n\nMore information: ");
		} else {
			message = new StringBuilder("Unexpected error.\n\nMore information: ");
		}
		
		if ( ex != null && ex.getMessage() != null && ex.getMessage().length() > 0 ) {
			message.append(ex.getMessage());
		} else {
			message.append("No error message available.");
		}
		
		new AlertDialog.Builder(this).setTitle(title)
			.setMessage(message)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) { finish(); }
			})
			.setPositiveButton("Close Application", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) { finish(); }
			})
			.show();			
	}	
	
	@Override
	protected void onResume() {
		super.onResume();
				
		if ( noRoot ) return;
		
		loadOrReloadData(false);
	}
	
	private void loadOrReloadData(boolean force)
	{
		// only load if it's been at least 5 minutes since the last load
		if ( task == null ) {
			if ( force || (System.currentTimeMillis() - lastLoad) > (1000 * 60 * 5) ) {
				task = new LoadDataTask();
				task.execute((Void[])null);
			}
		}			
	}
	
	@Override
	protected void onPause() {
//		if ( task != null ) {
//			task.cancel(true);
//			task = null;
//		}
		super.onPause();
	}
	
	private void initOverlays()
	{
		MapView mapView = (MapView) findViewById(R.id.mapview);
		mapView.setBuiltInZoomControls(true);
		
		Drawable drawWifi = getResources().getDrawable(R.drawable.icon_wifi);
		Drawable drawCell = getResources().getDrawable(R.drawable.icon_celltower);

		markersWifi = new Markers(drawWifi, this); 
		markersCell = new Markers(drawCell, this);
		
		markersWifi.setDrawCircles(true);
		markersCell.setDrawCircles(true);
		
		markersWifi.setFillColor(0xff9E7151);
		markersCell.setFillColor(0xff5680FC);
	}
	
	private void updateLabels()
	{
		int cellCount = 0;
		int wifiCount = 0;
		CharSequence wifiDateSpan = "No Wifi Data Loaded";
		CharSequence cellDateSpan = "No Cell Data Loaded";

		if ( markersCell != null && markersCell.size() > 0 ) {
			cellCount = markersCell.size();
			
			long minDateCell = Long.MAX_VALUE;
			long maxDateCell = Long.MIN_VALUE;
			for ( int i = 0; i < markersCell.size(); i++ ) {
				long timestamp = markersCell.getItem(i).location.timestamp;
				minDateCell = Math.min(minDateCell, timestamp);
				maxDateCell = Math.max(maxDateCell, timestamp);
			}

			cellDateSpan = formatDateSpan(minDateCell, maxDateCell);
		}
		
		if ( markersWifi != null && markersCell.size() > 0 ) {
			wifiCount = markersWifi.size();			
		
			long minDateWifi = Long.MAX_VALUE;
			long maxDateWifi = Long.MIN_VALUE;
			for ( int i = 0; i < markersWifi.size(); i++ ) {
				long timestamp = markersWifi.getItem(i).location.timestamp;
				minDateWifi = Math.min(minDateWifi, timestamp);
				maxDateWifi = Math.max(maxDateWifi, timestamp);
			}

			wifiDateSpan = formatDateSpan(minDateWifi, maxDateWifi);
		}
		
		((TextView)findViewById(R.id.wifiTowerCount)).setText(String.valueOf(wifiCount));
		((TextView)findViewById(R.id.cellTowerCount)).setText(String.valueOf(cellCount));
		((TextView)findViewById(R.id.wifiTowerDates)).setText(wifiDateSpan);
		((TextView)findViewById(R.id.cellTowerDates)).setText(cellDateSpan);
	
		findViewById(R.id.dataSummary).setVisibility(View.VISIBLE);
	}
	
	private CharSequence formatDate(long ts)
	{
		Calendar cal = Calendar.getInstance(Locale.getDefault());
		cal.setTimeInMillis(ts);
		
		SimpleDateFormat fmt = new SimpleDateFormat("yyy.MM.dd h:mm a");
		
		return fmt.format(cal.getTime());
	}
	
	private CharSequence formatDateSpan(long minTS, long maxTS)
	{
		StringBuilder b = new StringBuilder();
		b.append(formatDate(minTS)).append(" - ").append(formatDate(maxTS));
		
		return b;
	}
	
	private void drawLocations(List<LocationInformation> pointsCell, List<LocationInformation> pointsWifi) throws NoRootAccessException, RunCommandException {
		markersWifi.clear();
		markersCell.clear();
		
		if ( pointsCell != null ) {
			drawPoints(pointsCell, markersCell);
		} 
		if ( pointsWifi != null ) {
			drawPoints(pointsWifi, markersWifi);
		}
	}
	
	private void zoomToVisibleMarkers()
	{
		MapView mapView = (MapView) findViewById(R.id.mapview);
		
		int minLat = (int) (  90*1E6);
		int minLon = (int) ( 180*1E6);
		int maxLat = (int) ( -90*1E6);
		int maxLon = (int) (-180*1E6);
		for ( int i = 0; i < markersWifi.size(); i++ ) {
			minLat = Math.min(markersWifi.getItem(i).getPoint().getLatitudeE6 (), minLat);
			minLon = Math.min(markersWifi.getItem(i).getPoint().getLongitudeE6(), minLon);
			maxLat = Math.max(markersWifi.getItem(i).getPoint().getLatitudeE6 (), maxLat);
			maxLon = Math.max(markersWifi.getItem(i).getPoint().getLongitudeE6(), maxLon);
		}
		for ( int i = 0; i < markersCell.size(); i++ ) {
			minLat = Math.min(markersCell.getItem(i).getPoint().getLatitudeE6 (), minLat);
			minLon = Math.min(markersCell.getItem(i).getPoint().getLongitudeE6(), minLon);
			maxLat = Math.max(markersCell.getItem(i).getPoint().getLatitudeE6 (), maxLat);
			maxLon = Math.max(markersCell.getItem(i).getPoint().getLongitudeE6(), maxLon);
		}		
		
		int centerLat = minLat + ((maxLat-minLat)/2);
		int centerLon = minLon + ((maxLon-minLon)/2);

		mapView.getController().animateTo(new GeoPoint(centerLat, centerLon));
	
		int latSpan = (int) ((maxLat-minLat)+3*1E6);
		int lonSpan = (int) ((maxLon-minLon)+3*1E6);

		mapView.getController().zoomToSpan(latSpan, lonSpan);

		if ( firstLoad ) {
			mapView.getOverlays().add(markersCell);
			mapView.getOverlays().add(markersWifi);
			firstLoad = false;
		}

		mapView.invalidate();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuItem itemWifi = menu.add(Menu.NONE, MENU_ITEM_WIFI, Menu.NONE, "Hide Wifi Locations");
		itemWifi.setCheckable(true);
		itemWifi.setChecked(true);
		itemWifi.setIcon(R.drawable.icon_wifi);
		
		MenuItem itemCell = menu.add(Menu.NONE, MENU_ITEM_CELL, Menu.NONE, "Hide Cell Locations");
		itemCell.setCheckable(true);
		itemCell.setChecked(true);
		itemCell.setIcon(R.drawable.icon_celltower);
				
		MenuItem itemHeatmap = menu.add(Menu.NONE, MENU_ITEM_HEATMAP, Menu.NONE, "Disable Heatmap");
		itemHeatmap.setCheckable(true);
		itemHeatmap.setChecked(true);
		
		MenuItem itemAbout = menu.add(Menu.NONE, MENU_ITEM_ABOUT, Menu.NONE, "About");
		itemAbout.setIcon(android.R.drawable.ic_menu_info_details);
		
		MenuItem itemReplay = menu.add(Menu.NONE, MENU_ITEM_REPLAY, Menu.NONE, "Play Tracks");
		itemReplay.setIcon(android.R.drawable.ic_media_play);

		MenuItem itemExport = menu.add(Menu.NONE, MENU_ITEM_EXPORT, Menu.NONE, "Export to GPX");
		itemExport.setIcon(android.R.drawable.ic_menu_save);
		
		MenuItem itemZoom = menu.add(Menu.NONE, MENU_ITEM_ZOOM, Menu.NONE, "Zoom to All");
		itemZoom.setIcon(android.R.drawable.ic_menu_zoom);

		MenuItem itemReload = menu.add(Menu.NONE, MENU_ITEM_RELOAD, Menu.NONE, "Reload Points");

		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {		
		MenuItem itemReplay = menu.findItem(MENU_ITEM_REPLAY);
		if ( replayIterator != null && replayIterator.hasNext() ) {
			itemReplay.setTitle("Stop Playing");
			itemReplay.setIcon(android.R.drawable.ic_media_pause);
		} else {
			itemReplay.setTitle("Play Tracks");
			itemReplay.setIcon(android.R.drawable.ic_media_play);
		}

		return super.onPrepareOptionsMenu(menu);
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		MapView mapView = (MapView) findViewById(R.id.mapview);

		if ( item.getItemId() == MENU_ITEM_WIFI ) {
			if ( item.isChecked() ) {
				item.setTitle("Show Wifi Locations");
				mapView.getOverlays().remove(markersWifi);				
			} else {
				item.setTitle("Hide Wifi Locations");
				mapView.getOverlays().add(markersWifi);
			}
			mapView.invalidate();
			item.setChecked(!item.isChecked());
		} else if ( item.getItemId() == MENU_ITEM_CELL ) {
			if ( item.isChecked() ) {
				item.setTitle("Show Cell Locations");
				mapView.getOverlays().remove(markersCell);
			} else {
				item.setTitle("Hide Cell Locations");
				mapView.getOverlays().add(markersCell);
			}
			mapView.invalidate();
			item.setChecked(!item.isChecked());
		} else if ( item.getItemId() == MENU_ITEM_ZOOM ) {
			zoomToVisibleMarkers();
		} else if ( item.getItemId() == MENU_ITEM_HEATMAP ) {
			if ( item.isChecked() ) {
				item.setTitle("Enable Heatmap");
				markersCell.setDrawCircles(false);
				markersWifi.setDrawCircles(false);
			} else {
				item.setTitle("Disable Heatmap");
				markersCell.setDrawCircles(true);
				markersWifi.setDrawCircles(true);
			}
			
			int widthPx  = mapView.getWidth();
			double distanceAcrossMeters = calculateDistanceAcrossMeters(mapView);
			double metersPerPixel = distanceAcrossMeters / widthPx;
			boolean zoomedTooFarOut = metersPerPixel > THRESHOLD_HEATMAP;
			
			if ( zoomedTooFarOut ) {
				Toast.makeText(this, "Heatmap is only visible when zoomed in closer.", Toast.LENGTH_SHORT).show();
			}
			
			mapView.invalidate();
			item.setChecked(!item.isChecked());
		} else if ( item.getItemId() == MENU_ITEM_REPLAY ) {
			if ( replayIterator != null && replayIterator.hasNext() ) {
				replayIterator = null;
			} else {
				startReplayOfTracks();			
			}
		} else if ( item.getItemId() == MENU_ITEM_ABOUT ) {
			AboutDialog dialog = new AboutDialog(this);
			dialog.show();
		} else if ( item.getItemId() == MENU_ITEM_EXPORT ) {
			exportData();
		} else if ( item.getItemId() == MENU_ITEM_RELOAD ) {
			loadOrReloadData(true);
		}
		return super.onMenuItemSelected(featureId, item);
	}

	private int trackPosition = -1;
	
    private final Handler mMsgHandler = new Handler() {
    	public void handleMessage(android.os.Message msg) {
    		if ( trackPosition < 0 || pointsAll == null || replayIterator == null || !replayIterator.hasNext() ) {
    			trackPosition = -1;
    			replayIterator = null;
    			findViewById(R.id.replayProgress).setVisibility(View.GONE);
    			return;
    		}
    		
    		final long timeNow = System.currentTimeMillis();
    		
			LocationInformation item = replayIterator.next();
			GeoPoint point = item.getGeoPoint();
			if ( point != null ) { // null items are in africa and should be ignored
				MapView mapView = (MapView) findViewById(R.id.mapview);
				mapView.getController().animateTo(point, new Runnable() {
					@Override
					public void run() {
						long timeAfter = System.currentTimeMillis();
						long timeLeft  = 1-((timeAfter-timeNow)/1000);
						mMsgHandler.sendMessageDelayed(new Message(), Math.min(100, timeLeft));
					}
				});
				mapView.getController().setZoom(14);
				
				((TextView)findViewById(R.id.descReplay1)).setText(formatDate(item.timestamp));				
				((TextView)findViewById(R.id.descReplay2)).setText(item.type + " - " + item.key + " - " + item.accuracy + " meters");				
			} else {
				mMsgHandler.sendMessageDelayed(new Message(), 1000);				
			}

			trackPosition++;			
			
			findViewById(R.id.replayProgress).setVisibility(View.VISIBLE);
			((ProgressBar)findViewById(R.id.progressReplay)).setMax(pointsAll.size());
			((ProgressBar)findViewById(R.id.progressReplay)).setProgress(trackPosition);
    	};
    };
    
	private Iterator<LocationInformation> replayIterator;	private void startReplayOfTracks()
	{
		trackPosition = 0;
		replayIterator = pointsAll.iterator();		
		mMsgHandler.sendMessageDelayed(new Message(), 0);
	}

	private List<LocationInformation> loadPoints(String fileName, String type) throws NoRootAccessException, RunCommandException {
		ShellCommand cmd = new ShellCommand();
		CommandResult r = cmd.su.runWaitFor("cat " + fileName);

		if (!r.success()) {
			if ( r.stderr.contains("No such file") || r.exit_value == 1 ) {
				return null;
			} else {
				throw new RunCommandException(r.stderr);
			}
		} else {
			Log.v("LocationCacheViewer", "Success!");
			
			return LocationCacheParser.parseLocationCacheFile(r.stdout, type);
		}
	}	
	
	private void drawPoints(List<LocationInformation> locations, Markers markerSet)
	{
		for ( LocationInformation location : locations )
		{
			GeoPoint p = location.getGeoPoint();
			if ( p == null ) continue;			
			
			LocationInformationOverlayItem item = new LocationInformationOverlayItem(location);
			markerSet.addOverlay(item);
		}		
	}


	public class Markers extends ItemizedOverlay<LocationInformationOverlayItem> {
		private Context ctx;

		private Drawable drawablePoint;
		private int      fillColor;
		private  boolean  drawCircles;
		
		private ArrayList<LocationInformationOverlayItem> mOverlays = new ArrayList<LocationInformationOverlayItem>();

		public Markers(Drawable defaultMarker, Context cont) {
			super(boundCenterBottom(defaultMarker));
			this.ctx = cont;
		}

		@Override
		protected LocationInformationOverlayItem createItem(int i) {
			return mOverlays.get(i);
		}

		@Override
		public boolean onTap(GeoPoint p, MapView mapView) {
			return super.onTap(p, mapView);
		}


		@Override
		protected boolean onTap(int index) {
			Toast.makeText(this.ctx, mOverlays.get(index).getTitle().toString() + " - " + mOverlays.get(index).getSnippet(), Toast.LENGTH_SHORT).show();			
			return super.onTap(index);         
		}

		@Override
		public int size() {
			return mOverlays.size();
		}

		public void addOverlay(LocationInformationOverlayItem item) {
			mOverlays.add(item);
			setLastFocusedIndex(-1);
			populate();
		}
		
		public void clear() {
			mOverlays.clear();
			setLastFocusedIndex(-1);
			populate();
		}
		
		@Override
		public void draw(Canvas canvas, MapView mapView, boolean shadow) {
			int widthPx  = mapView.getWidth();
			double distanceAcrossMeters = calculateDistanceAcrossMeters(mapView);
			double metersPerPixel = distanceAcrossMeters / widthPx;

			boolean zoomedTooFarOut = metersPerPixel > THRESHOLD_HEATMAP;
			
			if ( drawCircles && !zoomedTooFarOut ) {		
				
	            Projection projection = mapView.getProjection();
				Point ptPx = new Point();
				LocationInformationOverlayItem item = null;
	
				Paint translucentBlob = new Paint();
	            translucentBlob.setAntiAlias(true);
	            translucentBlob.setStrokeWidth(1.0f);
	            translucentBlob.setColor(getFillColor());
	            translucentBlob.setStyle(Style.FILL_AND_STROKE);			
	            translucentBlob.setAlpha(20);

				Paint spotPoint = new Paint();
				spotPoint.setAntiAlias(true);
				spotPoint.setStrokeWidth(1.0f);
				spotPoint.setColor(getFillColor());
				spotPoint.setStyle(Style.FILL_AND_STROKE);			
				spotPoint.setAlpha(20);

				for ( int i = 0; i < mOverlays.size(); i++ ) {
					item = mOverlays.get(i);
	
					projection.toPixels(item.getPoint(), ptPx);
					
					float width = (float)(item.location.accuracy/metersPerPixel);
					if ( width > 5 ) {
						canvas.drawCircle((float)ptPx.x, (float)ptPx.y, (float)(item.location.accuracy/metersPerPixel), translucentBlob);
					} else {
						canvas.drawCircle((float)ptPx.x, (float)ptPx.y, 5, spotPoint);
					}
				}
			} else {
				super.draw(canvas, mapView, shadow);
			}
		}	

		public void setDrawablePoint(Drawable drawablePoint) {
			this.drawablePoint = drawablePoint;
		}

		public Drawable getDrawablePoint() {
			return drawablePoint;
		}

		public void setFillColor(int fillColor) {
			this.fillColor = fillColor;
		}

		public int getFillColor() {
			return fillColor;
		}

		public void setDrawCircles(boolean drawCircles) {
			this.drawCircles = drawCircles;
		}

		public boolean isDrawCircles() {
			return drawCircles;
		}
	}

	private double calculateDistanceAcrossMeters(MapView mapView)
	{
		double lat1 = mapView.getMapCenter().getLatitudeE6();
		double lat2 = lat1;
		
		int R = 6371; // km
		double dLat = Math.toRadians(mapView.getLatitudeSpan ()/1E6);
		double dLon = Math.toRadians(mapView.getLongitudeSpan()/1E6); 
		double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
		        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * 
		        Math.sin(dLon/2) * Math.sin(dLon/2); 
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
		double d = R * c;	
		
		return d * 1000; // meters
	}
	
	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}
	
	private void exportData()
	{
		try {
			if ( dialog != null ) {
				new AlertDialog.Builder(this).setTitle("Loading Data")
				.setMessage("Loading data... Please wait before exporting.")
				.setIcon(android.R.drawable.ic_dialog_info)
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {}
				})
				.show();
				return;
			}
			
			String externalState = Environment.getExternalStorageState();
			if ( !Environment.MEDIA_MOUNTED.equals(externalState) ) {
				new AlertDialog.Builder(this).setTitle("Missing External Storage?")
				.setMessage("It looks like your SD card (or other storage) isn't available.\n\nCannot export data.")
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {}
				})
				.show();
				
				return;
			}
			
			SimpleDateFormat fmt = new SimpleDateFormat("yyy.MM.dd_hhmmss");
			Calendar cal = Calendar.getInstance(Locale.getDefault());
			String filename = fmt.format(cal.getTime()) + "__Location_Cache.gpx";
			
			File external = Environment.getExternalStorageDirectory();
			File dir      = new File(external, "Android/data/" + getPackageName() + "/files/");
			File file     = new File(dir, filename);
			
			Log.d("LocationCacheViewer", "Can write: " + file.getPath() + " - " + file.canWrite());
			
			if ( !dir.exists() && !dir.mkdirs() ) {
				new AlertDialog.Builder(this).setTitle("Unable to Export")
				.setMessage("Unable to export file to: " + dir.getPath() + "\n\nCould not create that directory.")
				.setIcon(android.R.drawable.ic_dialog_info)
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {}
				})
				.show();
				return;
			}
			
			if ( !file.exists() && !file.createNewFile() ) {
				new AlertDialog.Builder(this).setTitle("Unable to Export")
				.setMessage("Unable to export file to: " + file.getPath() + "\n\nCould not create that file.")
				.setIcon(android.R.drawable.ic_dialog_info)
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {}
				})
				.show();
				return;
			}
			
			BufferedWriter out = new BufferedWriter(new FileWriter(file));

			out.write("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"1.1\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\" creator=\"Android Location Cache Viewer\">\n");
			out.write("<metadata>\n");
			out.write("<name>Android Location Cache</name>\n");
			out.write("<desc>Exported on " + fmt.format(cal.getTime()) + " Total Points: " + pointsAll.size() + "</desc></metadata>\n");
			//out.write("<trk>");
			//out.write("<trkseg>\n");
			final SimpleDateFormat iso8601Format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");			
			final String format = "<wpt lat=\"%f\" lon=\"%f\"><time>%sZ</time><name>%s</name><desc>type: %s, when: %s, accuracy: %d, confidence: %d</desc></wpt>\n";
			for ( LocationInformation loc : pointsAll ) {
				if ( loc.accuracy >= 0 && loc.confidence >= 0 ) {
					out.write(String.format(format, loc.latIE6/1E6, loc.lonIE6/1E6, iso8601Format.format(new Date(loc.timestamp)), loc.key, loc.type, loc.getTimeString(), loc.accuracy, loc.confidence));
				}
			}

			//out.write("</trkseg>\n");
			//out.write("</trk>");
			out.write("</gpx>");
			
			out.flush();
			out.close();
			
			new AlertDialog.Builder(this).setTitle("File Exported")
			.setMessage("GPX file saved to:\n\n" + file.getPath())
			.setIcon(android.R.drawable.ic_dialog_info)
			.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {}
			})
			.show();			
		} catch ( Exception ex ) {
			new AlertDialog.Builder(this).setTitle("Error Exporting Data")
			.setMessage("Unable to export the data.\n\nReason: " + ex.getMessage())
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {}
			})
			.show();
			return;
		}
	}
}