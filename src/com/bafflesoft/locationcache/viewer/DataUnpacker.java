package com.bafflesoft.locationcache.viewer;

public class DataUnpacker {
	public static String decodeString(final byte[] arr, final int idx, final int length)
	{
		final StringBuilder sb = new StringBuilder(length);
		for ( int i = idx; i < (idx+length); i++ ) {
			sb.append((char)arr[i]);
		}
		return sb.toString();
	}
	
	public static short decodeShort(final byte[] arr, final int idx)
	{
		final short short1 = arr[idx  ];
		final short short2 = arr[idx+1];

		return (short) (((short)(short1 & 255)<<8) + ((short)(short2 & 255)));
	}

	public static int decodeInt(final byte[] arr, final int idx)
	{
		final int int1 = arr[idx  ];
		final int int2 = arr[idx+1];
		final int int3 = arr[idx+2];
		final int int4 = arr[idx+3];

		return ((int)(int1 & 255)<<24) + ((int)(int2 & 255)<<16) + ((int)(int3 & 255)<<8) + ((int)(int4 & 255));
	}	

	public static long decodeLong(final byte[] arr, final int idx)
	{
		final int int1 = arr[idx  ];
		final int int2 = arr[idx+1];
		final int int3 = arr[idx+2];
		final int int4 = arr[idx+3];
		final int int5 = arr[idx+4];
		final int int6 = arr[idx+5];
		final int int7 = arr[idx+6];
		final int int8 = arr[idx+7];

		return ((long)(int1 & 255)<<56)
		+ ((long)(int2 & 255)<<48)
		+ ((long)(int3 & 255)<<40)
		+ ((long)(int4 & 255)<<32)
		+ ((long)(int5 & 255)<<24)
		+ ((long)(int6 & 255)<<16) 
		+ ((long)(int7 & 255)<<8) 
		+ ((long)(int8 & 255));
	}

	public static double decodeFloat(final byte[] arr, final int idx)
	{
		final long value = decodeLong(arr, idx);

		return Double.longBitsToDouble(value);
	}	
}
