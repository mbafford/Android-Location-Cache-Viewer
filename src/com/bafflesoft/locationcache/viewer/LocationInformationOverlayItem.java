package com.bafflesoft.locationcache.viewer;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.OverlayItem;

public class LocationInformationOverlayItem extends OverlayItem {
	public LocationInformationOverlayItem(LocationInformation location) {
		super(new GeoPoint(location.latIE6, location.lonIE6), location.key, location.getTimeString());
		this.location = location;
	}

	public LocationInformation location = null;	
}
