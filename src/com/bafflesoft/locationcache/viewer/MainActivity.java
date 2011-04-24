package com.bafflesoft.locationcache.viewer;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.bafflesoft.locationcache.viewer.ShellCommand.CommandResult;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Projection;

public class MainActivity extends MapActivity {
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

	private static final String FOLDER_CACHE        = "/data/data/com.google.android.location/files/";
	private static final String LOCATION_CACHE_CELL = FOLDER_CACHE + "cache.cell";
	private static final String LOCATION_CACHE_WIFI = FOLDER_CACHE + "cache.wifi";
	
	private Markers markersWifi = null;
	private Markers markersCell = null;

	private long lastLoad = 0;
	
	private boolean firstLoad = true;
	private boolean noRoot    = false;
	
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
				pointsCell = loadPoints(LOCATION_CACHE_CELL);
				publishProgress("Loading Wireless Access Point Locations");
				pointsWifi = loadPoints(LOCATION_CACHE_WIFI);
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
			
			if ( dialog != null ) {
				dialog.dismiss();
				dialog = null;
			}

			task = null;
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);

			dialog.dismiss();
			dialog = null;

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
				
				updateLabels();
				
				zoomToVisibleMarkers();
			}
		}
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			dialog = ProgressDialog.show(MainActivity.this, "Loading Cached Location Data", ""); 
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
		
//		((SlidingDrawer)findViewById(R.id.slider)).setOnDrawerOpenListener(new SlidingDrawer.OnDrawerOpenListener() {
//			@Override
//			public void onDrawerOpened() {
//				LayoutParams params = findViewById(R.id.slider).getLayoutParams();
//				params.height = findViewById(R.id.content).getMeasuredHeight() + findViewById(R.id.handle).getHeight();
//				findViewById(R.id.slider).setLayoutParams(params);
//			}
//		});
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
		
		// only load if it's been at least 5 minutes since the last load
		if ( task == null && (System.currentTimeMillis() - lastLoad) > (1000 * 60 * 5) ) {
			task = new LoadDataTask();
			task.execute((Void[])null);
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
	}
	
	private CharSequence formatDateSpan(long minTS, long maxTS)
	{
		Calendar calMin = Calendar.getInstance(Locale.getDefault());
		calMin.setTimeInMillis(minTS);

		Calendar calMax = Calendar.getInstance(Locale.getDefault());
		calMax.setTimeInMillis(maxTS);
		
		SimpleDateFormat fmt = new SimpleDateFormat("yyy.MM.dd h:mm a");
		StringBuilder b = new StringBuilder();
		b.append(fmt.format(calMin.getTime()));
		b.append(" - ");
		b.append(fmt.format(calMax.getTime()));
		
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
		
		MenuItem itemZoom = menu.add(Menu.NONE, MENU_ITEM_ZOOM, Menu.NONE, "Zoom to All");
		itemZoom.setIcon(android.R.drawable.ic_menu_zoom);
		
		MenuItem itemHeatmap = menu.add(Menu.NONE, MENU_ITEM_HEATMAP, Menu.NONE, "Disable Heatmap");
		itemHeatmap.setCheckable(true);
		itemHeatmap.setChecked(true);
		
		MenuItem itemAbout = menu.add(Menu.NONE, MENU_ITEM_ABOUT, Menu.NONE, "About");
		
		if ( Util.isDebugBuild(this) ) {
			MenuItem itemReplay = menu.add(Menu.NONE, MENU_ITEM_REPLAY, Menu.NONE, "Replay Tracks");
		}
		
		return super.onCreateOptionsMenu(menu);
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
			startReplayOfTracks();			
		} else if ( item.getItemId() == MENU_ITEM_ABOUT ) {
			AboutDialog dialog = new AboutDialog(this);
			dialog.show();
		}
		return super.onMenuItemSelected(featureId, item);
	}

	private int trackPosition = -1;
	
    private final Handler mMsgHandler = new Handler() {
    	public void handleMessage(android.os.Message msg) {
    		if ( trackPosition < 0 ) return;
    		if ( trackPosition >= markersCell.size() ) {
    			trackPosition = -1;
    			return;
    		}
    		
			LocationInformationOverlayItem item = markersCell.getItem(trackPosition);
			MapView mapView = (MapView) findViewById(R.id.mapview);
			mapView.getController().animateTo(item.getPoint());

			trackPosition++;
			mMsgHandler.sendMessageDelayed(new Message(), 1000);
    	};
    };
	
	private void startReplayOfTracks()
	{
		trackPosition = 0;
		mMsgHandler.sendMessageDelayed(new Message(), 100);
	}

	private List<LocationInformation> loadPoints(String fileName) throws NoRootAccessException, RunCommandException {
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
			
			return LocationCacheParser.parseLocationCacheFile(r.stdout);
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
}