// The present software is not subject to the US Export Administration Regulations (no exportation license required), May 2012
package com.morpho.morphosample;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.morpho.android.usb.USBDeviceAttributes;
import com.morpho.android.usb.USBManager;
import com.morpho.morphosample.info.MorphoInfo;
import com.morpho.morphosample.info.ProcessInfo;
import com.morpho.morphosample.tools.DeviceDetectionMode;
import com.morpho.morphosample.tools.MorphoTools;
import com.morpho.morphosmart.sdk.ErrorCodes;
import com.morpho.morphosmart.sdk.FieldAttribute;
import com.morpho.morphosmart.sdk.MorphoDatabase;
import com.morpho.morphosmart.sdk.MorphoDevice;
import com.morpho.morphosmart.sdk.MorphoField;
import com.morpho.morphosmart.sdk.TemplateType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ConnectionActivity extends Activity
{

	MorphoDevice	morphoDevice;
	private int	sensorBus	= -1;
	private int	sensorAddress	= -1;
	private int	sensorFileDescriptor	= -1;
	private String	sensorName	= "";
	private DeviceDetectionMode detectionMode = DeviceDetectionMode.SdkDetection;
	Button			buttonConnection;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		if (MorphoSample.isRebootSoft)
		{
			MorphoSample.isRebootSoft = false;
			finish();
		}

		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_connection);

		buttonConnection = (Button) findViewById(R.id.btn_ok);

		buttonConnection.setEnabled(false);

		morphoDevice = new MorphoDevice();	
		
		USBManager.getInstance().initialize(this, "com.morpho.morphosample.USB_ACTION");
		
		if(USBManager.getInstance().isDevicesHasPermission() == true)
		{
			Button buttonGrantPermission = (Button) findViewById(R.id.btn_grantPermission);
			buttonGrantPermission.setEnabled(false);
		}
	}

	public void grantPermission(View v)
	{
		USBManager.getInstance().initialize(this, "com.morpho.morphosample.USB_ACTION");
	}


	synchronized int countDevices() {
		int count = 0;
		UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
		HashMap<String, UsbDevice> usbDeviceList = usbManager.getDeviceList();

		Iterator<UsbDevice> usbDeviceIterator = usbDeviceList.values().iterator();
		while (usbDeviceIterator.hasNext())
		{
			UsbDevice usbDevice = usbDeviceIterator.next();
			if (MorphoTools.isSupported(usbDevice.getVendorId(), usbDevice.getProductId()))
			{
				boolean hasPermission = usbManager.hasPermission(usbDevice);
				if (!hasPermission)
				{
					// Request permission for using the device
					usbManager.requestPermission(usbDevice, PendingIntent.getBroadcast(this, 0, new Intent("com.morpho.android.usb.USB_PERMISSION"), 0));
				}
				else {
					count++;
				}
			}
		}
		return count;
	}

	synchronized String getFirstDeviceInfo() throws Exception{
		String sn = "";
		UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
		HashMap<String, UsbDevice> usbDeviceList = usbManager.getDeviceList();

		Iterator<UsbDevice> usbDeviceIterator = usbDeviceList.values().iterator();
		while (usbDeviceIterator.hasNext()) {
			UsbDevice usbDevice = usbDeviceIterator.next();
			if (MorphoTools.isSupported(usbDevice.getVendorId(), usbDevice.getProductId())) {
				boolean hasPermission = usbManager.hasPermission(usbDevice);

				if (hasPermission) {
					UsbDeviceConnection connection = usbManager.openDevice(usbDevice);
					if (connection != null)
					{
						//Log.d("MORPHO_USB" ,"getting serial number .. #" + mConnection.getSerial());
						sn = connection.getSerial();
						sensorFileDescriptor = connection.getFileDescriptor();
						String name = usbDevice.getDeviceName();

						String[] elts = name.split("/");
						if(elts.length < 5)
							continue;
						sensorBus = Integer.parseInt(elts[4].toString());
						sensorAddress = Integer.parseInt(elts[5].toString());

						break;
					}
				}
			}
		}

		return sn;
	}

	public void onRadioButtonClicked(View view) {
		// Is the button now checked?
		boolean checked = ((RadioButton) view).isChecked();

		// Check which radio button was clicked
		switch(view.getId()) {
			case R.id.radio_sn:
				if (checked)
					// using serial number
					detectionMode = DeviceDetectionMode.SdkDetection;
					break;
			case R.id.radio_fd:
				if (checked)
					// using file descriptor
					detectionMode = DeviceDetectionMode.UserDetection;
					countDevices();
					break;
		}
	}


		@SuppressLint("UseValueOf")
	public void enumerate(View v) {
			TextView textViewSensorName = (TextView) findViewById(R.id.textView_serialNumber);

			if (detectionMode == DeviceDetectionMode.SdkDetection) {
				Integer nbUsbDevice = new Integer(0);

				int ret = morphoDevice.initUsbDevicesNameEnum(nbUsbDevice);

				if (ret == ErrorCodes.MORPHO_OK) {
					if (nbUsbDevice > 0) {
						sensorName = morphoDevice.getUsbDeviceName(0);
						textViewSensorName.setText(sensorName);
						buttonConnection.setEnabled(true);
					} else {
						final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
						alertDialog.setTitle(this.getResources().getString(R.string.morphosample));
						alertDialog.setMessage("The device is not detected, or you have not asked USB permissions, please click the button 'Grant Permission'");
						alertDialog.setCancelable(false);
						alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								//finish();
							}
						});
						alertDialog.show();
					}

				} else {
					final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
					alertDialog.setTitle(this.getResources().getString(R.string.morphosample));
					alertDialog.setMessage(ErrorCodes.getError(ret, morphoDevice.getInternalError()));
					alertDialog.setCancelable(false);
					alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							//finish();
						}
					});
					alertDialog.show();
				}

			} else {
				try {
					int nbUsbDevice = countDevices();

					if (nbUsbDevice > 0) {
						sensorName = getFirstDeviceInfo();
						textViewSensorName.setText(sensorName);
						buttonConnection.setEnabled(true);
					} else {
						final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
						alertDialog.setTitle(this.getResources().getString(R.string.morphosample));
						alertDialog.setMessage("The device is not detected, or you have not asked USB permissions, please click the button 'Grant Permission'");
						alertDialog.setCancelable(false);
						alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								//finish();
							}
						});
						alertDialog.show();
					}
				} catch (Exception exc) {
					final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
					alertDialog.setTitle(this.getResources().getString(R.string.morphosample));
					alertDialog.setMessage(exc.getMessage());
					alertDialog.setCancelable(false);
					alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							//finish();
						}
					});
					alertDialog.show();
				}
			}
		}

	@SuppressLint("UseValueOf")
	public void connection(View v)
	{
		int ret = 0;
		if (detectionMode == DeviceDetectionMode.SdkDetection)
			ret = morphoDevice.openUsbDevice(sensorName, 0);
		else {
			if(sensorBus >0 && sensorAddress >0 && sensorFileDescriptor >0) {
				ret = morphoDevice.openUsbDeviceFD(sensorBus, sensorAddress, sensorFileDescriptor, 0);
				//Log.e("MORPHO_USB", "morphoDevice.openUsbDeviceFD : "  + ret);
			}
			else {
				final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
				alertDialog.setTitle(this.getResources().getString(R.string.morphosample));
				alertDialog.setMessage("The device is not detected, or you have not asked USB permissions, please click the button 'Grant Permission'");
				alertDialog.setCancelable(false);
				alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						//finish();
					}
				});
				alertDialog.show();
			}
		}

		if (ret != 0)
		{
			final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
			alertDialog.setTitle(this.getResources().getString(R.string.morphosample));
			alertDialog.setMessage(ErrorCodes.getError(ret, morphoDevice.getInternalError()));
			alertDialog.setCancelable(false);
			alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which)
				{
					finish();
				}
			});
			alertDialog.show();
		}
		else
		{
			ProcessInfo.getInstance().setMSOSerialNumber(sensorName);
			ProcessInfo.getInstance().setMSOBus(sensorBus);
			ProcessInfo.getInstance().setMSOAddress(sensorAddress);
			ProcessInfo.getInstance().setMSOFD(sensorFileDescriptor);
			ProcessInfo.getInstance().setMsoDetectionMode(detectionMode);
			String productDescriptor = morphoDevice.getProductDescriptor();
			java.util.StringTokenizer tokenizer = new java.util.StringTokenizer(productDescriptor, "\n");
			if (tokenizer.hasMoreTokens())
			{
				String l_s_current = tokenizer.nextToken();
				if (l_s_current.contains("FINGER VP") || l_s_current.contains("FVP"))
				{
					MorphoInfo.m_b_fvp = true;
				}
			}

			final MorphoDatabase morphoDatabase = new MorphoDatabase();
			ret = morphoDevice.getDatabase(0, morphoDatabase);
			Log.i("MORPHO_USB", "morphoDevice.getDatabase = " + ret);
			if (ret != ErrorCodes.MORPHO_OK)
			{
				if (ret == ErrorCodes.MORPHOERR_BASE_NOT_FOUND)
				{

					LayoutInflater factory = LayoutInflater.from(this);
					final View textEntryView = factory.inflate(R.layout.base_config, null);
					final EditText input1 = (EditText) textEntryView.findViewById(R.id.editTextMaximumnumberofrecord);
					final EditText input2 = (EditText) textEntryView.findViewById(R.id.editTextNumberoffingerperrecord);
					input1.setText("500");
					input2.setText("2");
					
					final RadioGroup radioEncryptDatabase = (RadioGroup) textEntryView.findViewById(R.id.radioEncryptDatabase);

					final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
					alertDialog.setTitle(this.getResources().getString(R.string.morphosample));
					alertDialog.setMessage("Data Base configuration : ");
					alertDialog.setCancelable(false);
					alertDialog.setView(textEntryView);		
					alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface dialog, int which)
						{
							finish();
						}
					});
					alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, this.getResources().getString(R.string.ok), new DialogInterface.OnClickListener()
					{						
						public void onClick(DialogInterface dialog, int which)
						{
							Integer index = new Integer(0);
							MorphoField morphoFieldFirstName = new MorphoField();
							morphoFieldFirstName.setName("First");
							morphoFieldFirstName.setMaxSize(15);
							morphoFieldFirstName.setFieldAttribute(FieldAttribute.MORPHO_PUBLIC_FIELD);
							morphoDatabase.putField(morphoFieldFirstName, index);
							MorphoField morphoFieldLastName = new MorphoField();
							morphoFieldLastName.setName("Last");
							morphoFieldLastName.setMaxSize(15);
							morphoFieldLastName.setFieldAttribute(FieldAttribute.MORPHO_PUBLIC_FIELD);
							morphoDatabase.putField(morphoFieldLastName, index);

							int maxRecord = Integer.parseInt(input1.getText().toString());
							int maxNbFinger = Integer.parseInt(input2.getText().toString());
							boolean encryptDatabase = false;
														
							if(radioEncryptDatabase.getCheckedRadioButtonId() == R.id.radioButtonencryptDatabaseYes)
							{
								encryptDatabase = true;
							}							
							
							final int ret = morphoDatabase.dbCreate(maxRecord, maxNbFinger, TemplateType.MORPHO_PK_COMP,0,encryptDatabase);
							if (ret == ErrorCodes.MORPHO_OK)
							{
								ProcessInfo.getInstance().setBaseStatusOk(true);
								morphoDevice.closeDevice();
								Intent dialogActivity = new Intent(ConnectionActivity.this, MorphoSample.class);
								startActivity(dialogActivity);
								finish();
							}
							else
							{
								Handler mHandler = new Handler();
								mHandler.post(new Runnable()
								{
									@Override
									public synchronized void run()
									{										
										AlertDialog alertDialog = new AlertDialog.Builder(ConnectionActivity.this).create();
										alertDialog.setTitle("DataBase : dbCreate");
										String msg = getString(R.string.OP_FAILED) + "\n" +  getString(R.string.MORPHOERR_BADPARAMETER);
										alertDialog.setMessage(msg);
										alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.ok), new DialogInterface.OnClickListener()
										{
											public void onClick(DialogInterface dialog, int which)
											{
											}
										});
										alertDialog.show();
									}
								});								
							}
						}
					});
					
					alertDialog.show();
				}
			}
			else
			{
				morphoDevice.closeDevice();
				Intent dialogActivity = new Intent(ConnectionActivity.this, MorphoSample.class);
				startActivity(dialogActivity);
				finish();
			}
		}
	}

	public void finishDialog(View v)
	{
		this.finish();
	}
}
