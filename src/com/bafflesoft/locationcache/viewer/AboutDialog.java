package com.bafflesoft.locationcache.viewer;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;

public class AboutDialog extends Dialog {
	public AboutDialog(Context context) {
		super(context);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.about_dialog);
	}
}
