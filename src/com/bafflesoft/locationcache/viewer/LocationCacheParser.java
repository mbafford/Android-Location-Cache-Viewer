package com.bafflesoft.locationcache.viewer;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

public class LocationCacheParser {
	public static List<LocationInformation> parseLocationCacheFile(byte[] data, String type)
	{
		// unpack ">hh" - version, count
		// big-endian short (2), short(2)
		short version = DataUnpacker.decodeShort(data, 0);
		short count   = DataUnpacker.decodeShort(data, 2);

		Log.v("LocationCacheViewer", "version: " + version);
		Log.v("LocationCacheViewer", "count:   " + count  );

		List<LocationInformation> locations = new ArrayList<LocationInformation>(count);
		
		// unpack ">hSiiddQ" - keyLength, key, accuracy, confidence, latitude, longitude, time
		// big-endian short (2), string(keyLength), int (4), int (4), double (8), double(8), unsigned long long (8)
		// total - 34 bytes
		for ( int i = 4; i < data.length; i += 34 )
		{
			if ( (i + 34) > data.length ) {
				Log.v("LocationCacheViewer", "malformed last record? at i: " + i + " but only length: " + data.length);
				break;
			}

			LocationInformation location = new LocationInformation();

			int keyLength = DataUnpacker.decodeShort(data, i+ 0);

			location.key = DataUnpacker.decodeString(data, i+2, keyLength);

			i += keyLength;
			
			location.accuracy   = DataUnpacker.decodeInt(data, i+ 2);
			location.confidence = DataUnpacker.decodeInt(data, i+ 6);
			location.latIE6     = (int)(DataUnpacker.decodeFloat(data, i+10) * 1E6);
			location.lonIE6     = (int)(DataUnpacker.decodeFloat(data, i+18) * 1E6);
			location.timestamp  = DataUnpacker.decodeLong(data, i+26);
			location.type       = type;
			
			locations.add(location);
		}						
		
		return locations;
	}
	
	
}
