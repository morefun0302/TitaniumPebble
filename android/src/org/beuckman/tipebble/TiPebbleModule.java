package org.beuckman.tipebble;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollProxyListener;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiConfig;
import org.appcelerator.titanium.TiApplication;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

@Kroll.module(name="TiPebble", id="org.beuckman.tipebble")
public class TiPebbleModule extends KrollModule
{
	private static final String LCAT = "TiPebble";
	private static UUID uuid;
	private static boolean isListeningToPebble = false;
	
	private BroadcastReceiver connectedReceiver = null;
	private BroadcastReceiver disconnectedReceiver = null;
	private PebbleKit.PebbleDataReceiver dataReceiver = null;
	private PebbleKit.PebbleAckReceiver ackReceiver = null;
	private PebbleKit.PebbleNackReceiver nackReceiver = null;

	public TiPebbleModule()
	{
		super();
	}
	
	public TiApplication getApplicationContext()
	{
		return TiApplication.getInstance();
	}

	// Lifecycle Events
	@Kroll.onAppCreate
	public static void onAppCreate(TiApplication app)
	{
		
	}
	
	@Override
	public void onPause(Activity activity)
	{
		super.onPause(activity);
		
		if(connectedReceiver != null)
		{
			PebbleKit.registerPebbleConnectedReceiver(getApplicationContext(), null);
			connectedReceiver = null;
		}
		
		if(disconnectedReceiver != null)
		{
			PebbleKit.registerPebbleDisconnectedReceiver(getApplicationContext(), null);
			disconnectedReceiver = null;
		}
		
		if(dataReceiver != null)
		{
			PebbleKit.registerReceivedDataHandler(getApplicationContext(), null);
			dataReceiver = null;
		}
		
		if(ackReceiver != null)
		{
			PebbleKit.registerReceivedAckHandler(getApplicationContext(), null);
			ackReceiver = null;
		}
		
		if(nackReceiver != null)
		{
			PebbleKit.registerReceivedNackHandler(getApplicationContext(), null);
			nackReceiver = null;
		}
	}
	
	@Override
	public void onResume(Activity activity)
	{
		super.onResume(activity);
		
		if(isListeningToPebble)
		{
			addReceivers();
		}
	}
	
	@Override
	public void onDestroy(Activity activity) 
	{
		super.onDestroy(activity);
	}

	// Methods
	@Kroll.method
	private void listenToConnectedWatch()
	{
		Log.d(LCAT, "listenToConnectedWatch: Listening");
		
		if(!checkWatchConnected())
		{
			Log.w(LCAT, "listenToConnectedWatch: No watch connected");
			
			return;
		}
		
		if(!isListeningToPebble)
		{
			isListeningToPebble = true;
		}
		
		addReceivers();
	}
	
	@Kroll.method
	private void addReceivers()
	{
		connectedReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				Log.d(LCAT, "watchDidConnect");
				fireEvent("watchConnected", new KrollDict());
			}
		};
		
		disconnectedReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				Log.d(LCAT, "watchDidDisconnect");
				fireEvent("watchDisconnected", new KrollDict());
			}
		};
		
		dataReceiver = new PebbleKit.PebbleDataReceiver(uuid)
		{
			@Override
			public void receiveData(final Context context, final int transactionId, final PebbleDictionary data)
			{
				if(!data.contains(0))
				{
					Log.e(LCAT, "listenToConnectedWatch: Received Message, Data Corrupt");
					
					PebbleKit.sendNackToPebble(context, transactionId);
					
					return;
				}

				PebbleKit.sendAckToPebble(context, transactionId);
				
				try
				{
					JSONArray json = new JSONArray(data.toJsonString());
					Log.e(LCAT, json.toString());
				} catch(Throwable e) {
					Log.e(LCAT, "listenToConnectedWatch: Received Message, Data Corrupt");
				}
				
				
				//Log.i(LCAT, "Received value=" + data.getUnsignedInteger(0) + " for key: 0");
				
			}
		};
		
		ackReceiver = new PebbleKit.PebbleAckReceiver(uuid)
		{
			@Override
			public void receiveAck(Context context, int transactionId)
			{
				Log.i(LCAT, "Received ACK");
			}
		};
		
		nackReceiver = new PebbleKit.PebbleNackReceiver(uuid)
		{
			@Override
			public void receiveNack(Context context, int transactionId)
			{
				Log.i(LCAT, "Received NACK");
			}
		};
		
		if(isListeningToPebble)
		{
			PebbleKit.registerPebbleConnectedReceiver(getApplicationContext(), connectedReceiver);
			PebbleKit.registerPebbleDisconnectedReceiver(getApplicationContext(), disconnectedReceiver);
			PebbleKit.registerReceivedDataHandler(getApplicationContext(), dataReceiver);
			PebbleKit.registerReceivedAckHandler(getApplicationContext(), ackReceiver);
			PebbleKit.registerReceivedNackHandler(getApplicationContext(), nackReceiver);
		}
	}

	@Kroll.method
	public void setAppUUID(String uuidString)
	{
		uuid = UUID.fromString(uuidString);
	}

	@Kroll.method
	public boolean checkWatchConnected()
	{
		try
		{
			boolean connected = PebbleKit.isWatchConnected(getApplicationContext());
			
			if(connected)
			{
				Log.w(LCAT, "checkWatchConnected: No watch connected");
			}
			
			return connected;
		} catch(SecurityException e) {
			return false;
		}
	}
	
	@Kroll.method
	public int connectedCount()
	{
		if(checkWatchConnected())
		{
			return 1;
		} else {
			return 0;
		}
	}
	
	@Kroll.method
	public void connect(HashMap args)
	{
		Log.d(LCAT, "connect");
		
		final KrollFunction successCallback = (KrollFunction)args.get("success");
		final KrollFunction errorCallback = (KrollFunction)args.get("error");
		
		if(!PebbleKit.areAppMessagesSupported(getApplicationContext()))
		{
			Log.e(LCAT, "connect: Watch does not support messages");
			
			if(errorCallback != null)
			{
				errorCallback.call(getKrollObject(), new Object[] {});
			}
			
			return;
		}
		
		listenToConnectedWatch();
		
		Log.d(LCAT, "connect: Messages supported");
		
		if(successCallback != null)
		{
			successCallback.call(getKrollObject(), new Object[] {});
		}
	}
	
	@Kroll.method
	public void getVersionInfo(HashMap args)
	{
		Log.d(LCAT, "getVersionInfo");
		
		final KrollFunction successCallback = (KrollFunction)args.get("success");
		final KrollFunction errorCallback = (KrollFunction)args.get("error");
		
		int majorVersion;
		int minorVersion;
		
		try
		{
			PebbleKit.FirmwareVersionInfo versionInfo = PebbleKit.getWatchFWVersion(getApplicationContext());
			
			majorVersion = versionInfo.getMajor();
			minorVersion = versionInfo.getMinor();
		} catch(Exception e) {
			Log.w(LCAT, "Could not retrieve version info from Pebble");
			
			HashMap event = new HashMap();
			event.put("message", "Could not retrieve version info from Pebble");
			
			errorCallback.call(getKrollObject(), event);
			
			return;
		}

		Log.d(LCAT, "Pebble FW Major " + majorVersion);
		Log.d(LCAT, "Pebble FW Minor " + minorVersion);
		
		if(successCallback != null)
		{
			HashMap versionInfoHash = new HashMap();
			versionInfoHash.put("major", majorVersion);
			versionInfoHash.put("minor", minorVersion);
			
			successCallback.call(getKrollObject(), versionInfoHash);
		}
	}
	
	@Kroll.method
	public void launchApp(HashMap args)
	{
		Log.d(LCAT, "launchApp");
		
		if(!checkWatchConnected())
		{
			Log.w(LCAT, "launchApp: No watch connected");
			
			return;
		}
		
		final KrollFunction successCallback = (KrollFunction)args.get("success");
		final KrollFunction errorCallback = (KrollFunction)args.get("error");
		
		try
		{
			PebbleKit.startAppOnPebble(getApplicationContext(), uuid);
			
			Log.d(LCAT, "launchApp: Success");
			
			if(successCallback != null)
			{
				HashMap event = new HashMap();
				event.put("message", "Successfully launched app");
				
				successCallback.call(getKrollObject(), event);
			}
		} catch(IllegalArgumentException e) {
			Log.e(LCAT, "launchApp: Error");
			
			if(errorCallback != null)
			{
				errorCallback.call(getKrollObject(), new Object[] {});
			}
			
			return;
		}
	}
	
	@Kroll.method
	public void killApp(HashMap args)
	{
		Log.d(LCAT, "killApp");
		
		if(!checkWatchConnected())
		{
			Log.w(LCAT, "killApp: No watch connected");
			
			return;
		}
		
		final KrollFunction successCallback = (KrollFunction)args.get("success");
		final KrollFunction errorCallback = (KrollFunction)args.get("error");
		
		try
		{
			PebbleKit.closeAppOnPebble(getApplicationContext(), uuid);
			
			Log.d(LCAT, "killApp: Success");
			
			if(successCallback != null)
			{
				HashMap event = new HashMap();
				event.put("message", "Successfully killed app");
				
				successCallback.call(getKrollObject(), event);
			}
		} catch(IllegalArgumentException e) {
			Log.e(LCAT, "killApp: Error");
			
			if(errorCallback != null)
			{
				errorCallback.call(getKrollObject(), new Object[] {});
			}
			
			return;
		}
	}
	
	@Kroll.method
	public void sendMessage(HashMap args)
	{
		Log.d(LCAT, "sendMessage");
		
		if(!checkWatchConnected())
		{
			Log.w(LCAT, "sendMessage: No watch connected");
			
			return;
		}
		
		final KrollFunction successCallback = (KrollFunction)args.get("success");
		final KrollFunction errorCallback = (KrollFunction)args.get("error");
		final Object message = args.get("message");
		
		Map<Integer, Object> messageHash = (HashMap<Integer, Object>) message;
		Iterator<Map.Entry<Integer, Object>> entries = messageHash.entrySet().iterator();
		
		PebbleDictionary data = new PebbleDictionary();
		
		while(entries.hasNext())
		{
			Map.Entry<Integer, Object> entry = entries.next();
			
			if(entry.getValue() instanceof Integer)
			{
				data.addInt32((Integer) entry.getKey(), (Integer) entry.getValue());
			} else if(entry.getValue() instanceof String) {
				data.addString((Integer) entry.getKey(), (String) entry.getValue());
			}
		}
		
		PebbleKit.sendDataToPebble(getApplicationContext(), uuid, data);
	}
}

//TODO: Here's the todo list
/*
	- Map ACK/NACK handler to sendMessage so it can fire success/error callback for each message
*/