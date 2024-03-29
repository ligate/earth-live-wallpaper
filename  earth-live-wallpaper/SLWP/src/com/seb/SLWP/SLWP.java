package com.seb.SLWP;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import com.seb.SLWP.SLWP.GlEngine.DownloadTask;

public class SLWP extends GLWallpaperService implements
		OnSharedPreferenceChangeListener {
	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Service#onLowMemory()
	 */

	public static final long SLEEPTIME = 1000 * 60 * 30; // 30 minutes
	private GlEngine mGle;
	private CubeRenderer renderer;
	public static Context mContext;
	public static boolean ShowClouds;
	public static boolean TouchRot;
	private boolean Direction;
	private boolean Freespin;
	public static int Bg;
	public static boolean Usebg;
	public static int Tex;
	public static File cache;
	static long Synctime;
	private boolean Realaxis;
	public boolean Slidedir;
	public boolean Slideplanet;
	public boolean Syncrot;
	public static boolean Randomtex = true;
	public static boolean visible = false;
	public static File Fcache;
	public static boolean useCropper = true;
	public static int Cropaspect;
	public static boolean loading = false;
	public static DownloadTask DT = null;
	public static final Handler mHandler = new Handler();
	public static final int RNDMAP = -1;
	public static boolean destroyed;
	public static String bgfile;
	public ConnectivityManager cm;
	public boolean needresume;
	public boolean fstart;
	public static String[] randlist;
	public int curtexidx = -99;

	private long randsynctime=0;
	
	private static final Class[] mStartForegroundSignature = new Class[] {
			int.class, Notification.class };
	private static final Class[] mStopForegroundSignature = new Class[] { boolean.class };
	static final String ACTION_FOREGROUND = "com.seb.SLWP.FOREGROUND";
	static final String ACTION_BACKGROUND = "com.seb.SLWP.BACKGROUND";
	private NotificationManager mNM;
	private Method mStartForeground;
	private Method mStopForeground;
	private Object[] mStartForegroundArgs = new Object[2];
	private Object[] mStopForegroundArgs = new Object[1];
	public static File mapcache;

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();

		Init();
	}

	private void StartFG() {

		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		try {
			mStartForeground = getClass().getMethod("startForeground",
					mStartForegroundSignature);
			mStopForeground = getClass().getMethod("stopForeground",
					mStopForegroundSignature);
		} catch (NoSuchMethodException e) {
			mStartForeground = mStopForeground = null;
		}
		CharSequence text = "Running in foreground...";
		Notification notification = new Notification(R.drawable.notificon,
				text, System.currentTimeMillis());
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, Prefs.class), 0);
		notification.setLatestEventInfo(this, "Earth live wallpaper", text,
				contentIntent);

		mStartForegroundArgs[0] = Integer.valueOf(R.string.app_name);
		mStartForegroundArgs[1] = notification;
		try {
			mStartForeground.invoke(this, mStartForegroundArgs);
		} catch (InvocationTargetException e) {
			// Should not happen.
			Log.w("SLWP", "Unable to invoke startForeground", e);
		} catch (IllegalAccessException e) {
			// Should not happen.
			Log.w("SLWP", "Unable to invoke startForeground", e);
		}
	}

	private void StopFG() {
		mStopForegroundArgs[0] = Boolean.TRUE;
		try {
			mStopForeground.invoke(this, mStopForegroundArgs);
		} catch (InvocationTargetException e) {
			// Should not happen.
			Log.w("SLWP", "Unable to invoke stopForeground", e);
		} catch (IllegalAccessException e) {
			// Should not happen.
			Log.w("SLWP", "Unable to invoke stopForeground", e);
		}

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// handleCommand(intent);
		// We want this service to continue running until it is explicitly
		// stopped, so return sticky.
		return START_STICKY;
	}

	static public boolean hasStorage() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			return true;
		}
		return false;
	}

	private void Init() {
		try {
			mContext = this;
			cm = (ConnectivityManager) mContext
					.getSystemService(Context.CONNECTIVITY_SERVICE);

			while (!hasStorage()) {
				Thread.sleep(100);
			}
			InitCache();

			if (renderer == null)
				renderer = new CubeRenderer(mContext);

			if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
					"Runinforeground", false)) {
				StartFG();
			}
			// renderer = new CubeRenderer(mContext);
			// zoomfactor ?
			CubeRenderer.zoomfactor = PreferenceManager
					.getDefaultSharedPreferences(SLWP.this).getInt(
							"Zoomfactor", 100) / 100f;

			renderer.xpos = ((PreferenceManager.getDefaultSharedPreferences(
					SLWP.this).getInt("Xpos", 100) / 100f) - 1f) * 2f;
			renderer.ypos = ((PreferenceManager.getDefaultSharedPreferences(
					SLWP.this).getInt("Ypos", 100) / 100f) - 1f) * 2f;

			renderer.setYpos(PreferenceManager.getDefaultSharedPreferences(
					SLWP.this).getInt("Lypos", 100) / 200f);
			renderer.showText = PreferenceManager.getDefaultSharedPreferences(
					this).getBoolean("Showtext", false);

			// rot speed ?
			CubeRenderer.rs = PreferenceManager.getDefaultSharedPreferences(
					SLWP.this).getInt("RotSpeed", 100) / 100f;
			// touch sensitive ?
			TouchRot = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("TouchRot", false);
			renderer.trot = TouchRot ? 1f : 0f;
			// texture ?
			Tex = Integer.parseInt(PreferenceManager
					.getDefaultSharedPreferences(SLWP.this).getString("Tex",
							"1"));
			if (Tex == 15) {
				renderer.showrings = true;
				renderer.curtex = Tex;
			}
			if (Tex == 22) {
				renderer.deathstar2 = true;
			} else {
				renderer.deathstar2 = false;
			}
			Randomtex = Tex == RNDMAP ? true : false;

			randlist = ListPreferenceMultiSelect
					.parseStoredValue(PreferenceManager
							.getDefaultSharedPreferences(this).getString(
									"Randlist", "1"));

			Sphere.useshading = PreferenceManager.getDefaultSharedPreferences(
					this).getBoolean("Useshading", false);
			Sphere.shadowcolor = 1.0f - (PreferenceManager
					.getDefaultSharedPreferences(this).getInt("Darkness", 100) / 200f);

			// Direction
			Direction = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Direction", true);
			if (Direction)
				CubeRenderer.direction = -1f;
			else
				CubeRenderer.direction = 1f;
			// freespin
			Freespin = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Freespin", false);
			if (Freespin)
				CubeRenderer.freespin = 1f;
			else
				CubeRenderer.freespin = 0f;

			// starfield
			CubeRenderer.useStarfield = PreferenceManager
					.getDefaultSharedPreferences(this).getBoolean(
							"Usestarfield", false);
			StarField.speedfactor = (PreferenceManager
					.getDefaultSharedPreferences(this).getInt("Starspeed", 100) / 100f);

			renderer.mStarfield.stardensity = 2 * (PreferenceManager
					.getDefaultSharedPreferences(this).getInt("Nbstars", 100) / 100f);
			renderer.mStarfield.InitStars();
			// usebackground ??
			Usebg = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Usebg", true);
			CubeRenderer.usebg = Usebg;

			Bg = Integer.parseInt(PreferenceManager
					.getDefaultSharedPreferences(SLWP.this)
					.getString("Bg", "2"));

			renderer.showmoon = PreferenceManager.getDefaultSharedPreferences(
					this).getBoolean("Showmoon", true);

			useCropper = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Usecropper", false);

			Cropaspect = Integer.parseInt(PreferenceManager
					.getDefaultSharedPreferences(SLWP.this).getString(
							"Cropaspect", "1"));

			Synctime = Long.parseLong(PreferenceManager
					.getDefaultSharedPreferences(SLWP.this).getString(
							"Synctime", "60")) * 1000 * 60;
			
			randsynctime = Long.parseLong(PreferenceManager
					.getDefaultSharedPreferences(SLWP.this).getString(
							"Randrate", "0")) * 1000 * 60;

			Realaxis = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Realaxis", false);
			renderer.setRA(Realaxis);

			Slidedir = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Bgslidedir", false);

			Slideplanet = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Slideplanet", false);

			Syncrot = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Syncrot", false);

			renderer.setAnimbg(PreferenceManager.getDefaultSharedPreferences(
					this).getBoolean("Animbg", false));

			// reg pref listener
			PreferenceManager.getDefaultSharedPreferences(SLWP.this)
					.registerOnSharedPreferenceChangeListener(SLWP.this);
			// if(Tex==0){
			// DoDownload();
			// }
			while (Tex == 0
					&& !Environment.getExternalStorageState().equalsIgnoreCase(
							Environment.MEDIA_MOUNTED)) {

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void InitCache() {
		File sd = Environment.getExternalStorageDirectory();
		cache = new File(sd.getAbsolutePath() + "/EarthRot");
		File imgnomedia = new File(sd.getAbsolutePath() + "/EarthRot/.nomedia");
		if (!cache.exists()) {
			if (!cache.mkdirs()) {
				Log.e("SLWP", "Cache error");
			} else {
				Log.i("SLWP", "Cache init ok");
				if (!imgnomedia.exists()) {
					try {
						imgnomedia.createNewFile();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} else {
			if (!imgnomedia.exists()) {
				try {
					imgnomedia.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		Fcache = new File(cache + "/earthrot.jpg");
		if (Fcache.exists()) {
			if (Fcache.length() < 40 * 1024)
				Fcache.delete();
			// else
			// Fcache.setLastModified(new Date().getTime());
		}
		mapcache = new File(cache + "/maps");
		if (!mapcache.exists()) {
			mapcache.mkdir();
			// maps2sd();
		}
	}

	private void maps2sd() {
		try {
			Field[] fields = dClass.getDeclaredFields();
			for (Field f : fields) {
				String fName = f.getName();
				Object fp = f.get(dClass);
				if (fp instanceof Integer) {
					if (!fName.equalsIgnoreCase("dstartwo")
							&& !fName.equalsIgnoreCase("lmap")
							&& !fName.equalsIgnoreCase("loading")
							&& !fName.equalsIgnoreCase("notificon")
							&& !fName.equalsIgnoreCase("satring")
							&& !fName.equalsIgnoreCase("starfield")
							&& !fName.equalsIgnoreCase("icon")
							&& !fName.equalsIgnoreCase("thumb")
							&& !fName.equalsIgnoreCase("bg1")
							&& !fName.equalsIgnoreCase("bg2")
							&& !fName.equalsIgnoreCase("bg3")

					)
						map2sd(((Integer) fp).intValue(), fName, "jpg");
					else if (fName.equalsIgnoreCase("dstartwo"))
						map2sd(((Integer) fp).intValue(), fName, "png");
					else
						continue;
				}
			}
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	private void map2sd(int rid, String rname, String ext) {
		InputStream is = this.getResources().openRawResource(rid);
		File out = new File(mapcache + "/" + rname + "." + ext);
		if (out.exists() && out.length() > 1024)
			return;
		try {
			out.createNewFile();
			DataOutputStream os = new DataOutputStream(
					new BufferedOutputStream(new FileOutputStream(out), 1024));
			byte buf[] = new byte[1024];
			int len;
			while ((len = is.read(buf)) > 0)
				os.write(buf, 0, len);
			os.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	static Class dClass;
	static {
		try {
			dClass = Class.forName("com.seb.SLWP.R$drawable");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.compareToIgnoreCase("RotSpeed") == 0) {
			CubeRenderer.rs = PreferenceManager.getDefaultSharedPreferences(
					this).getInt("RotSpeed", 100) / 100f;
		} else if (key.compareToIgnoreCase("Zoomfactor") == 0) {
			CubeRenderer.zoomfactor = PreferenceManager
					.getDefaultSharedPreferences(this)
					.getInt("Zoomfactor", 100) / 100f;
		} else if (key.compareToIgnoreCase("Showmoon") == 0) {
			renderer.showmoon = PreferenceManager.getDefaultSharedPreferences(
					this).getBoolean("Showmoon", true);

		} else if (key.compareToIgnoreCase("Xpos") == 0) {
			renderer.xpos = ((PreferenceManager.getDefaultSharedPreferences(
					SLWP.this).getInt("Xpos", 100) / 100f) - 1f) * 2f;
		}

		else if (key.compareToIgnoreCase("Ypos") == 0) {
			renderer.ypos = ((PreferenceManager.getDefaultSharedPreferences(
					SLWP.this).getInt("Ypos", 100) / 100f) - 1f) * 2f;
		} else if (key.compareToIgnoreCase("AnimBg") == 0) {
			renderer.setAnimbg(PreferenceManager.getDefaultSharedPreferences(
					this).getBoolean("Animbg", false));
		} else if (key.compareToIgnoreCase("TouchRot") == 0) {
			TouchRot = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("TouchRot", false);
			if (!TouchRot) {
				CubeRenderer.freespin = 0f;
				renderer.resetAngles();
			}
			renderer.trot = TouchRot ? 1f : 0f;
		} else if (key.compareToIgnoreCase("Usebg") == 0) {
			Usebg = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Usebg", true);
			CubeRenderer.usebg = Usebg;

		} else if (key.compareToIgnoreCase("Slideplanet") == 0) {
			Slideplanet = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Slideplanet", false);
		} else if (key.compareToIgnoreCase("Syncrot") == 0) {
			Syncrot = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Syncrot", false);
		} else if (key.compareToIgnoreCase("Bgslidedir") == 0) {
			Slidedir = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Bgslidedir", false);
		} else if (key.compareToIgnoreCase("Realaxis") == 0) {
			Realaxis = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Realaxis", false);
			renderer.setRA(Realaxis);

		} else if (key.compareToIgnoreCase("Direction") == 0) {
			Direction = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Direction", true);
			if (Direction)
				CubeRenderer.direction = -1f;
			else
				CubeRenderer.direction = 1f;
		} else if (key.compareToIgnoreCase("Tex") == 0) {
			Tex = Integer.parseInt(PreferenceManager
					.getDefaultSharedPreferences(SLWP.this).getString("Tex",
							"1"));
			if (Tex == RNDMAP) {
				Randomtex = true;
			} else {
				Randomtex = false;
				renderer.setTex(Tex);
			}
			if (Tex == 22) {
				renderer.deathstar2 = true;
			} else {
				renderer.deathstar2 = false;
			}

		} else if (key.compareToIgnoreCase("Randlist") == 0) {
			randlist = ListPreferenceMultiSelect
					.parseStoredValue(PreferenceManager
							.getDefaultSharedPreferences(this).getString(
									"Randlist", "1"));
		}

		else if (key.compareToIgnoreCase("Synctime") == 0) {
			Synctime = Integer.parseInt(PreferenceManager
					.getDefaultSharedPreferences(SLWP.this).getString(
							"Synctime", "60")) * 1000 * 60;
		} else if (key.compareToIgnoreCase("Bg") == 0) {
			Bg = Integer.parseInt(PreferenceManager
					.getDefaultSharedPreferences(SLWP.this)
					.getString("Bg", "2"));
			renderer.setBg(Bg);
		} else if (key.compareToIgnoreCase("Freespin") == 0) {
			Freespin = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Freespin", false);
			if (Freespin)
				CubeRenderer.freespin = 1f;
			else
				CubeRenderer.freespin = 0f;
		} else if (key.compareToIgnoreCase("Usestarfield") == 0) {
			CubeRenderer.useStarfield = PreferenceManager
					.getDefaultSharedPreferences(this).getBoolean(
							"Usestarfield", false);
		} else if (key.compareToIgnoreCase("Starspeed") == 0) {
			StarField.speedfactor = (PreferenceManager
					.getDefaultSharedPreferences(this).getInt("Starspeed", 100) / 100f);
		} else if (key.compareToIgnoreCase("Runinforeground") == 0) {
			if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
					"Runinforeground", false)) {
				StartFG();
			} else {
				StopFG();
			}
		} else if (key.compareToIgnoreCase("Nbstars") == 0) {
			renderer.mStarfield.stardensity = 2f * (PreferenceManager
					.getDefaultSharedPreferences(this).getInt("Nbstars", 100) / 100f);
			renderer.mStarfield.InitStars();
		} else if (key.compareToIgnoreCase("Lypos") == 0) {
			renderer.setYpos(PreferenceManager.getDefaultSharedPreferences(
					SLWP.this).getInt("Lypos", 100) / 200f);
		} else if (key.compareToIgnoreCase("Showtext") == 0) {
			renderer.showText = PreferenceManager.getDefaultSharedPreferences(
					this).getBoolean("Showtext", false);
		} else if (key.compareToIgnoreCase("Useshading") == 0) {
			Sphere.useshading = PreferenceManager.getDefaultSharedPreferences(
					this).getBoolean("Useshading", false);
		} else if (key.compareToIgnoreCase("Darkness") == 0) {
			Sphere.shadowcolor = 1.0f - (PreferenceManager
					.getDefaultSharedPreferences(this).getInt("Darkness", 100) / 200f);
		} else if (key.compareToIgnoreCase("Usecropper") == 0) {
			useCropper = PreferenceManager.getDefaultSharedPreferences(this)
					.getBoolean("Usecropper", false);

		} else if (key.compareToIgnoreCase("Cropaspect") == 0) {
			Cropaspect = Integer.parseInt(PreferenceManager
					.getDefaultSharedPreferences(SLWP.this).getString(
							"Cropaspect", "1"));
		}
		else if (key.compareToIgnoreCase("Randrate") == 0) {
			randsynctime = Integer.parseInt(PreferenceManager
					.getDefaultSharedPreferences(SLWP.this).getString(
							"Randrate", "0")) * 1000 * 60;
		} 
	}

	@Override
	public Engine onCreateEngine() {
		if (mGle != null) {
			mGle = null;
		}
		try {
			mGle = new GlEngine();
		} catch (Exception e) {
			return null;
		}

		return mGle;
	}

	class GlEngine extends GLEngine {

		private static final float TOUCH_SCALE_FACTOR = 1E-3f;
		private float mPreviousX = 0.0f;
		private float mPreviousY = 0.0f;
		public Handler mHandler = new Handler();
		// public Runnable downloader = new Downloader();
		long NOW;
		private long lastrand;
		@Override
		public void onCreate(SurfaceHolder surfaceHolder) {
			super.onCreate(surfaceHolder);
			try {
				this.setTouchEventsEnabled(true);
				fstart = true;
				setRenderer(renderer);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onTouchEvent(MotionEvent e) {
			if (!TouchRot)
				return;
			float x = e.getX();
			float y = e.getY();

			switch (e.getAction()) {
			case MotionEvent.ACTION_MOVE:
				renderer.dx = x - mPreviousX;
				renderer.dy = y - mPreviousY;
				// Log.e("SLWP","rot:"+renderer.dx*
				// TOUCH_SCALE_FACTOR+"-"+renderer.dy* TOUCH_SCALE_FACTOR);
				// CubeRenderer.mAngleX += (renderer.dx * TOUCH_SCALE_FACTOR);
				// CubeRenderer.mAngleY += (renderer.dy * TOUCH_SCALE_FACTOR);
				// requestRender();
				break;
			case MotionEvent.ACTION_UP:
				break;
			}
			mPreviousX = x;
			mPreviousY = y;
			return;
		}

		@Override
		public void onOffsetsChanged(float xOffset, float yOffset,
				float xOffsetStep, float yOffsetStep, int xPixelOffset,
				int yPixelOffset) {
			super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep,
					xPixelOffset, yPixelOffset);
			if (Slidedir) {
				Background.xpos = -(Background.vH - Background.vW)
						* (1f - xOffset);
				if (Slideplanet)
					renderer.xpos = (xOffset - 0.5f) * 2f;
			} else {
				Background.xpos = -(Background.vH - Background.vW) * xOffset;
				if (Slideplanet)
					renderer.xpos = ((1f - xOffset) - 0.5f) * 2f;
			}
			if (Syncrot) {
				CubeRenderer.mAngleZ = (360f * xOffset);
			}
		}

		@Override
		public void onVisibilityChanged(boolean visible) {
			// TODO Auto-generated method stub
			super.onVisibilityChanged(visible);
			SLWP.visible = visible;
			if (visible) {

			} else {

			}
		}

		public void startDownload() {
			DT = new DownloadTask();
			DT.execute();
		}

		public void stopDownload() {
			if (DT != null) {
				DT.cancel(true);
			}
		}

		@Override
		public void onPause() {
			if (DT != null) {
				DT.cancel(true);
				needresume = true;
			}

			super.onPause();
		}

		@Override
		public void onResume() {
			if (Randomtex) {
				Tex = randtex();
			}
			try {
				NOW = new Date().getTime();
				if ((Tex == 0
						&& (!Fcache.exists() || Fcache.length() < 35 * 1024 || Fcache
								.lastModified() < NOW - Synctime) && !isPreview() /*
																				 * &&
																				 * !
																				 * loading
																				 */)
						/* || (Tex == 0 && needresume) */
						|| (Tex == 0
								&& (!Fcache.exists() || Fcache.length() < 35 * 1024) && isPreview())) {

					needresume = false;
					startDownload();
				}
			} catch (Exception e) {

			} finally {
				if (fstart) {
					if (Tex == 0 && !isPreview()) {
						GlEngine.this.queueEvent(Sphere.mUpdateTex);
					}
					fstart = false;
				}
			}
			super.onResume();
		}

		@Override
		public void onSurfaceChanged(SurfaceHolder holder, int format,
				int width, int height) {
			// TODO Auto-generated method stub
			super.onSurfaceChanged(holder, format, width, height);
			fstart = true;
		}

		private int randtex() {
			int rval = -1;
			long currand = new Date().getTime();
			if(currand<lastrand+randsynctime) return Tex;
			if (randlist == null || randlist.length <= 1)
				return 1;
			float rmax = randlist.length - 1;
			while ((rval = (int) Math.rint(Math.random() * rmax)) == curtexidx)
				;
			curtexidx = rval;
			lastrand=currand;
			return Integer.parseInt(randlist[rval]);
		}

		class DownloadTask extends AsyncTask<Void, Void, Boolean> {
			private HttpURLConnection conn;
			private InputStream is = null;
			private URL myFileUrl = null;
			private File Ftemp = new File(Fcache.getAbsolutePath() + ".tmp");

			public DownloadTask() {
				try {
					myFileUrl = new URL(
							"http://www.gmodules.com/ig/proxy?url=http://static.die.net/earth/mercator/1024.jpg");
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}

			@Override
			protected Boolean doInBackground(Void... params) {
				if (Ftemp.exists())
					Ftemp.delete();

				Log.i("SLWP", "Loading texture");
				loading = true;
				try {
					Ftemp.createNewFile();
					conn = (HttpURLConnection) myFileUrl.openConnection();
					conn.setDoInput(true);
					conn.connect();
					is = conn.getInputStream();

					DataOutputStream out = new DataOutputStream(
							new BufferedOutputStream(
									new FileOutputStream(Ftemp), 1024));
					byte buf[] = new byte[1024];
					int len;
					while ((len = is.read(buf)) > 0)
						out.write(buf, 0, len);
					out.close();

				} catch (Exception e) {
					Log.e("SLWP", "ERROR: " + e.getMessage());
				} finally {

				}

				return null;
			}

			@Override
			protected void onCancelled() {
				// TODO Auto-generated method stub
				super.onCancelled();
				loading = false;
				Log.i("SLWP", "DT Interrupted");
				if (conn != null)
					conn.disconnect();
				if (Ftemp.exists())
					Ftemp.delete();
			}

			@Override
			protected void onPostExecute(Boolean result) {
				// TODO Auto-generated method stub
				super.onPostExecute(result);
				if (Ftemp.length() > 35 * 1024) {
					// rename tempfile
					if (Fcache.exists()) {
						destroyed = true;
						Fcache.delete();
					}
					Ftemp.renameTo(Fcache);
					destroyed = false;
					if (Sphere.gl11 != null && visible && Fcache.exists()
							&& Fcache.length() > 0) {
						Log.i("SLWP", "Posting texture request");
						GlEngine.this.queueEvent(Sphere.mUpdateTex);
						needresume = false;
						if (Ftemp.exists())
							Ftemp.delete();
						Log.i("SLWP", "Texture refreshed");
					}
				}
				loading = false;
			}

			@Override
			protected void onPreExecute() {
				// TODO Auto-generated method stub
				super.onPreExecute();
				if (Ftemp.exists())
					Ftemp.delete();
			}

		}
	}

}