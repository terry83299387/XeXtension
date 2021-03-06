/**
 * 
 */
package xextension;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import xextension.autoupdate.AutoUpdater;
import xextension.global.ConfigHelper;
import xextension.global.Configurations;
import xextension.global.UIManager;
import xextension.operation.VersionInfo;
import xextension.service.XEService;

/**
 * @author QiaoMingkui
 *
 */
public class Main {
	private static final Logger	logger = LogManager.getLogger(Main.class);

	private static final String	EQUAL = "=";
	private static final String	SERVER_URL_PART = "/?";
	private static final String	SERVER_URL_PREFIX = "https://localhost:";
	private static final String	AUTOUPDATE_FALSE = "autoupdate=false";
	private static final String	AUTOUPDATE_TRUE = "autoupdate=true";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			boolean autoUpdate = false;
			if (args.length > 0 && AUTOUPDATE_TRUE.equalsIgnoreCase(args[0])) {
				autoUpdate = true;
			} else if (args.length > 0 && AUTOUPDATE_FALSE.equalsIgnoreCase(args[0])) {
				;
			} else if ("true".equals(ConfigHelper.getProperty(Configurations.CUSTOMER_AUTOUPDATE))) {
				autoUpdate = true;
			}

			if (autoUpdate) {
				String oldVer = ConfigHelper.getProperty(Configurations.CUSTOMER_VERSION);
				AutoUpdater autoUpdater = new AutoUpdater();
				autoUpdater.autoUpdate();
				String newVer = ConfigHelper.getProperty(Configurations.CUSTOMER_VERSION);
				if (!oldVer.equals(newVer)/* && Native.isAvailable()*/) {
//					Native.shellExecute(Native.SHELLEXECUTE_OPEN, "XEService.exe", null, null, Native.SW_NORMAL);
					// jsmooth.Native is invalid (don't know why),
					// so I use another approach to rerun application after updating
					Runtime.getRuntime().exec(Configurations.EXE_FILE_NAME + " " + AUTOUPDATE_FALSE);
					logger.info("update completed, restart now");
					System.exit(0);
				}
			}
		} catch (Exception e) {
			logger.warn("Update failed, an error occurs while trying to update", e);
		}

		// keep going no matter how update succeeds or not

		// check if service already started
		checkServiceAlreadyStarted();

		UIManager.initLookAndFeel();

		try {
			new XEService();
		} catch (Exception e) {
			logger.error("XeXtension service startup failed: ", e);
		}
	}

	private static void checkServiceAlreadyStarted() {
		HttpURLConnection connection = null;
		BufferedReader reader = null;
		JSONObject json;
		String line;
		String name;
		for (int p : Configurations.CANDIDATE_PORTS) {
			try {
				connection = (HttpURLConnection) new URL(SERVER_URL_PREFIX + p + SERVER_URL_PART
						+ Configurations.REQUEST_OPERATOR + EQUAL + Configurations.VERSION_INFO
					).openConnection();
				connection.connect();
		        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		        line = reader.readLine();
			} catch (IOException e) {
				continue;
			} finally {
        try {
        	if (reader != null) {
        		reader.close();
        		reader = null;
        	}
        	if (connection != null) {
        		connection.disconnect();
        		connection = null;
        	}
				} catch (IOException e) {
				}
			}

			try {
        json = new JSONObject(line);
        if (json != null) {
        	json = json.optJSONObject(Configurations.RESPONSE_EXTRA_DATA);
        }
      	if (json != null) {
        	name = json.optString(VersionInfo.NAME);
	        if (Configurations.NAME.equals(name)) {
	        	logger.error("Startup failed, there already has a running service on port " + p);
	        	System.exit(1);
	        }
      	}
			} catch (JSONException e) {
				/* ignore */
			}

		} // end for loop
	}

}
