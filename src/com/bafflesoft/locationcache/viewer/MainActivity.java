package com.bafflesoft.locationcache.viewer;

import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.bafflesoft.locationcache.viewer.ShellCommand.CommandResult;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.Projection;

public class MainActivity extends MapActivity {
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

	private static final int MENU_ITEM_CELL = 2;
	private static final int MENU_ITEM_WIFI = 1;
	private static final int MENU_ITEM_ZOOM = 3;

	private static final String FOLDER_CACHE        = "/data/data/com.google.android.location/files/";
	private static final String LOCATION_CACHE_CELL = FOLDER_CACHE + "cache.cell";
	private static final String LOCATION_CACHE_WIFI = FOLDER_CACHE + "cache.wifi";
	
	private Markers markersWifi = null;
	private Markers markersCell = null;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);			
	
		try {
			ShellCommand cmd = new ShellCommand();
			if ( !cmd.canSU(true) ) {
				throw new NoRootAccessException(String.valueOf(cmd.result.stderr));
			}
			
			loadAndDrawLocations();
		} catch ( NoRootAccessException ex ) {
			showError(this, "Root Access Required", ex);
		} catch ( RunCommandException ex ) {
			showError(this, "Error Reading Location Cache", ex);
		}
	}
	
	private void showError(Context context, String title, Throwable ex)
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
	}

	private void loadAndDrawLocations() throws NoRootAccessException, RunCommandException {
		MapView mapView = (MapView) findViewById(R.id.mapview);
		mapView.setBuiltInZoomControls(true);
		
		Drawable drawWifi = getResources().getDrawable(R.drawable.icon_wifi);
		Drawable drawCell = getResources().getDrawable(R.drawable.icon_celltower);

		if ( markersWifi != null ) {
			markersWifi.clear();
			markersCell.clear();
		} else {			
			markersWifi = new Markers(drawWifi, this); 
			markersCell = new Markers(drawCell, this);
			
			markersWifi.setDrawCircles(true);
			markersCell.setDrawCircles(true);
			
			markersWifi.setFillColor(0xff9E7151);
			markersCell.setFillColor(0xff5680FC);

			mapView.getOverlays().add(markersCell);
			mapView.getOverlays().add(markersWifi);
		}
		
		loadPointsAndDraw(mapView, LOCATION_CACHE_CELL, markersCell, drawCell);
		loadPointsAndDraw(mapView, LOCATION_CACHE_WIFI, markersWifi, drawWifi);			
		
		zoomToVisibleMarkers();
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
		

		mapView.invalidate();
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuItem itemWifi = menu.add(Menu.NONE, MENU_ITEM_WIFI, Menu.NONE, "Show Wifi Locations");
		itemWifi.setCheckable(true);
		itemWifi.setChecked(true);
		itemWifi.setIcon(R.drawable.icon_wifi);
		
		MenuItem itemCell = menu.add(Menu.NONE, MENU_ITEM_CELL, Menu.NONE, "Show Cell Locations");
		itemCell.setCheckable(true);
		itemCell.setChecked(true);
		itemCell.setIcon(R.drawable.icon_celltower);
		
		MenuItem itemZoom = menu.add(Menu.NONE, MENU_ITEM_ZOOM, Menu.NONE, "Zoom to All Points");
		itemZoom.setIcon(android.R.drawable.ic_menu_zoom);
		
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		MapView mapView = (MapView) findViewById(R.id.mapview);

		if ( item.getItemId() == MENU_ITEM_WIFI ) {
			if ( item.isChecked() ) {
				mapView.getOverlays().remove(markersWifi);				
			} else {
				mapView.getOverlays().add(markersWifi);
			}
			mapView.invalidate();
			item.setChecked(!item.isChecked());
		} else if ( item.getItemId() == MENU_ITEM_CELL ) {
			if ( item.isChecked() ) {
				mapView.getOverlays().remove(markersCell);
			} else {
				mapView.getOverlays().add(markersCell);
			}
			mapView.invalidate();
			item.setChecked(!item.isChecked());
		} else if ( item.getItemId() == MENU_ITEM_ZOOM ) {
			zoomToVisibleMarkers();
		}
		return super.onMenuItemSelected(featureId, item);
	}
	

	private void loadPointsAndDraw(MapView mapView, String fileName, Markers markerSet, Drawable drawable) throws NoRootAccessException, RunCommandException {
		ShellCommand cmd = new ShellCommand();
		CommandResult r = cmd.su.runWaitFor("cat " + fileName);

		if (!r.success()) {
			throw new RunCommandException(r.stderr);
		} else {
			Log.v("LocationCacheViewer", "Success!");
			
			List<LocationInformation> locations = LocationCacheParser.parseLocationCacheFile(r.stdout);

			for ( LocationInformation location : locations )
			{
				GeoPoint p = location.getGeoPoint();
				if ( p == null ) continue;			
				
				LocationInformationOverlayItem item = new LocationInformationOverlayItem(location);
				markerSet.addOverlay(item);
			}
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

			boolean zoomedTooFarOut = metersPerPixel > 150;
			
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

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}
}